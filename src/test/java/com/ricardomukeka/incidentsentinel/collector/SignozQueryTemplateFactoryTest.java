package com.ricardomukeka.incidentsentinel.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.ricardomukeka.incidentsentinel.dto.signoz.GroupByField;
import com.ricardomukeka.incidentsentinel.dto.signoz.QueryRangeRequest;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;

class SignozQueryTemplateFactoryTest {

	private final Instant desde = Instant.parse("2026-08-20T11:55:00Z");
	private final Instant ate = Instant.parse("2026-08-20T12:00:00Z");

	@ParameterizedTest
	@EnumSource(TipoErro.class)
	void build_geraRequestComJanelaEServiceNameCorretos(TipoErro tipoErro) {
		QueryRangeRequest request = SignozQueryTemplateFactory.build(tipoErro, "service-orders", desde, ate);

		assertThat(request.start()).isEqualTo(desde.toEpochMilli());
		assertThat(request.end()).isEqualTo(ate.toEpochMilli());
		assertThat(request.compositeQuery().queries()).hasSize(1);
		assertThat(request.compositeQuery().queries().getFirst().spec().filter().expression())
				.contains("service-orders");
	}

	@ParameterizedTest
	@EnumSource(value = TipoErro.class, names = { "HTTP_5XX", "TIMEOUT", "ERRO_DEPENDENCIA_DOWNSTREAM",
			"FALHA_PROCESSAMENTO_ASSINCRONO" })
	void build_tiposDeEventoDiscreto_usaRequestTypeRaw(TipoErro tipoErro) {
		QueryRangeRequest request = SignozQueryTemplateFactory.build(tipoErro, "service-orders", desde, ate);

		assertThat(request.requestType()).isEqualTo("raw");
	}

	@org.junit.jupiter.api.Test
	void build_latenciaAnomala_usaRequestTypeTimeSeriesComAggregation() {
		QueryRangeRequest request = SignozQueryTemplateFactory.build(TipoErro.LATENCIA_ANOMALA, "service-orders",
				desde, ate);

		var spec = request.compositeQuery().queries().getFirst().spec();
		assertThat(request.requestType()).isEqualTo("time_series");
		assertThat(spec.aggregations()).isNotNull().isNotEmpty();
		assertThat(spec.filter().expression()).contains("parent_span_id = ''");
	}

	@org.junit.jupiter.api.Test
	void atributoExtraFieldName_soDownstreamEAssincronoTemAtributoExtra() {
		assertThat(SignozQueryTemplateFactory.atributoExtraFieldName(TipoErro.ERRO_DEPENDENCIA_DOWNSTREAM))
				.isEqualTo("peer.service");
		assertThat(SignozQueryTemplateFactory.atributoExtraFieldName(TipoErro.FALHA_PROCESSAMENTO_ASSINCRONO))
				.isEqualTo("messaging.destination.name");
		assertThat(SignozQueryTemplateFactory.atributoExtraFieldName(TipoErro.HTTP_5XX)).isNull();
		assertThat(SignozQueryTemplateFactory.atributoExtraFieldName(TipoErro.TIMEOUT)).isNull();
		assertThat(SignozQueryTemplateFactory.atributoExtraFieldName(TipoErro.LATENCIA_ANOMALA)).isNull();
	}

	@ParameterizedTest
	@EnumSource(value = TipoErro.class, names = { "HTTP_5XX", "TIMEOUT", "ERRO_DEPENDENCIA_DOWNSTREAM",
			"FALHA_PROCESSAMENTO_ASSINCRONO" })
	void traceIdFieldName_todosOsTiposDeEventoDiscretoTemTraceId(TipoErro tipoErro) {
		assertThat(SignozQueryTemplateFactory.traceIdFieldName(tipoErro)).isEqualTo("traceID");
	}

	@org.junit.jupiter.api.Test
	void traceIdFieldName_latenciaAnomalaNaoTemTraceId() {
		assertThat(SignozQueryTemplateFactory.traceIdFieldName(TipoErro.LATENCIA_ANOMALA)).isNull();
	}

	@ParameterizedTest
	@EnumSource(value = TipoErro.class, names = { "HTTP_5XX", "TIMEOUT", "ERRO_DEPENDENCIA_DOWNSTREAM",
			"FALHA_PROCESSAMENTO_ASSINCRONO" })
	void build_tiposDeEventoDiscreto_selectFieldsIncluiTraceId(TipoErro tipoErro) {
		QueryRangeRequest request = SignozQueryTemplateFactory.build(tipoErro, "service-orders", desde, ate);

		assertThat(request.compositeQuery().queries().getFirst().spec().selectFields())
				.extracting(GroupByField::name)
				.contains("traceID");
	}

	@org.junit.jupiter.api.Test
	void build_latenciaAnomala_naoTemSelectFields() {
		QueryRangeRequest request = SignozQueryTemplateFactory.build(TipoErro.LATENCIA_ANOMALA, "service-orders",
				desde, ate);

		assertThat(request.compositeQuery().queries().getFirst().spec().selectFields()).isNull();
	}
}
