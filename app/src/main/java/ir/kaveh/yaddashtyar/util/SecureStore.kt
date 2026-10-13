package ir.kaveh.yaddashtyar.util

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * ذخیرهٔ رمزشدهٔ مقادیر حساس (مثل کلید API) با کلید AES داخل Android Keystore.
 * کلید از گوشی خارج نمی‌شود؛ پس حتی با دسترسی به فایل‌های برنامه، مقدار خوانده نمی‌شود.
 */
object SecureStore {
    private const val ALIAS = "yy_secret_key_v1"
    private const val PREFS = "secure"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return String(c.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }

    fun get(ctx: Context, name: String): String {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(name, null) ?: return ""
        return try {
            decrypt(s)
        } catch (e: Exception) {
            "" // مثلاً بعد از انتقال به گوشی دیگر؛ کاربر دوباره وارد می‌کند
        }
    }

    fun put(ctx: Context, name: String, value: String) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (value.isEmpty()) e.remove(name)
        else try {
            e.putString(name, encrypt(value))
        } catch (ex: Exception) {
            e.remove(name)
        }
        e.apply()
    }
}
