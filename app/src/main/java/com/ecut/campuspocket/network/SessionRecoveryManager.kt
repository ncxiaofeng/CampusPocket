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
 * 统一认证会话自愈：先试本机 CASTGC，失败后按用户开关尝试账号密码自动登录。
 */
class SessionRecoveryManager(
    private val ssoManager: CasSsoManager,
    private val vault: CredentialVault,
    private val preferences: AppPreferences,
    private val autoLoginFactory: () -> CasAutoLogin
) {

    private val mutex = Mutex()

    suspend fun recoverCasSession(): SessionRecoveryResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (ssoManager.ensureServiceSession(CampusEndpoints.EHALL_CARD_SERVICE_URL)) {
                return@withLock SessionRecoveryResult.AlreadyValid
            }
            if (!preferences.autoLoginEnabled || !vault.hasCredentials) {
                return@withLock SessionRecoveryResult.NoCredentials
            }
            val now = System.currentTimeMillis()
            if (now < preferences.autoLoginBlockedUntil) {
                return@withLock SessionRecoveryResult.Failed("自动登录刚刚失败，已暂停 10 分钟")
            }
            val credentials = vault.load()
                ?: return@withLock SessionRecoveryResult.Failed("本机保存的账号密码无法解密")
            autoLoginFactory().login(credentials.username, credentials.password).fold(
                onSuccess = {
                    preferences.autoLoginBlockedUntil = 0L
                    val serviceReady = ssoManager.ensureServiceSession(
                        CampusEndpoints.EHALL_CARD_SERVICE_URL
                    )
                    if (serviceReady) SessionRecoveryResult.AutoLoggedIn
                    else SessionRecoveryResult.Failed("密码登录成功，但业务系统仍未授权")
                },
                onFailure = {
                    preferences.autoLoginBlockedUntil = now + 10 * 60 * 1000L
                    SessionRecoveryResult.Failed(it.message ?: "自动登录失败")
                }
            )
        }
    }

    /**
     * 电费专用：先恢复 CAS，再单独用 CAS 换一次 xgwx 会话。
     * 返回 true 表示电费接口现在应该可用。
     */
    suspend fun recoverElectricitySession(): SessionRecoveryResult = withContext(Dispatchers.IO) {
        val casResult = recoverCasSession()
        if (casResult is SessionRecoveryResult.Failed ||
            casResult is SessionRecoveryResult.NoCredentials
        ) {
            return@withContext casResult
        }
        // 电费 service 换票失败不一定代表本机 xgwx 会话不可用；
        // 仍交给上层重试一次真实接口，再由错误信息决定是否让用户登录。
        ssoManager.ensureServiceSession(CampusEndpoints.ELECTRICITY_INDEX_URL)
        casResult
    }
}
