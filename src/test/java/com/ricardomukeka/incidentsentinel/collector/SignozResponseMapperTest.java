package com.ricardomukeka.incidentsentinel.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricardomukeka.incidentsentinel.dto.ErrorSignalDto;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;

/**
 * Cobre o shape assumido em SignozResponseMapper (docs no próprio arquivo:
 * inferido do padrão v3/v4 do SigNoz, não confirmado contra STG). Estes
 * testes travam a suposição documentada — se a resposta real do SigNoz
 * divergir, é aqui que vai quebrar primeiro, antes de virar bug silencioso
 * em produção.
 */
class SignozResponseMapperTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void mapEventoDiscreto_extraiTimestampAtributoExtraETraceId() throws Exception {
		JsonNode response = objectMapper.readTree("""
				{
				  "data": {
				    "result": [
				      {
				        "list": [
				          { "timestamp": "2026-08-20T12:00:00Z", "peer.service": "service-checkout", "traceID": "trace-1" },
				          { "timestamp": 1755691200000, "peer.service": "outro-servico", "traceID": "trace-2" }
				        ]
				      }
				    ]
				  }
				}
				""");

		List<ErrorSignalDto> sinais = SignozResponseMapper.mapEventoDiscreto(response, "service-orders",
				TipoErro.ERRO_DEPENDENCIA_DOWNSTREAM, "peer.service", "traceID");

		assertThat(sinais).hasSize(2);
		assertThat(sinais.get(0).timestamp()).isEqualTo(Instant.parse("2026-08-20T12:00:00Z"));
		assertThat(sinais.get(0).atributoExtra()).isEqualTo("service-checkout");
		assertThat(sinais.get(0).app()).isEqualTo("service-orders");
		assertThat(sinais.get(0).tipoErro()).isEqualTo(TipoErro.ERRO_DEPENDENCIA_DOWNSTREAM);
		assertThat(sinais.get(0).traceId()).isEqualTo("trace-1");
		assertThat(sinais.get(1).atributoExtra()).isEqualTo("outro-servico");
		assertThat(sinais.get(1).traceId()).isEqualTo("trace-2");
	}

	@Test
	void mapEventoDiscreto_semAtributoExtraFieldNameNemTraceIdFieldName_naoTentaExtrair() throws Exception {
		JsonNode response = objectMapper.readTree("""
				{ "data": { "result": [ { "list": [ { "timestamp": "2026-08-20T12:00:00Z" } ] } ] } }
				""");

		List<ErrorSignalDto> sinais = SignozResponseMapper.mapEventoDiscreto(response, "service-orders",
				TipoErro.HTTP_5XX, null, null);

		assertThat(sinais).hasSize(1);
		assertThat(sinais.getFirst().atributoExtra()).isNull();
		assertThat(sinais.getFirst().traceId()).isNull();
	}

	@Test
	void mapEventoDiscreto_shapeInesperado_naoLancaExcecao_devolveVazio() throws Exception {
		JsonNode response = objectMapper.readTree("""
				{ "algumOutroFormato": true }
				""");

		List<ErrorSignalDto> sinais = SignozResponseMapper.mapEventoDiscreto(response, "service-orders",
				TipoErro.HTTP_5XX, null, null);

		assertThat(sinais).isEmpty();
	}

	@Test
	void mapUltimoValorAgregado_pegaValorMaisRecenteDaSerie() throws Exception {
		JsonNode response = objectMapper.readTree("""
				{
				  "data": {
				    "result": [
				      { "series": [ { "values": [ { "value": "100.5" }, { "value": "250.75" } ] } ] }
				    ]
				  }
				}
				""");

		Optional<Double> valor = SignozResponseMapper.mapUltimoValorAgregado(response);

		assertThat(valor).contains(250.75);
	}

	@Test
	void mapUltimoValorAgregado_shapeInesperado_devolveVazioSemExcecao() throws Exception {
		JsonNode response = objectMapper.readTree("{}");

		assertThat(SignozResponseMapper.mapUltimoValorAgregado(response)).isEmpty();
	}
}
