package com.ricardomukeka.incidentsentinel.ticket;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.model.Ticket;

import jakarta.persistence.criteria.Predicate;

/**
 * Filtros combináveis pro TicketController (docs/ARCHITECTURE.md). Construído
 * manualmente com {@code CriteriaBuilder.and(Predicate...)} em vez de
 * encadear {@code Specification.where(...).and(...)} — não confirmei se a
 * API de composição estática mudou entre spring-data-jpa 3.x e 4.0.x
 * (Boot 4), então evito depender dela; a interface funcional
 * {@code toPredicate(Root, CriteriaQuery, CriteriaBuilder)} é a parte mais
 * estável da API.
 */
public final class TicketSpecifications {

	private TicketSpecifications() {
	}

	public static Specification<Ticket> filtros(String app, TicketStatus status, LocalDate data) {
		return (root, query, cb) -> {
			List<Predicate> predicados = new ArrayList<>();
			// !isBlank(), não só != null (bug corrigido em 2026-08-21, achado de revisão):
			// ?app= (string vazia) era tratado como "filtro por app igual a vazio" em vez
			// de "sem filtro" — devolvia página vazia em vez de todos os tickets.
			if (app != null && !app.isBlank()) {
				predicados.add(cb.equal(root.get("app"), app));
			}
			if (status != null) {
				predicados.add(cb.equal(root.get("status"), status));
			}
			if (data != null) {
				predicados.add(cb.equal(root.get("data"), data));
			}
			return cb.and(predicados.toArray(new Predicate[0]));
		};
	}
}
