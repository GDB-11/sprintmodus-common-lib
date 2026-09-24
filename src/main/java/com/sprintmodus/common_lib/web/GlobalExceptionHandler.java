package com.sprintmodus.common_lib.web;

import org.slf4j.Logger;
import org.springframework.beans.TypeMismatchException;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into responses. Business errors never get here (they are {@code Result}s, mapped by the
 * controllers); this is for malformed requests and for infrastructure failures such as the database being down, which
 * are logged in full but reported to the client as a generic 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> malformedBody() {
		return ResponseEntity.badRequest()
				.body(new ErrorResponse("MALFORMED_REQUEST", "The request body is missing or is not valid JSON."));
	}

	/** A path variable or parameter of the wrong type, e.g. {@code /api/projects/not-a-uuid}. */
	@ExceptionHandler(TypeMismatchException.class)
	ResponseEntity<ErrorResponse> wrongParameterType() {
		return ResponseEntity.badRequest()
				.body(new ErrorResponse("INVALID_PARAMETER", "A parameter in the request has an invalid format."));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> unexpected(Exception exception) {
		// Spring MVC's own rejections (missing parameter, wrong method, ...) already carry the right status
		if (exception instanceof org.springframework.web.ErrorResponse rejection) {
			return ResponseEntity.status(rejection.getStatusCode())
					.body(new ErrorResponse("REQUEST_REJECTED", "The request was rejected."));
		}
		log.error("Unexpected error", exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred. Please try again."));
	}

}
