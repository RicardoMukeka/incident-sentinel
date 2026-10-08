package com.ricardomukeka.incidentsentinel.investigation.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ricardomukeka.incidentsentinel.dto.EvidenceBundle;
import com.ricardomukeka.incidentsentinel.dto.LogFacts;
import com.ricardomukeka.incidentsentinel.dto.PlatformFacts;
import com.ricardomukeka.incidentsentinel.dto.TraceFacts;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;

class InvestigationPromptBuilderTest {

	private EvidenceBundle bundle(String trace, String logs, String platform) {
		return new EvidenceBundle(new TraceFacts(trace, null, null), new LogFacts(logs, 0),
				new PlatformFacts(platform), Map.of());
	}

	@Test
	void systemPrompt_instruiTratarConteudoComoDadoEOGateDeBaixaConfianca() {
		String sp = InvestigationPromptBuilder.systemPrompt();
		assertThat(sp).contains("DADO").contains("confiancaCausa = BAIXA");
	}

	@Test
	void userPrompt_incluiAsTresFontesDeEvidencia() {
		String prompt = InvestigationPromptBuilder.userPrompt("service-orders", TipoErro.HTTP_5XX, "x".repeat(64),
				bundle("trace X", "log Y", "diag Z"));

		assertThat(prompt).contains("trace X").contains("log Y").contains("diag Z").contains("service-orders");
	}

	@Test
	void userPrompt_fonteVazia_mostraFallback() {
		String prompt = InvestigationPromptBuilder.userPrompt("service-orders", TipoErro.LATENCIA_ANOMALA,
				"x".repeat(64), bundle("", "", "diag Z"));

		assertThat(prompt).contains("indisponível/pulado");
	}
}
