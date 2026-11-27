import 'dart:async';

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../app_state.dart';
import '../../core/campus_endpoints.dart';
import '../../network/ujing_client.dart';
import '../theme/glass_theme.dart';

/// U净洗衣页：官方 H5 免配置登录 + 精准纠偏定位 + 门店空闲/等待/价格 + 支付宝跳转。
class UjingScreen extends StatefulWidget {
  final CampusAppState appState;

  const UjingScreen({super.key, required this.appState});

  @override
  State<UjingScreen> createState() => _UjingScreenState();
}

class _UjingScreenState extends State<UjingScreen> {
  final Map<String, UjingStoreInfo> _storeDetails = {};
  final Map<String, int> _waitMinutes = {};
  final Map<String, List<UjingProgram>> _programs = {};

  @override
  void initState() {
    super.initState();
    if (widget.appState.hasUjingSession) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        widget.appState.refreshUjing();
      });
    }
  }

  Future<void> _openLogin() async {
    final ok = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => _UjingLoginWebViewScreen(appState: widget.appState),
      ),
    );
    if (ok == true && mounted) {
      await widget.appState.refreshUjing();
    }
  }

  Future<void> _loadStoreDetail(String storeId) async {
    final cred = await widget.appState.authRepository
        .getCredential(CampusEndpoints.systemUjing);
    if (cred == null) return;
    try {
      final info =
          await widget.appState.ujingClient.storeInfo(storeId, cred.cookie);
      if (!mounted) return;
      setState(() => _storeDetails[storeId] = info);
      for (final d in info.devices) {
        final key = '$storeId|${d.deviceTypeId}';
        try {
          final wt = await widget.appState.ujingClient
              .waitTime(storeId, d.deviceTypeId, cred.cookie);
          final progs = await widget.appState.ujingClient
              .programInfo(storeId, d.deviceTypeId, cred.cookie);
          if (mounted) {
            setState(() {
              _waitMinutes[key] = wt;
              _programs[key] = progs;
            });
          }
        } catch (_) {}
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.appState,
      builder: (context, _) {
        final app = widget.appState;
        return CampusBackdrop(
          child: Scaffold(
            appBar: AppBar(
              title: const Text('U净洗衣'),
              actions: [
                TextButton(
                  onPressed: () => launchUrl(
                    Uri.parse(CampusEndpoints.ujingAlipayUrl),
                    mode: LaunchMode.externalApplication,
                  ),
                  child: const Text('支付宝下单'),
                ),
                if (app.hasUjingSession)
                  IconButton(
                    onPressed: app.loadingUjing ? null : app.refreshUjing,
                    icon: const Icon(Icons.refresh_rounded),
                  ),
              ],
            ),
            body: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
              children: [
                if (!app.hasUjingSession)
                  GlassCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'U净免配置登录',
                          style: TextStyle(
                            fontSize: 17,
                            fontWeight: FontWeight.w800,
                            color: GlassColors.ink(context),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '无需配置任何密钥：内嵌 U净官方网页发送验证码，填码直接登录并加密保存约 90 天会话；本 App 仅只读查询空闲机器，下单支付跳转官方支付宝小程序。',
                          style: TextStyle(
                            fontSize: 13,
                            height: 1.5,
                            color: GlassColors.muted(context),
                          ),
                        ),
                        const SizedBox(height: 12),
                        FilledButton.icon(
                          style: FilledButton.styleFrom(
                            backgroundColor: GlassColors.primary,
                          ),
                          onPressed: _openLogin,
                          icon: const Icon(Icons.login_rounded, size: 18),
                          label: const Text('打开 U净免配置登录'),
                        ),
                      ],
                    ),
                  )
                else ...[
                  GlassCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                '位置：${app.ujingLocationLabel}',
                                style: TextStyle(
                                  fontSize: 13.5,
                                  fontWeight: FontWeight.w700,
                                  color: GlassColors.ink(context),
                                ),
                              ),
                            ),
                            TextButton(
                              onPressed: app.logoutUjing,
                              child: const Text('退出U净'),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Wrap(
                          spacing: 8,
                          runSpacing: 6,
                          children: [
                            for (final opt in const [
                              ('auto', '当前位置'),
                              ('guanglan', '广兰校区'),
                              ('qinglan', '青岚校区'),
                              ('fuzhou', '抚州校区'),
                            ])
                              ChoiceChip(
                                label: Text(opt.$2,
                                    style: const TextStyle(fontSize: 12)),
                                selected: app.ujingCampusPref == opt.$1,
                                visualDensity: VisualDensity.compact,
                                onSelected: (_) =>
                                    app.setUjingCampusPref(opt.$1),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  if (app.ujingError != null) ...[
                    GlassCard(
                      child: Text(
                        app.ujingError!,
                        style: const TextStyle(color: GlassColors.danger),
                      ),
                    ),
                    const SizedBox(height: 12),
                  ],
                  if (app.ujingRunning.isNotEmpty) ...[
                    _sectionTitle(context, '运行中订单'),
                    for (final o in app.ujingRunning) ...[
                      GlassCard(
                        child: ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text('${o.storeName} · ${o.deviceTypeName}'),
                          subtitle: Text(
                            '设备号 ${o.deviceNo} · ${o.statusRemark}',
                          ),
                          trailing: Text(
                            '剩 ${o.remainTime} 分',
                            style: const TextStyle(
                              fontWeight: FontWeight.w800,
                              color: GlassColors.primary,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 10),
                    ],
                  ],
                  if (app.ujingRecentStore != null) ...[
                    _sectionTitle(context, '最近使用楼栋'),
                    _buildStoreCard(
                      context,
                      storeId: app.ujingRecentStore!.$1,
                      title: app.ujingRecentStore!.$2,
                      subtitle: '来自历史订单反查',
                    ),
                    const SizedBox(height: 12),
                  ],
                  _sectionTitle(context, '附近洗衣房（按距离排序）'),
                  if (app.loadingUjing && app.ujingStores.isEmpty)
                    const Padding(
                      padding: EdgeInsets.all(20),
                      child: Center(child: CircularProgressIndicator()),
                    ),
                  for (final s in app.ujingStores) ...[
                    _buildStoreCard(
                      context,
                      storeId: s.id,
                      title: s.name,
                      subtitle:
                          '${s.distanceKm < 1 ? '${(s.distanceKm * 1000).round()}m' : '${s.distanceKm.toStringAsFixed(2)}km'} · 洗衣 ${s.washerCount} 台 / 烘干 ${s.dryerCount} 台',
                    ),
                    const SizedBox(height: 10),
                  ],
                  if (app.ujingHistory.isNotEmpty) ...[
                    const SizedBox(height: 6),
                    _sectionTitle(context, '历史订单'),
                    GlassCard(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 6,
                      ),
                      child: Column(
                        children: [
                          for (var i = 0; i < app.ujingHistory.length; i++) ...[
                            if (i > 0) const Divider(height: 1),
                            ListTile(
                              dense: true,
                              contentPadding: EdgeInsets.zero,
                              title: Text(
                                '${app.ujingHistory[i].storeName} · ${app.ujingHistory[i].deviceTypeName}',
                              ),
                              subtitle: Text(app.ujingHistory[i].createAt),
                              trailing: Text(app.ujingHistory[i].statusRemark),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ],
                ],
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _sectionTitle(BuildContext context, String text) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 2, 4, 8),
        child: Text(
          text,
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w700,
            color: GlassColors.muted(context),
          ),
        ),
      );

  Widget _buildStoreCard(
    BuildContext context, {
    required String storeId,
    required String title,
    required String subtitle,
  }) {
    final detail = _storeDetails[storeId];
    return GlassCard(
      onTap: () => _loadStoreDetail(storeId),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: GlassColors.ink(context),
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: 12,
                        color: GlassColors.muted(context),
                      ),
                    ),
                  ],
                ),
              ),
              Text(
                detail == null ? '点查空闲' : '已刷新',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: GlassColors.primary,
                ),
              ),
            ],
          ),
          if (detail != null) ...[
            const SizedBox(height: 10),
            for (final d in detail.devices) ...[
              Builder(
                builder: (context) {
                  final key = '$storeId|${d.deviceTypeId}';
                  final wt = _waitMinutes[key];
                  final progs = _programs[key] ?? const <UjingProgram>[];
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 6),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                d.name,
                                style: const TextStyle(
                                  fontSize: 13.5,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ),
                            Text(
                              '空闲 ${d.idle}/${d.total}${wt != null && wt > 0 ? ' · 预计等 $wt 分' : ''}',
                              style: TextStyle(
                                fontSize: 12.5,
                                fontWeight: FontWeight.w700,
                                color: d.idle > 0
                                    ? GlassColors.primary
                                    : GlassColors.warning,
                              ),
                            ),
                          ],
                        ),
                        if (progs.isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.only(top: 3),
                            child: Text(
                              progs
                                  .map((p) =>
                                      '${p.name} ¥${(p.priceCents / 100).toStringAsFixed(2)}/${p.minutes}分')
                                  .join(' · '),
                              style: TextStyle(
                                fontSize: 11.5,
                                color: GlassColors.muted(context),
                              ),
                            ),
                          ),
                      ],
                    ),
                  );
                },
              ),
            ],
          ],
        ],
      ),
    );
  }
}

class _UjingLoginWebViewScreen extends StatefulWidget {
  final CampusAppState appState;
  const _UjingLoginWebViewScreen({required this.appState});

  @override
  State<_UjingLoginWebViewScreen> createState() =>
      _UjingLoginWebViewScreenState();
}

class _UjingLoginWebViewScreenState extends State<_UjingLoginWebViewScreen> {
  static const _readTokenJs = '''
(function(){
  try {
    return window.localStorage.getItem('token')
      || window.localStorage.getItem('wechatWorkH5-washer-token')
      || '';
  } catch(e) { return ''; }
})();
''';

  late final WebViewController _controller;
  final _mobileCtrl = TextEditingController();
  final _codeCtrl = TextEditingController();
  Timer? _timer;
  bool _submitting = false;
  bool _captured = false;
  String? _status;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setUserAgent(CampusEndpoints.mobileBrowserUserAgent)
      ..loadRequest(Uri.parse(CampusEndpoints.ujingH5LoginUrl));
    _timer = Timer.periodic(const Duration(seconds: 1), (_) => _probeToken());
  }

  @override
  void dispose() {
    _timer?.cancel();
    _mobileCtrl.dispose();
    _codeCtrl.dispose();
    super.dispose();
  }

  Future<void> _probeToken() async {
    if (_captured) return;
    try {
      final raw = await _controller.runJavaScriptReturningResult(_readTokenJs);
      final jwt = UjingTokenExtractor.extractFromJsResult(raw);
      if (jwt != null && !_captured) {
        _captured = true;
        await widget.appState.saveUjingToken(jwt);
        if (mounted) Navigator.of(context).pop(true);
      }
    } catch (_) {}
  }

  Future<void> _submitNativeLogin() async {
    final mobile = _mobileCtrl.text.trim();
    final code = _codeCtrl.text.trim();
    if (mobile.length != 11 || code.length < 4) {
      setState(() => _status = '请输入 11 位手机号和收到的短信验证码');
      return;
    }
    setState(() {
      _submitting = true;
      _status = '正在登录 U净…';
    });
    try {
      await widget.appState.loginUjing(mobile, code);
      _captured = true;
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _status = '登录失败：$e';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return CampusBackdrop(
      child: Scaffold(
        appBar: AppBar(title: const Text('U净免配置登录')),
        body: Column(
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              color: GlassColors.primary.withValues(alpha: 0.12),
              child: Text(
                _status ?? '第 1 步：在上方官方页输手机号点「获取验证码」；第 2 步：在下方输入验证码直接登录。',
                style: const TextStyle(fontSize: 12),
              ),
            ),
            Expanded(child: WebViewWidget(controller: _controller)),
            SafeArea(
              top: false,
              child: Container(
                padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
                color: Theme.of(context).colorScheme.surface,
                child: Row(
                  children: [
                    Expanded(
                      flex: 3,
                      child: TextField(
                        controller: _mobileCtrl,
                        keyboardType: TextInputType.phone,
                        decoration: const InputDecoration(
                          labelText: '手机号',
                          isDense: true,
                          border: OutlineInputBorder(),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      flex: 2,
                      child: TextField(
                        controller: _codeCtrl,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(
                          labelText: '验证码',
                          isDense: true,
                          border: OutlineInputBorder(),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    FilledButton(
                      onPressed: _submitting ? null : _submitNativeLogin,
                      style: FilledButton.styleFrom(
                        backgroundColor: GlassColors.primary,
                      ),
                      child: const Text('登录'),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
