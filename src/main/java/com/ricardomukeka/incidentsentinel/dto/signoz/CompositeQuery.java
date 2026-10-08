package com.ricardomukeka.incidentsentinel.dto.signoz;

import java.util.List;

public record CompositeQuery(List<BuilderQuery> queries) {

	public static CompositeQuery of(BuilderQuery query) {
		return new CompositeQuery(List.of(query));
	}
}
