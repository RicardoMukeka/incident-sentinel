package com.ricardomukeka.incidentsentinel.controller.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ricardomukeka.incidentsentinel.collector.CollectorService;
import com.ricardomukeka.incidentsentinel.config.properties.CollectorProperties;
import com.ricardomukeka.incidentsentinel.registry.AppRegistryService;

/**
 * Endpoint interno disparado por HTTP pelo Tsuru Job a cada 5 min — não
 * {@code @Scheduled}, não processo Job separado (docs/ARCHITECTURE.md). A
 * chamada HTTP é entregue a um único pod pelo load balancer do K8s, por
 * isso não precisa de lock distribuído.
 */
@RestController
@RequestMapping("/internal/collector")
public class CollectorTriggerController {

	private final CollectorService collectorService;
	private final AppRegistryService appRegistryService;
	private final CollectorProperties collectorProperties;

	public CollectorTriggerController(CollectorService collectorService, AppRegistryService appRegistryService,
			CollectorProperties collectorProperties) {
		this.collectorService = collectorService;
		this.appRegistryService = appRegistryService;
		this.collectorProperties = collectorProperties;
	}

	@PostMapping("/run")
	public ResponseEntity<Void> run(@RequestHeader("X-Trigger-Secret") String secret) {
		validarSecret(secret);
		collectorService.runAsync();
		return ResponseEntity.accepted().build();
	}

	@GetMapping("/status")
	public List<CollectorStatusDto> status(@RequestHeader("X-Trigger-Secret") String secret) {
		validarSecret(secret);
		return appRegistryService.listarAtivos().stream()
				.map(app -> new CollectorStatusDto(app.getServiceName(), app.getUltimaExecucaoInicio(),
						app.getUltimaExecucaoFim(), app.getUltimaExecucaoStatus()))
				.toList();
	}

	/** Comparação em tempo constante — segredo compartilhado, não pode vazar por timing (docs/ARCHITECTURE.md). */
	private void validarSecret(String secretRecebido) {
		String esperado = collectorProperties.triggerSecret();
		boolean valido = secretRecebido != null && esperado != null
				&& MessageDigest.isEqual(secretRecebido.getBytes(StandardCharsets.UTF_8),
						esperado.getBytes(StandardCharsets.UTF_8));
		if (!valido) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
	}
}
