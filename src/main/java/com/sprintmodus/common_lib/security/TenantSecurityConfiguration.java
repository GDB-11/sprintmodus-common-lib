package com.sprintmodus.common_lib.security;

import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import com.sprintmodus.common_lib.tenant.TenantDataSourceConfiguration;
import com.sprintmodus.common_lib.tenant.TenantDatabaseNameResolver;

/**
 * Secures a tenant-aware service: everything under {@code /api/**} needs a valid token, and the
 * {@link TenantSecurityFilter} routes the request to the caller's tenant database. Everything else is closed. Import it
 * explicitly ({@code @Import(TenantSecurityConfiguration.class)}); it brings {@link TenantDataSourceConfiguration} along.
 * <p>
 * CORS is not configured: the gateway is the only CORS authority.
 */
@Configuration(proxyBeanMethods = false)
@Import(TenantDataSourceConfiguration.class)
@EnableConfigurationProperties(JwtVerificationProperties.class)
public class TenantSecurityConfiguration {

	@Bean
	JwtTokenVerifier jwtTokenVerifier(JwtVerificationProperties properties, ObjectProvider<Clock> clock) {
		return new JwtTokenVerifier(properties, clock.getIfAvailable(Clock::systemUTC));
	}

	@Bean
	TenantMembershipVerifier tenantMembershipVerifier(@Qualifier("tenantDataSource") DataSource tenantDataSource) {
		return new JdbcTenantMembershipVerifier(new JdbcTemplate(tenantDataSource));
	}

	@Bean
	SecurityFilterChain tenantSecurityFilterChain(HttpSecurity http, JwtTokenVerifier tokens,
			TenantMembershipVerifier membership, TenantDatabaseNameResolver databaseNames) {
		// Not a bean: a Filter bean would also be registered as a plain servlet filter, outside the security chain
		TenantSecurityFilter filter = new TenantSecurityFilter(tokens, membership, databaseNames);
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, exception) -> JsonErrors.write(response,
								HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED", "Authentication is required."))
						.accessDeniedHandler((request, response, exception) -> JsonErrors.write(response,
								HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "You do not have permission to do this.")))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/error").permitAll()
						.requestMatchers("/api/**").authenticated()
						.anyRequest().denyAll())
				.addFilterBefore(filter, AuthorizationFilter.class)
				.build();
	}

}
