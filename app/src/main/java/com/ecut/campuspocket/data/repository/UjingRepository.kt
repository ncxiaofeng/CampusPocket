package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.local.AuthCredentialEntity
import com.ecut.campuspocket.data.local.AuthDao
import com.ecut.campuspocket.data.remote.ujing.UjingClient
import com.ecut.campuspocket.data.remote.ujing.UjingOrder
import com.ecut.campuspocket.data.remote.ujing.UjingProgramInfo
import com.ecut.campuspocket.data.remote.ujing.UjingSession
import com.ecut.campuspocket.data.remote.ujing.UjingStore
import com.ecut.campuspocket.data.remote.ujing.UjingStoreInfo
import com.ecut.campuspocket.data.remote.ujing.UjingStoreRef
import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.SessionExpiredException

/**
 * U净洗衣会话与只读查询。
 *
 * JWT 存在 auth_credentials 的 cookie 字段里（SQLCipher 加密），不单独建表，
 * 避免数据库迁移；退出登录时删除该行即可。
 */
class UjingRepository(
    private val authDao: AuthDao,
    private val client: UjingClient
) {

    suspend fun token(): String? =
        authDao.get(CampusEndpoints.SYSTEM_UJING)?.cookie?.takeIf { it.isNotBlank() }

    suspend fun hasSession(): Boolean = token() != null

    suspend fun sendCaptcha(mobile: String) {
        client.sendCaptcha(mobile)
    }

    suspend fun login(mobile: String, captcha: String): UjingSession {
        val session = client.login(mobile, captcha)
        authDao.upsert(
            AuthCredentialEntity(
                systemId = CampusEndpoints.SYSTEM_UJING,
                domain = "phoenix.ujing.online",
                cookie = session.token,
                updatedAt = System.currentTimeMillis()
            )
        )
        return session
    }

    suspend fun logout() {
        authDao.delete(CampusEndpoints.SYSTEM_UJING)
    }

    suspend fun nearbyStores(lat: Double, lng: Double): List<UjingStore> =
        client.nearbyStores(lat, lng, requireToken())

    suspend fun storeInfo(storeId: String): UjingStoreInfo =
        client.storeInfo(storeId, requireToken())

    suspend fun programInfo(storeId: String, deviceTypeId: Int): UjingProgramInfo =
        client.programInfo(storeId, deviceTypeId, requireToken())

    suspend fun waitTime(storeId: String, deviceTypeId: Int): Int =
        client.waitTime(storeId, deviceTypeId, requireToken())

    suspend fun runningOrders(): List<UjingOrder> =
        client.runningOrders(requireToken())

    suspend fun historyOrders(limit: Int = 10): List<UjingOrder> =
        client.historyOrders(requireToken(), limit)

    /**
     * 从最近订单反查洗衣房。`stores/near` 只返回最近的 10 家，
     * 用户常去的 16栋-1 可能不在里面，所以用订单详情里的 storeId 补一个“最近使用”。
     */
    suspend fun recentStores(orders: List<UjingOrder>): List<UjingStoreRef> {
        val token = requireToken()
        return orders.take(3)
            .mapNotNull { order ->
                runCatching { client.orderDetailStore(order.orderId, token) }.getOrNull()
            }
            .distinctBy { it.storeId }
    }

    private suspend fun requireToken(): String =
        token() ?: throw SessionExpiredException("U净登录已过期，请重新登录")
}
