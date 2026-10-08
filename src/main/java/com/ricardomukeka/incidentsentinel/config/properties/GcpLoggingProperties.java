package com.ricardomukeka.incidentsentinel.config.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config do retrieval de logs do GCP Cloud Logging (docs/ARCHITECTURE.md).
 * {@code projects} — projetos onde os apps monitorados logam (o service
 * account do App precisa de {@code roles/logging.viewer} neles). {@code
 * resourceType} — {@code k8s_container} ou o resource específico do Tsuru,
 * a confirmar contra STG (docs/ARCHITECTURE.md).
 */
@ConfigurationProperties(prefix = "incidentsentinel.gcp.logging")
public record GcpLoggingProperties(List<String> projects, String resourceType) {
}
