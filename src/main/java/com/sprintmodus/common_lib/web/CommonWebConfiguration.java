package com.sprintmodus.common_lib.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the shared {@link GlobalExceptionHandler}. Import it explicitly; this library is not component-scanned. */
@Configuration(proxyBeanMethods = false)
public class CommonWebConfiguration {

	@Bean
	GlobalExceptionHandler globalExceptionHandler() {
		return new GlobalExceptionHandler();
	}

}
