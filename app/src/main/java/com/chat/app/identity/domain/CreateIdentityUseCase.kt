package com.chat.app.identity.domain

import com.chat.app.core.common.Result
import com.chat.app.domain.model.Identity
import com.chat.app.domain.repository.IdentityRepository
import javax.inject.Inject

/**
 * Creates a new local identity during onboarding.
 */
class CreateIdentityUseCase @Inject constructor(
    private val identityRepository: IdentityRepository,
) {
    suspend operator fun invoke(displayName: String, avatarUri: String? = null): Result<Identity> {
        if (displayName.isBlank()) {
            return Result.Failure(
                com.chat.app.core.common.AppError.IdentityCreationFailed("Display name cannot be blank")
            )
        }
        return identityRepository.createIdentity(displayName.trim(), avatarUri)
    }
}
