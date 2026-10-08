package com.ricardomukeka.incidentsentinel.collector;

import java.time.Instant;
import java.util.List;

import com.ricardomukeka.incidentsentinel.dto.signoz.Aggregation;
import com.ricardomukeka.incidentsentinel.dto.signoz.BuilderQuery;
import com.ricardomukeka.incidentsentinel.dto.signoz.BuilderQuerySpec;
import com.ricardomukeka.incidentsentinel.dto.signoz.CompositeQuery;
import com.ricardomukeka.incidentsentinel.dto.signoz.GroupByField;
import com.ricardomukeka.incidentsentinel.dto.signoz.OrderBy;
import com.ricardomukeka.incidentsentinel.dto.signoz.QueryRangeRequest;
import com.ricardomukeka.incidentsentinel.dto.signoz.SignozFilter;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;

/**
 * Os 5 query templates (docs/ARCHITECTURE.md), parametrizados só por service.name.
 *
 * <p><b>Nível de confiança por campo/expressão (documentado em 2026-08-20,
 * ao implementar — a API pública do SigNoz não publica um schema de resposta
 * nem uma referência completa de nomes de atributo intrínseco):</b>
 * <ul>
 * <li>Alta confiança (confirmado em docs.spring.io/exemplos oficiais do
 * SigNoz): endpoint {@code POST /api/v5/query_range}, prefixo
 * {@code resource.} pra atributos de resource em expressão de filtro,
 * {@code kind_string} ('Server'/'Client'/'Consumer'), {@code has_error},
 * {@code duration_nano}, {@code parent_span_id = ''} pra span raiz,
 * {@code response_status_code}.</li>
 * <li>Baixa confiança, precisa validar contra STG antes de confiar (docs/ARCHITECTURE.md,
 * verificação end-to-end, passo 3): expressão exata do timeout (não há
 * atributo padrão documentado publicamente — usamos {@code status_message}
 * como proxy), nome do atributo de tópico assíncrono ({@code messaging.destination.name},
 * convenção semântica OTel mas não confirmado no schema real do SigNoz), nome
 * da função de agregação de percentil no builder ({@code p99(...)}) e se
 * {@code fieldContext: "attribute"} é o valor certo pra atributos de span
 * customizados (peer.service, tópico) nos arrays selectFields/groupBy — só
 * vi exemplos confirmados com "resource" e "span".</li>
 * </ul>
 */
public final class SignozQueryTemplateFactory {

	/** Pacote-privado de propósito — SignozErrorSignalSource usa pra detectar truncamento (ver 6.4/§4). */
	static final int LIMITE_RESULTADOS_POR_CICLO = 500;

	private SignozQueryTemplateFactory() {
	}

	public static QueryRangeRequest build(TipoErro tipoErro, String serviceName, Instant desde, Instant ate) {
		validarServiceName(serviceName);
		String atributoExtra = atributoExtraFieldName(tipoErro);
		return switch (tipoErro) {
			case HTTP_5XX -> eventoDiscreto(desde, ate,
					"resource.service.name = '%s' AND kind_string = 'Server' AND response_status_code >= 500"
							.formatted(serviceName),
					atributoExtra);
			case TIMEOUT -> eventoDiscreto(desde, ate,
					"resource.service.name = '%s' AND has_error = true AND status_message ILIKE '%%timeout%%'"
							.formatted(serviceName),
					atributoExtra);
			case ERRO_DEPENDENCIA_DOWNSTREAM -> eventoDiscreto(desde, ate,
					"resource.service.name = '%s' AND kind_string = 'Client' AND has_error = true"
							.formatted(serviceName),
					atributoExtra);
			case FALHA_PROCESSAMENTO_ASSINCRONO -> eventoDiscreto(desde, ate,
					"resource.service.name = '%s' AND kind_string = 'Consumer' AND has_error = true"
							.formatted(serviceName),
					atributoExtra);
			case LATENCIA_ANOMALA -> latenciaAnomala(serviceName, desde, ate);
		};
	}

	/**
	 * Falha rápido e alto em vez de gerar uma expressão de filtro quebrada
	 * (achado de revisão, 2026-08-21) — as expressões abaixo interpolam
	 * {@code serviceName} sem escapar aspas simples; um valor com aspas
	 * simples (erro de digitação no cadastro manual de {@code app_registry})
	 * quebraria a sintaxe do filtro no SigNoz de forma silenciosa e
	 * recorrente (warning por ciclo, a cada 5 min, sem indicar a causa). Não
	 * sabemos a convenção de escape exata do SigNoz pra essa expressão
	 * (schema não documentado publicamente) — rejeitar aqui, alto e cedo, é
	 * mais seguro que arriscar um escape errado.
	 */
	private static void validarServiceName(String serviceName) {
		if (serviceName.contains("'")) {
			throw new IllegalArgumentException(
					"service_name contém aspas simples, incompatível com a expressão de filtro do SigNoz: "
							+ serviceName);
		}
	}

	/**
	 * Nome do atributo extra da chave de dedup por tipo (docs/ARCHITECTURE.md) —
	 * fonte única, usado tanto pra montar o selectFields/groupBy da query
	 * quanto pra extrair o valor da resposta em SignozResponseMapper.
	 */
	public static String atributoExtraFieldName(TipoErro tipoErro) {
		return switch (tipoErro) {
			case ERRO_DEPENDENCIA_DOWNSTREAM -> "peer.service";
			case FALHA_PROCESSAMENTO_ASSINCRONO -> "messaging.destination.name";
			case HTTP_5XX, TIMEOUT, LATENCIA_ANOMALA -> null;
		};
	}

	/**
	 * Nome do atributo de trace na resposta do SigNoz, usado pra propagar
	 * {@code trace_id} até o ticket (docs/ARCHITECTURE.md) — consultado
	 * deterministicamente na investigação via SignozTraceRetriever (REST).
	 * {@code TRACE_ID_ATTRIBUTE} tem o MESMO grau de confiança baixa
	 * documentado no topo desta classe pra status_message/messaging.destination.name:
	 * é a convenção histórica do SigNoz/ClickHouse ("traceID"), não confirmada
	 * contra uma resposta real de STG. {@code LATENCIA_ANOMALA} não tem um
	 * span causador único (é agregação p99 sobre uma janela) — fica sempre
	 * {@code null} de propósito, não é uma lacuna.
	 */
	private static final String TRACE_ID_ATTRIBUTE = "traceID";

	public static String traceIdFieldName(TipoErro tipoErro) {
		return switch (tipoErro) {
			case HTTP_5XX, TIMEOUT, ERRO_DEPENDENCIA_DOWNSTREAM, FALHA_PROCESSAMENTO_ASSINCRONO -> TRACE_ID_ATTRIBUTE;
			case LATENCIA_ANOMALA -> null;
		};
	}

	private static QueryRangeRequest eventoDiscreto(Instant desde, Instant ate,
			String filterExpression, String atributoExtraFieldName) {
		// Todo chamador deste método é um dos 4 tipos de evento discreto — traceIdFieldName()
		// nunca retorna null aqui (só LATENCIA_ANOMALA, que não passa por eventoDiscreto).
		List<GroupByField> selectFields = atributoExtraFieldName == null
				? List.of(GroupByField.of("timestamp"), GroupByField.of(TRACE_ID_ATTRIBUTE))
				: List.of(GroupByField.of("timestamp"), new GroupByField(atributoExtraFieldName, "attribute"),
						GroupByField.of(TRACE_ID_ATTRIBUTE));

		BuilderQuerySpec spec = new BuilderQuerySpec(
				"A",
				"traces",
				null,
				null,
				new SignozFilter(filterExpression),
				selectFields,
				null,
				List.of(OrderBy.desc("timestamp")),
				LIMITE_RESULTADOS_POR_CICLO,
				0,
				false);

		return new QueryRangeRequest(desde.toEpochMilli(), ate.toEpochMilli(), "raw",
				CompositeQuery.of(BuilderQuery.of(spec)));
	}

	/**
	 * Template do trace lookup da investigação (docs/ARCHITECTURE.md) —
	 * mesmo endpoint {@code /api/v5/query_range}, filtro {@code trace_id = '<id>'},
	 * {@code raw}, sem ordenação (a hierarquia é reconstruída por
	 * {@code parent_span_id} no SignozTraceResponseMapper, não pela ordem de
	 * retorno). {@code LIMITE_SPANS_POR_TRACE} = 1000, mesmo hardcode do
	 * {@code signoz-mcp-server} (docs/ARCHITECTURE.md) — trace com mais spans é
	 * truncado, aceito.
	 *
	 * <p>Os {@code selectFields} são os campos intrínsecos de span que a
	 * análise de causa raiz precisa. A semântica exata de {@code fieldContext}
	 * pra atributo de span custom em v5 não é confirmada (ver javadoc da
	 * classe) — usados sem {@code fieldContext} (via {@code GroupByField.of}),
	 * confirmar contra STG.
	 */
	static final int LIMITE_SPANS_POR_TRACE = 1000;

	public static QueryRangeRequest buildTraceById(String traceId, Instant desde, Instant ate) {
		if (traceId != null && traceId.contains("'")) {
			throw new IllegalArgumentException("trace_id contém aspas simples, incompatível com o filtro do SigNoz");
		}
		BuilderQuerySpec spec = new BuilderQuerySpec(
				"A",
				"traces",
				null,
				null,
				new SignozFilter("trace_id = '%s'".formatted(traceId)),
				List.of(GroupByField.of("timestamp"), GroupByField.of("span_id"), GroupByField.of("parent_span_id"),
						GroupByField.of("name"), GroupByField.of("resource.service.name"),
						GroupByField.of("kind_string"), GroupByField.of("duration_nano"), GroupByField.of("has_error"),
						GroupByField.of("status_message"), GroupByField.of("response_status_code")),
				null,
				List.of(OrderBy.desc("timestamp")),
				LIMITE_SPANS_POR_TRACE,
				0,
				false);
		return new QueryRangeRequest(desde.toEpochMilli(), ate.toEpochMilli(), "raw",
				CompositeQuery.of(BuilderQuery.of(spec)));
	}

	private static QueryRangeRequest latenciaAnomala(String serviceName, Instant desde, Instant ate) {
		long stepIntervalSegundos = Math.max(1, (ate.toEpochMilli() - desde.toEpochMilli()) / 1000);

		BuilderQuerySpec spec = new BuilderQuerySpec(
				"A",
				"traces",
				stepIntervalSegundos,
				List.of(new Aggregation("p99(duration_nano)", "p99_duration_nano")),
				new SignozFilter("resource.service.name = '%s' AND parent_span_id = ''".formatted(serviceName)),
				null,
				null,
				null,
				null,
				null,
				false);

		return new QueryRangeRequest(desde.toEpochMilli(), ate.toEpochMilli(), "time_series",
				CompositeQuery.of(BuilderQuery.of(spec)));
	}
}
