package com.sprintmodus.common_lib.security;

import java.util.UUID;

/**
 * Who is calling and for which tenant, as stated by a verified JWT. {@code role} is the organization-level role
 * ({@code OWNER}, {@code ADMIN} or {@code MEMBER}); per-work-item roles are a separate concept. The limits come from
 * the subscription at the time the token was issued, so services never need the master database.
 */
public record AuthenticatedUser(UUID userCode, String email, UUID tenantId, String organizationCode, String role,
		String plan, int maxProjects, int maxUsers, int maxStorageMB) {
}
