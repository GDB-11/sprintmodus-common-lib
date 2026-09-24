package com.sprintmodus.common_lib.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What a service needs to check tokens issued by auth-service ({@code sprintmodus.jwt.*}). The secret must be the same
 * one auth-service signs with.
 *
 * @param secret HMAC key, at least 64 bytes for HS512
 * @param issuer required value of the {@code iss} claim
 */
@ConfigurationProperties("sprintmodus.jwt")
public record JwtVerificationProperties(String secret, String issuer) {

	private static final int MIN_SECRET_BYTES = 64;

	public JwtVerificationProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("sprintmodus.jwt.secret (env JWT_SECRET) must be set and have at least "
					+ MIN_SECRET_BYTES + " bytes");
		}
		if (issuer == null || issuer.isBlank()) {
			throw new IllegalStateException("sprintmodus.jwt.issuer must be set");
		}
	}

}
