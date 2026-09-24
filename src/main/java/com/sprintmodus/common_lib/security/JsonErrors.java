package com.sprintmodus.common_lib.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes the standard error body from code that runs outside Spring MVC (servlet filters, security handlers), where the
 * exception handler cannot help. The fields match {@code ErrorResponse}. Codes and messages are fixed literals, so no
 * escaping is needed.
 */
final class JsonErrors {

	private JsonErrors() {
	}

	static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"code\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}".formatted(code, message, Instant.now()));
	}

}
