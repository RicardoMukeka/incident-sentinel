package com.ricardomukeka.incidentsentinel.investigation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Redação best-effort de segredo em texto livre (docs/ARCHITECTURE.md).
 * Roda em DOIS pontos, defesa em profundidade:
 * <ul>
 * <li>na entrada — sobre a evidência destilada (trace/log/diagnose_app) ANTES
 * de montar o prompt, pra não vazar token/senha de log de app irmão pra a
 * Vertex AI ({@code InvestigationOrchestrator});</li>
 * <li>na saída — sobre {@code causa_raiz}/{@code solucao_proposta} do modelo
 * ANTES de persistir o ticket ({@code TicketGenerationService}), pra o ticket
 * não virar vetor secundário de vazamento.</li>
 * </ul>
 * Regra: reportar só o NOME da variável/segredo, nunca o valor. Filtro por
 * regex — não é prova formal de que nada escapa, é rede de contenção.
 */
@Component
public class RedactionService {

	private static final String REDACTED = "[REDACTED]";

	private static final Pattern AWS_ACCESS_KEY = Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b");
	private static final Pattern GOOGLE_API_KEY = Pattern.compile("\\bAIza[0-9A-Za-z_\\-]{35}\\b");
	private static final Pattern GITLAB_PAT = Pattern.compile("\\bglpat-[0-9A-Za-z_\\-]{16,}\\b");
	/** JWT / token OAuth de 3 segmentos base64url — cobre id_token, access_token de service account etc. */
	private static final Pattern JWT = Pattern.compile("\\beyJ[0-9A-Za-z_\\-]+\\.[0-9A-Za-z_\\-]+\\.[0-9A-Za-z_\\-]+\\b");
	private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
			"-----BEGIN[^-]*PRIVATE KEY-----[\\s\\S]*?-----END[^-]*PRIVATE KEY-----");
	/** URI com credencial embutida — {@code mysql://user:senha@host}, {@code amqp://u:p@h}. Mantém o esquema. */
	private static final Pattern URI_COM_CREDENCIAL = Pattern.compile(
			"\\b([a-z][a-z0-9+.\\-]*://)[^\\s:/@]+:[^\\s:/@]+@");
	private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9\\-._~+/]+=*");
	/**
	 * Sem {@code \b} ANTES do grupo, de propósito (bug corrigido em 2026-08-21):
	 * a versão anterior exigia fronteira de palavra logo antes da palavra-chave,
	 * mas em {@code GITLAB_TOKEN=} o caractere anterior a "TOKEN" é {@code _} —
	 * caractere de palavra, sem fronteira ali — então o padrão nunca casava.
	 *
	 * <p>{@code "?} opcional em volta da chave/valor (bug corrigido em
	 * 2026-08-22): retorno de tool MCP costuma vir como JSON
	 * ({@code "token": "glpat-..."}). O valor exclui aspas/vírgula/chave de
	 * fechamento do que é capturado, pra não engolir o resto do JSON.
	 */
	private static final Pattern KEY_VALUE_SEGREDO = Pattern.compile(
			"(?i)(senha|password|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)\\b\"?\\s*[:=]\\s*\"?[^\"\\s,}]+\"?");

	public String redigir(String texto) {
		if (texto == null) {
			return null;
		}

		String resultado = replaceAll(PRIVATE_KEY_BLOCK, texto, REDACTED);
		resultado = replaceAll(AWS_ACCESS_KEY, resultado, REDACTED);
		resultado = replaceAll(GOOGLE_API_KEY, resultado, REDACTED);
		resultado = replaceAll(GITLAB_PAT, resultado, REDACTED);
		resultado = replaceAll(JWT, resultado, REDACTED);
		resultado = replaceFirstGroup(URI_COM_CREDENCIAL, resultado);
		resultado = replaceAll(BEARER_TOKEN, resultado, "Bearer " + REDACTED);
		resultado = replaceKeyValue(resultado);
		return resultado;
	}

	/** {@code true} se {@link #redigir} teria alterado o texto — pra sinalizar métrica de vazamento. */
	public boolean contemSegredo(String texto) {
		return texto != null && !texto.equals(redigir(texto));
	}

	private String replaceKeyValue(String texto) {
		Matcher matcher = KEY_VALUE_SEGREDO.matcher(texto);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String chave = matcher.group(1);
			matcher.appendReplacement(sb, Matcher.quoteReplacement(chave + "=" + REDACTED));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private String replaceFirstGroup(Pattern pattern, String texto) {
		Matcher matcher = pattern.matcher(texto);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + REDACTED + ":" + REDACTED + "@"));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private String replaceAll(Pattern pattern, String texto, String substituto) {
		return pattern.matcher(texto).replaceAll(Matcher.quoteReplacement(substituto));
	}
}
