package com.sprintmodus.common_lib.security;

/** Why a token was refused. */
public enum TokenProblem {

	/** Missing, malformed, wrongly signed, from another issuer, or lacking a required claim. */
	INVALID,

	/** Correctly signed but past its expiry. */
	EXPIRED

}
