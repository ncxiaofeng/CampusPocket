import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../app_state.dart';
import '../../core/campus_endpoints.dart';
import '../../data/models.dart';
import '../../data/platform_bridges.dart';
import '../../network/cas_sso_manager.dart';
import '../theme/glass_theme.dart';

/// 内嵌学校官方网页：支持零信任门户高度修复、WebVPN 代理会话捕获、PC/手机 UA 切换。
class CampusWebPageScreen extends StatefulWidget {
  final CampusAppState appState;
  final String title;
  final String url;
  final String? helperText;
  final bool desktopUserAgent;
  final bool allowUserAgentSwitch;
  final bool openInBrowser;
  final bool saveProxySession;
  final String? saveSessionHost;
  final String saveSessionSystemId;
  final String? saveSessionPathPrefix;

  const CampusWebPageScreen({
    super.key,
    required this.appState,
    required this.title,
    required this.url,
    this.helperText,
    this.desktopUserAgent = true,
    this.allowUserAgentSwitch = false,
    this.openInBrowser = true,
    this.saveProxySession = false,
    this.saveSessionHost,
    this.saveSessionSystemId = 'ecut_session',
    this.saveSessionPathPrefix,
  });

  @override
  State<CampusWebPageScreen> createState() => _CampusWebPageScreenState();
}

class _CampusWebPageScreenState extends State<CampusWebPageScreen> {
  static const String _portalHeightFixScript = '''
(function(){
  function fix(){
    try{
      var H=Math.round(window.innerHeight||600);
      var s=document.getElementById('cp-portal-fix');
      if(!s){s=document.createElement('style');s.id='cp-portal-fix';document.head.appendChild(s);}
      s.textContent='html,body{height:'+H+'px!important;min-height:'+H+'px!important}'+
        '#app,.h-screen,.for-main-window,.main-page-wrapper{height:'+H+'px!important;'+
        'min-height:'+H+'px!important;width:100%!important}';
    }catch(e){}
  }
  fix();
  var n=0;
  var timer=setInterval(function(){fix();n++;if(n>=12){clearInterval(timer);}},800);
})();
''';

  late final WebViewController _controller;
  late bool _desktopMode;
  bool _ready = false;
  bool _loading = true;
  String? _banner;
  String? _lastCasTicket;

  @override
  void initState() {
    super.initState();
    _desktopMode = widget.desktopUserAgent;
    _initController();
  }

  Future<void> _initController() async {
    await _injectStoredCookies();
    final controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(
        _desktopMode
            ? CampusEndpoints.desktopUserAgent
            : CampusEndpoints.mobileBrowserUserAgent,
      )
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: (request) {
            final uri = Uri.tryParse(request.url);
            if (uri != null &&
                uri.scheme == 'http' &&
                uri.host.toLowerCase() == CampusEndpoints.casHost) {
              _controller.loadRequest(uri.replace(scheme: 'https'));
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
          onPageStarted: (_) {
            if (mounted) setState(() => _loading = true);
          },
          onPageFinished: (url) async {
            if (mounted) setState(() => _loading = false);
            await _onPageFinished(url);
          },
        ),
      );
    _controller = controller;
    await _controller.loadRequest(Uri.parse(widget.url));
    if (mounted) setState(() => _ready = true);
  }

  Future<void> _injectStoredCookies() async {
    final targetHost = Uri.tryParse(widget.url)?.host ?? '';
    final hosts = <String>{
      if (targetHost.isNotEmpty) targetHost,
      CampusEndpoints.casHost,
      if (widget.saveProxySession) ...[
        CampusEndpoints.atrustPortalHost,
        CampusEndpoints.atrustCasHost,
        CampusEndpoints.jwProxyHost,
      ],
      if (widget.saveSessionHost != null) widget.saveSessionHost!,
    };
    final cookies = <CookieRecord>[];
    for (final host in hosts) {
      final rows = await widget.appState.authRepository.loadCookiesForHost(host);
      if (host.toLowerCase() == CampusEndpoints.casHost &&
          !rows.any(
            (r) => CampusEndpoints.casTicketCookieNames
                .contains(r.name.toLowerCase()),
          )) {
        continue;
      }
      cookies.addAll(rows);
    }
    await WkCookieBridge.setCookies(cookies);
  }

  Future<void> _onPageFinished(String url) async {
    if (url.contains('atrust1.ecut.edu.cn/portal')) {
      try {
        await _controller.runJavaScript(_portalHeightFixScript);
      } catch (_) {}
    }

    final allCookies = await WkCookieBridge.getAllCookies();
    final repo = widget.appState.authRepository;

    // 顺手收割 CAS 主凭据
    final casHeader = allCookies
        .where((c) => _hostMatch(CampusEndpoints.casHost, c.domain))
        .map((c) => '${c.name}=${c.value}')
        .join('; ');
    final ticket = CasSsoManager.casTicketValue(casHeader);
    if (ticket != null && ticket != _lastCasTicket) {
      _lastCasTicket = ticket;
      await repo.saveCasMasterCookies(casHeader, fromCasHost: true);
    }

    final uri = Uri.tryParse(url);
    final currentHost = uri?.host.toLowerCase() ?? '';
    final currentPath = uri?.path ?? '';

    // 捕获校外 WebVPN 代理教务会话
    if (widget.saveProxySession && currentHost == CampusEndpoints.jwProxyHost) {
      final proxyCookies = allCookies
          .where((c) => _hostMatch(CampusEndpoints.jwProxyHost, c.domain))
          .toList();
      if (proxyCookies.isNotEmpty) {
        await repo.saveStructuredCookies(
          systemId: CampusEndpoints.systemJwProxy,
          primaryHost: CampusEndpoints.jwProxyHost,
          records: proxyCookies,
        );
        await widget.appState.refreshSessionFlags();
        if (mounted) {
          setState(() {
            _banner = '已捕获校外 WebVPN 教务代理会话，现在可返回首页直接同步课表与成绩。';
          });
        }
      }
    }

    // 捕获指定业务域会话（例如 ehall 一卡通 / 校内教务）
    if (widget.saveSessionHost != null &&
        currentHost == widget.saveSessionHost!.toLowerCase()) {
      final prefix = widget.saveSessionPathPrefix;
      if (prefix == null || currentPath.startsWith(prefix)) {
        final hostCookies =
            allCookies.where((c) => _hostMatch(currentHost, c.domain)).toList();
        if (hostCookies.isNotEmpty) {
          await repo.saveStructuredCookies(
            systemId: widget.saveSessionSystemId,
            primaryHost: currentHost,
            records: hostCookies,
          );
          await widget.appState.refreshSessionFlags();
        }
      }
    }
  }

  static bool _hostMatch(String host, String cookieDomain) {
    final h = host.toLowerCase();
    final d = cookieDomain.replaceFirst(RegExp(r'^\.+'), '').toLowerCase();
    return d.isNotEmpty && (h == d || h.endsWith('.$d'));
  }

  Future<void> _toggleUa() async {
    setState(() => _desktopMode = !_desktopMode);
    await _controller.setUserAgent(
      _desktopMode
          ? CampusEndpoints.desktopUserAgent
          : CampusEndpoints.mobileBrowserUserAgent,
    );
    await _controller.reload();
  }

  @override
  Widget build(BuildContext context) {
    return CampusBackdrop(
      child: Scaffold(
        appBar: AppBar(
          title: Text(widget.title),
          actions: [
            if (widget.allowUserAgentSwitch)
              TextButton(
                onPressed: _toggleUa,
                child: Text(_desktopMode ? '手机版' : 'PC版'),
              ),
            if (widget.openInBrowser)
              IconButton(
                tooltip: '在 Safari 打开',
                onPressed: () => launchUrl(
                  Uri.parse(widget.url),
                  mode: LaunchMode.externalApplication,
                ),
                icon: const Icon(Icons.open_in_browser_rounded),
              ),
            IconButton(
              tooltip: '刷新',
              onPressed: () => _controller.reload(),
              icon: const Icon(Icons.refresh_rounded),
            ),
          ],
        ),
        body: Column(
          children: [
            if (widget.helperText != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 0, 14, 6),
                child: Container(
                  width: double.infinity,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: GlassColors.primary.withValues(alpha: 0.10),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    widget.helperText!,
                    style: TextStyle(
                      fontSize: 12,
                      color: GlassColors.muted(context),
                    ),
                  ),
                ),
              ),
            if (_banner != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 0, 14, 6),
                child: Container(
                  width: double.infinity,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: GlassColors.primary.withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    _banner!,
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                      color: GlassColors.ink(context),
                    ),
                  ),
                ),
              ),
            if (_loading) const LinearProgressIndicator(minHeight: 2),
            Expanded(
              child: _ready
                  ? WebViewWidget(controller: _controller)
                  : const Center(child: CircularProgressIndicator()),
            ),
          ],
        ),
      ),
    );
  }
}
