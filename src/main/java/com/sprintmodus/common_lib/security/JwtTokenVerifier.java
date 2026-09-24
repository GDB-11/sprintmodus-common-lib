package com.sprintmodus.common_lib.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.sprintmodus.common_lib.result.Result;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Verifies the HS512 tokens auth-service issues and reads their claims: {@code sub} (user code), {@code email},
 * {@code tenantId}, {@code organizationCode}, {@code role}, {@code plan}, {@code maxProjects}, {@code maxUsers},
 * {@code maxStorageMB}, {@code iat}, {@code exp} and {@code iss}.
 * <p>
 * It only checks the token itself. Whether the user is still active in their tenant is a separate question, see
 * {@link TenantMembershipVerifier}.
 */
public class JwtTokenVerifier {

	private final SecretKey key;

	private final String issuer;

	private final Clock clock;

	public JwtTokenVerifier(JwtVerificationProperties properties, Clock clock) {
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.issuer = properties.issuer();
		this.clock = clock;
	}

	/** Never throws for a bad token. */
	public Result<AuthenticatedUser, TokenProblem> verify(String token) {
		if (token == null || token.isBlank()) {
			return Result.failure(TokenProblem.INVALID);
		}
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.requireIssuer(issuer)
					.clock(() -> Date.from(clock.instant()))
					.build()
					.parseSignedClaims(token)
					.getPayload();
			return Result.success(toUser(claims));
		}
		catch (ExpiredJwtException e) {
			return Result.failure(TokenProblem.EXPIRED);
		}
		catch (JwtException | IllegalArgumentException e) {
			// Bad signature, wrong issuer, malformed, or a claim that is missing or not of the expected form
			return Result.failure(TokenProblem.INVALID);
		}
	}

	private static AuthenticatedUser toUser(Claims claims) {
		return new AuthenticatedUser(UUID.fromString(required(claims.getSubject(), "sub")),
				required(claims.get("email", String.class), "email"),
				UUID.fromString(required(claims.get("tenantId", String.class), "tenantId")),
				required(claims.get("organizationCode", String.class), "organizationCode"),
				required(claims.get("role", String.class), "role"), required(claims.get("plan", String.class), "plan"),
				intClaim(claims, "maxProjects"), intClaim(claims, "maxUsers"), intClaim(claims, "maxStorageMB"));
	}

	/** Gson reads every JSON number as a Double, which jjwt will not convert to Integer, so go through Number. */
	private static int intClaim(Claims claims, String name) {
		return required(claims.get(name, Number.class), name).intValue();
	}

	private static <T> T required(T value, String claim) {
		if (value == null) {
			throw new IllegalArgumentException("Missing claim " + claim);
		}
		return value;
	}

}
