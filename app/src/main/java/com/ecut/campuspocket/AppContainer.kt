package com.ecut.campuspocket

import android.content.Context
import com.ecut.campuspocket.data.local.CampusDatabase
import com.ecut.campuspocket.data.location.DeviceLocationProvider
import com.ecut.campuspocket.data.remote.ElectricitySource
import com.ecut.campuspocket.data.remote.electricity.XgwxElectricityClient
import com.ecut.campuspocket.data.remote.card.CampusCardClient
import com.ecut.campuspocket.data.remote.jw.JwAdapter
import com.ecut.campuspocket.data.remote.jw.JwBaseUrl
import com.ecut.campuspocket.data.remote.library.LibrarySeatClient
import com.ecut.campuspocket.data.remote.ujing.UjingClient
import com.ecut.campuspocket.data.remote.weather.WeatherClient
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
import com.ecut.campuspocket.network.CasSsoManager
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
        XgwxElectricityClient(baseHttpClient, ssoManager = casSsoManager)
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

    /**
     * CAS 单点登录：CASTGC 是唯一的“总凭证”，
     * 教务 / 电费 / 办事大厅 / 一卡通各自过期时都从这里静默换票。
     */
    val casSsoManager: CasSsoManager by lazy {
        CasSsoManager(authRepository, baseHttpClient)
    }

    val jwAdapter: JwAdapter by lazy {
        JwAdapter(casHttpClient, ssoManager = casSsoManager) {
            val state = CampusNetworkStatus.state.value
            val proxyCredential = authRepository.getCredential(CampusEndpoints.SYSTEM_JW_PROXY)
            // 只有落在教务代理域名上的会话才可用；门户主页 atrust1 的 Cookie 不算。
            val proxyReady = proxyCredential?.domain == CampusEndpoints.JW_PROXY_HOST
            val direct = JwBaseUrl(CampusEndpoints.JW_BASE_URL, isProxy = false)
            val proxy = JwBaseUrl(CampusEndpoints.JW_PROXY_BASE_URL, isProxy = true)
            when {
                // 校内只走直连：直连失败是会话问题，回退到代理只会把错误伪装成“代理过期”。
                state is CampusNetworkState.OnCampus -> listOf(direct)
                // 明确校外：只走代理；没有代理会话就给出可操作提示。
                state is CampusNetworkState.OffCampus -> {
                    if (proxyReady) listOf(proxy) else throw ProxySessionRequiredException()
                }
                // 检测中/未知：有代理会话时先代理再直连，否则按校内直连尝试。
                proxyReady -> listOf(proxy, direct)
                else -> listOf(direct)
            }
        }
    }

    val librarySeatClient: LibrarySeatClient by lazy {
        LibrarySeatClient(baseHttpClient, CampusEndpoints.LIBRARY_BASE_URL)
    }

    val campusCardClient: CampusCardClient by lazy {
        CampusCardClient(baseHttpClient, ssoManager = casSsoManager)
    }

    /** 公开天气接口，不需要登录态；复用同一个 OkHttp 连接池。 */
    val weatherClient: WeatherClient by lazy {
        WeatherClient(baseHttpClient)
    }

    /** 原生 LocationManager 定位，用于让天气使用设备实际位置。 */
    val deviceLocationProvider: DeviceLocationProvider by lazy {
        DeviceLocationProvider(requireContext())
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
