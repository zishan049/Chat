package com.chat.app.identity.domain

import com.chat.app.core.common.Result
import com.chat.app.domain.model.Identity
import com.chat.app.domain.repository.IdentityRepository
import javax.inject.Inject

/**
 * Loads the existing local identity.
 * Used on app startup to check if onboarding is needed.
 */
class GetIdentityUseCase @Inject constructor(
    private val identityRepository: IdentityRepository,
) {
    suspend operator fun invoke(): Result<Identity> {
        return identityRepository.getIdentity()
    }
}
