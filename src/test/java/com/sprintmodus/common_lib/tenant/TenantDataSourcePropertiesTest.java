package com.sprintmodus.common_lib.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantDataSourcePropertiesTest {

	@Test
	void appliesDefaults() {
		TenantDataSourceProperties properties = new TenantDataSourceProperties("db.internal", 0, "app", "secret", 0, null);

		assertThat(properties.port()).isEqualTo(3306);
		assertThat(properties.maxPoolSize()).isEqualTo(5);
		assertThat(properties.jdbcUrl("tenant_x")).isEqualTo("jdbc:mysql://db.internal:3306/tenant_x?connectionTimeZone=UTC");
	}

	@Test
	void omitsTheQueryStringWhenThereAreNoParameters() {
		TenantDataSourceProperties properties = new TenantDataSourceProperties("db", 3307, "app", "secret", 2, "");

		assertThat(properties.jdbcUrl("tenant_x")).isEqualTo("jdbc:mysql://db:3307/tenant_x");
	}

	@Test
	void requiresAHost() {
		assertThatThrownBy(() -> new TenantDataSourceProperties(" ", 3306, "app", "secret", 5, null))
				.isInstanceOf(IllegalStateException.class);
	}

}
