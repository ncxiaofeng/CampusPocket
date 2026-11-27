import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app_state.dart';
import '../../core/app_links.dart';
import '../../data/platform_bridges.dart';
import '../../data/runtime_config.dart';
import '../theme/glass_theme.dart';

/// 高级配置页：运行期第三方参数管理（剪贴板导入 / 扫码导入 / 一次性安全配置码 + 防明文锁定）。
class RuntimeConfigScreen extends StatefulWidget {
  final CampusAppState appState;

  const RuntimeConfigScreen({super.key, required this.appState});

  @override
  State<RuntimeConfigScreen> createState() => _RuntimeConfigScreenState();
}

class _RuntimeConfigScreenState extends State<RuntimeConfigScreen> {
  final _elecCtrl = TextEditingController();
  final _ujingCtrl = TextEditingController();
  final _huiCtrl = TextEditingController();
  bool _obscure = true;
  bool _busy = false;
  String? _message;

  @override
  void initState() {
    super.initState();
    _syncControllers();
  }

  void _syncControllers() {
    final cfg = widget.appState.runtimeConfig;
    _elecCtrl.text = cfg.isSealed('e') ? '' : cfg.electricityOperatorPwd;
    _ujingCtrl.text = cfg.isSealed('u') ? '' : cfg.ujingHmacKey;
    _huiCtrl.text = cfg.isSealed('h') ? '' : cfg.huilifeAppSignSalt;
  }

  @override
  void dispose() {
    _elecCtrl.dispose();
    _ujingCtrl.dispose();
    _huiCtrl.dispose();
    super.dispose();
  }

  Future<void> _importFromText(String raw, {bool markSealed = false}) async {
    final claim = RemoteConfigClaimParser.parse(raw);
    if (claim != null) {
      setState(() {
        _busy = true;
        _message = '正在通过安全白名单拉取远程配置码…';
      });
      try {
        final outcome = await widget.appState.remoteClaimClient.claim(claim);
        await _applyMap(outcome.values, markSealed: true);
        if (mounted) {
          setState(() {
            _message = outcome.mode == RemoteClaimMode.once
                ? '一次性安全配置码已导入并锁定明文${outcome.burned ? '（云端已阅后即焚销毁）' : ''}'
                : '限时安全配置码已导入并锁定明文';
          });
        }
      } catch (e) {
        if (mounted) setState(() => _message = '远程配置码拉取失败：$e');
      } finally {
        if (mounted) setState(() => _busy = false);
      }
      return;
    }

    final parsed = RuntimeConfigTextParser.parse(raw);
    if (parsed.isEmpty) {
      setState(() => _message = '未识别到有效配置（支持 key=value、JSON 或安全配置码）');
      return;
    }
    await _applyMap(parsed, markSealed: markSealed);
    if (mounted) {
      setState(() => _message = '已加密保存 ${parsed.length} 项参数到本机 Keychain');
    }
  }

  Future<void> _applyMap(
    Map<String, String> values, {
    required bool markSealed,
  }) async {
    final prev = widget.appState.runtimeConfig;
    final sealed = Set<String>.from(prev.sealedFields);
    var e = prev.electricityOperatorPwd;
    var u = prev.ujingHmacKey;
    var h = prev.huilifeAppSignSalt;
    if (values.containsKey(RuntimeConfigTextParser.keyElectricity)) {
      e = values[RuntimeConfigTextParser.keyElectricity]!;
      if (markSealed) sealed.add('e');
    }
    if (values.containsKey(RuntimeConfigTextParser.keyUjing)) {
      u = values[RuntimeConfigTextParser.keyUjing]!;
      if (markSealed) sealed.add('u');
    }
    if (values.containsKey(RuntimeConfigTextParser.keyHuiLife)) {
      h = values[RuntimeConfigTextParser.keyHuiLife]!;
      if (markSealed) sealed.add('h');
    }
    await widget.appState.updateRuntimeConfig(
      RuntimeConfig(
        electricityOperatorPwd: e,
        ujingHmacKey: u,
        huilifeAppSignSalt: h,
        sealedFields: sealed,
      ),
    );
    _syncControllers();
  }

  Future<void> _importFromClipboard() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text ?? '';
    if (text.trim().isEmpty) {
      setState(() => _message = '剪贴板为空');
      return;
    }
    await _importFromText(text);
  }

  Future<void> _scanQr() async {
    final raw = await QrScannerBridge.scanQr(title: '扫描高级配置二维码');
    if (raw == null || !mounted) return;
    await _importFromText(raw);
  }

  Future<void> _saveManual() async {
    final prev = widget.appState.runtimeConfig;
    await widget.appState.updateRuntimeConfig(
      RuntimeConfig(
        electricityOperatorPwd: prev.isSealed('e')
            ? prev.electricityOperatorPwd
            : _elecCtrl.text.trim(),
        ujingHmacKey:
            prev.isSealed('u') ? prev.ujingHmacKey : _ujingCtrl.text.trim(),
        huilifeAppSignSalt: prev.isSealed('h')
            ? prev.huilifeAppSignSalt
            : _huiCtrl.text.trim(),
        sealedFields: prev.sealedFields,
      ),
    );
    setState(() => _message = '配置已加密保存到 iOS Keychain');
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.appState,
      builder: (context, _) {
        final cfg = widget.appState.runtimeConfig;
        return CampusBackdrop(
          child: Scaffold(
            appBar: AppBar(
              title: const Text('高级配置'),
              actions: [
                IconButton(
                  tooltip: _obscure ? '显示明文' : '隐藏明文',
                  onPressed: () => setState(() => _obscure = !_obscure),
                  icon: Icon(
                    _obscure
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                  ),
                ),
              ],
            ),
            body: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
              children: [
                GlassCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        '快捷导入（仅存本机 Keychain）',
                        style: TextStyle(
                          fontSize: 15.5,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'U净洗衣与慧生活798 查询已免配置可用；宿舍电费与 798 设备启停需在此导入运行期参数。支持普通配置二维码与一次性阅后即焚安全配置码。',
                        style: TextStyle(
                          fontSize: 12.5,
                          color: GlassColors.muted(context),
                        ),
                      ),
                      const SizedBox(height: 10),
                      Wrap(
                        spacing: 10,
                        runSpacing: 8,
                        children: [
                          FilledButton.icon(
                            style: FilledButton.styleFrom(
                              backgroundColor: GlassColors.primary,
                            ),
                            onPressed: _busy ? null : _scanQr,
                            icon: const Icon(Icons.qr_code_scanner_rounded,
                                size: 18),
                            label: const Text('扫码导入配置'),
                          ),
                          OutlinedButton.icon(
                            onPressed: _busy ? null : _importFromClipboard,
                            icon: const Icon(Icons.content_paste_rounded,
                                size: 18),
                            label: const Text('从剪贴板导入'),
                          ),
                        ],
                      ),
                      if (_message != null) ...[
                        const SizedBox(height: 10),
                        Text(
                          _message!,
                          style: const TextStyle(
                            fontSize: 12.5,
                            fontWeight: FontWeight.w600,
                            color: GlassColors.primary,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                GlassCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _field(
                        label: '宿舍电费 operatorPwd',
                        ctrl: _elecCtrl,
                        sealed: cfg.isSealed('e'),
                      ),
                      const SizedBox(height: 10),
                      _field(
                        label: 'U净 HMAC Key（可选，网页登录免配）',
                        ctrl: _ujingCtrl,
                        sealed: cfg.isSealed('u'),
                      ),
                      const SizedBox(height: 10),
                      _field(
                        label: '慧生活798 设备登录盐',
                        ctrl: _huiCtrl,
                        sealed: cfg.isSealed('h'),
                      ),
                      const SizedBox(height: 14),
                      Row(
                        children: [
                          Expanded(
                            child: FilledButton(
                              style: FilledButton.styleFrom(
                                backgroundColor: GlassColors.primary,
                              ),
                              onPressed: _busy ? null : _saveManual,
                              child: const Text('保存配置'),
                            ),
                          ),
                          const SizedBox(width: 10),
                          OutlinedButton(
                            onPressed: () async {
                              await widget.appState.clearRuntimeConfig();
                              _syncControllers();
                              setState(() => _message = '已清空全部高级配置');
                            },
                            child: const Text('清空'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                GlassCard(
                  onTap: () async {
                    await Clipboard.setData(
                      const ClipboardData(text: AppLinks.qqGroupNumber),
                    );
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('已复制 QQ 交流群号 ${AppLinks.qqGroupNumber}'),
                        ),
                      );
                    }
                  },
                  child: Row(
                    children: [
                      const IconBadge(
                        icon: Icons.forum_outlined,
                        color: Color(0xFFF97316),
                        size: 36,
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          'QQ 交流群：${AppLinks.qqGroupNumber}（点此复制）',
                          style: const TextStyle(fontWeight: FontWeight.w600),
                        ),
                      ),
                      const Icon(Icons.copy_rounded, size: 18),
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

  Widget _field({
    required String label,
    required TextEditingController ctrl,
    required bool sealed,
  }) {
    if (sealed) {
      return TextField(
        readOnly: true,
        controller: TextEditingController(text: '••••••••（安全配置码已锁定防明文查看）'),
        decoration: InputDecoration(
          labelText: label,
          isDense: true,
          border: const OutlineInputBorder(),
          prefixIcon: const Icon(Icons.lock_rounded, size: 18),
        ),
      );
    }
    return TextField(
      controller: ctrl,
      obscureText: _obscure,
      decoration: InputDecoration(
        labelText: label,
        isDense: true,
        border: const OutlineInputBorder(),
      ),
    );
  }
}
