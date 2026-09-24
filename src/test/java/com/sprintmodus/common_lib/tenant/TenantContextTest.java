package com.sprintmodus.common_lib.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

	private final UUID tenant = UUID.randomUUID();

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	void isEmptyByDefault() {
		assertThat(TenantContext.getCurrentTenant()).isEmpty();
		assertThatThrownBy(TenantContext::requireCurrentTenant).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void holdsTheTenantAndSubscriptionLimits() {
		UUID user = UUID.randomUUID();

		TenantContext.set(tenant, user, 10, 50, 5000);

		assertThat(TenantContext.getCurrentTenant()).contains(tenant);
		assertThat(TenantContext.getCurrentUser()).contains(user);
		assertThat(TenantContext.getMaxProjects()).contains(10);
		assertThat(TenantContext.getMaxUsers()).contains(50);
		assertThat(TenantContext.getMaxStorageMB()).contains(5000);
	}

	@Test
	void clearRemovesEverything() {
		TenantContext.set(tenant, UUID.randomUUID(), 1, 5, 100);

		TenantContext.clear();

		assertThat(TenantContext.getCurrentTenant()).isEmpty();
		assertThat(TenantContext.getMaxProjects()).isEmpty();
	}

	@Test
	void rejectsANullTenant() {
		assertThatThrownBy(() -> TenantContext.setCurrentTenant(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void callAsScopesTheTenantAndClearsItAfterwards() {
		UUID seen = TenantContext.callAs(tenant, TenantContext::requireCurrentTenant);

		assertThat(seen).isEqualTo(tenant);
		assertThat(TenantContext.getCurrentTenant()).isEmpty();
	}

	@Test
	void callAsClearsTheTenantEvenWhenTheActionThrows() {
		assertThatThrownBy(() -> TenantContext.callAs(tenant, () -> {
			throw new IllegalStateException("boom");
		})).hasMessage("boom");

		assertThat(TenantContext.getCurrentTenant()).isEmpty();
	}

	@Test
	void callAsRestoresThePreviousContext() {
		UUID outer = UUID.randomUUID();
		TenantContext.setCurrentTenant(outer);

		TenantContext.callAs(tenant, () -> null);

		assertThat(TenantContext.getCurrentTenant()).contains(outer);
	}

	@Test
	void doesNotLeakToOtherThreads() throws InterruptedException {
		TenantContext.setCurrentTenant(tenant);
		boolean[] otherThreadHasTenant = new boolean[1];

		Thread thread = new Thread(() -> otherThreadHasTenant[0] = TenantContext.getCurrentTenant().isPresent());
		thread.start();
		thread.join();

		assertThat(otherThreadHasTenant[0]).isFalse();
	}

}
