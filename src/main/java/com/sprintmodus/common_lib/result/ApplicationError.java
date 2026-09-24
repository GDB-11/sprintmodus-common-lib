package com.sprintmodus.common_lib.result;

/**
 * A business error carried by a {@link Result}. Each service declares its own <em>sealed</em> hierarchy extending this
 * interface (for example {@code AuthenticationError}); it is an interface rather than a sealed class here because a
 * sealed type cannot permit subtypes that live in another jar.
 */
public interface ApplicationError {

	/** Stable, machine-readable code for clients, e.g. {@code INVALID_CREDENTIALS}. */
	String code();

	/** Message that is safe to show to the caller. Never put tenant or user details in it. */
	String message();

}
