package com.ricardomukeka.incidentsentinel.enums;

/**
 * Lista completa de desfechos de um ticket (docs/ARCHITECTURE.md).
 * Filosofia "não descarta": todo caminho de falha (orçamento, parsing,
 * infra, flag desligada) termina num status visível no TicketController,
 * nunca numa mensagem silenciosamente perdida.
 */
public enum TicketStatus {
	/** Criado pela triagem ao enfileirar — reserva a chave de dedup. */
	EM_INVESTIGACAO,
	/** Investigação concluída: causa raiz e solução proposta disponíveis, aguardando ação humana. */
	ABERTO,
	/** Causa raiz apurada com confiança BAIXA, sem solução proposta — precisa de análise humana (docs/ARCHITECTURE.md). */
	CAUSA_INCONCLUSIVA,
	INVESTIGACAO_PULADA_ORCAMENTO_EXCEDIDO,
	INVESTIGACAO_FALHOU_PARSING,
	INVESTIGACAO_FALHOU_INFRA,
	INVESTIGACAO_DESABILITADA,
	/** Marcado manualmente após ação humana. */
	RESOLVIDO
}
