package com.ecut.campuspocket.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedLoginCredentials(
    val username: String,
    val password: String
)

/**
 * 可选的「账号密码自动登录」保险箱。
 *
 * 只有用户在设置里明确开启并保存后才会写入；密码用 Android Keystore 里的
 * AES-GCM 密钥加密，密钥不可导出，明文只在本机内存中短暂存在。
 * 关闭自动登录时会立即删除密文和 Keystore 密钥。
 */
class CredentialVault(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("campus_pocket_vault", Context.MODE_PRIVATE)

    val hasCredentials: Boolean
        get() = !prefs.getString(KEY_PAYLOAD, null).isNullOrBlank()

    fun save(credentials: SavedLoginCredentials) {
        val plain = JSONObject()
            .put("u", credentials.username)
            .put("p", credentials.password)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain)
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        cipher.iv.copyInto(payload, 0)
        encrypted.copyInto(payload, cipher.iv.size)
        prefs.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    fun load(): SavedLoginCredentials? {
        val encoded = prefs.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLength = 12
            require(payload.size > ivLength)
            val iv = payload.copyOfRange(0, ivLength)
            val encrypted = payload.copyOfRange(ivLength, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plain = String(cipher.doFinal(encrypted), Charsets.UTF_8)
            val json = JSONObject(plain)
            val username = json.optString("u")
            val password = json.optString("p")
            if (username.isBlank() || password.isBlank()) null
            else SavedLoginCredentials(username, password)
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_PAYLOAD).apply()
        runCatching {
            val keyStore = keyStore()
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val KEY_ALIAS = "campuspocket.login.vault"
        private const val KEY_PAYLOAD = "encrypted_login"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
