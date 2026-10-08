package com.ricardomukeka.incidentsentinel.dto.signoz;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupByField(String name, String fieldContext) {

	public static GroupByField of(String name) {
		return new GroupByField(name, null);
	}
}
