package com.ricardomukeka.incidentsentinel.investigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricardomukeka.incidentsentinel.dto.CausaSolucaoResult;
import com.ricardomukeka.incidentsentinel.dto.LogFacts;
import com.ricardomukeka.incidentsentinel.dto.PlatformFacts;
import com.ricardomukeka.incidentsentinel.dto.TraceFacts;
import com.ricardomukeka.incidentsentinel.enums.AgenteLlm;
import com.ricardomukeka.incidentsentinel.enums.CausaCategoria;
import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.exception.McpToolException;
import com.ricardomukeka.incidentsentinel.investigation.retriever.GcpLogRetriever;
import com.ricardomukeka.incidentsentinel.investigation.retriever.SignozTraceRetriever;
import com.ricardomukeka.incidentsentinel.investigation.retriever.TsuruDiagnoseRetriever;
import com.ricardomukeka.incidentsentinel.ticket.GeneratedTicketFields;
import com.ricardomukeka.incidentsentinel.ticket.TicketGenerationService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class InvestigationOrchestratorTest {

	@Mock
	private SignozTraceRetriever signozTraceRetriever;
	@Mock
	private GcpLogRetriever gcpLogRetriever;
	@Mock
	private TsuruDiagnoseRetriever tsuruDiagnoseRetriever;
	@Mock
	private ChatClientInvestigationCaller chatClientInvestigationCaller;
	@Mock
	private TicketGenerationService ticketGenerationService;
	@Mock
	private LlmCallRecorder llmCallRecorder;

	private InvestigationOrchestrator orchestrator;

	private static final String DEDUP = "x".repeat(64);
	private static final Instant TS = Instant.parse("2026-08-31T12:00:00Z");

	@BeforeEach
	void setUp() {
		var props = new com.ricardomukeka.incidentsentinel.config.properties.InvestigationProperties(25,
				new java.math.BigDecimal("999"), true, 40, 50, java.time.Duration.ofMinutes(15), 20000,
				java.time.Duration.ofSeconds(25), java.time.Duration.ofSeconds(60));
		orchestrator = new InvestigationOrchestrator(signozTraceRetriever, gcpLogRetriever, tsuruDiagnoseRetriever,
				chatClientInvestigationCaller, ticketGenerationService, llmCallRecorder, new RedactionService(), props,
				new ObjectMapper(), new SimpleMeterRegistry());
	}

	private LlmCallResult llmResult() {
		return new LlmCallResult(
				new CausaSolucaoResult("causa", CausaCategoria.INFRA_REDE, ConfiancaDiagnostico.ALTA, "sol",
						ConfiancaDiagnostico.MEDIA),
				"gemini-2.0-flash", 100, 50, 150);
	}

	@Test
	void investigar_caminhoFeliz_3retrieversConsultadosEUmaChamadaDeLlm() {
		when(signozTraceRetriever.buscar(eq("trace-abc"), any())).thenReturn(new TraceFacts("t", "svc", "NPE"));
		when(gcpLogRetriever.buscar(eq("trace-abc"), any())).thenReturn(new LogFacts("l", 3));
		when(tsuruDiagnoseRetriever.buscar("service-orders")).thenReturn(new PlatformFacts("p"));
		LlmCallResult chamada = llmResult();
		when(chatClientInvestigationCaller.gerar(anyString(), anyString())).thenReturn(chamada);
		when(ticketGenerationService.fromResultado(chamada.resultado())).thenReturn(new GeneratedTicketFields(
				TicketStatus.ABERTO, "causa", CausaCategoria.INFRA_REDE, ConfiancaDiagnostico.ALTA, "sol",
				ConfiancaDiagnostico.MEDIA));

		InvestigationOutcome outcome = orchestrator.investigar(1L, "service-orders", TipoErro.HTTP_5XX, DEDUP,
				"trace-abc", TS);

		assertThat(outcome.status()).isEqualTo(TicketStatus.ABERTO);
		assertThat(outcome.solucaoProposta()).isEqualTo("sol");
		assertThat(outcome.fontesConsultadas()).contains("\"signoz_trace\":\"consultada\"")
				.contains("\"gcp_logs\":\"consultada\"").contains("\"tsuru_diagnose\":\"consultada\"");
		verify(llmCallRecorder).registrar(1L, AgenteLlm.INVESTIGADOR, chamada);
	}

	@Test
	void investigar_semTraceId_traceELogsPulados_diagnoseAppRoda() {
		when(tsuruDiagnoseRetriever.buscar(any())).thenReturn(new PlatformFacts("p"));
		LlmCallResult chamada = llmResult();
		when(chatClientInvestigationCaller.gerar(anyString(), anyString())).thenReturn(chamada);
		when(ticketGenerationService.fromResultado(any())).thenReturn(new GeneratedTicketFields(TicketStatus.ABERTO,
				"causa", CausaCategoria.INFRA_RECURSO, ConfiancaDiagnostico.ALTA, "sol", ConfiancaDiagnostico.ALTA));

		InvestigationOutcome outcome = orchestrator.investigar(1L, "service-orders", TipoErro.LATENCIA_ANOMALA,
				DEDUP, null, TS);

		assertThat(outcome.fontesConsultadas()).contains("\"signoz_trace\":\"pulada\"")
				.contains("\"gcp_logs\":\"pulada\"").contains("\"tsuru_diagnose\":\"consultada\"");
		verify(signozTraceRetriever, never()).buscar(any(), any());
		verify(gcpLogRetriever, never()).buscar(any(), any());
	}

	@Test
	void investigar_retrieverFalha_marcaIndisponivelEProsseguePraLlm() {
		when(signozTraceRetriever.buscar(any(), any())).thenThrow(new McpToolException("circuito aberto", null));
		when(gcpLogRetriever.buscar(any(), any())).thenReturn(new LogFacts("l", 1));
		when(tsuruDiagnoseRetriever.buscar(any())).thenReturn(new PlatformFacts("p"));
		LlmCallResult chamada = llmResult();
		when(chatClientInvestigationCaller.gerar(anyString(), anyString())).thenReturn(chamada);
		when(ticketGenerationService.fromResultado(any())).thenReturn(new GeneratedTicketFields(TicketStatus.ABERTO,
				"causa", CausaCategoria.INFRA_REDE, ConfiancaDiagnostico.MEDIA, "sol", ConfiancaDiagnostico.MEDIA));

		InvestigationOutcome outcome = orchestrator.investigar(1L, "service-orders", TipoErro.HTTP_5XX, DEDUP,
				"trace-abc", TS);

		assertThat(outcome.status()).isEqualTo(TicketStatus.ABERTO);
		assertThat(outcome.fontesConsultadas()).contains("\"signoz_trace\":\"indisponivel\"");
	}

	@Test
	void investigar_falhaNaChamadaAoLlm_delegaParaFalhaParsing_semRegistrarCusto() {
		when(tsuruDiagnoseRetriever.buscar(any())).thenReturn(new PlatformFacts("p"));
		when(chatClientInvestigationCaller.gerar(anyString(), anyString()))
				.thenThrow(new RuntimeException("json malformado"));
		when(ticketGenerationService.falhaParsing()).thenReturn(new GeneratedTicketFields(
				TicketStatus.INVESTIGACAO_FALHOU_PARSING, null, null, null, null, null));

		InvestigationOutcome outcome = orchestrator.investigar(1L, "service-orders", TipoErro.HTTP_5XX, DEDUP, null,
				TS);

		assertThat(outcome.status()).isEqualTo(TicketStatus.INVESTIGACAO_FALHOU_PARSING);
		verify(llmCallRecorder, never()).registrar(any(), any(), any());
	}

	@Test
	void investigar_llmInvocationException_propaga_naoViraFalhaParsing() {
		when(tsuruDiagnoseRetriever.buscar(any())).thenReturn(new PlatformFacts("p"));
		when(chatClientInvestigationCaller.gerar(anyString(), anyString()))
				.thenThrow(new com.ricardomukeka.incidentsentinel.exception.LlmInvocationException("CB aberto", null));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> orchestrator.investigar(1L, "service-orders",
				TipoErro.HTTP_5XX, DEDUP, null, TS))
				.isInstanceOf(com.ricardomukeka.incidentsentinel.exception.LlmInvocationException.class);
		verify(ticketGenerationService, never()).falhaParsing();
		verify(llmCallRecorder, never()).registrar(any(), any(), any());
	}

	@Test
	void investigar_gateCausaInconclusiva_propagaStatus() {
		when(tsuruDiagnoseRetriever.buscar(any())).thenReturn(new PlatformFacts("p"));
		LlmCallResult chamada = llmResult();
		when(chatClientInvestigationCaller.gerar(anyString(), anyString())).thenReturn(chamada);
		when(ticketGenerationService.fromResultado(any())).thenReturn(new GeneratedTicketFields(
				TicketStatus.CAUSA_INCONCLUSIVA, "causa incerta", CausaCategoria.INDETERMINADA,
				ConfiancaDiagnostico.BAIXA, null, null));

		InvestigationOutcome outcome = orchestrator.investigar(1L, "service-orders", TipoErro.HTTP_5XX, DEDUP, null,
				TS);

		assertThat(outcome.status()).isEqualTo(TicketStatus.CAUSA_INCONCLUSIVA);
		assertThat(outcome.solucaoProposta()).isNull();
	}
}
