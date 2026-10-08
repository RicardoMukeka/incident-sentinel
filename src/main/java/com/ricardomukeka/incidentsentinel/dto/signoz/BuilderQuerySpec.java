package com.ricardomukeka.incidentsentinel.dto.signoz;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuilderQuerySpec(
		String name,
		String signal,
		Long stepInterval,
		List<Aggregation> aggregations,
		SignozFilter filter,
		/** Reaproveitado para selectFields — mesmo shape {name, fieldContext}. */
		List<GroupByField> selectFields,
		List<GroupByField> groupBy,
		List<OrderBy> order,
		Integer limit,
		Integer offset,
		Boolean disabled) {
}
