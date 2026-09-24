package com.sprintmodus.common_lib.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings shared by every tenant database ({@code spring.datasource.tenant.*}). All tenants currently live
 * on one MySQL instance, so only the database name differs per tenant.
 *
 * @param host host name of the MySQL server
 * @param port port of the MySQL server
 * @param username user that can access every tenant database
 * @param password password of that user
 * @param maxPoolSize maximum connections per tenant pool; kept small because there is one pool per active tenant
 * @param jdbcParameters query string appended to the JDBC URL, without the leading {@code ?}
 */
@ConfigurationProperties("spring.datasource.tenant")
public record TenantDataSourceProperties(String host, int port, String username, String password, int maxPoolSize,
		String jdbcParameters) {

	public TenantDataSourceProperties {
		if (host == null || host.isBlank()) {
			throw new IllegalStateException("spring.datasource.tenant.host is required");
		}
		if (port == 0) {
			port = 3306;
		}
		if (maxPoolSize == 0) {
			maxPoolSize = 5;
		}
		if (jdbcParameters == null) {
			jdbcParameters = "connectionTimeZone=UTC";
		}
	}

	/** JDBC URL of the given database on the tenant host. */
	public String jdbcUrl(String databaseName) {
		String query = jdbcParameters.isBlank() ? "" : "?" + jdbcParameters;
		return "jdbc:mysql://" + host + ":" + port + "/" + databaseName + query;
	}

}
