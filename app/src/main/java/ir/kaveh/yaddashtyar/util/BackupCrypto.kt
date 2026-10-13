package ir.kaveh.yaddashtyar.util

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * رمزنگاری فایل پشتیبان: AES-256-GCM با کلیدِ برگرفته از رمز کاربر (PBKDF2-SHA256).
 * قالب فایل: "YYENC1" + salt(16) + iv(12) + داده‌ی رمزشده.
 */
object BackupCrypto {
    private val MAGIC = "YYENC1".toByteArray(Charsets.US_ASCII)
    private const val ITER = 150_000

    private fun key(pw: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(pw.toCharArray(), salt, ITER, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun encryptStream(out: OutputStream, password: String): OutputStream {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        out.write(MAGIC); out.write(salt); out.write(iv)
        return CipherOutputStream(out, c)
    }

    fun isEncrypted(input: InputStream): Boolean {
        val b = ByteArray(MAGIC.size)
        var n = 0
        while (n < b.size) {
            val r = input.read(b, n, b.size - n)
            if (r < 0) break
            n += r
        }
        return n == b.size && b.contentEquals(MAGIC)
    }

    /** رمزگشایی کامل در یک فایل موقت؛ با رمز اشتباه استثنا می‌دهد و فایل را پاک می‌کند. */
    fun decryptToFile(input: InputStream, password: String, dest: File) {
        try {
            val head = ByteArray(MAGIC.size + 16 + 12)
            var n = 0
            while (n < head.size) {
                val r = input.read(head, n, head.size - n)
                if (r < 0) throw IllegalStateException("short")
                n += r
            }
            require(head.copyOfRange(0, MAGIC.size).contentEquals(MAGIC))
            val salt = head.copyOfRange(MAGIC.size, MAGIC.size + 16)
            val iv = head.copyOfRange(MAGIC.size + 16, head.size)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            CipherInputStream(input, c).use { cin ->
                dest.outputStream().use { cin.copyTo(it) }
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }
}
