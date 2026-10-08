package com.ricardomukeka.incidentsentinel.dto.signoz;

public record OrderBy(OrderKey key, String direction) {

	public static OrderBy desc(String fieldName) {
		return new OrderBy(new OrderKey(fieldName), "desc");
	}

	public record OrderKey(String name) {
	}
}
