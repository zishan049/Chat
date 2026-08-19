package com.chat.app.domain.repository

import com.chat.app.core.common.Result
import com.chat.app.domain.model.Identity

/**
 * Repository interface for local identity operations.
 * The implementation handles persistence and crypto key management.
 */
interface IdentityRepository {

    /**
     * Creates a new local identity with the given display name.
     * Generates an EC key pair and persists the identity.
     * Should only be called once during onboarding.
     */
    suspend fun createIdentity(displayName: String, avatarUri: String? = null): Result<Identity>

    /**
     * Loads the existing local identity.
     * Returns Failure if no identity has been created yet.
     */
    suspend fun getIdentity(): Result<Identity>

    /**
     * Updates the display name and/or avatar of the existing identity.
     */
    suspend fun updateIdentity(displayName: String, avatarUri: String? = null): Result<Identity>

    /**
     * Returns true if a local identity exists.
     */
    suspend fun hasIdentity(): Boolean
}
