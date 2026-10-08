package com.ricardomukeka.incidentsentinel.triage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.ricardomukeka.incidentsentinel.enums.TipoErro;

/**
 * chave_dedup = SHA-256(app + "|" + tipo_erro + "|" + atributo_extra), hash
 * completo (64 hex chars), nunca truncado (docs/ARCHITECTURE.md — correção
 * pós-2ª rodada: truncar arrisca colisão e não resolve o limite de prefixo
 * de índice único do MySQL, que CHAR(64) já respeita sem folga a mais).
 * atributo_extra ausente (5xx, timeout, latência) vira string vazia, não
 * {@code null} concatenado — mantém a entrada do hash determinística.
 */
public final class DedupKeyBuilder {

	private DedupKeyBuilder() {
	}

	public static String build(String app, TipoErro tipoErro, String atributoExtra) {
		String entrada = app + "|" + tipoErro.name() + "|" + (atributoExtra == null ? "" : atributoExtra);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(entrada.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponível na JVM", ex);
		}
	}
}
