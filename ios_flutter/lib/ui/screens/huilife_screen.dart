import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../app_state.dart';
import '../../core/campus_endpoints.dart';
import '../../data/platform_bridges.dart';
import '../../network/huilife_client.dart';
import '../theme/glass_theme.dart';
import 'runtime_config_screen.dart';

/// 慧生活798 页面：查询端免配置默认登录 + 设备端扫码启动与本人安全停止 + 10 秒轮询状态。
class HuiLifeScreen extends StatefulWidget {
  final CampusAppState appState;

  const HuiLifeScreen({super.key, required this.appState});

  @override
  State<HuiLifeScreen> createState() => _HuiLifeScreenState();
}

class _HuiLifeScreenState extends State<HuiLifeScreen> {
  final _phoneCtrl = TextEditingController();
  final _graphCtrl = TextEditingController();
  final _smsCtrl = TextEditingController();

  HuiLifePlatform _loginPlatform = HuiLifePlatform.account;
  String _captchaKey = HuiLifeClient.newCaptchaKey();
  Uint8List? _captchaBytes;
  bool _busy = false;
  String? _tip;
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    _refreshCaptcha();
    if (widget.appState.hasHuiLifeAccount || widget.appState.hasHuiLifeApp) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        widget.appState.refreshHuiLife();
      });
    }
    _pollTimer = Timer.periodic(const Duration(seconds: 10), (_) {
      if (widget.appState.hasHuiLifeAccount || widget.appState.hasHuiLifeApp) {
        widget.appState.refreshHuiLifeDeviceStatuses();
      }
    });
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _phoneCtrl.dispose();
    _graphCtrl.dispose();
    _smsCtrl.dispose();
    super.dispose();
  }

  Future<void> _refreshCaptcha() async {
    _captchaKey = HuiLifeClient.newCaptchaKey();
    try {
      final bytes =
          await widget.appState.huiLifeClient.fetchCaptchaImage(_captchaKey);
      if (mounted) setState(() => _captchaBytes = Uint8List.fromList(bytes));
    } catch (_) {}
  }

  Future<void> _sendSms() async {
    final phone = _phoneCtrl.text.trim();
    final graph = _graphCtrl.text.trim();
    if (phone.length != 11 || graph.isEmpty) {
      setState(() => _tip = '请先输入 11 位手机号和图形验证码');
      return;
    }
    if (_loginPlatform == HuiLifePlatform.app &&
        !widget.appState.runtimeConfig.hasHuiLife) {
      setState(() => _tip = '设备控制端需要先在「高级配置」导入 798 盐值；查询余额可先用免配置的「查询端」。');
      return;
    }
    setState(() {
      _busy = true;
      _tip = null;
    });
    try {
      await widget.appState.huiLifeClient.sendSms(
        phone: phone,
        graphCode: graph,
        sessionKey: _captchaKey,
        platform: _loginPlatform,
      );
      if (mounted) setState(() => _tip = '短信验证码已发送');
    } catch (e) {
      if (mounted) setState(() => _tip = '发送失败：$e');
      _refreshCaptcha();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _doLogin() async {
    final phone = _phoneCtrl.text.trim();
    final sms = _smsCtrl.text.trim();
    if (phone.length != 11 || sms.isEmpty) {
      setState(() => _tip = '请输入手机号和短信验证码');
      return;
    }
    setState(() {
      _busy = true;
      _tip = null;
    });
    try {
      await widget.appState.loginHuiLife(
        phone: phone,
        smsCode: sms,
        platform: _loginPlatform,
      );
      if (mounted) setState(() => _tip = '登录成功');
    } catch (e) {
      if (mounted) setState(() => _tip = '登录失败：$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _scanDeviceQr() async {
    final raw = await QrScannerBridge.scanQr(title: '扫描 798 设备二维码');
    if (raw == null || !mounted) return;
    await _handleQrPayload(raw);
  }

  Future<void> _handleQrPayload(String raw) async {
    final parsed = HuiLifeDeviceIdParser.parse(raw);
    if (parsed.deviceId == null && parsed.qrId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('未识别到有效的慧生活798 设备编号')),
      );
      return;
    }
    try {
      HuiLifeDevice device;
      if (parsed.qrId != null) {
        final appToken =
            await widget.appState.getHuiLifeToken(HuiLifePlatform.app);
        if (appToken == null) {
          throw StateError('该二维码需要先完成「设备控制端」登录才能解析');
        }
        device = await widget.appState.huiLifeClient
            .resolveQrDevice(appToken, parsed.qrId!);
      } else {
        device = HuiLifeDevice(
          id: parsed.deviceId!,
          name: '扫码设备',
          detail: '本机扫码添加',
        );
      }
      await widget.appState.addHuiLifeScannedDevice(device);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已添加设备：${device.name} (${device.id})')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('添加失败：$e')));
      }
    }
  }

  Future<void> _confirmStart(HuiLifeDevice dev) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('确认启动 ${dev.name}？'),
        content: const Text('启动前请确认已在设备旁就位，App 不会自动接水或自动充值。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: GlassColors.primary),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('确认启动'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    try {
      await widget.appState.startHuiLifeDevice(dev.id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('已发送启动请求')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('启动失败：$e')));
      }
    }
  }

  Future<void> _confirmStop(HuiLifeDevice dev) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('确认停止 ${dev.name}？'),
        content: const Text('仅用于停止您本人当前正在使用的设备。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: GlassColors.danger),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('确认停止'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    try {
      await widget.appState.stopHuiLifeDevice(dev.id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('已发送停止请求')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('停止失败：$e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.appState,
      builder: (context, _) {
        final app = widget.appState;
        final loggedIn = app.hasHuiLifeAccount || app.hasHuiLifeApp;

        return CampusBackdrop(
          child: Scaffold(
            appBar: AppBar(
              title: const Text('慧生活798'),
              actions: [
                TextButton(
                  onPressed: () => launchUrl(
                    Uri.parse(CampusEndpoints.huiLifeAlipayUrl),
                    mode: LaunchMode.externalApplication,
                  ),
                  child: const Text('支付宝'),
                ),
                if (loggedIn)
                  IconButton(
                    onPressed: app.loadingHuiLife ? null : app.refreshHuiLife,
                    icon: const Icon(Icons.refresh_rounded),
                  ),
              ],
            ),
            body: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
              children: [
                if (loggedIn && app.huiLifeWallet != null) ...[
                  GlassCard(
                    child: Row(
                      children: [
                        const IconBadge(
                          icon: Icons.water_drop_outlined,
                          color: Color(0xFF0EA5E9),
                          size: 42,
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                app.huiLifeWallet!.name,
                                style: const TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              Text(
                                '查询端：${app.hasHuiLifeAccount ? '已登录' : '未登录'} · 设备端：${app.hasHuiLifeApp ? '已登录' : '未登录'}',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: GlassColors.muted(context),
                                ),
                              ),
                            ],
                          ),
                        ),
                        Text(
                          '¥${app.huiLifeWallet!.balance.toStringAsFixed(2)}',
                          style: const TextStyle(
                            fontSize: 26,
                            fontWeight: FontWeight.w800,
                            color: Color(0xFF0EA5E9),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                ],
                if (loggedIn) ...[
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '我的设备（每 10 秒刷新状态）',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                            color: GlassColors.muted(context),
                          ),
                        ),
                      ),
                      FilledButton.tonalIcon(
                        onPressed: _scanDeviceQr,
                        icon: const Icon(Icons.qr_code_scanner_rounded, size: 16),
                        label: const Text('扫码加设备'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  if (app.huiLifeDevices.isEmpty)
                    GlassCard(
                      child: Text(
                        '暂无收藏或扫码设备。点右上角「扫码加设备」扫描机器上的二维码即可查看实时状态。',
                        style: TextStyle(color: GlassColors.muted(context)),
                      ),
                    ),
                  for (final dev in app.huiLifeDevices) ...[
                    Builder(
                      builder: (context) {
                        final st = app.huiLifeStatuses[dev.id];
                        final isScanned = app.huiLifeScannedIds.contains(dev.id);
                        final canStart = app.hasHuiLifeApp &&
                            isScanned &&
                            st != null &&
                            st.canStart;
                        final canStop = app.hasHuiLifeApp &&
                            st != null &&
                            HuiLifeClient.canSafelyStop(
                              status: st,
                              currentUid: app.huiLifeUid,
                              localStartedAtMillis:
                                  app.huiLifeLocalStartedAt[dev.id],
                            );
                        final displayName =
                            (st != null && st.deviceName.isNotEmpty)
                                ? st.deviceName
                                : dev.name;
                        return GlassCard(
                          child: Row(
                            children: [
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      displayName,
                                      style: const TextStyle(
                                        fontSize: 15,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                    const SizedBox(height: 2),
                                    Text(
                                      '${st?.label ?? '查询中…'} · ${isScanned ? '已扫码认证' : '仅查看 · 扫码后可启动'}',
                                      style: TextStyle(
                                        fontSize: 12,
                                        color: st?.canStart == true
                                            ? GlassColors.primary
                                            : GlassColors.muted(context),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              if (canStart)
                                FilledButton(
                                  style: FilledButton.styleFrom(
                                    backgroundColor: GlassColors.primary,
                                  ),
                                  onPressed: () => _confirmStart(dev),
                                  child: const Text('启动'),
                                ),
                              if (canStop)
                                FilledButton(
                                  style: FilledButton.styleFrom(
                                    backgroundColor: GlassColors.danger,
                                  ),
                                  onPressed: () => _confirmStop(dev),
                                  child: const Text('停止'),
                                ),
                            ],
                          ),
                        );
                      },
                    ),
                    const SizedBox(height: 10),
                  ],
                  if (app.huiLifeBills.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      '最近消费',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: GlassColors.muted(context),
                      ),
                    ),
                    const SizedBox(height: 8),
                    GlassCard(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 6,
                      ),
                      child: Column(
                        children: [
                          for (var i = 0; i < app.huiLifeBills.length; i++) ...[
                            if (i > 0) const Divider(height: 1),
                            ListTile(
                              dense: true,
                              contentPadding: EdgeInsets.zero,
                              title: Text(app.huiLifeBills[i].title),
                              trailing: Text(
                                '-¥${app.huiLifeBills[i].amount.toStringAsFixed(2)}',
                                style: const TextStyle(
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),
                  ],
                ],
                // 登录卡片（默认查询端免配置；需设备启停时切换设备控制端）
                GlassCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        loggedIn ? '切换 / 补充登录' : '登录慧生活798',
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        children: [
                          ChoiceChip(
                            label: const Text('查询端（免配置）'),
                            selected: _loginPlatform == HuiLifePlatform.account,
                            onSelected: (_) => setState(
                              () => _loginPlatform = HuiLifePlatform.account,
                            ),
                          ),
                          ChoiceChip(
                            label: const Text('设备控制端'),
                            selected: _loginPlatform == HuiLifePlatform.app,
                            onSelected: (_) => setState(
                              () => _loginPlatform = HuiLifePlatform.app,
                            ),
                          ),
                        ],
                      ),
                      if (_loginPlatform == HuiLifePlatform.app &&
                          !app.runtimeConfig.hasHuiLife) ...[
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            const Expanded(
                              child: Text(
                                '设备控制端需要先在「高级配置」导入 798 设备盐值。',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: GlassColors.warning,
                                ),
                              ),
                            ),
                            TextButton(
                              onPressed: () {
                                Navigator.of(context).push(
                                  MaterialPageRoute(
                                    builder: (_) =>
                                        RuntimeConfigScreen(appState: app),
                                  ),
                                );
                              },
                              child: const Text('去配置'),
                            ),
                          ],
                        ),
                      ],
                      const SizedBox(height: 10),
                      TextField(
                        controller: _phoneCtrl,
                        keyboardType: TextInputType.phone,
                        decoration: const InputDecoration(
                          labelText: '手机号',
                          isDense: true,
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _graphCtrl,
                              decoration: const InputDecoration(
                                labelText: '图形验证码',
                                isDense: true,
                                border: OutlineInputBorder(),
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          GestureDetector(
                            onTap: _refreshCaptcha,
                            child: Container(
                              width: 108,
                              height: 44,
                              alignment: Alignment.center,
                              decoration: BoxDecoration(
                                color: Colors.black12,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: _captchaBytes != null
                                  ? Image.memory(_captchaBytes!, fit: BoxFit.contain)
                                  : const Text('点刷新',
                                      style: TextStyle(fontSize: 12)),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _smsCtrl,
                              keyboardType: TextInputType.number,
                              decoration: const InputDecoration(
                                labelText: '短信验证码',
                                isDense: true,
                                border: OutlineInputBorder(),
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          OutlinedButton(
                            onPressed: _busy ? null : _sendSms,
                            child: const Text('发短信'),
                          ),
                        ],
                      ),
                      if (_tip != null) ...[
                        const SizedBox(height: 8),
                        Text(
                          _tip!,
                          style: const TextStyle(
                            fontSize: 12.5,
                            color: GlassColors.primary,
                          ),
                        ),
                      ],
                      const SizedBox(height: 10),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          style: FilledButton.styleFrom(
                            backgroundColor: GlassColors.primary,
                          ),
                          onPressed: _busy ? null : _doLogin,
                          child: Text(_busy ? '处理中…' : '登录'),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
