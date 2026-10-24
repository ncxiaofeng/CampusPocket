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
    val helperText: String? = null
)

object CampusEndpoints {
    const val SYSTEM_JW = "ecut_jw"
    const val SYSTEM_CAS = "ecut_cas"
    const val SYSTEM_ELECTRICITY = "ecut_electricity"
    /** 零信任 WebVPN 代理会话（从门户里打开“新教务管理系统”后获得）。 */
    const val SYSTEM_JW_PROXY = "ecut_jw_proxy"
    /** ehall 一卡通会话（打开校园卡官方页后保存）。 */
    const val SYSTEM_CARD = "ecut_card"
    /** U净洗衣会话（JWT，存在 auth_credentials.cookie 字段里，按不透明密钥处理）。 */
    const val SYSTEM_UJING = "ecut_ujing"

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

    /** CAS 本体，用于补充 CASTGC 等 Cookie。 */
    const val AUTH_SERVER_LOGIN_URL = "https://authserver.ecut.edu.cn/authserver/login"

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

    val JW_LOGIN = LoginTarget(
        systemId = SYSTEM_JW,
        title = "教务系统登录",
        startUrl = JW_SSO_URL,
        primaryHost = "172.20.130.13",
        extraCookieUrls = listOf(AUTH_SERVER_LOGIN_URL),
        requiredPrimaryCookie = "jsessionid"
    )

    /** 网上办事大厅（ehall），聚合教务、校园卡、通知等官方应用。 */
    const val EHALL_BASE_URL = "https://ehall.ecut.edu.cn"
    const val EHALL_URL = "$EHALL_BASE_URL/new/index.html"

    /** ehall 一卡通：余额与流水明细。 */
    const val EHALL_CARD_URL =
        "https://ehall.ecut.edu.cn/psfw/sys/psxykapp/*default/index.do" +
            "?EMAP_LANG=zh&THEME=indigo#/xykxx"

    /** U净洗衣后端（H5 与原生 App 共用）。 */
    const val UJING_BASE_URL = "https://phoenix.ujing.online:443/api/v1"
    const val UJING_H5_URL =
        "https://wx.zhinengxiyifang.cn/static/washer-h5/ujing/latest/index.html"

    /** 图书馆空间预约（libspace），空位接口无需登录，预约在官方 H5 完成。 */
    const val LIBRARY_BASE_URL = "https://libspace.ecut.edu.cn"
    const val LIBRARY_H5_URL = "$LIBRARY_BASE_URL/h5/index.html#/seat"

    /** 宿舍水电缴费 H5（今日校园“宿舍缴费”里的学生宿舍缴费）。 */
    const val ELECTRICITY_H5_BASE = "https://xgwx.ecut.edu.cn"
    const val ELECTRICITY_INDEX_URL =
        "https://xgwx.ecut.edu.cn/app/wechat/index.jsp" +
            "?appId=wx3be3089d48d67c4e&operatorId=admin" +
            "&operatorPwd=REDACTED"

    val ELECTRICITY_LOGIN = LoginTarget(
        systemId = SYSTEM_ELECTRICITY,
        title = "宿舍电费登录",
        startUrl = ELECTRICITY_INDEX_URL,
        primaryHost = "xgwx.ecut.edu.cn",
        extraCookieUrls = listOf(AUTH_SERVER_LOGIN_URL),
        requiredPrimaryCookie = "jsessionid",
        primaryPathPrefix = "/app",
        helperText = "登录后可在电费页同步电表余额与每日用电量。"
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
        helperText = "网页登录只完成零信任认证；要访问课表/成绩，请同时连接校园 Wi-Fi，或使用 aTrust 客户端建立内网隧道。"
    )
}
