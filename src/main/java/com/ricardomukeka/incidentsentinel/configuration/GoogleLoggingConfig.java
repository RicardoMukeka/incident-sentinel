package com.ricardomukeka.incidentsentinel.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;

/**
 * Bean {@link Logging} do Cloud Logging (docs/ARCHITECTURE.md) — autenticação
 * por Application Default Credentials (ADC), o mesmo service account que o
 * App já usa no GKE/Tsuru pra Vertex AI e Pub/Sub. Precisa de
 * {@code roles/logging.viewer} nos projetos onde os apps monitorados logam.
 *
 * <p>Fechado como {@code prototype}? Não — {@link Logging} é thread-safe e
 * caro de criar (canal gRPC); um singleton pelo ciclo de vida do App é o
 * padrão da lib. O {@code close()} fica a cargo do container (o bean
 * implementa {@link AutoCloseable}).
 */
@Configuration
public class GoogleLoggingConfig {

	@Bean(destroyMethod = "close")
	public Logging cloudLogging() {
		return LoggingOptions.getDefaultInstance().getService();
	}
}
