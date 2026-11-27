/// 登录目标描述：WebView 打开哪个入口、抓哪些域名的 Cookie、如何判断登录完成。
class LoginTarget {
  final String systemId;
  final String title;
  final String startUrl;
  final String primaryHost;
  final List<String> extraCookieUrls;
  final String? requiredPrimaryCookie;
  final String? primaryPathPrefix;
  final Set<String> requiredAnyCookieNames;
  final bool clearWebViewCookiesAfterSave;
  final Set<String> autoCompleteHosts;
  /// 若非空，自动完成除了匹配 host 外，还要求路径命中这些前缀之一。
  final Set<String> autoCompletePathPrefixes;
  /// 页面 URL 包含这些关键词（如短信/二次验证/新设备认证）时禁止自动退出。
  final Set<String> blockAutoCompleteKeywords;
  final bool preloadStoredCookies;
  final String? helperText;
  final String? userAgent;
  final Set<String> httpsHosts;
  final bool rememberLogin;

  const LoginTarget({
    required this.systemId,
    required this.title,
    required this.startUrl,
    required this.primaryHost,
    required this.extraCookieUrls,
    this.requiredPrimaryCookie,
    this.primaryPathPrefix = '/jwglxt',
    this.requiredAnyCookieNames = const {},
    this.clearWebViewCookiesAfterSave = true,
    this.autoCompleteHosts = const {},
    this.autoCompletePathPrefixes = const {},
    this.blockAutoCompleteKeywords = const {
      'mfa',
      'sms',
      'verify',
      'captcha',
      'second',
      'device',
      'bind',
      'check',
      'login',
    },
    this.preloadStoredCookies = false,
    this.helperText,
    this.userAgent,
    this.httpsHosts = const {},
    this.rememberLogin = false,
  });

  /// 判断当前落页 URL 是否允许触发自动保存并退出。
  bool shouldAutoCompleteUrl(String url) {
    final uri = Uri.tryParse(url);
    if (uri == null) return false;
    final host = uri.host.toLowerCase();
    if (!autoCompleteHosts.contains(host)) return false;

    final lowerFull = url.toLowerCase();
    for (final kw in blockAutoCompleteKeywords) {
      if (lowerFull.contains(kw)) return false;
    }

    if (autoCompletePathPrefixes.isNotEmpty) {
      final path = uri.path.toLowerCase();
      return autoCompletePathPrefixes.any((p) => path.startsWith(p.toLowerCase()));
    }
    return true;
  }
}

/// 东华理工大学校内服务地址与登录配置（完整对齐 Android v0.6.4）。
class CampusEndpoints {
  static const String systemJw = 'ecut_jw';
  static const String systemCas = 'ecut_cas';
  static const String systemCasMaster = 'ecut_cas_master';
  static const String systemElectricity = 'ecut_electricity';
  static const String systemJwProxy = 'ecut_jw_proxy';
  static const String systemCard = 'ecut_card';
  static const String systemUjing = 'ecut_ujing';
  static const String systemChaoxing = 'ecut_chaoxing';
  static const String systemHuiLifeAccount = 'ecut_huilife_account';
  static const String systemHuiLifeApp = 'ecut_huilife_app';

  static const String desktopUserAgent =
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

  static const String mobileBrowserUserAgent =
      'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) '
      'AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 '
      'Mobile/15E148 Safari/604.1';

  static const String jwBaseUrl = 'http://172.20.130.13';
  static const String jwProxyBaseUrl = 'https://172-20-130-13.atrust.ecut.edu.cn';
  static const String jwProxyHost = '172-20-130-13.atrust.ecut.edu.cn';

  static const String casHost = 'authserver.ecut.edu.cn';
  static const String authServerLoginUrl =
      'https://authserver.ecut.edu.cn/authserver/login';

  static const Set<String> casTicketCookieNames = {
    'castgc',
    'tgc',
    'mod_auth_cas',
  };

  static const String jwSsoUrl =
      'http://172.20.130.13/sso/jziotlogin'
      '?url=kbcx%2fxskbcx_cxXskbcxIndex.html%3fgnmkdm%3dN253508%26layout%3ddefault';

  static const String jwClassroomUrl =
      '$jwBaseUrl/sso/jziotlogin'
      '?url=cdjy%2fcdjy_cxKxcdlb.html%3fgnmkdm%3dN2155%26layout%3ddefault';

  static const String jwNoticePath = '/jwglxt/xtgl/xwglMobile_cxMoreXwList.html';

  static const String atrustCasHost = 'authserver-ecut-edu-cn-s.atrust.ecut.edu.cn';
  static const String atrustPortalHost = 'atrust1.ecut.edu.cn';
  static const String atrustLoginUrl =
      'https://authserver-ecut-edu-cn-s.atrust.ecut.edu.cn/authserver/login'
      '?service=https%3A%2F%2Fatrust1.ecut.edu.cn%3A443%2Fpassport%2Fv1%2Fauth%2Fcas'
      '%3FsfDomain%3Dcas.ecut.edu.cn';
  static const String atrustServiceUrl =
      'https://atrust1.ecut.edu.cn:443/passport/v1/auth/cas?sfDomain=cas.ecut.edu.cn';
  static const String atrustPortalUrl = 'https://atrust1.ecut.edu.cn';

  static const String ehallBaseUrl = 'https://ehall.ecut.edu.cn';
  static const String ehallUrl = '$ehallBaseUrl/new/index.html';
  static const String ehallCardUrl =
      'https://ehall.ecut.edu.cn/psfw/sys/psxykapp/*default/index.do'
      '?EMAP_LANG=zh&THEME=indigo#/xykxx';
  static const String ehallCardServiceUrl =
      'https://ehall.ecut.edu.cn/psfw/sys/psxykapp/*default/index.do'
      '?EMAP_LANG=zh&THEME=indigo';

  static const String libraryBaseUrl = 'https://libspace.ecut.edu.cn';
  static const String libraryH5Url = '$libraryBaseUrl/h5/index.html#/seat';

  // 宿舍电费 xgwx
  static const String electricityH5Base = 'https://xgwx.ecut.edu.cn';
  static const String electricityBareIndexUrl =
      '$electricityH5Base/app/wechat/index.jsp';
  static const String electricityBareAppIndexUrl =
      '$electricityH5Base/app/wechat/index.jsp?appId=wx3be3089d48d67c4e&operatorId=admin';
  static const String electricitySurplusUrl =
      '$electricityH5Base/app/wechat/houseMeterSurplus.jsp';

  static String electricityIndexUrl([String operatorPwd = '']) {
    final trimmed = operatorPwd.trim();
    if (trimmed.isEmpty) return electricityBareIndexUrl;
    return '$electricityBareAppIndexUrl&operatorPwd=${Uri.encodeQueryComponent(trimmed)}';
  }

  static LoginTarget electricityLogin([String operatorPwd = '']) {
    final service = electricityIndexUrl(operatorPwd);
    return LoginTarget(
      systemId: systemElectricity,
      title: '宿舍电费登录',
      startUrl:
          '$authServerLoginUrl?service=${Uri.encodeQueryComponent(service)}',
      primaryHost: 'xgwx.ecut.edu.cn',
      extraCookieUrls: const [authServerLoginUrl],
      requiredPrimaryCookie: 'jsessionid',
      primaryPathPrefix: '/app/wechat/',
      autoCompleteHosts: const {'xgwx.ecut.edu.cn'},
      autoCompletePathPrefixes: const {'/app/wechat/'},
      preloadStoredCookies: true,
      httpsHosts: const {'xgwx.ecut.edu.cn', casHost},
      rememberLogin: true,
      helperText: '已登录统一身份认证时会自动换票进入宿舍缴费页并保存会话。',
    );
  }

  // U净洗衣
  static const String ujingBaseUrl = 'https://phoenix.ujing.online:443/api/v1';
  static const String ujingH5Url =
      'https://wx.zhinengxiyifang.cn/static/washer-h5/ujing/latest/index.html';
  static const String ujingH5LoginUrl = '$ujingH5Url#/login';
  static const String ujingAlipayUrl =
      'alipays://platformapi/startapp?appId=2021002174638299&page=pages%2Fhome%2Fhome';

  // 慧生活798
  static const String huiLifeBaseUrl = 'https://i.ilife798.com/api/v1';
  static const String huiLifeCid = 'drinkwaterapp123456789';
  static const String huiLifeAlipayUrl =
      'alipays://platformapi/startapp?appId=2019061465519660&page=pages%2Findex%2Findex';

  // 超星学习通
  static const String chaoxingHost = 'i.chaoxing.com';
  static const String chaoxingLoginUrl = 'https://i.chaoxing.com/base';
  static const String chaoxingMoocApi = 'https://mooc1-api.chaoxing.com';
  static const String chaoxingMoocWeb = 'https://mooc1.chaoxing.com';
  static const String chaoxingMobileLearn = 'https://mobilelearn.chaoxing.com';
  static const String chaoxingPassport = 'https://passport2.chaoxing.com';
  static const String chaoxingNotice = 'https://notice.chaoxing.com';

  static const Set<String> chaoxingHosts = {
    chaoxingHost,
    'passport2.chaoxing.com',
    'passport2-api.chaoxing.com',
    'mooc1.chaoxing.com',
    'mooc1-api.chaoxing.com',
    'mobilelearn.chaoxing.com',
    'notice.chaoxing.com',
  };

  static const LoginTarget chaoxingLogin = LoginTarget(
    systemId: systemChaoxing,
    title: '学习通登录',
    startUrl: chaoxingLoginUrl,
    primaryHost: chaoxingHost,
    extraCookieUrls: [
      'https://i.chaoxing.com/base',
      '$chaoxingPassport/',
      'https://passport2-api.chaoxing.com/',
      '$chaoxingMoocApi/',
      '$chaoxingMobileLearn/',
      '$chaoxingMoocWeb/',
      '$chaoxingNotice/',
    ],
    requiredAnyCookieNames: {'_uid', '_d', 'uid'},
    primaryPathPrefix: null,
    autoCompleteHosts: {chaoxingHost},
    autoCompletePathPrefixes: {'/base'},
    preloadStoredCookies: true,
    userAgent: mobileBrowserUserAgent,
    httpsHosts: chaoxingHosts,
    helperText: '登录后仅只读查询课程、进行中签到、作业与考试，不会代签、代交作业。',
  );

  static final LoginTarget casLogin = LoginTarget(
    systemId: systemCasMaster,
    title: '统一身份认证',
    startUrl:
        '$authServerLoginUrl?service=${Uri.encodeQueryComponent(ehallCardServiceUrl)}',
    primaryHost: casHost,
    extraCookieUrls: const [authServerLoginUrl, ehallCardServiceUrl],
    requiredPrimaryCookie: null,
    primaryPathPrefix: null,
    requiredAnyCookieNames: casTicketCookieNames,
    clearWebViewCookiesAfterSave: false,
    preloadStoredCookies: true,
    httpsHosts: const {casHost, 'ehall.ecut.edu.cn'},
    rememberLogin: true,
    helperText: '一次登录后，课表、成绩、考试、教室、电费、校园卡和办事大厅共用同一个统一认证会话。',
  );

  static const LoginTarget jwLogin = LoginTarget(
    systemId: systemJw,
    title: '教务系统登录',
    startUrl: jwSsoUrl,
    primaryHost: '172.20.130.13',
    extraCookieUrls: [authServerLoginUrl],
    requiredPrimaryCookie: 'jsessionid',
    primaryPathPrefix: '/jwglxt',
    preloadStoredCookies: true,
    httpsHosts: {casHost},
    rememberLogin: true,
    helperText: '已登录统一身份认证时会自动静默授权，无需重复输入密码。',
  );

  // 修复真机 Bug：只有真正落到 /portal/shortcut.html 且不是短信/二次验证页时才自动退出，
  // 避免跳转到新设备短信验证码页时秒退。
  static const LoginTarget atrustLogin = LoginTarget(
    systemId: systemCas,
    title: '零信任 VPN 登录',
    startUrl: atrustLoginUrl,
    primaryHost: atrustCasHost,
    extraCookieUrls: [atrustLoginUrl, atrustServiceUrl],
    requiredPrimaryCookie: null,
    primaryPathPrefix: null,
    requiredAnyCookieNames: casTicketCookieNames,
    clearWebViewCookiesAfterSave: false,
    autoCompleteHosts: {atrustPortalHost},
    autoCompletePathPrefixes: {'/portal/shortcut.html'},
    preloadStoredCookies: true,
    httpsHosts: {atrustCasHost, atrustPortalHost},
    rememberLogin: true,
    helperText: '若弹出新设备短信验证码请先在页面内完成验证，最终进入门户后会自动保存或手动点右上角「完成登录」。',
  );
}
