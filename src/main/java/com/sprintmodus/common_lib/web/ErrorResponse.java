package com.sprintmodus.common_lib.web;

import java.time.Instant;

/** Body of every error response: a stable {@code code} for programs and a {@code message} that is safe to show. */
public record ErrorResponse(String code, String message, Instant timestamp) {

	public ErrorResponse(String code, String message) {
		this(code, message, Instant.now());
	}

}
