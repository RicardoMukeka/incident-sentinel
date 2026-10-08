package com.ricardomukeka.incidentsentinel.investigation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedactionServiceTest {

	private final RedactionService service = new RedactionService();

	@Test
	void redigir_awsAccessKey_removeOValor() {
		String resultado = service.redigir("Encontrado AKIAABCDEFGHIJKLMNOP no log do app");

		assertThat(resultado).doesNotContain("AKIAABCDEFGHIJKLMNOP").contains("[REDACTED]");
	}

	@Test
	void redigir_bearerToken_removeOValor() {
		String resultado = service.redigir("Authorization: Bearer abc123.def456-xyz");

		assertThat(resultado).doesNotContain("abc123.def456-xyz").contains("Bearer [REDACTED]");
	}

	@Test
	void redigir_atribuicaoComPrefixoDeEnvVar_removeOValor() {
		String resultado = service.redigir("GITLAB_TOKEN=glpat-abcd1234 e DB_PASSWORD=hunter2");

		assertThat(resultado).contains("GITLAB_TOKEN=[REDACTED]").contains("DB_PASSWORD=[REDACTED]")
				.doesNotContain("hunter2");
	}

	@Test
	void redigir_jwt_removeOValor() {
		String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTYifQ.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";

		String resultado = service.redigir("token recebido: " + jwt);

		assertThat(resultado).doesNotContain(jwt).contains("[REDACTED]");
	}

	@Test
	void redigir_googleApiKey_removeOValor() {
		String resultado = service.redigir("key=AIzaSyA1234567890abcdefghijklmnopqrstuv no header");

		assertThat(resultado).doesNotContain("AIzaSyA1234567890abcdefghijklmnopqrstuv").contains("[REDACTED]");
	}

	@Test
	void redigir_uriComCredencial_mantemEsquemaRemoveUsuarioESenha() {
		String resultado = service.redigir("jdbc falhou: mysql://appuser:s3nh4Secreta@db-host:3306/appdb");

		assertThat(resultado).contains("mysql://[REDACTED]:[REDACTED]@").doesNotContain("s3nh4Secreta")
				.doesNotContain("appuser:");
	}

	@Test
	void redigir_chavePrivada_removeOBloco() {
		String bloco = "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADAN...\n-----END PRIVATE KEY-----";

		String resultado = service.redigir("credencial de service account:\n" + bloco);

		assertThat(resultado).doesNotContain("MIIEvQIBADAN").contains("[REDACTED]");
	}

	@Test
	void redigir_semSegredo_naoAlteraOTexto() {
		String texto = "Timeout ao chamar dependência downstream, status 504";

		assertThat(service.redigir(texto)).isEqualTo(texto);
		assertThat(service.contemSegredo(texto)).isFalse();
	}

	@Test
	void contemSegredo_comToken_true() {
		assertThat(service.contemSegredo("api_key=abc123")).isTrue();
	}

	@Test
	void redigir_null_devolveNull() {
		assertThat(service.redigir(null)).isNull();
	}
}
