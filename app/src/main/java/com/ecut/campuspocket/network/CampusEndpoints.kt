package com.ecut.campuspocket.network


/**
 * 登录目标描述：WebView 打开哪个入口、抓哪些域名的 Cookie、如何判断登录完成。
 */
data class LoginTarget(
    val systemId: String,
    val title: String,
    val startUrl: String,
    val primaryHost: String,
    val extraCookieUrls: List<String>,
    /** 主域名必须具备的 Cookie 名（小写），null 表示只做通用校验。 */
    val requiredPrimaryCookie: String? = null,
    /** 主域名页面路径前缀，用于确认已经离开登录页。 */
    val primaryPathPrefix: String? = "/jwglxt",
    /** 只要存在其中一个 Cookie 即视为已登录（用于零信任 CAS）。 */
    val requiredAnyCookieNames: Set<String> = emptySet(),
    /** 凭证落库后是否清空 WebView 明文 Cookie。零信任门户需要保留，否则设为 false。 */
    val clearWebViewCookiesAfterSave: Boolean = true,
    /** 落到这些主机后自动完成登录，避免官方门户白屏卡住。 */
    val autoCompleteHosts: Set<String> = emptySet(),
    /** 打开 WebView 前先把加密库里该目标域的 Cookie 注入，已登录时可直接 SSO。 */
    val preloadStoredCookies: Boolean = false,
    /** 登录页下方的补充提示。 */
    val helperText: String? = null,
    /** 指定 WebView 的 User-Agent；null 表示教务桌面 UA。 */
    val userAgent: String? = null,
    /** 这些域名注入 Cookie 时使用 https（其余按主页面协议推断）。 */
    val httpsHosts: Set<String> = emptySet(),
    /** 在 CAS 登录页自动勾选「一周内免登录」。 */
    val rememberLogin: Boolean = false
)

object CampusEndpoints {
    const val SYSTEM_JW = "ecut_jw"
    /** 零信任 aTrust 登录态（与直接访问的 CAS 主凭据分开保存）。 */
    const val SYSTEM_CAS = "ecut_cas"
    /**
     * 统一身份认证主凭据（authserver.ecut.edu.cn 的 CASTGC）。
     *
     * 这是唯一真正需要“登录一次”的凭证：教务 / 电费 / 办事大厅 / 一卡通
     * 都是 CAS 的 service，只要 CASTGC 有效就能静默换票。
     */
    const val SYSTEM_CAS_MASTER = "ecut_cas_master"
    const val SYSTEM_ELECTRICITY = "ecut_electricity"
    /** 零信任 WebVPN 代理会话（从门户里打开“新教务管理系统”后获得）。 */
    const val SYSTEM_JW_PROXY = "ecut_jw_proxy"
    /** ehall 一卡通会话（打开校园卡官方页后保存）。 */
    const val SYSTEM_CARD = "ecut_card"
    /** U净洗衣会话（JWT，存在 auth_credentials.cookie 字段里，按不透明密钥处理）。 */
    const val SYSTEM_UJING = "ecut_ujing"
    /** 超星学习通会话（Cookie）。 */
    const val SYSTEM_CHAOXING = "ecut_chaoxing"
    /** 慧生活798 查询端 Token（ApplicationType=1,5）。 */
    const val SYSTEM_HUILIFE_ACCOUNT = "ecut_huilife_account"
    /** 慧生活798 设备控制端 Token（ApplicationType=1,1）。 */
    const val SYSTEM_HUILIFE_APP = "ecut_huilife_app"

    /** 教务系统按桌面浏览器处理，WebView 与 OkHttp 使用同一 UA。 */
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0"

    /** 教务系统（正方 jwglxt），仅校内网可访问。 */
    const val JW_BASE_URL = "http://172.20.130.13"

    /**
     * 零信任 WebVPN 代理下的教务系统。
     * 门户把内网 IP 映射成 `172-20-130-13.atrust.ecut.edu.cn`，路径与内网一致。
     */
    const val JW_PROXY_BASE_URL = "https://172-20-130-13.atrust.ecut.edu.cn"

    /** 代理会话必须落在该主机上；门户域 `atrust1.ecut.edu.cn` 的 Cookie 不能当教务代理用。 */
    val JW_PROXY_HOST: String = java.net.URI(JW_PROXY_BASE_URL).host

    /** CAS 本体，用于补充 CASTGC 等 Cookie。 */
    const val CAS_HOST = "authserver.ecut.edu.cn"
    const val AUTH_SERVER_LOGIN_URL = "https://authserver.ecut.edu.cn/authserver/login"

    /** CAS 有效主凭据的 Cookie 名（小写）。 */
    val CAS_TICKET_COOKIE_NAMES = setOf("castgc", "tgc", "mod_auth_cas")

    /**
     * 金智 SSO 入口，登录后落到“个人课表查询”。
     * url 参数已按 ehall 应用入口原样 URL 编码。
     */
    const val JW_SSO_URL =
        "http://172.20.130.13/sso/jziotlogin" +
            "?url=kbcx%2fxskbcx_cxXskbcxIndex.html%3fgnmkdm%3dN253508%26layout%3ddefault"

    /**
     * 空闲教室查询页（正方 cdjy 模块）。
     * 该模块各校功能号不同，先按 N2155 打开；若提示无权限，再尝试 N2151。
     * 走 /sso/jziotlogin 入口：本地 JSESSIONID 失效但 CASTGC 仍有效时可自动换票，
     * 不必让用户重新输密码。
     */
    const val JW_CLASSROOM_URL =
        "$JW_BASE_URL/sso/jziotlogin" +
            "?url=cdjy%2fcdjy_cxKxcdlb.html%3fgnmkdm%3dN2155%26layout%3ddefault"

    /** 学生通知公告移动页；原生列表与官方兜底页共用该路径。 */
    const val JW_NOTICE_PATH = "/jwglxt/xtgl/xwglMobile_cxMoreXwList.html"

    /** aTrust 代理入口（用户最初提供的地址），保留作为备用登录目标。 */
    const val ATRUST_LOGIN_URL =
        "https://authserver-ecut-edu-cn-s.atrust.ecut.edu.cn/authserver/login" +
            "?service=https%3A%2F%2Fatrust1.ecut.edu.cn%3A443%2Fpassport%2Fv1%2Fauth%2Fcas" +
            "%3FsfDomain%3Dcas.ecut.edu.cn"

    const val ATRUST_SERVICE_URL =
        "https://atrust1.ecut.edu.cn:443/passport/v1/auth/cas?sfDomain=cas.ecut.edu.cn"

    /** 零信任门户：认证后从这里进入校内系统 / 数字图书资源（官方指南）。 */
    const val ATRUST_PORTAL_URL = "https://atrust1.ecut.edu.cn"

    /**
     * 普通移动浏览器 UA（不带 WebView 的 `; wv`）。
     * aTrust 门户会检测客户端 WebView，带 wv 时只显示客户端专用的白屏 SPA。
     */
    const val MOBILE_BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36"

    /**
     * 统一身份认证登录：只拿 CASTGC，不绑定任何具体业务系统。
     * 登录一次后，教务 / 电费 / 办事大厅 / 一卡通都由 [CasSsoManager] 静默换票。
     */
    val CAS_LOGIN = LoginTarget(
        systemId = SYSTEM_CAS_MASTER,
        title = "统一身份认证",
        // 带一个公开可访问的 service：登录成功后 CAS 会落到办事大厅一卡通页，
        // 既完成主凭据保存，也顺带建立 ehall 会话；校外同样可用。
        startUrl = AUTH_SERVER_LOGIN_URL +
            "?service=" +
            java.net.URLEncoder.encode(EHALL_CARD_SERVICE_URL, "UTF-8"),
        primaryHost = CAS_HOST,
        extraCookieUrls = listOf(AUTH_SERVER_LOGIN_URL),
        requiredPrimaryCookie = null,
        primaryPathPrefix = null,
        requiredAnyCookieNames = CAS_TICKET_COOKIE_NAMES,
        // 保留 WebView Cookie：CAS 登录页与后续 service 跳转在同一个 WebView 里完成。
        clearWebViewCookiesAfterSave = false,
        preloadStoredCookies = true,
        httpsHosts = setOf(CAS_HOST, "ehall.ecut.edu.cn"),
        rememberLogin = true,
        helperText = "一次登录后，课表、成绩、考试、教室、电费、校园卡和办事大厅共用同一个统一认证会话。"
    )

    val JW_LOGIN = LoginTarget(
        systemId = SYSTEM_JW,
        title = "教务系统登录",
        startUrl = JW_SSO_URL,
        primaryHost = "172.20.130.13",
        extraCookieUrls = listOf(AUTH_SERVER_LOGIN_URL),
        requiredPrimaryCookie = "jsessionid",
        // 已有 CASTGC 时直接静默 SSO，不再让用户重复输入密码。
        preloadStoredCookies = true,
        httpsHosts = setOf(CAS_HOST),
        rememberLogin = true,
        helperText = "已登录统一身份认证时，这里会自动完成教务授权，无需再次输入密码。"
    )

    /** 网上办事大厅（ehall），聚合教务、校园卡、通知等官方应用。 */
    const val EHALL_BASE_URL = "https://ehall.ecut.edu.cn"
    const val EHALL_URL = "$EHALL_BASE_URL/new/index.html"

    /** ehall 一卡通：余额与流水明细。 */
    const val EHALL_CARD_URL =
        "https://ehall.ecut.edu.cn/psfw/sys/psxykapp/*default/index.do" +
            "?EMAP_LANG=zh&THEME=indigo#/xykxx"

    /** 一卡通的 CAS service 地址（去掉前端路由 #/xykxx，服务端不接收 fragment）。 */
    const val EHALL_CARD_SERVICE_URL =
        "https://ehall.ecut.edu.cn/psfw/sys/psxykapp/*default/index.do" +
            "?EMAP_LANG=zh&THEME=indigo"

    /** U净洗衣后端（H5 与原生 App 共用）。 */
    const val UJING_BASE_URL = "https://phoenix.ujing.online:443/api/v1"
    const val UJING_H5_URL =
        "https://wx.zhinengxiyifang.cn/static/washer-h5/ujing/latest/index.html"

    /** 超星学习通：登录落到 i.chaoxing.com，接口分布在多个子域。 */
    const val CHAOXING_HOST = "i.chaoxing.com"
    const val CHAOXING_LOGIN_URL = "https://i.chaoxing.com/base"
    const val CHAOXING_MOOC_API = "https://mooc1-api.chaoxing.com"
    const val CHAOXING_MOOC_WEB = "https://mooc1.chaoxing.com"
    const val CHAOXING_MOBILE_LEARN = "https://mobilelearn.chaoxing.com"
    const val CHAOXING_PASSPORT = "https://passport2.chaoxing.com"
    const val CHAOXING_NOTICE = "https://notice.chaoxing.com"

    val CHAOXING_HOSTS = setOf(
        CHAOXING_HOST,
        "passport2.chaoxing.com",
        "passport2-api.chaoxing.com",
        "mooc1.chaoxing.com",
        "mooc1-api.chaoxing.com",
        "mobilelearn.chaoxing.com",
        "notice.chaoxing.com"
    )

    val CHAOXING_LOGIN = LoginTarget(
        systemId = SYSTEM_CHAOXING,
        title = "学习通登录",
        startUrl = CHAOXING_LOGIN_URL,
        primaryHost = CHAOXING_HOST,
        extraCookieUrls = listOf(
            "https://i.chaoxing.com/base",
            "$CHAOXING_PASSPORT/",
            "https://passport2-api.chaoxing.com/",
            "$CHAOXING_MOOC_API/",
            "$CHAOXING_MOBILE_LEARN/",
            "$CHAOXING_MOOC_WEB/",
            "$CHAOXING_NOTICE/"
        ),
        requiredAnyCookieNames = setOf("_uid", "_d", "uid"),
        primaryPathPrefix = null,
        autoCompleteHosts = setOf(CHAOXING_HOST),
        preloadStoredCookies = true,
        userAgent = MOBILE_BROWSER_USER_AGENT,
        httpsHosts = CHAOXING_HOSTS,
        helperText = "登录后只读取作业、考试和签到活动，不会代签、代交。"
    )

    /** 慧生活798：H5 只是说明页，登录与设备接口走 i.ilife798.com。 */
    const val HUILIFE_BASE_URL = "https://i.ilife798.com/api/v1"
    const val HUILIFE_CID = "drinkwaterapp123456789"

    /** 图书馆空间预约（libspace），空位接口无需登录，预约在官方 H5 完成。 */
    const val LIBRARY_BASE_URL = "https://libspace.ecut.edu.cn"
    const val LIBRARY_H5_URL = "$LIBRARY_BASE_URL/h5/index.html#/seat"

    /** 宿舍水电缴费 H5（今日校园“宿舍缴费”里的学生宿舍缴费）。 */
    const val ELECTRICITY_H5_BASE = "https://xgwx.ecut.edu.cn"

    /**
     * operatorPwd 是宿舍缴费 H5 的固定系统参数，不再编进 APK；
     * 用户在「更多 → 高级配置」填写后运行期拼接。
     */
    fun electricityIndexUrl(operatorPwd: String): String =
        "https://xgwx.ecut.edu.cn/app/wechat/index.jsp" +
            "?appId=wx3be3089d48d67c4e&operatorId=admin" +
            "&operatorPwd=" + java.net.URLEncoder.encode(operatorPwd, "UTF-8")

    fun electricityLogin(operatorPwd: String): LoginTarget = LoginTarget(
        systemId = SYSTEM_ELECTRICITY,
        title = "宿舍电费登录",
        startUrl = electricityIndexUrl(operatorPwd),
        primaryHost = "xgwx.ecut.edu.cn",
        extraCookieUrls = listOf(AUTH_SERVER_LOGIN_URL),
        requiredPrimaryCookie = "jsessionid",
        // 只有真正落到缴费首页才算成功，避免把 xgwx 自己的登录页当成已登录。
        primaryPathPrefix = "/app/wechat/index",
        // 已有 CASTGC 时由 CAS 自动换票，直接落到宿舍缴费页；落到首页后自动保存返回。
        autoCompleteHosts = setOf("xgwx.ecut.edu.cn"),
        preloadStoredCookies = true,
        httpsHosts = setOf("xgwx.ecut.edu.cn", CAS_HOST),
        rememberLogin = true,
        helperText = "已登录统一身份认证时，这里会自动完成宿舍缴费授权，无需再次输入密码。"
    )

    val ATRUST_LOGIN = LoginTarget(
        systemId = SYSTEM_CAS,
        title = "零信任 VPN 登录",
        startUrl = ATRUST_LOGIN_URL,
        primaryHost = "authserver-ecut-edu-cn-s.atrust.ecut.edu.cn",
        extraCookieUrls = listOf(ATRUST_LOGIN_URL, ATRUST_SERVICE_URL),
        requiredAnyCookieNames = setOf("castgc", "tgc", "mod_auth_cas"),
        clearWebViewCookiesAfterSave = false,
        // aTrust 的 portal/shortcut.html 是给客户端 WebView 用的 SPA，普通 WebView 会一直白屏；
        // 到 atrust1 就说明 CAS 已回调，直接保存凭证并返回，不再渲染门户。
        autoCompleteHosts = setOf("atrust1.ecut.edu.cn"),
        preloadStoredCookies = true,
        httpsHosts = setOf(
            "authserver-ecut-edu-cn-s.atrust.ecut.edu.cn",
            "atrust1.ecut.edu.cn"
        ),
        rememberLogin = true,
        helperText = "网页登录只完成零信任认证；要访问课表/成绩，请同时连接校园 Wi-Fi，或使用 aTrust 客户端建立内网隧道。"
    )
}
