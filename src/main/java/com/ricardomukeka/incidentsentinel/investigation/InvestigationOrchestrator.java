package com.ricardomukeka.incidentsentinel.investigation;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricardomukeka.incidentsentinel.config.properties.InvestigationProperties;
import com.ricardomukeka.incidentsentinel.dto.EvidenceBundle;
import com.ricardomukeka.incidentsentinel.dto.LogFacts;
import com.ricardomukeka.incidentsentinel.dto.PlatformFacts;
import com.ricardomukeka.incidentsentinel.dto.TraceFacts;
import com.ricardomukeka.incidentsentinel.enums.AgenteLlm;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.exception.LlmInvocationException;
import com.ricardomukeka.incidentsentinel.investigation.prompt.InvestigationPromptBuilder;
import com.ricardomukeka.incidentsentinel.investigation.retriever.GcpLogRetriever;
import com.ricardomukeka.incidentsentinel.investigation.retriever.SignozTraceRetriever;
import com.ricardomukeka.incidentsentinel.investigation.retriever.TsuruDiagnoseRetriever;
import com.ricardomukeka.incidentsentinel.ticket.GeneratedTicketFields;
import com.ricardomukeka.incidentsentinel.ticket.TicketGenerationService;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Orquestra a investigação (docs/ARCHITECTURE.md):
 * <ol>
 * <li>3 retrievers determinísticos EM PARALELO (virtual threads), cada um com
 * timeout próprio ({@code retriever-timeout}) — sem ele um subprocesso MCP
 * travado travaria a investigação inteira. Falha/timeout isolado →
 * {@code fontes_consultadas}.</li>
 * <li>agrega num {@link EvidenceBundle} destilado; aplica hard cap no prompt
 * ({@code prompt-max-chars}) como rede de segurança;</li>
 * <li>UMA chamada de LLM, SEM tools;</li>
 * <li>gate: {@code confianca_causa == BAIXA} → {@code CAUSA_INCONCLUSIVA}
 * (em {@link TicketGenerationService});</li>
 * <li>registra a chamada (tokens + custo) via {@link LlmCallRecorder}.</li>
 * </ol>
 * Falha transitória do LLM ({@link LlmInvocationException}) NÃO é convertida
 * em desfecho — propaga pra o {@code NovoErroSub} nack'ar e deixar o Pub/Sub
 * retentar; falha de parsing vira {@code INVESTIGACAO_FALHOU_PARSING}.
 */
@Component
public class InvestigationOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(InvestigationOrchestrator.class);

	private final SignozTraceRetriever signozTraceRetriever;
	private final GcpLogRetriever gcpLogRetriever;
	private final TsuruDiagnoseRetriever tsuruDiagnoseRetriever;
	private final ChatClientInvestigationCaller chatClientInvestigationCaller;
	private final TicketGenerationService ticketGenerationService;
	private final LlmCallRecorder llmCallRecorder;
	private final RedactionService redactionService;
	private final InvestigationProperties investigationProperties;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;

	public InvestigationOrchestrator(SignozTraceRetriever signozTraceRetriever, GcpLogRetriever gcpLogRetriever,
			TsuruDiagnoseRetriever tsuruDiagnoseRetriever,
			ChatClientInvestigationCaller chatClientInvestigationCaller,
			TicketGenerationService ticketGenerationService, LlmCallRecorder llmCallRecorder,
			RedactionService redactionService, InvestigationProperties investigationProperties,
			ObjectMapper objectMapper, MeterRegistry meterRegistry) {
		this.signozTraceRetriever = signozTraceRetriever;
		this.gcpLogRetriever = gcpLogRetriever;
		this.tsuruDiagnoseRetriever = tsuruDiagnoseRetriever;
		this.chatClientInvestigationCaller = chatClientInvestigationCaller;
		this.ticketGenerationService = ticketGenerationService;
		this.llmCallRecorder = llmCallRecorder;
		this.redactionService = redactionService;
		this.investigationProperties = investigationProperties;
		this.objectMapper = objectMapper;
		this.meterRegistry = meterRegistry;
	}

	public InvestigationOutcome investigar(Long ticketId, String app, TipoErro tipoErro, String chaveDedup,
			String traceId, Instant ocorrenciaTs) {
		MDC.put("ticketId", String.valueOf(ticketId));
		MDC.put("app", app);
		MDC.put("tipoErro", tipoErro.name());
		try {
			EvidenceBundle evidencia = redigirEvidencia(coletarEvidencia(app, traceId, ocorrenciaTs));
			String userPrompt = limitarTamanho(
					InvestigationPromptBuilder.userPrompt(app, tipoErro, chaveDedup, evidencia));

			GeneratedTicketFields campos;
			try {
				LlmCallResult chamada = chatClientInvestigationCaller.gerar(
						InvestigationPromptBuilder.systemPrompt(), userPrompt);
				llmCallRecorder.registrar(ticketId, AgenteLlm.INVESTIGADOR, chamada);
				campos = ticketGenerationService.fromResultado(chamada.resultado());
			} catch (LlmInvocationException infra) {
				// Transitório / CB aberto — NÃO vira desfecho terminal. Propaga pra nack + redelivery
				// (Pub/Sub retenta com backoff; a guarda de re-pagamento no NovoErroSub cobre o caso
				// de uma tentativa anterior já ter pago).
				throw infra;
			} catch (RuntimeException parsing) {
				log.warn("Falha ao parsear a saída do modelo pra {}/{}: {}", app, tipoErro, parsing.getMessage());
				campos = ticketGenerationService.falhaParsing();
			}

			return new InvestigationOutcome(campos.status(), campos.causaRaiz(), campos.causaCategoria(),
					campos.confiancaCausa(), campos.solucaoProposta(), campos.confiancaSolucao(),
					escreverJson(evidencia.fontesConsultadas()));
		} finally {
			MDC.remove("ticketId");
			MDC.remove("app");
			MDC.remove("tipoErro");
		}
	}

	private EvidenceBundle coletarEvidencia(String app, String traceId, Instant ocorrenciaTs) {
		Map<String, String> fontes = new ConcurrentHashMap<>();
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			CompletableFuture<TraceFacts> traceFut = supervisionar(executor, fontes, "signoz_trace", traceId != null,
					() -> signozTraceRetriever.buscar(traceId, ocorrenciaTs),
					() -> TraceFacts.vazio("sem trace_id (ex. latência anômala)"));
			CompletableFuture<LogFacts> logFut = supervisionar(executor, fontes, "gcp_logs", traceId != null,
					() -> gcpLogRetriever.buscar(traceId, ocorrenciaTs),
					() -> LogFacts.vazio("sem trace_id (ex. latência anômala)"));
			CompletableFuture<PlatformFacts> platFut = supervisionar(executor, fontes, "tsuru_diagnose", true,
					() -> tsuruDiagnoseRetriever.buscar(app), () -> PlatformFacts.vazio("indisponível"));

			return new EvidenceBundle(traceFut.join(), logFut.join(), platFut.join(), fontes);
		}
	}

	private <T> CompletableFuture<T> supervisionar(ExecutorService executor, Map<String, String> fontes, String fonte,
			boolean aplicavel, Supplier<T> chamada, Supplier<T> vazio) {
		if (!aplicavel) {
			marcar(fontes, fonte, "pulada");
			return CompletableFuture.completedFuture(vazio.get());
		}
		return CompletableFuture.supplyAsync(chamada, executor)
				.orTimeout(investigationProperties.retrieverTimeout().toMillis(), TimeUnit.MILLISECONDS)
				.handle((valor, erro) -> {
					if (erro == null) {
						marcar(fontes, fonte, "consultada");
						return valor;
					}
					Throwable causa = erro instanceof CompletionException ce ? ce.getCause() : erro;
					boolean timeout = causa instanceof TimeoutException;
					log.warn("Retriever {} {}: {}", fonte, timeout ? "estourou o timeout" : "falhou",
							causa == null ? "" : causa.toString());
					marcar(fontes, fonte, timeout ? "timeout" : "indisponivel");
					return vazio.get();
				});
	}

	private void marcar(Map<String, String> fontes, String fonte, String outcome) {
		fontes.put(fonte, outcome);
		meterRegistry.counter("incidentsentinel.investigation.retriever", "retriever", fonte, "outcome", outcome)
				.increment();
	}

	/**
	 * Redige segredo na evidência destilada ANTES de montar o prompt (docs/ARCHITECTURE.md
	 * §6.4) — log de app irmão pode carregar token/senha/connection string, e sem
	 * isso o valor cru iria pra Vertex AI. Se algo foi redigido, incrementa
	 * {@code incidentsentinel.investigation.prompt.redacted} (sinal operacional: um app
	 * monitorado está logando segredo).
	 */
	private EvidenceBundle redigirEvidencia(EvidenceBundle e) {
		boolean[] alterou = { false };
		TraceFacts t = e.trace();
		TraceFacts trace = t == null ? null
				: new TraceFacts(red(t.resumo(), alterou), t.servicoQueFalhou(), red(t.tipoExcecao(), alterou));
		LogFacts l = e.logs();
		LogFacts logs = l == null ? null : new LogFacts(red(l.resumo(), alterou), l.totalLinhas());
		PlatformFacts p = e.platform();
		PlatformFacts platform = p == null ? null : new PlatformFacts(red(p.resumo(), alterou));

		if (alterou[0]) {
			meterRegistry.counter("incidentsentinel.investigation.prompt.redacted").increment();
			log.warn("Segredo redigido na evidência antes do prompt — app monitorado logando credencial?");
		}
		return new EvidenceBundle(trace, logs, platform, e.fontesConsultadas());
	}

	private String red(String valor, boolean[] alterou) {
		String redigido = redactionService.redigir(valor);
		if (redigido != null && !redigido.equals(valor)) {
			alterou[0] = true;
		}
		return redigido;
	}

	private String limitarTamanho(String prompt) {
		int max = investigationProperties.promptMaxChars();
		if (max > 0 && prompt.length() > max) {
			meterRegistry.counter("incidentsentinel.investigation.prompt.truncated").increment();
			log.warn("Prompt da investigação truncado: {} chars > teto de {}", prompt.length(), max);
			return prompt.substring(0, max) + "\n[...prompt truncado no teto de segurança...]";
		}
		return prompt;
	}

	private String escreverJson(Map<String, String> fontesConsultadas) {
		try {
			return objectMapper.writeValueAsString(fontesConsultadas);
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}
}
