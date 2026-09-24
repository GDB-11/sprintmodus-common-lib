package com.sprintmodus.common_lib.security;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.sprintmodus.common_lib.tenant.TenantContext;

/**
 * Looks the user up in the tenant's own {@code User} table over the routing DataSource. Because it queries the tenant
 * database, a deactivated user loses access at once instead of when their token expires.
 */
public class JdbcTenantMembershipVerifier implements TenantMembershipVerifier {

	private static final String QUERY = "SELECT COUNT(*) FROM `User` WHERE UserCode = UUID_TO_BIN(?) AND IsActive = TRUE AND DeletedAt IS NULL";

	private final JdbcTemplate jdbc;

	public JdbcTenantMembershipVerifier(JdbcTemplate tenantJdbcTemplate) {
		this.jdbc = tenantJdbcTemplate;
	}

	@Override
	public boolean isActiveMember(UUID tenantId, UUID userCode) {
		Integer count = TenantContext.callAs(tenantId, () -> jdbc.queryForObject(QUERY, Integer.class, userCode.toString()));
		return count != null && count > 0;
	}

}
