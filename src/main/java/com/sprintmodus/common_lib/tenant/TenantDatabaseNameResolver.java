package com.sprintmodus.common_lib.tenant;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Derives a tenant's database name from its id alone: {@code tenant_{tenantId without dashes}}. This is the convention
 * used by onboarding when it creates the database, so no lookup or cache is needed while every tenant lives on the same
 * MySQL host.
 * <p>
 * Extension point: if tenants ever get dedicated hosts or credentials, replace the body of this class with a cached
 * lookup against the master DB. Callers only depend on {@link #resolve(UUID)}.
 */
public class TenantDatabaseNameResolver {

	static final String PREFIX = "tenant_";

	private static final Pattern DATABASE_NAME = Pattern.compile("tenant_[0-9a-f]{32}");

	public String resolve(UUID tenantId) {
		return PREFIX + tenantId.toString().replace("-", "");
	}

	/** Whether {@code name} looks like a database name produced by {@link #resolve(UUID)}. */
	public boolean isTenantDatabaseName(String name) {
		return name != null && DATABASE_NAME.matcher(name).matches();
	}

}
