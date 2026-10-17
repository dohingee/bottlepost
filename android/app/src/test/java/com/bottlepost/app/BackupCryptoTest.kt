package com.bottlepost.app

import com.bottlepost.app.backup.DriveBackupManager
import com.bottlepost.app.ui.onboarding.OnboardingState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Year
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

class BackupCryptoTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test
    fun encryptThenDecryptRoundTrips() {
        val plain = DriveBackupManager.gzip("안녕하세요, 낯선 이여".repeat(50).toByteArray())
        val encrypted = DriveBackupManager.encrypt(key, plain)
        assertArrayEquals(plain, DriveBackupManager.decrypt(key, encrypted))
    }

    @Test(expected = AEADBadTagException::class)
    fun tamperedBackupIsRejected() {
        val encrypted = DriveBackupManager.encrypt(key, "hello".toByteArray())
        encrypted[encrypted.size - 1] = (encrypted.last() + 1).toByte()
        DriveBackupManager.decrypt(key, encrypted)
    }

    @Test
    fun onboardingRequiresAdultBirthYear() {
        val thisYear = Year.now().value
        assertFalse(OnboardingState(birthYear = "${thisYear - 18}").isAdult)
        assertTrue(OnboardingState(birthYear = "${thisYear - 19}").isAdult)
    }
}
