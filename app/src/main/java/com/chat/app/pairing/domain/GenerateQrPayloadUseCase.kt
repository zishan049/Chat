package com.chat.app.pairing.domain

import android.util.Base64
import com.chat.app.core.common.AppError
import com.chat.app.core.common.Result
import com.chat.app.core.network.PortProvider
import com.chat.app.crypto.KeyManager
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.pairing.domain.model.QrPayload
import java.net.NetworkInterface
import java.util.Collections
import javax.inject.Inject

class GenerateQrPayloadUseCase @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val keyManager: KeyManager,
    private val portProvider: PortProvider
) {

    suspend operator fun invoke(): Result<String> {
        val identityResult = identityRepository.getIdentity()
        if (identityResult is Result.Failure) {
            return identityResult
        }
        val identity = (identityResult as Result.Success).data

        val localIp = getLocalIpAddress()

        val unsignedPayload = QrPayload(
            version = 1,
            id = identity.id,
            displayName = identity.displayName,
            publicKeyBase64 = identity.publicKeyBase64,
            fingerprint = identity.fingerprint,
            lanIp = localIp,
            port = portProvider.getActivePort(),
            timestamp = System.currentTimeMillis()
        )

        val canonicalBytes = unsignedPayload.toCanonicalString().toByteArray(Charsets.UTF_8)
        val signResult = keyManager.signPayload(canonicalBytes)
        if (signResult is Result.Failure) {
            return Result.Failure(AppError.EncryptionFailed("Failed to sign QR payload", (signResult as Result.Failure).error.cause))
        }

        val signatureBase64 = Base64.encodeToString((signResult as Result.Success).data, Base64.NO_WRAP)
        val signedPayload = unsignedPayload.copy(signature = signatureBase64)

        return Result.Success(signedPayload.toJson())
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
