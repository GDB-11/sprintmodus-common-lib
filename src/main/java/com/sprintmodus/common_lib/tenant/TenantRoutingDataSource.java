package com.sprintmodus.common_lib.tenant;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * A {@link DataSource} that hands out connections to the database of the tenant in {@link TenantContext}. A pool is
 * created lazily the first time a tenant is seen, since tenants are onboarded at runtime and cannot be listed at
 * startup. The tenant must be set <em>before</em> a connection is requested, which for a transaction means before it
 * begins.
 * <p>
 * It never falls back to another database: with no tenant in the context, {@link #getConnection()} fails rather than
 * silently reading or writing the wrong data.
 */
public class TenantRoutingDataSource extends AbstractDataSource implements Closeable {

	private static final Logger log = LoggerFactory.getLogger(TenantRoutingDataSource.class);

	private final TenantDatabaseNameResolver resolver;

	private final Function<String, DataSource> dataSourceFactory;

	private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

	public TenantRoutingDataSource(TenantDatabaseNameResolver resolver, Function<String, DataSource> dataSourceFactory) {
		this.resolver = resolver;
		this.dataSourceFactory = dataSourceFactory;
	}

	/** Pools tenant databases with Hikari, using the shared tenant connection settings. */
	public static TenantRoutingDataSource pooled(TenantDatabaseNameResolver resolver, TenantDataSourceProperties properties) {
		return new TenantRoutingDataSource(resolver, databaseName -> {
			HikariConfig config = new HikariConfig();
			config.setPoolName("tenant-" + databaseName);
			config.setJdbcUrl(properties.jdbcUrl(databaseName));
			config.setUsername(properties.username());
			config.setPassword(properties.password());
			config.setMaximumPoolSize(properties.maxPoolSize());
			// One pool per active tenant: hold no idle connections so many quiet tenants stay cheap
			config.setMinimumIdle(0);
			return new HikariDataSource(config);
		});
	}

	@Override
	public Connection getConnection() throws SQLException {
		return currentDataSource().getConnection();
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		throw new UnsupportedOperationException("Tenant connections use the shared tenant credentials");
	}

	private DataSource currentDataSource() throws SQLException {
		UUID tenantId = TenantContext.requireCurrentTenant();
		String databaseName = resolver.resolve(tenantId);
		DataSource existing = dataSources.get(databaseName);
		if (existing != null) {
			return existing;
		}
		return createPool(databaseName);
	}

	/**
	 * Opens the pool for a tenant seen for the first time. A pool that cannot be created (typically because the database
	 * does not exist) is reported as an {@link SQLException}, like any other failure to connect, so Spring translates it
	 * to a {@code DataAccessException}; and nothing is kept, so the next request tries again.
	 */
	private synchronized DataSource createPool(String databaseName) throws SQLException {
		DataSource existing = dataSources.get(databaseName);
		if (existing != null) {
			return existing;
		}
		log.debug("Creating connection pool for tenant database {}", databaseName);
		try {
			DataSource created = dataSourceFactory.apply(databaseName);
			dataSources.put(databaseName, created);
			return created;
		}
		catch (RuntimeException e) {
			throw new SQLException("Cannot open a connection pool for tenant database " + databaseName, e);
		}
	}

	/** Number of tenant pools currently open. */
	public int poolCount() {
		return dataSources.size();
	}

	@Override
	public void close() {
		dataSources.values().forEach(dataSource -> {
			if (dataSource instanceof AutoCloseable closeable) {
				try {
					closeable.close();
				}
				catch (Exception e) {
					log.warn("Failed to close a tenant pool", e);
				}
			}
		});
		dataSources.clear();
	}

}
