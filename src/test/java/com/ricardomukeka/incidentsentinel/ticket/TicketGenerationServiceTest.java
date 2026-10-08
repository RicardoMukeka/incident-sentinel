package com.ricardomukeka.incidentsentinel.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ricardomukeka.incidentsentinel.dto.CausaSolucaoResult;
import com.ricardomukeka.incidentsentinel.investigation.RedactionService;
import com.ricardomukeka.incidentsentinel.enums.CausaCategoria;
import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;

@ExtendWith(MockitoExtension.class)
class TicketGenerationServiceTest {

	@Mock
	private RedactionService redactionService;

	private TicketGenerationService service;

	@BeforeEach
	void setUp() {
		service = new TicketGenerationService(redactionService);
		lenient().when(redactionService.redigir(org.mockito.ArgumentMatchers.anyString()))
				.thenAnswer(inv -> inv.getArgument(0));
		lenient().when(redactionService.redigir(org.mockito.ArgumentMatchers.isNull())).thenReturn(null);
	}

	@Test
	void fromResultado_confiancaCausaAlta_retornaAbertoComCausaESolucao() {
		when(redactionService.redigir("causa bruta")).thenReturn("causa redigida");
		when(redactionService.redigir("solucao bruta")).thenReturn("solucao redigida");
		CausaSolucaoResult r = new CausaSolucaoResult("causa bruta", CausaCategoria.INFRA_REDE,
				ConfiancaDiagnostico.ALTA, "solucao bruta", ConfiancaDiagnostico.MEDIA);

		GeneratedTicketFields campos = service.fromResultado(r);

		assertThat(campos.status()).isEqualTo(TicketStatus.ABERTO);
		assertThat(campos.causaRaiz()).isEqualTo("causa redigida");
		assertThat(campos.causaCategoria()).isEqualTo(CausaCategoria.INFRA_REDE);
		assertThat(campos.confiancaCausa()).isEqualTo(ConfiancaDiagnostico.ALTA);
		assertThat(campos.solucaoProposta()).isEqualTo("solucao redigida");
		assertThat(campos.confiancaSolucao()).isEqualTo(ConfiancaDiagnostico.MEDIA);
	}

	@Test
	void fromResultado_confiancaCausaBaixa_gateCausaInconclusivaSemSolucao() {
		CausaSolucaoResult r = new CausaSolucaoResult("causa incerta", CausaCategoria.INDETERMINADA,
				ConfiancaDiagnostico.BAIXA, "solucao que devia ser ignorada", ConfiancaDiagnostico.ALTA);

		GeneratedTicketFields campos = service.fromResultado(r);

		assertThat(campos.status()).isEqualTo(TicketStatus.CAUSA_INCONCLUSIVA);
		assertThat(campos.solucaoProposta()).isNull();
		assertThat(campos.confiancaSolucao()).isNull();
	}

	@Test
	void fromResultado_categoriaNula_caiParaIndeterminada() {
		CausaSolucaoResult r = new CausaSolucaoResult("causa", null, ConfiancaDiagnostico.MEDIA, "sol",
				ConfiancaDiagnostico.MEDIA);

		GeneratedTicketFields campos = service.fromResultado(r);

		assertThat(campos.causaCategoria()).isEqualTo(CausaCategoria.INDETERMINADA);
	}

	@Test
	void fromResultado_confiancaCausaNula_caiParaBaixaEGate() {
		CausaSolucaoResult r = new CausaSolucaoResult("causa", CausaCategoria.CODIGO, null, "sol", null);

		GeneratedTicketFields campos = service.fromResultado(r);

		assertThat(campos.status()).isEqualTo(TicketStatus.CAUSA_INCONCLUSIVA);
	}

	@Test
	void fromResultado_causaRaizNula_caiEmFalhouParsing() {
		GeneratedTicketFields campos = service.fromResultado(
				new CausaSolucaoResult(null, null, null, null, null));

		assertThat(campos.status()).isEqualTo(TicketStatus.INVESTIGACAO_FALHOU_PARSING);
	}

	@Test
	void fromResultado_resultadoNulo_caiEmFalhouParsing() {
		assertThat(service.fromResultado(null).status()).isEqualTo(TicketStatus.INVESTIGACAO_FALHOU_PARSING);
	}
}
