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

data class RuntimeConfig(
    val electricityOperatorPwd: String = "",
    val ujingHmacKey: String = "",
    val huilifeAppSignSalt: String = ""
) {
    val hasElectricity: Boolean get() = electricityOperatorPwd.isNotBlank()
    val hasUjing: Boolean get() = ujingHmacKey.isNotBlank()
    val hasHuiLife: Boolean get() = huilifeAppSignSalt.isNotBlank()
}

/**
 * 第三方固定参数的运行期配置。
 *
 * 这些值不再编进 APK，而是由用户在「高级配置」里填写后，用 Android Keystore
 * 的 AES-GCM 密钥加密保存在本机 SharedPreferences 中。
 */
class RuntimeConfigStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("campus_pocket_runtime_config", Context.MODE_PRIVATE)

    @Volatile
    private var cached: RuntimeConfig? = null

    fun get(): RuntimeConfig {
        cached?.let { return it }
        val loaded = load()
        cached = loaded
        return loaded
    }

    fun update(config: RuntimeConfig) {
        val plain = JSONObject()
            .put("e", config.electricityOperatorPwd)
            .put("u", config.ujingHmacKey)
            .put("h", config.huilifeAppSignSalt)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain)
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        cipher.iv.copyInto(payload, 0)
        encrypted.copyInto(payload, cipher.iv.size)
        prefs.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
        cached = config
    }

    fun clear() {
        prefs.edit().remove(KEY_PAYLOAD).apply()
        cached = RuntimeConfig()
        runCatching {
            val keyStore = keyStore()
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun load(): RuntimeConfig {
        val encoded = prefs.getString(KEY_PAYLOAD, null)?.takeIf { it.isNotBlank() }
            ?: return RuntimeConfig()
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > 12)
            val iv = payload.copyOfRange(0, 12)
            val encrypted = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            RuntimeConfig(
                electricityOperatorPwd = json.optString("e"),
                ujingHmacKey = json.optString("u"),
                huilifeAppSignSalt = json.optString("h")
            )
        }.getOrDefault(RuntimeConfig())
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
        private const val KEY_ALIAS = "campuspocket.runtime.config"
        private const val KEY_PAYLOAD = "encrypted_config"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
