package com.redxela.api;

import com.redxela.domain.ServiceLevel;

/** Cuerpo de {@code POST /api/customers}. */
public record CreateCustomerRequest(String name, ServiceLevel serviceLevel) {
}
