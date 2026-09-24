package com.sprintmodus.common_lib.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;

class TenantRoutingDataSourceTest {

	private final List<String> created = new ArrayList<>();

	private final TenantDatabaseNameResolver resolver = new TenantDatabaseNameResolver();

	/** Fails with the database name, so a test can see which pool a connection request was routed to. */
	private final TenantRoutingDataSource routing = new TenantRoutingDataSource(resolver, databaseName -> {
		created.add(databaseName);
		return new AbstractDataSource() {
			@Override
			public Connection getConnection() throws SQLException {
				throw new SQLException("routed to " + databaseName);
			}

			@Override
			public Connection getConnection(String username, String password) throws SQLException {
				return getConnection();
			}
		};
	});

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	void routesToTheCurrentTenantsDatabase() {
		UUID tenant = UUID.randomUUID();

		assertThatThrownBy(() -> TenantContext.callAs(tenant, this::connect))
				.hasMessage("routed to " + resolver.resolve(tenant));
	}

	@Test
	void keepsOnePoolPerTenantAndCreatesItOnce() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();

		for (UUID tenant : new UUID[] { first, second, first, first }) {
			assertThatThrownBy(() -> TenantContext.callAs(tenant, this::connect)).isInstanceOf(RuntimeException.class);
		}

		assertThat(created).containsExactlyInAnyOrder(resolver.resolve(first), resolver.resolve(second));
		assertThat(routing.poolCount()).isEqualTo(2);
	}

	@Test
	void failsInsteadOfFallingBackWhenThereIsNoTenant() {
		assertThatThrownBy(routing::getConnection).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No tenant");
		assertThat(created).isEmpty();
	}

	@Test
	void doesNotAcceptPerCallCredentials() {
		assertThatThrownBy(() -> routing.getConnection("user", "password"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void reportsAPoolThatCannotBeOpenedAsAnSqlExceptionAndRetriesLater() {
		UUID tenant = UUID.randomUUID();
		boolean[] databaseExists = { false };
		TenantRoutingDataSource flaky = new TenantRoutingDataSource(resolver, databaseName -> {
			if (!databaseExists[0]) {
				// what Hikari does for an unknown database: a RuntimeException from its constructor
				throw new IllegalStateException("Failed to initialize pool: Unknown database '" + databaseName + "'");
			}
			return new AbstractDataSource() {
				@Override
				public Connection getConnection() throws SQLException {
					throw new SQLException("connected to " + databaseName);
				}

				@Override
				public Connection getConnection(String username, String password) throws SQLException {
					return getConnection();
				}
			};
		});

		assertThatThrownBy(() -> TenantContext.callAs(tenant, () -> {
			try {
				return flaky.getConnection();
			}
			catch (SQLException e) {
				throw new IllegalArgumentException(e);
			}
		})).cause().isInstanceOf(SQLException.class).hasMessageContaining(resolver.resolve(tenant))
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertThat(flaky.poolCount()).as("nothing is cached for a failed pool").isZero();

		databaseExists[0] = true;
		assertThatThrownBy(() -> TenantContext.callAs(tenant, () -> {
			try {
				return flaky.getConnection();
			}
			catch (SQLException e) {
				throw new IllegalArgumentException(e);
			}
		})).cause().hasMessage("connected to " + resolver.resolve(tenant));
		assertThat(flaky.poolCount()).isEqualTo(1);
	}

	private Connection connect() {
		try {
			return routing.getConnection();
		}
		catch (SQLException e) {
			// Surface the routing decision to the assertion
			throw new RuntimeException(e.getMessage(), e);
		}
	}

}
