package com.evolearn.kidszone.core

import java.security.MessageDigest
import java.security.SecureRandom

/** 家长PIN：SHA-256 加盐哈希存储；忘记只能卸载重装 */
object PinLock {

    fun isSet(): Boolean = Prefs.pinHash.isNotBlank()

    fun setup(pin: String) {
        val salt = newSalt()
        Prefs.pinSalt = salt
        Prefs.pinHash = sha256(salt + pin)
    }

    fun verify(pin: String): Boolean {
        val expect = Prefs.pinHash
        if (expect.isBlank() || pin.length != 4) return false
        val actual = sha256(Prefs.pinSalt + pin)
        // 定长hex常量时间比较
        return MessageDigest.isEqual(
            expect.toByteArray(), actual.toByteArray()
        )
    }

    private fun newSalt(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
