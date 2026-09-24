package com.sprintmodus.common_lib.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtTokenVerifierTest {

	static final String SECRET = "common-lib-test-secret-that-is-long-enough-for-hs512-0123456789-0123456789-abc";

	static final String ISSUER = "sprintmodus-auth";

	static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

	private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

	private final JwtTokenVerifier verifier = new JwtTokenVerifier(new JwtVerificationProperties(SECRET, ISSUER),
			Clock.fixed(NOW, ZoneOffset.UTC));

	private final UUID userCode = UUID.randomUUID();

	private final UUID tenantId = UUID.randomUUID();

	/** A token built exactly as auth-service builds it. */
	private String token(Instant expiresAt, String issuer) {
		return Jwts.builder().issuer(issuer).subject(userCode.toString()).issuedAt(Date.from(NOW.minusSeconds(60)))
				.expiration(Date.from(expiresAt)).claim("email", "ana@acme.io").claim("tenantId", tenantId.toString())
				.claim("organizationCode", "acme").claim("role", "ADMIN").claim("plan", "PRO").claim("maxProjects", 10)
				.claim("maxUsers", 50).claim("maxStorageMB", 5000).signWith(key, Jwts.SIG.HS512).compact();
	}

	@Test
	void readsEveryClaimOfAValidToken() {
		var result = verifier.verify(token(NOW.plusSeconds(3600), ISSUER));

		assertThat(result.getValue()).isEqualTo(new AuthenticatedUser(userCode, "ana@acme.io", tenantId, "acme", "ADMIN", "PRO",
				10, 50, 5000));
	}

	@Test
	void reportsAnExpiredTokenAsExpired() {
		assertThat(verifier.verify(token(NOW.minusSeconds(1), ISSUER)).getError()).isEqualTo(TokenProblem.EXPIRED);
	}

	@Test
	void rejectsATokenFromAnotherIssuer() {
		assertThat(verifier.verify(token(NOW.plusSeconds(60), "someone-else")).getError()).isEqualTo(TokenProblem.INVALID);
	}

	@Test
	void rejectsATokenSignedWithAnotherKey() {
		SecretKey other = Keys.hmacShaKeyFor("another-secret-that-is-long-enough-for-hs512-0123456789-0123456789-abcdef".getBytes(StandardCharsets.UTF_8));
		String foreign = Jwts.builder().issuer(ISSUER).subject(userCode.toString()).expiration(Date.from(NOW.plusSeconds(60)))
				.signWith(other, Jwts.SIG.HS512).compact();

		assertThat(verifier.verify(foreign).getError()).isEqualTo(TokenProblem.INVALID);
	}

	@Test
	void rejectsATamperedPayload() {
		String[] parts = token(NOW.plusSeconds(60), ISSUER).split("\\.");
		String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
				("{\"sub\":\"" + userCode + "\",\"role\":\"OWNER\",\"iss\":\"" + ISSUER + "\"}").getBytes(StandardCharsets.UTF_8));

		assertThat(verifier.verify(parts[0] + "." + payload + "." + parts[2]).getError()).isEqualTo(TokenProblem.INVALID);
	}

	@Test
	void rejectsAnUnsignedToken() {
		String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
		String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
				("{\"sub\":\"" + userCode + "\",\"iss\":\"" + ISSUER + "\"}").getBytes(StandardCharsets.UTF_8));

		assertThat(verifier.verify(header + "." + payload + ".").getError()).isEqualTo(TokenProblem.INVALID);
	}

	@Test
	void rejectsAValidlySignedTokenWithMissingOrMalformedClaims() {
		String missing = Jwts.builder().issuer(ISSUER).subject(userCode.toString()).expiration(Date.from(NOW.plusSeconds(60)))
				.signWith(key, Jwts.SIG.HS512).compact();
		String malformed = Jwts.builder().issuer(ISSUER).subject("not-a-uuid").expiration(Date.from(NOW.plusSeconds(60)))
				.claims(Map.of("email", "a@b.io", "tenantId", "x", "organizationCode", "acme", "role", "ADMIN", "plan", "PRO",
						"maxProjects", 1, "maxUsers", 1, "maxStorageMB", 1))
				.signWith(key, Jwts.SIG.HS512).compact();

		assertThat(verifier.verify(missing).getError()).isEqualTo(TokenProblem.INVALID);
		assertThat(verifier.verify(malformed).getError()).isEqualTo(TokenProblem.INVALID);
	}

	@Test
	void rejectsGarbageBlankAndNull() {
		for (String bad : new String[] { "garbage", "a.b.c", "  ", "", null }) {
			assertThat(verifier.verify(bad).getError()).as(String.valueOf(bad)).isEqualTo(TokenProblem.INVALID);
		}
	}

	@Test
	void refusesToStartWithAWeakSecretOrNoIssuer() {
		assertThatThrownBy(() -> new JwtVerificationProperties("too-short", ISSUER)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET");
		assertThatThrownBy(() -> new JwtVerificationProperties(null, ISSUER)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new JwtVerificationProperties(SECRET, " ")).isInstanceOf(IllegalStateException.class);
	}

}
