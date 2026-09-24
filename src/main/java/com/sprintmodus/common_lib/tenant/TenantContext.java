package com.sprintmodus.common_lib.tenant;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The tenant a thread is currently working for, plus the subscription limits from the caller's JWT. The context lives in
 * a {@link ThreadLocal}, so whoever sets it must clear it on the same thread (use {@link #callAs} or a
 * {@code finally} block); a leaked context would send the next request on that pooled thread to the wrong tenant.
 * <p>
 * It deliberately holds no database name: that is derived from the tenant id on demand by
 * {@link TenantDatabaseNameResolver}.
 */
public final class TenantContext {

	private record State(UUID tenantId, UUID userId, Integer maxProjects, Integer maxUsers, Integer maxStorageMB) {
	}

	private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

	private TenantContext() {
	}

	public static void setCurrentTenant(UUID tenantId) {
		set(new State(tenantId, null, null, null, null));
	}

	/** Sets the full context for an authenticated request. */
	public static void set(UUID tenantId, UUID userId, int maxProjects, int maxUsers, int maxStorageMB) {
		set(new State(tenantId, userId, maxProjects, maxUsers, maxStorageMB));
	}

	private static void set(State state) {
		if (state.tenantId() == null) {
			throw new IllegalArgumentException("tenantId must not be null");
		}
		CURRENT.set(state);
	}

	public static Optional<UUID> getCurrentTenant() {
		return state().map(State::tenantId);
	}

	/** The tenant, for code that must not run without one. */
	public static UUID requireCurrentTenant() {
		return getCurrentTenant().orElseThrow(() -> new IllegalStateException("No tenant in the current context"));
	}

	public static Optional<UUID> getCurrentUser() {
		return state().map(State::userId);
	}

	public static Optional<Integer> getMaxProjects() {
		return state().map(State::maxProjects);
	}

	public static Optional<Integer> getMaxUsers() {
		return state().map(State::maxUsers);
	}

	public static Optional<Integer> getMaxStorageMB() {
		return state().map(State::maxStorageMB);
	}

	public static void clear() {
		CURRENT.remove();
	}

	/** Runs {@code action} for {@code tenantId}, then restores whatever context was there before. */
	public static <T> T callAs(UUID tenantId, Supplier<T> action) {
		State previous = CURRENT.get();
		setCurrentTenant(tenantId);
		try {
			return action.get();
		}
		finally {
			if (previous == null) {
				CURRENT.remove();
			}
			else {
				CURRENT.set(previous);
			}
		}
	}

	private static Optional<State> state() {
		return Optional.ofNullable(CURRENT.get());
	}

}
