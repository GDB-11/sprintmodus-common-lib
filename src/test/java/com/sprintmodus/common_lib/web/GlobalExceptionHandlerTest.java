package com.sprintmodus.common_lib.web;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

	@RestController
	static class Probe {

		@GetMapping("/items/{code}")
		String item(@PathVariable UUID code) {
			return code.toString();
		}

		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("secret internal detail");
		}

		@PostMapping("/body")
		String body(@RequestBody java.util.Map<String, String> body) {
			return "ok";
		}

	}

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();

	@Test
	void aMalformedPathVariableIsA400NotA500() throws Exception {
		mvc.perform(get("/items/not-a-uuid")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void aMalformedBodyIsA400() throws Exception {
		mvc.perform(post("/body").contentType("application/json").content("{not json")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void springsOwnRejectionsKeepTheirStatus() throws Exception {
		mvc.perform(post("/items/" + UUID.randomUUID())).andExpect(status().isMethodNotAllowed());
	}

	@Test
	void anUnexpectedExceptionIsAGeneric500() throws Exception {
		mvc.perform(get("/boom")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
	}


}
