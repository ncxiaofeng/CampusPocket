package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.AppPreferences
import com.ecut.campuspocket.data.remote.huilife.HuiLifeBill
import com.ecut.campuspocket.data.remote.huilife.HuiLifeClient
import com.ecut.campuspocket.data.remote.huilife.HuiLifeDevice
import com.ecut.campuspocket.data.remote.huilife.HuiLifePlatform
import com.ecut.campuspocket.data.remote.huilife.HuiLifeSession
import com.ecut.campuspocket.data.remote.huilife.HuiLifeTokenExpiredException
import com.ecut.campuspocket.data.remote.huilife.HuiLifeWallet
import com.ecut.campuspocket.network.CampusEndpoints
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class HuiLifeDashboard(
    val devices: List<HuiLifeDevice>,
    val wallet: HuiLifeWallet?,
    val bills: List<HuiLifeBill>,
    val usingAppToken: Boolean,
    val deviceError: String? = null,
    val otherErrors: List<String> = emptyList()
)

class HuiLifeRepository(
    private val client: HuiLifeClient,
    private val authRepository: AuthRepository,
    private val preferences: AppPreferences
) {

    suspend fun accountToken(): String? =
        authRepository.getCredential(CampusEndpoints.SYSTEM_HUILIFE_ACCOUNT)
            ?.cookie?.takeIf { it.isNotBlank() }

    suspend fun appToken(): String? =
        authRepository.getCredential(CampusEndpoints.SYSTEM_HUILIFE_APP)
            ?.cookie?.takeIf { it.isNotBlank() }

    suspend fun hasAccount(): Boolean = accountToken() != null

    suspend fun hasApp(): Boolean = appToken() != null

    suspend fun saveSession(platform: HuiLifePlatform, session: HuiLifeSession, phone: String) {
        val systemId = if (platform == HuiLifePlatform.APP) {
            CampusEndpoints.SYSTEM_HUILIFE_APP
        } else {
            CampusEndpoints.SYSTEM_HUILIFE_ACCOUNT
        }
        authRepository.saveOpaqueToken(systemId, "i.ilife798.com", session.token)
        if (phone.isNotBlank()) preferences.huiLifePhone = phone
    }

    suspend fun logout(platform: HuiLifePlatform) {
        val systemId = if (platform == HuiLifePlatform.APP) {
            CampusEndpoints.SYSTEM_HUILIFE_APP
        } else {
            CampusEndpoints.SYSTEM_HUILIFE_ACCOUNT
        }
        authRepository.clearSystem(systemId, emptyList())
    }

    suspend fun loadDashboard(): Result<HuiLifeDashboard> = runCatching {
        val app = appToken()
        val account = accountToken()
        if (app == null && account == null) error("请先登录慧生活798")

        val appResult = app?.let { token ->
            runCatching { loadWith(token, HuiLifePlatform.APP) }
                .onFailure { error ->
                    if (error is HuiLifeTokenExpiredException) {
                        authRepository.clearSystem(CampusEndpoints.SYSTEM_HUILIFE_APP, emptyList())
                    }
                }
        }
        val appDashboard = appResult?.getOrNull()
        if (appDashboard != null && appDashboard.devices.isNotEmpty()) {
            return@runCatching appDashboard
        }
        val accountResult = account?.let { token ->
            runCatching { loadWith(token, HuiLifePlatform.ACCOUNT) }
                .onFailure { error ->
                    if (error is HuiLifeTokenExpiredException) {
                        authRepository.clearSystem(CampusEndpoints.SYSTEM_HUILIFE_ACCOUNT, emptyList())
                    }
                }
        }

        // 两个 Token 看到的设备列表可能不同：设备端 master 失败时，
        // 再用查询端试一次，避免“明明有设备却显示读取失败”。
        val accountDashboard = accountResult?.getOrNull()
        if (accountDashboard != null && accountDashboard.devices.isNotEmpty()) {
            return@runCatching accountDashboard.copy(
                wallet = appDashboard?.wallet ?: accountDashboard.wallet,
                bills = if (appDashboard?.bills?.isNotEmpty() == true) {
                    appDashboard.bills
                } else {
                    accountDashboard.bills
                },
                otherErrors = (
                    (appDashboard?.otherErrors ?: emptyList()) +
                        accountDashboard.otherErrors
                    ).distinct()
            )
        }
        appDashboard
            ?: accountDashboard
            ?: appResult?.exceptionOrNull()?.let { throw it }
            ?: accountResult?.exceptionOrNull()?.let { throw it }
            ?: error("慧生活设备列表读取失败")
    }

    private suspend fun loadWith(token: String, platform: HuiLifePlatform): HuiLifeDashboard =
        coroutineScope {
            val devicesDeferred = async { client.fetchMaster(token, platform) }
            val walletDeferred = async { client.fetchWallet(token, platform) }
            val billsDeferred = async { client.fetchBills(token, platform) }
            val devicesResult = devicesDeferred.await()
            val walletResult = walletDeferred.await()
            val billsResult = billsDeferred.await()
            val tokenError = sequenceOf(devicesResult, walletResult, billsResult)
                .mapNotNull { it.exceptionOrNull() }
                .filterIsInstance<com.ecut.campuspocket.data.remote.huilife.HuiLifeTokenExpiredException>()
                .firstOrNull()
            if (tokenError != null) throw tokenError
            val bills = billsResult.getOrDefault(emptyList())
            val favorites = devicesResult.getOrDefault(emptyList())
            // master 只返回“收藏设备”；用户说的“常用设备”往往在订单历史里。
            // 收藏为空时用最近账单里的 dev.id/name 补出来。
            val recentDevices = if (favorites.isEmpty()) {
                bills.mapNotNull { bill ->
                    bill.deviceId.takeIf { it.isNotBlank() }?.let { id ->
                        HuiLifeDevice(
                            id = id,
                            name = bill.deviceName.ifBlank { "最近使用设备" },
                            detail = "来自最近消费记录"
                        )
                    }
                }.distinctBy { it.id }
            } else {
                emptyList()
            }
            HuiLifeDashboard(
                devices = favorites.ifEmpty { recentDevices },
                wallet = walletResult.getOrNull(),
                bills = bills,
                usingAppToken = platform == HuiLifePlatform.APP,
                deviceError = devicesResult.exceptionOrNull()?.message,
                otherErrors = listOfNotNull(
                    walletResult.exceptionOrNull()?.message,
                    billsResult.exceptionOrNull()?.message
                )
            )
        }

    suspend fun startDevice(deviceId: String): Result<String> {
        val token = appToken() ?: return Result.failure(
            IllegalStateException("需要先完成设备控制登录")
        )
        return client.startDevice(token, deviceId)
    }

    suspend fun stopDevice(deviceId: String): Result<String> {
        val token = appToken() ?: return Result.failure(
            IllegalStateException("需要先完成设备控制登录")
        )
        return client.stopDevice(token, deviceId)
    }

    suspend fun captcha(sessionKey: String): ByteArray = client.captcha(sessionKey)

    suspend fun sendSms(
        phone: String,
        graphCode: String,
        sessionKey: String,
        platform: HuiLifePlatform
    ): Result<Unit> = client.sendSms(phone, graphCode, sessionKey, platform)

    suspend fun login(
        phone: String,
        smsCode: String,
        platform: HuiLifePlatform
    ): Result<HuiLifeSession> = client.login(phone, smsCode, platform).onSuccess {
        saveSession(platform, it, phone)
    }
}
