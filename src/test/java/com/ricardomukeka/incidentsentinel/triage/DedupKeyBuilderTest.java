package com.ricardomukeka.incidentsentinel.triage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ricardomukeka.incidentsentinel.enums.TipoErro;

/**
 * Hashes esperados calculados de forma independente via {@code shasum -a 256}
 * (não pela própria implementação) — trava o algoritmo real, não só "tem 64
 * caracteres".
 */
class DedupKeyBuilderTest {

	@Test
	void build_comAtributoExtra_bateComHashCalculadoIndependente() {
		String chave = DedupKeyBuilder.build("service-orders", TipoErro.ERRO_DEPENDENCIA_DOWNSTREAM,
				"service-checkout");

		assertThat(chave).isEqualTo("649a601de66fd5e8a2f94fc235a1f3bbdf44e0d1363e10a47f121f3bcb6102fa");
		assertThat(chave).hasSize(64);
	}

	@Test
	void build_semAtributoExtra_tratraNullComoStringVazia() {
		String chave = DedupKeyBuilder.build("service-orders", TipoErro.HTTP_5XX, null);

		assertThat(chave).isEqualTo("17d29ca720a90683ced83b9b25ad86dbcdb0321f72da545eaee12379ac69fa46");
		assertThat(chave).hasSize(64);
	}

	@Test
	void build_atributoExtraDiferente_geraChavesDiferentes() {
		String chave1 = DedupKeyBuilder.build("service-orders", TipoErro.ERRO_DEPENDENCIA_DOWNSTREAM, "servico-a");
		String chave2 = DedupKeyBuilder.build("service-orders", TipoErro.ERRO_DEPENDENCIA_DOWNSTREAM, "servico-b");

		assertThat(chave1).isNotEqualTo(chave2);
	}
}
