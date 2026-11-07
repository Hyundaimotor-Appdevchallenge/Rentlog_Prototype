package com.example.pleosrantlog.data.backend

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    fun sha256Hex(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).hex()
    fun hmacSha256Hex(content: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return mac.doFinal(content.toByteArray()).hex()
    }
}

/** B-16: hash = SHA-256(previous hash + record content). */
class HashChain {
    var last: String = GENESIS
        private set

    fun append(content: String): String {
        last = Crypto.sha256Hex(last + content)
        return last
    }

    /** First 8 hex chars of the last hash formatted like "7f3a·c21e". */
    fun verifyCode(): String = formatVerifyCode(last)

    companion object {
        val GENESIS = "0".repeat(64)
        fun formatVerifyCode(hash: String): String = hash.take(8).let { "${it.take(4)}·${it.drop(4)}" }
    }
}
