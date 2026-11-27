import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../app_state.dart';
import '../../core/campus_endpoints.dart';
import '../../data/models.dart';
import '../../data/platform_bridges.dart';
import '../../network/cas_sso_manager.dart';
import '../theme/glass_theme.dart';

/// 官方网页登录容器（iOS WKWebView + WKHTTPCookieStore 原生同步）。
///
/// 用户只在学校官方页面输入账号密码，App 不读取 DOM 表单、不保存明文密码；
/// 登录完成后直接通过 WKHTTPCookieStore 提取完整 Cookie（含 HttpOnly CASTGC/JSESSIONID）
/// 并加密写入 iOS Keychain。
class LoginWebViewScreen extends StatefulWidget {
  final CampusAppState appState;
  final LoginTarget target;

  const LoginWebViewScreen({
    super.key,
    required this.appState,
    required this.target,
  });

  @override
  State<LoginWebViewScreen> createState() => _LoginWebViewScreenState();
}

class _LoginWebViewScreenState extends State<LoginWebViewScreen> {
  static const String _rememberMeScript = '''
(function(){
  function tick(){
    try {
      var box = document.getElementById('rememberMe');
      if (!box) return;
      if (window.jQuery) {
        var jq = window.jQuery('#rememberMe');
        if (jq.length && typeof jq.iCheck === 'function') { jq.iCheck('check'); }
      }
      if (!box.checked) {
        box.checked = true;
        box.dispatchEvent(new Event('change', {bubbles: true}));
        box.dispatchEvent(new Event('ifChecked', {bubbles: true}));
      }
    } catch (e) {}
  }
  tick();
  setTimeout(tick, 500);
  setTimeout(tick, 1500);
})();
''';

  late final WebViewController _controller;
  bool _ready = false;
  bool _loading = true;
  bool _saved = false;
  bool _autoCompleting = false;
  String? _currentUrl;
  String? _statusMessage;
  bool _statusIsError = false;

  @override
  void initState() {
    super.initState();
    _initController();
  }

  Future<void> _initController() async {
    if (widget.target.preloadStoredCookies) {
      await _preloadCookies();
    }
    final controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(
        widget.target.userAgent ?? CampusEndpoints.desktopUserAgent,
      )
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: (request) {
            final uri = Uri.tryParse(request.url);
            if (uri != null &&
                uri.scheme == 'http' &&
                widget.target.httpsHosts.contains(uri.host.toLowerCase())) {
              final upgraded = uri.replace(scheme: 'https').toString();
              _controller.loadRequest(Uri.parse(upgraded));
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
          onPageStarted: (url) {
            if (!mounted) return;
            setState(() {
              _loading = true;
              _currentUrl = url;
            });
          },
          onPageFinished: (url) async {
            if (!mounted) return;
            setState(() {
              _loading = false;
              _currentUrl = url;
            });
            await _handlePageFinished(url);
          },
          onWebResourceError: (error) {
            if (error.isForMainFrame != true || !mounted) return;
            setState(() {
              _loading = false;
              _statusIsError = true;
              _statusMessage = '网页加载失败：${error.description}';
            });
          },
        ),
      );
    _controller = controller;
    await _controller.loadRequest(Uri.parse(widget.target.startUrl));
    if (mounted) {
      setState(() => _ready = true);
    }
  }

  Future<void> _preloadCookies() async {
    final repo = widget.appState.authRepository;
    final hosts = <String>{
      widget.target.primaryHost,
      CampusEndpoints.casHost,
      if (widget.target.systemId == CampusEndpoints.systemCas) ...[
        CampusEndpoints.atrustCasHost,
        CampusEndpoints.atrustPortalHost,
      ],
      for (final u in widget.target.extraCookieUrls)
        if (Uri.tryParse(u)?.host.isNotEmpty == true) Uri.parse(u).host,
    };

    final toInject = <CookieRecord>[];
    for (final host in hosts) {
      final rows = await repo.loadCookiesForHost(host);
      // CAS 域没有 CASTGC 时不注入残留旧 route / JSESSIONID，防止表单提交串节点回弹。
      if (host.toLowerCase() == CampusEndpoints.casHost &&
          !rows.any(
            (r) => CampusEndpoints.casTicketCookieNames
                .contains(r.name.toLowerCase()),
          )) {
        continue;
      }
      toInject.addAll(rows);
    }
    await WkCookieBridge.setCookies(toInject);
  }

  Future<void> _handlePageFinished(String url) async {
    final uri = Uri.tryParse(url);
    final currentHost = uri?.host.toLowerCase() ?? '';

    if (widget.target.rememberLogin && url.contains('/authserver/login')) {
      try {
        await _controller.runJavaScript(_rememberMeScript);
      } catch (_) {}
    }

    if (widget.target.autoCompleteHosts.contains(currentHost) &&
        !_saved &&
        !_autoCompleting) {
      _autoCompleting = true;
      setState(() {
        _statusIsError = false;
        _statusMessage = '认证已完成，正在加密保存会话…';
      });
      await Future<void>.delayed(const Duration(milliseconds: 600));
      await _completeLogin();
      return;
    }

    if (widget.target.systemId == CampusEndpoints.systemCasMaster &&
        currentHost.isNotEmpty &&
        currentHost != CampusEndpoints.casHost &&
        !_saved &&
        !_autoCompleting) {
      final all = await WkCookieBridge.getAllCookies();
      final casCookies = all.where(
        (c) => _hostMatch(CampusEndpoints.casHost, c.domain),
      );
      final hasTicket = casCookies.any(
        (c) => CampusEndpoints.casTicketCookieNames
            .contains(c.name.toLowerCase()),
      );
      if (hasTicket) {
        _autoCompleting = true;
        if (mounted) {
          setState(() {
            _statusIsError = false;
            _statusMessage = '统一认证已完成，正在加密保存…';
          });
        }
        await Future<void>.delayed(const Duration(milliseconds: 450));
        await _completeLogin();
      }
    }
  }

  Future<void> _completeLogin() async {
    if (_saved) return;
    final allCookies = await WkCookieBridge.getAllCookies();
    final primaryHost = widget.target.primaryHost.toLowerCase();
    final primaryCookies =
        allCookies.where((c) => _hostMatch(primaryHost, c.domain)).toList();
    final primaryNames =
        primaryCookies.map((c) => c.name.toLowerCase()).toSet();
    final allNames = allCookies.map((c) => c.name.toLowerCase()).toSet();

    final currentUrl = _currentUrl ?? '';
    final currentUri = Uri.tryParse(currentUrl);
    final requiredCookie = widget.target.requiredPrimaryCookie;
    final requiredPath = widget.target.primaryPathPrefix ?? '';

    final onPrimaryPage = requiredCookie == null ||
        (currentUri != null &&
            currentUri.host.toLowerCase() == primaryHost &&
            (requiredPath.isEmpty || currentUri.path.startsWith(requiredPath)) &&
            !currentUrl.contains('login_slogin') &&
            !currentUrl.contains('/authserver/'));

    final looksLoggedIn = requiredCookie != null
        ? (primaryNames.contains(requiredCookie) && onPrimaryPage)
        : widget.target.requiredAnyCookieNames.isNotEmpty
            ? allNames.any(widget.target.requiredAnyCookieNames.contains)
            : allNames.any(
                const {'castgc', 'tgc', 'jsessionid', 'mod_auth_cas'}.contains,
              );

    if (allCookies.isEmpty || !looksLoggedIn) {
      _autoCompleting = false;
      if (!mounted) return;
      setState(() {
        _statusIsError = true;
        _statusMessage = requiredCookie != null
            ? '尚未检测到${widget.target.title}会话。请完成登录并进入业务页后，再点击「完成登录」。'
            : '未检测到登录凭证。请先在页面中完成登录，再点击「完成登录」。';
      });
      return;
    }

    if (mounted) {
      setState(() {
        _statusIsError = false;
        _statusMessage = '正在写入 iOS Keychain…';
      });
    }

    final repo = widget.appState.authRepository;
    // 只收集与本次登录相关的域名 Cookie（含 CAS / ehall / 目标域）
    final relevantHosts = <String>{
      primaryHost,
      CampusEndpoints.casHost,
      'ehall.ecut.edu.cn',
      if (currentUri?.host.isNotEmpty == true) currentUri!.host.toLowerCase(),
      for (final u in widget.target.extraCookieUrls)
        if (Uri.tryParse(u)?.host.isNotEmpty == true)
          Uri.parse(u).host.toLowerCase(),
    };
    final filtered = allCookies
        .where((c) => relevantHosts.any((h) => _hostMatch(h, c.domain)))
        .toList();

    await repo.saveStructuredCookies(
      systemId: widget.target.systemId,
      primaryHost: primaryHost,
      records: filtered,
    );

    // 顺手保存 CAS 主凭据（CASTGC + route + JSESSIONID 整套保留）
    final casHeader = allCookies
        .where((c) => _hostMatch(CampusEndpoints.casHost, c.domain))
        .map((c) => '${c.name}=${c.value}')
        .join('; ');
    if (CasSsoManager.hasCasTicket(casHeader)) {
      await repo.saveCasMasterCookies(casHeader, fromCasHost: true);
    }
    if (primaryHost != CampusEndpoints.casHost) {
      final primaryHeader =
          primaryCookies.map((c) => '${c.name}=${c.value}').join('; ');
      if (CasSsoManager.hasCasTicket(primaryHeader)) {
        await repo.saveCasMasterCookies(primaryHeader, fromCasHost: false);
      }
    }

    if (widget.target.clearWebViewCookiesAfterSave) {
      await WkCookieBridge.clearAllCookies();
    }

    await widget.appState.refreshSessionFlags();
    if (!mounted) return;
    setState(() {
      _saved = true;
      _statusIsError = false;
      _statusMessage = '登录凭证已加密保存到本机 Keychain';
    });
    await Future<void>.delayed(const Duration(milliseconds: 650));
    if (mounted) Navigator.of(context).pop(true);
  }

  Future<void> _restartLogin() async {
    await WkCookieBridge.clearAllCookies();
    await widget.appState.authRepository.clearSystem(
      widget.target.systemId,
      [widget.target.primaryHost],
    );
    if (!mounted) return;
    setState(() {
      _saved = false;
      _autoCompleting = false;
      _statusMessage = null;
    });
    await _controller.loadRequest(Uri.parse(widget.target.startUrl));
  }

  static bool _hostMatch(String host, String cookieDomain) {
    final h = host.toLowerCase();
    final d = cookieDomain.replaceFirst(RegExp(r'^\.+'), '').toLowerCase();
    return d.isNotEmpty && (h == d || h.endsWith('.$d'));
  }

  @override
  Widget build(BuildContext context) {
    return CampusBackdrop(
      child: Scaffold(
        appBar: AppBar(
          title: Text(widget.target.title),
          actions: [
            TextButton(
              onPressed: _restartLogin,
              child: const Text('重登'),
            ),
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: FilledButton(
                onPressed: _saved ? null : _completeLogin,
                style: FilledButton.styleFrom(
                  backgroundColor: GlassColors.primary,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                ),
                child: Text(_saved ? '已保存' : '完成登录'),
              ),
            ),
          ],
        ),
        body: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 6),
              child: Container(
                width: double.infinity,
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: GlassColors.primary.withValues(alpha: 0.10),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.lock_outline_rounded,
                      size: 16,
                      color: GlassColors.primary,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        widget.target.helperText ??
                            '登录在官方网页完成，密码不会保存在本机，仅加密保存会话 Cookie。',
                        style: TextStyle(
                          fontSize: 12,
                          color: GlassColors.muted(context),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            if (_statusMessage != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 0, 14, 6),
                child: Container(
                  width: double.infinity,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: _statusIsError
                        ? GlassColors.danger.withValues(alpha: 0.14)
                        : GlassColors.primary.withValues(alpha: 0.16),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    _statusMessage!,
                    style: TextStyle(
                      fontSize: 12.5,
                      color: _statusIsError
                          ? GlassColors.danger
                          : GlassColors.ink(context),
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
