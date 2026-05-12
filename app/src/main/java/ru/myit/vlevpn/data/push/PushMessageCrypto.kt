package ru.myit.vlevpn.data.push

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PushMessageCrypto {
    fun verifySignature(
        deviceSecretBase64: String,
        deliveryId: String,
        campaignId: String,
        encryptedPayload: String,
        signatureHex: String,
    ): Boolean {
        val deviceSecret = decodeBase64Url(deviceSecretBase64)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(deviceSecret, "HmacSHA256"))
        val message = "$deliveryId.$campaignId.$encryptedPayload".toByteArray(Charsets.UTF_8)
        val expected = mac.doFinal(message)
        val actual = runCatching { signatureHex.hexToBytes() }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    fun decryptPayload(deviceSecretBase64: String, encryptedPayload: String): String {
        val deviceSecret = decodeBase64Url(deviceSecretBase64)
        val combined = decodeBase64Url(encryptedPayload)
        require(combined.size > NONCE_BYTES) { "Invalid push payload" }

        val nonce = combined.copyOfRange(0, NONCE_BYTES)
        val ciphertext = combined.copyOfRange(NONCE_BYTES, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(deriveDeviceEncryptionKey(deviceSecret), "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    fun signInAppRequest(
        deviceSecretBase64: String,
        appKey: String,
        installId: String,
        timestamp: Long,
        nonce: String,
    ): String {
        val deviceSecret = decodeBase64Url(deviceSecretBase64)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(deviceSecret, "HmacSHA256"))
        val message = "vle-in-app-v1.$appKey.$installId.$timestamp.$nonce".toByteArray(Charsets.UTF_8)
        return mac.doFinal(message).joinToString("") { "%02x".format(it) }
    }

    private fun deriveDeviceEncryptionKey(deviceSecret: ByteArray): ByteArray =
        MessageDigest
            .getInstance("SHA-256")
            .digest(AES_KEY_PREFIX + deviceSecret)

    private fun decodeBase64Url(value: String): ByteArray {
        val padding = "=".repeat((4 - value.length % 4) % 4)
        return Base64.decode(value + padding, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private const val NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private val AES_KEY_PREFIX = "vle-vpn-push-aes-gcm-v1:".toByteArray(Charsets.UTF_8)
}
