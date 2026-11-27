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
    this.preloadStoredCookies = false,
    this.helperText,
    this.userAgent,
    this.httpsHosts = const {},
    this.rememberLogin = false,
  });
}

/// 东华理工大学校内服务地址与登录配置（与 Android v0.6.4 保持一致）。
class CampusEndpoints {
  static const String systemJw = 'ecut_jw';
  static const String systemCas = 'ecut_cas';
  static const String systemCasMaster = 'ecut_cas_master';
  static const String systemJwProxy = 'ecut_jw_proxy';
  static const String systemCard = 'ecut_card';

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
    helperText: '一次登录后，课表、成绩、考试、教室、校园卡和办事大厅共用同一个统一认证会话。',
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
    preloadStoredCookies: true,
    httpsHosts: {atrustCasHost, atrustPortalHost},
    rememberLogin: true,
    helperText: '认证完成后请打开「零信任门户」并进入一次“新教务管理系统”，即可在校外免 VPN 同步课表与成绩。',
  );
}
