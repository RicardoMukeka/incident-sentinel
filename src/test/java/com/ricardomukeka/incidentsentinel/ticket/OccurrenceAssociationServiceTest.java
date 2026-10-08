package com.ricardomukeka.incidentsentinel.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ricardomukeka.incidentsentinel.dto.ErrorSignalDto;
import com.ricardomukeka.incidentsentinel.enums.SinalOutcome;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.model.ErrorSignalLog;
import com.ricardomukeka.incidentsentinel.repository.ErrorSignalLogRepository;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;
import com.ricardomukeka.incidentsentinel.triage.DedupKeyBuilder;

@ExtendWith(MockitoExtension.class)
class OccurrenceAssociationServiceTest {

	@Mock
	private TicketRepository ticketRepository;

	@Mock
	private ErrorSignalLogRepository errorSignalLogRepository;

	private OccurrenceAssociationService service;

	private final ErrorSignalDto sinal = new ErrorSignalDto("service-orders", TipoErro.HTTP_5XX,
			Instant.parse("2026-08-20T12:00:00Z"), null, "trace-abc-123");

	@BeforeEach
	void setUp() {
		service = new OccurrenceAssociationService(ticketRepository, errorSignalLogRepository);
	}

	@Test
	void associar_affectedRowsUm_devolveNovoTicketComId() {
		when(ticketRepository.upsertOcorrencia(anyString(), anyString(), anyString(), any(LocalDate.class),
				any(Instant.class), nullable(String.class))).thenReturn(1);
		when(ticketRepository.findIdByAppAndTipoErroAndChaveDedupAndData(anyString(), any(TipoErro.class),
				anyString(), any(LocalDate.class))).thenReturn(Optional.of(42L));

		OccurrenceAssociationResult resultado = service.associar(sinal);

		assertThat(resultado.outcome()).isEqualTo(SinalOutcome.NOVO_TICKET);
		assertThat(resultado.ticketId()).isEqualTo(42L);

		ArgumentCaptor<ErrorSignalLog> captor = ArgumentCaptor.forClass(ErrorSignalLog.class);
		verify(errorSignalLogRepository).save(captor.capture());
		assertThat(captor.getValue().getOutcome()).isEqualTo(SinalOutcome.NOVO_TICKET);
		assertThat(captor.getValue().getApp()).isEqualTo("service-orders");
		assertThat(captor.getValue().getTipoErro()).isEqualTo(TipoErro.HTTP_5XX);
		assertThat(captor.getValue().getTraceId()).isEqualTo("trace-abc-123");
	}

	@Test
	void associar_propagaTraceIdParaUpsertOcorrencia() {
		when(ticketRepository.upsertOcorrencia(anyString(), anyString(), anyString(), any(LocalDate.class),
				any(Instant.class), nullable(String.class))).thenReturn(1);
		when(ticketRepository.findIdByAppAndTipoErroAndChaveDedupAndData(anyString(), any(TipoErro.class),
				anyString(), any(LocalDate.class))).thenReturn(Optional.of(42L));

		service.associar(sinal);

		verify(ticketRepository).upsertOcorrencia(eq("service-orders"), eq("HTTP_5XX"), anyString(),
				any(LocalDate.class), eq(sinal.timestamp()), eq("trace-abc-123"));
	}

	@Test
	void associar_affectedRowsDois_devolveAssociadoSemId() {
		when(ticketRepository.upsertOcorrencia(anyString(), anyString(), anyString(), any(LocalDate.class),
				any(Instant.class), nullable(String.class))).thenReturn(2);

		OccurrenceAssociationResult resultado = service.associar(sinal);

		assertThat(resultado.outcome()).isEqualTo(SinalOutcome.ASSOCIADO);
		assertThat(resultado.ticketId()).isNull();

		ArgumentCaptor<ErrorSignalLog> captor = ArgumentCaptor.forClass(ErrorSignalLog.class);
		verify(errorSignalLogRepository).save(captor.capture());
		assertThat(captor.getValue().getOutcome()).isEqualTo(SinalOutcome.ASSOCIADO);
	}

	@Test
	void associar_chaveDedupPassadaParaUpsertBateComDedupKeyBuilder() {
		when(ticketRepository.upsertOcorrencia(anyString(), anyString(), anyString(), any(LocalDate.class),
				any(Instant.class), nullable(String.class))).thenReturn(2);
		String chaveEsperada = DedupKeyBuilder.build(sinal.app(), sinal.tipoErro(), sinal.atributoExtra());

		service.associar(sinal);

		verify(ticketRepository).upsertOcorrencia(eq("service-orders"), eq("HTTP_5XX"), eq(chaveEsperada),
				any(LocalDate.class), eq(sinal.timestamp()), eq(sinal.traceId()));
	}

	@Test
	void associar_ticketNovoNaoEncontradoAposInsert_lancaExcecao() {
		when(ticketRepository.upsertOcorrencia(anyString(), anyString(), anyString(), any(LocalDate.class),
				any(Instant.class), nullable(String.class))).thenReturn(1);
		when(ticketRepository.findIdByAppAndTipoErroAndChaveDedupAndData(anyString(), any(TipoErro.class),
				anyString(), any(LocalDate.class))).thenReturn(Optional.empty());

		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.associar(sinal));
	}
}
