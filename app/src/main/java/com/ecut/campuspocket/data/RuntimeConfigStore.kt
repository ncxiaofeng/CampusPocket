package com.ecut.campuspocket.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class RuntimeConfig(
    val electricityOperatorPwd: String = "",
    val ujingHmacKey: String = "",
    val huilifeAppSignSalt: String = "",
    /** 通过远程一次性/限时码导入的字段键集合（"e"/"u"/"h"），UI 中锁定不展示明文防抄写。 */
    val sealedFields: Set<String> = emptySet()
) {
    val hasElectricity: Boolean get() = electricityOperatorPwd.isNotBlank()
    val hasUjing: Boolean get() = ujingHmacKey.isNotBlank()
    val hasHuiLife: Boolean get() = huilifeAppSignSalt.isNotBlank()

    fun isSealed(shortKey: String): Boolean = shortKey in sealedFields
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

    /** 配置保存/清空时递增，页面可以据此重新读取 [get]。 */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun get(): RuntimeConfig {
        cached?.let { return it }
        val loaded = load()
        cached = loaded
        return loaded
    }

    fun update(config: RuntimeConfig) {
        val normalizedSealed = buildSet {
            if ("e" in config.sealedFields && config.hasElectricity) add("e")
            if ("u" in config.sealedFields && config.hasUjing) add("u")
            if ("h" in config.sealedFields && config.hasHuiLife) add("h")
        }
        val normalized = config.copy(sealedFields = normalizedSealed)
        val plain = JSONObject()
            .put("e", normalized.electricityOperatorPwd)
            .put("u", normalized.ujingHmacKey)
            .put("h", normalized.huilifeAppSignSalt)
            .put("sealed", normalizedSealed.joinToString(","))
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
        cached = normalized
        _version.value++
    }

    fun clear() {
        prefs.edit().remove(KEY_PAYLOAD).apply()
        cached = RuntimeConfig()
        _version.value++
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
            val sealedRaw = json.optString("sealed")
            val sealed = sealedRaw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            RuntimeConfig(
                electricityOperatorPwd = json.optString("e"),
                ujingHmacKey = json.optString("u"),
                huilifeAppSignSalt = json.optString("h"),
                sealedFields = sealed
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
