package com.ricardomukeka.incidentsentinel.client.signoz;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.ricardomukeka.incidentsentinel.dto.signoz.QueryRangeRequest;

/**
 * Endpoint confirmado na implementação em 2026-08-20: POST /api/v5/query_range
 * (docs/ARCHITECTURE.md citava "v4 ou equivalente" — a versão real documentada
 * pelo SigNoz é v5). Retorno como JsonNode — o SigNoz não publica um schema
 * de resposta documentado publicamente; SignozResponseMapper extrai os
 * campos de forma defensiva. Validar contra STG antes de confiar cegamente
 * (docs/ARCHITECTURE.md, passo de verificação end-to-end).
 */
@FeignClient(name = "signoz", url = "${signoz.api.base-url}", configuration = SignozFeignConfig.class)
public interface SignozQueryClient {

	@PostMapping(value = "/api/v5/query_range", consumes = MediaType.APPLICATION_JSON_VALUE)
	JsonNode queryRange(@RequestBody QueryRangeRequest request);
}
