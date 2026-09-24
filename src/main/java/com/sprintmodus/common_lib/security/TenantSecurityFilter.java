package com.sprintmodus.common_lib.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sprintmodus.common_lib.tenant.TenantContext;
import com.sprintmodus.common_lib.tenant.TenantDatabaseNameResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates a request from its {@code Authorization: Bearer} token and points the thread at the caller's tenant:
 * <ol>
 * <li>verifies the JWT (signature, issuer, expiry);</li>
 * <li>checks the user is an active member of the tenant named in the token;</li>
 * <li>sets the {@link TenantContext} (which selects the tenant database) and the Spring {@code SecurityContext}.</li>
 * </ol>
 * Both contexts are cleared in a {@code finally}, so nothing leaks to the next request on a pooled thread.
 * <p>
 * A request without a bearer token continues unauthenticated, and the filter chain's authorization rules decide
 * whether that is allowed. A bearer token that is present but not acceptable is answered with 401 right here.
 */
public class TenantSecurityFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(TenantSecurityFilter.class);

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenVerifier tokens;

	private final TenantMembershipVerifier membership;

	private final TenantDatabaseNameResolver databaseNames;

	public TenantSecurityFilter(JwtTokenVerifier tokens, TenantMembershipVerifier membership,
			TenantDatabaseNameResolver databaseNames) {
		this.tokens = tokens;
		this.membership = membership;
		this.databaseNames = databaseNames;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			chain.doFilter(request, response);
			return;
		}
		String token = header.substring(BEARER_PREFIX.length()).trim();

		var verified = tokens.verify(token);
		if (verified.isFailure()) {
			boolean expired = verified.getError() == TokenProblem.EXPIRED;
			JsonErrors.write(response, HttpServletResponse.SC_UNAUTHORIZED, expired ? "TOKEN_EXPIRED" : "INVALID_TOKEN",
					expired ? "The token has expired." : "The token is invalid.");
			return;
		}
		AuthenticatedUser user = verified.getValue();

		try {
			// Set first: the membership check queries the tenant database, which the context selects
			TenantContext.set(user.tenantId(), user.userCode(), user.maxProjects(), user.maxUsers(), user.maxStorageMB());

			boolean member;
			try {
				member = membership.isActiveMember(user.tenantId(), user.userCode());
			}
			catch (DataAccessException e) {
				log.error("Could not check membership in the database of tenant {}", user.tenantId(), e);
				JsonErrors.write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
						"An unexpected error occurred. Please try again.");
				return;
			}
			if (!member) {
				log.warn("Rejected a valid token: user {} is not an active member of tenant {}", user.userCode(), user.tenantId());
				JsonErrors.write(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN", "The token is invalid.");
				return;
			}
			log.debug("Authenticated user {} for tenant {}, routing to database {}", user.userCode(), user.tenantId(),
					databaseNames.resolve(user.tenantId()));

			SecurityContext context = SecurityContextHolder.createEmptyContext();
			// The token is kept as the credentials so outgoing service calls can relay it
			context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, token,
					List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
			SecurityContextHolder.setContext(context);

			chain.doFilter(request, response);
		}
		finally {
			TenantContext.clear();
			SecurityContextHolder.clearContext();
		}
	}

}
