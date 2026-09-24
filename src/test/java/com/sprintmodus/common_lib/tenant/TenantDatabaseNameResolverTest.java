package com.sprintmodus.common_lib.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TenantDatabaseNameResolverTest {

	private final TenantDatabaseNameResolver resolver = new TenantDatabaseNameResolver();

	@Test
	void derivesTheNameFromTheTenantIdWithoutDashes() {
		UUID tenantId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");

		assertThat(resolver.resolve(tenantId)).isEqualTo("tenant_123e4567e89b42d3a456426614174000");
	}

	@Test
	void recognizesItsOwnNames() {
		assertThat(resolver.isTenantDatabaseName(resolver.resolve(UUID.randomUUID()))).isTrue();
	}

	@Test
	void rejectsAnythingElse() {
		assertThat(resolver.isTenantDatabaseName(null)).isFalse();
		assertThat(resolver.isTenantDatabaseName("master_db")).isFalse();
		assertThat(resolver.isTenantDatabaseName("tenant_abc")).isFalse();
		assertThat(resolver.isTenantDatabaseName("tenant_123e4567e89b42d3a456426614174000`; DROP DATABASE x; --"))
				.isFalse();
	}

}
