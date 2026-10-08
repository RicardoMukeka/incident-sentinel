package com.ricardomukeka.incidentsentinel.enums;

/**
 * Categoria da causa raiz (docs/ARCHITECTURE.md) — parte do structured output
 * do LLM. Usada como filtro na recuperação do Failure Book na v2 (docs/ARCHITECTURE.md
 * §10). Valor não reconhecido cai em {@link #INDETERMINADA} na camada de
 * parsing (TicketGenerationService), não falha dura.
 */
public enum CausaCategoria {
	/** Memória, CPU, quota, autoscale insuficiente. */
	INFRA_RECURSO,
	/** Timeout, conexão recusada, DNS, pool esgotado. */
	INFRA_REDE,
	/** Falha originada num serviço do qual dependemos (o sintoma aparece aqui, a causa é lá). */
	DEPENDENCIA_DOWNSTREAM,
	/** Variável de ambiente, secret, certificado, configuração de deploy. */
	CONFIGURACAO,
	/** Bug de código (NPE, regressão, lógica). */
	CODIGO,
	/** Evidência insuficiente pra categorizar. */
	INDETERMINADA
}
