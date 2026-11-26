package com.ecut.campuspocket.network

import com.ecut.campuspocket.data.AppPreferences
import com.ecut.campuspocket.data.CredentialVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SessionRecoveryResult {
    /** 本机会话仍然有效。 */
    data object AlreadyValid : SessionRecoveryResult

    /** 用保存的账号密码自动登录成功。 */
    data object AutoLoggedIn : SessionRecoveryResult

    /** 未开启自动登录或没有保存密码。 */
    data object NoCredentials : SessionRecoveryResult

    /** 尝试过自动登录但失败。 */
    data class Failed(val message: String) : SessionRecoveryResult
}

/**
 * 统一认证与零信任门户的会话自愈：
 * 先试本机 CASTGC/门户会话，失败后按用户开关尝试加密保存的账号密码后台无感登录。
 */
class SessionRecoveryManager(
    private val ssoManager: CasSsoManager,
    private val vault: CredentialVault,
    private val preferences: AppPreferences,
    private val autoLoginFactory: () -> CasAutoLogin,
    private val electricityIndexUrlProvider: () -> String,
    private val clearHostCookies: suspend (String) -> Unit = {}
) {

    private val mutex = Mutex()

    fun isAutoLoginReady(): Boolean = preferences.autoLoginEnabled && vault.hasCredentials

    suspend fun recoverCasSession(forceRetry: Boolean = false): SessionRecoveryResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (ssoManager.ensureServiceSession(CampusEndpoints.EHALL_CARD_SERVICE_URL)) {
                    return@withLock SessionRecoveryResult.AlreadyValid
                }
                if (!preferences.autoLoginEnabled || !vault.hasCredentials) {
                    return@withLock SessionRecoveryResult.NoCredentials
                }
                val now = System.currentTimeMillis()
                if (!forceRetry && now < preferences.autoLoginBlockedUntil) {
                    return@withLock SessionRecoveryResult.Failed("自动登录刚刚失败，已暂停 3 分钟")
                }
                val credentials = vault.load()
                    ?: return@withLock SessionRecoveryResult.Failed("本机保存的账号密码无法解密")
                autoLoginFactory().login(credentials.username, credentials.password).fold(
                    onSuccess = {
                        preferences.autoLoginBlockedUntil = 0L
                        SessionRecoveryResult.AutoLoggedIn
                    },
                    onFailure = {
                        preferences.autoLoginBlockedUntil = now + FAILURE_COOLDOWN_MS
                        SessionRecoveryResult.Failed(it.message ?: "自动登录失败")
                    }
                )
            }
        }

    /**
     * 零信任门户（atrust1.ecut.edu.cn）无感登录。
     *
     * 先检查本机门户会话是否依然有效；失效且开启自动登录时，后台走零信任 CAS 自动认证，
     * 成功后直接把门户 Cookie 落库，打开门户 WebView 时注入即可免输入密码。
     */
    suspend fun recoverPortalSession(forceRetry: Boolean = false): SessionRecoveryResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val autoLogin = autoLoginFactory()
                if (autoLogin.checkPortalSessionValid()) {
                    return@withLock SessionRecoveryResult.AlreadyValid
                }
                if (!preferences.autoLoginEnabled || !vault.hasCredentials) {
                    return@withLock SessionRecoveryResult.NoCredentials
                }
                val now = System.currentTimeMillis()
                if (!forceRetry && now < preferences.autoLoginBlockedUntil) {
                    return@withLock SessionRecoveryResult.Failed("自动登录刚刚失败，已暂停 3 分钟")
                }
                val credentials = vault.load()
                    ?: return@withLock SessionRecoveryResult.Failed("本机保存的账号密码无法解密")
                autoLogin.loginPortal(credentials.username, credentials.password).fold(
                    onSuccess = {
                        preferences.autoLoginBlockedUntil = 0L
                        SessionRecoveryResult.AutoLoggedIn
                    },
                    onFailure = {
                        preferences.autoLoginBlockedUntil = now + FAILURE_COOLDOWN_MS
                        SessionRecoveryResult.Failed(it.message ?: "零信任门户自动登录失败")
                    }
                )
            }
        }

    /**
     * 校内教务直连专用：恢复 CAS 后立刻换一次正方教务会话。
     */
    suspend fun recoverJwDirectSession(forceRetry: Boolean = false): SessionRecoveryResult =
        withContext(Dispatchers.IO) {
            val casResult = recoverCasSession(forceRetry = forceRetry)
            if (casResult is SessionRecoveryResult.Failed ||
                casResult is SessionRecoveryResult.NoCredentials
            ) {
                return@withContext casResult
            }
            ssoManager.ensureServiceSession(CampusEndpoints.JW_SSO_URL)
            casResult
        }

    /**
     * 电费专用：先恢复 CAS，清掉可能损坏的旧 xgwx Cookie，再用 CAS 换一次 xgwx 会话。
     */
    suspend fun recoverElectricitySession(forceRetry: Boolean = false): SessionRecoveryResult =
        withContext(Dispatchers.IO) {
            val casResult = recoverCasSession(forceRetry = forceRetry)
            if (casResult is SessionRecoveryResult.Failed ||
                casResult is SessionRecoveryResult.NoCredentials
            ) {
                return@withContext casResult
            }
            val electricityUrl = runCatching { electricityIndexUrlProvider() }.getOrDefault("")
            if (electricityUrl.isNotBlank()) {
                runCatching { clearHostCookies("xgwx.ecut.edu.cn") }
                ssoManager.ensureServiceSession(electricityUrl)
            }
            casResult
        }

    companion object {
        private const val FAILURE_COOLDOWN_MS = 3 * 60 * 1000L
    }
}
