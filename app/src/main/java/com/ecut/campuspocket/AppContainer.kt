package com.ecut.campuspocket

import android.content.Context
import com.ecut.campuspocket.data.local.CampusDatabase
import com.ecut.campuspocket.data.remote.ElectricitySource
import com.ecut.campuspocket.data.remote.electricity.XgwxElectricityClient
import com.ecut.campuspocket.data.remote.card.CampusCardClient
import com.ecut.campuspocket.data.remote.jw.JwAdapter
import com.ecut.campuspocket.data.remote.library.LibrarySeatClient
import com.ecut.campuspocket.data.remote.ujing.UjingClient
import com.ecut.campuspocket.data.repository.AuthRepository
import com.ecut.campuspocket.data.repository.ElectricityRepository
import com.ecut.campuspocket.data.repository.ExamRepository
import com.ecut.campuspocket.data.repository.GradeRepository
import com.ecut.campuspocket.data.repository.JwSyncManager
import com.ecut.campuspocket.data.repository.ScheduleRepository
import com.ecut.campuspocket.data.repository.UjingRepository
import com.ecut.campuspocket.data.repository.XgwxElectricityRepository
import com.ecut.campuspocket.network.CampusCookieJar
import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.CampusNetworkState
import com.ecut.campuspocket.network.CampusNetworkStatus
import com.ecut.campuspocket.network.ProxySessionRequiredException
import com.ecut.campuspocket.network.SessionExpiredInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 极简手工依赖容器；纯本地应用不需要引入 DI 框架。
 */
object AppContainer {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private fun requireContext(): Context =
        appContext ?: error("AppContainer 尚未初始化")

    val database: CampusDatabase by lazy {
        CampusDatabase.create(requireContext())
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            authDao = database.authDao(),
            cookieDao = database.cookieDao(),
            electricityDao = database.electricityDao(),
            courseDao = database.courseDao(),
            gradeDao = database.gradeDao(),
            examDao = database.examDao()
        )
    }

    /**
     * 电费数据源。真实接口接入前为 null，Worker 不会启动。
     */
    @Volatile
    var electricitySource: ElectricitySource? = null

    val scheduleRepository: ScheduleRepository by lazy {
        ScheduleRepository(database.courseDao())
    }

    val electricityRepository: ElectricityRepository by lazy {
        ElectricityRepository(database.electricityDao())
    }

    val gradeRepository: GradeRepository by lazy {
        GradeRepository(database.gradeDao())
    }

    val examRepository: ExamRepository by lazy {
        ExamRepository(database.examDao())
    }

    val xgwxElectricityClient: XgwxElectricityClient by lazy {
        // 宿舍缴费过期时会 302 到 CAS；允许跟随重定向，用本地 CASTGC 自动换票。
        XgwxElectricityClient(baseHttpClient)
    }

    val xgwxElectricityRepository: XgwxElectricityRepository by lazy {
        XgwxElectricityRepository(xgwxElectricityClient, electricityRepository)
    }

    val baseHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(CampusCookieJar(database.authDao(), database.cookieDao()))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 需要统一认证的请求使用这个客户端，会话过期会抛 SessionExpiredException。
     */
    val casHttpClient: OkHttpClient by lazy {
        baseHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(SessionExpiredInterceptor())
            .build()
    }

    val jwAdapter: JwAdapter by lazy {
        JwAdapter(casHttpClient) {
            val state = CampusNetworkStatus.state.value
            val proxy = authRepository.getCredential(CampusEndpoints.SYSTEM_JW_PROXY)
            when {
                // 校内直连最快。
                state is CampusNetworkState.OnCampus -> CampusEndpoints.JW_BASE_URL
                // 有 WebVPN 会话时优先走代理；检测中/未知也走代理，避免刚启动时抢跑超时。
                proxy != null -> CampusEndpoints.JW_PROXY_BASE_URL
                // 明确校外且没有代理会话，给出可操作的提示。
                state is CampusNetworkState.OffCampus -> throw ProxySessionRequiredException()
                // 检测中/未知且没有代理会话，先按校内直连尝试。
                else -> CampusEndpoints.JW_BASE_URL
            }
        }
    }

    val librarySeatClient: LibrarySeatClient by lazy {
        LibrarySeatClient(baseHttpClient, CampusEndpoints.LIBRARY_BASE_URL)
    }

    val campusCardClient: CampusCardClient by lazy {
        CampusCardClient(baseHttpClient)
    }

    val ujingClient: UjingClient by lazy {
        UjingClient(baseHttpClient)
    }

    val ujingRepository: UjingRepository by lazy {
        UjingRepository(database.authDao(), ujingClient)
    }

    val jwSyncManager: JwSyncManager by lazy {
        JwSyncManager(jwAdapter, scheduleRepository, gradeRepository, examRepository)
    }
}
