package com.sprintmodus.common_lib.security;

import java.util.UUID;

/** Answers "is this user still an active member of this tenant?", which a signed token alone cannot. */
public interface TenantMembershipVerifier {

	/**
	 * Whether {@code userCode} exists, is active and is not deleted in the database of {@code tenantId}. Throws a Spring
	 * {@code DataAccessException} if that database cannot be reached; that is an infrastructure failure, not a "no".
	 */
	boolean isActiveMember(UUID tenantId, UUID userCode);

}
