package com.ricardomukeka.incidentsentinel.dto.signoz;

public record BuilderQuery(String type, BuilderQuerySpec spec) {

	public static BuilderQuery of(BuilderQuerySpec spec) {
		return new BuilderQuery("builder_query", spec);
	}
}
