package com.sprintmodus.common_lib.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.sprintmodus.common_lib.tenant.TenantContext;
import com.sprintmodus.common_lib.tenant.TenantDatabaseNameResolver;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;

class TenantSecurityFilterTest {

	private static final Instant NOW = JwtTokenVerifierTest.NOW;

	private final SecretKey key = Keys.hmacShaKeyFor(JwtTokenVerifierTest.SECRET.getBytes(StandardCharsets.UTF_8));

	private final JwtTokenVerifier tokens = new JwtTokenVerifier(
			new JwtVerificationProperties(JwtTokenVerifierTest.SECRET, JwtTokenVerifierTest.ISSUER), Clock.fixed(NOW, ZoneOffset.UTC));

	private final UUID tenantId = UUID.randomUUID();

	private final UUID userCode = UUID.randomUUID();

	/** Membership answers, and the tenant the context pointed at while it was asked. */
	private boolean member = true;

	private RuntimeException membershipFailure;

	private final List<Optional<UUID>> tenantSeenByMembershipCheck = new ArrayList<>();

	private final TenantMembershipVerifier membership = (tenant, user) -> {
		tenantSeenByMembershipCheck.add(TenantContext.getCurrentTenant());
		if (membershipFailure != null) {
			throw membershipFailure;
		}
		return member;
	};

	private final TenantSecurityFilter filter = new TenantSecurityFilter(tokens, membership, new TenantDatabaseNameResolver());

	@AfterEach
	void cleanUp() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	private String token(Instant expiresAt) {
		return Jwts.builder().issuer(JwtTokenVerifierTest.ISSUER).subject(userCode.toString()).expiration(Date.from(expiresAt))
				.claim("email", "ana@acme.io").claim("tenantId", tenantId.toString()).claim("organizationCode", "acme")
				.claim("role", "ADMIN").claim("plan", "PRO").claim("maxProjects", 10).claim("maxUsers", 50)
				.claim("maxStorageMB", 5000).signWith(key, Jwts.SIG.HS512).compact();
	}

	private MockHttpServletRequest request(String authorization) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
		if (authorization != null) {
			request.addHeader("Authorization", authorization);
		}
		return request;
	}

	/** What the chain saw on the request thread, captured while it ran. */
	private record Seen(Optional<UUID> tenant, Optional<Integer> maxProjects, Authentication authentication) {
	}

	private final List<Seen> chainRuns = new ArrayList<>();

	private final FilterChain recordingChain = (req, res) -> chainRuns.add(new Seen(TenantContext.getCurrentTenant(),
			TenantContext.getMaxProjects(), SecurityContextHolder.getContext().getAuthentication()));

	@Test
	void authenticatesAValidTokenAndScopesTheTenantToTheChain() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request("Bearer " + token(NOW.plusSeconds(60))), response, recordingChain);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(chainRuns).singleElement().satisfies(seen -> {
			assertThat(seen.tenant()).contains(tenantId);
			assertThat(seen.maxProjects()).contains(10);
			assertThat(seen.authentication().isAuthenticated()).isTrue();
			assertThat(seen.authentication().getPrincipal()).isInstanceOfSatisfying(AuthenticatedUser.class,
					user -> assertThat(user.userCode()).isEqualTo(userCode));
			assertThat(seen.authentication().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
			assertThat(seen.authentication().getCredentials()).isNotNull();
		});
	}

	@Test
	void theTenantIsAlreadySetWhenMembershipIsChecked() throws Exception {
		filter.doFilter(request("Bearer " + token(NOW.plusSeconds(60))), new MockHttpServletResponse(), recordingChain);

		// the routing DataSource needs the tenant in the context to pick the database the check queries
		assertThat(tenantSeenByMembershipCheck).containsExactly(Optional.of(tenantId));
	}

	@Test
	void clearsBothContextsAfterwards() throws Exception {
		filter.doFilter(request("Bearer " + token(NOW.plusSeconds(60))), new MockHttpServletResponse(), recordingChain);

		assertThat(TenantContext.getCurrentTenant()).isEmpty();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void clearsBothContextsEvenWhenTheChainThrows() {
		FilterChain failing = (req, res) -> {
			throw new IllegalStateException("boom");
		};

		assertThatThrownBy(() -> filter.doFilter(request("Bearer " + token(NOW.plusSeconds(60))), new MockHttpServletResponse(), failing))
				.hasMessageContaining("boom");

		assertThat(TenantContext.getCurrentTenant()).isEmpty();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void aRequestWithoutABearerTokenContinuesUnauthenticated() throws Exception {
		for (String header : new String[] { null, "Basic dXNlcjpwYXNz", "bearer lowercase", "Bearer" }) {
			chainRuns.clear();

			filter.doFilter(request(header), new MockHttpServletResponse(), recordingChain);

			assertThat(chainRuns).as(String.valueOf(header)).singleElement().satisfies(seen -> {
				assertThat(seen.tenant()).isEmpty();
				assertThat(seen.authentication()).isNull();
			});
		}
		assertThat(tenantSeenByMembershipCheck).isEmpty();
	}

	@Test
	void answers401ForABadTokenAndNeverRunsTheChain() throws Exception {
		for (String bad : new String[] { "Bearer garbage", "Bearer a.b.c", "Bearer " + token(NOW.plusSeconds(60)) + "x" }) {
			MockHttpServletResponse response = new MockHttpServletResponse();

			filter.doFilter(request(bad), response, recordingChain);

			assertThat(response.getStatus()).as(bad).isEqualTo(401);
			assertThat(response.getContentType()).startsWith("application/json");
			assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_TOKEN\"");
		}
		assertThat(chainRuns).isEmpty();
		assertThat(tenantSeenByMembershipCheck).isEmpty();
	}

	@Test
	void anExpiredTokenGetsItsOwnCode() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request("Bearer " + token(NOW.minusSeconds(1))), response, recordingChain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("\"code\":\"TOKEN_EXPIRED\"");
		assertThat(chainRuns).isEmpty();
	}

	@Test
	void aValidTokenOfAUserWhoIsNoLongerAMemberIsRejected() throws Exception {
		member = false;
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request("Bearer " + token(NOW.plusSeconds(60))), response, recordingChain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_TOKEN\"");
		assertThat(chainRuns).isEmpty();
		assertThat(TenantContext.getCurrentTenant()).isEmpty();
	}

	@Test
	void aTenantDatabaseFailureIsA500ThatHidesTheDetails() throws Exception {
		membershipFailure = new CannotGetJdbcConnectionException("Unknown database 'tenant_secret'");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request("Bearer " + token(NOW.plusSeconds(60))), response, recordingChain);

		assertThat(response.getStatus()).isEqualTo(500);
		assertThat(response.getContentAsString()).contains("\"code\":\"INTERNAL_ERROR\"").doesNotContain("tenant_secret");
		assertThat(chainRuns).isEmpty();
		assertThat(TenantContext.getCurrentTenant()).isEmpty();
	}

	@Test
	void aDatabaseErrorRaisedByTheChainItselfIsNotRewrittenByTheFilter() throws Exception {
		FilterChain failing = (req, res) -> {
			throw new CannotGetJdbcConnectionException("from the application");
		};
		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThatThrownBy(() -> filter.doFilter(request("Bearer " + token(NOW.plusSeconds(60))), response, failing))
				.isInstanceOf(CannotGetJdbcConnectionException.class);
		assertThat(response.getContentAsString()).isEmpty();
	}

	@Test
	void theErrorBodyHasTheSameFieldsAsErrorResponse() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request("Bearer garbage"), response, new MockFilterChain());

		assertThat(response.getContentAsString()).matches("\\{\"code\":\"[A-Z_]+\",\"message\":\"[^\"]+\",\"timestamp\":\"[^\"]+\"}");
	}

}
