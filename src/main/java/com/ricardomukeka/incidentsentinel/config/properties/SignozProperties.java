package com.ricardomukeka.incidentsentinel.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signoz.api")
public record SignozProperties(String baseUrl, String token) {
}
