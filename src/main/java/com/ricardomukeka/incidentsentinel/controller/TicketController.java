package com.ricardomukeka.incidentsentinel.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ricardomukeka.incidentsentinel.dto.TicketDetailDto;
import com.ricardomukeka.incidentsentinel.dto.TicketSummaryDto;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.exception.TicketNotFoundException;
import com.ricardomukeka.incidentsentinel.model.Ticket;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;
import com.ricardomukeka.incidentsentinel.ticket.TicketSpecifications;

/**
 * Consulta de tíquetes (docs/ARCHITECTURE.md) — obrigatório na v1: sem isso, o
 * único jeito de ver o resultado do sistema é SQL direto. Autenticação/
 * autorização segue o padrão dos endpoints internos dos apps irmãos —
 * não confirmado nesta implementação (docs/ARCHITECTURE.md já deixava isso
 * como pendência, não decisão nova deste passo), sem Spring Security no
 * classpath ainda.
 */
@RestController
@RequestMapping("/tickets")
public class TicketController {

	private final TicketRepository ticketRepository;

	public TicketController(TicketRepository ticketRepository) {
		this.ticketRepository = ticketRepository;
	}

	@GetMapping
	public PagedModel<TicketSummaryDto> listar(@RequestParam(required = false) String app,
			@RequestParam(required = false) TicketStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
			Pageable pageable) {
		Page<Ticket> pagina = ticketRepository.findAll(TicketSpecifications.filtros(app, status, data), pageable);
		return new PagedModel<>(pagina.map(TicketSummaryDto::from));
	}

	@GetMapping("/{id}")
	public TicketDetailDto detalhar(@PathVariable Long id) {
		Ticket ticket = ticketRepository.findById(id).orElseThrow(() -> new TicketNotFoundException(id));
		return TicketDetailDto.from(ticket);
	}
}
