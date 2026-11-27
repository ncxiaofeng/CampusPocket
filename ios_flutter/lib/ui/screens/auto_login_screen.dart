import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../theme/glass_theme.dart';

/// 可选自动登录设置页：默认关闭，仅用户主动验证并开启后加密保存在 iOS Keychain。
class AutoLoginScreen extends StatefulWidget {
  final CampusAppState appState;

  const AutoLoginScreen({super.key, required this.appState});

  @override
  State<AutoLoginScreen> createState() => _AutoLoginScreenState();
}

class _AutoLoginScreenState extends State<AutoLoginScreen> {
  final _userCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  bool _busy = false;
  String? _status;
  bool _isError = false;

  @override
  void initState() {
    super.initState();
    _userCtrl.text = widget.appState.autoLoginUsername;
  }

  @override
  void dispose() {
    _userCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  Future<void> _enable() async {
    final u = _userCtrl.text.trim();
    final p = _passCtrl.text;
    if (u.isEmpty || p.isEmpty) {
      setState(() {
        _isError = true;
        _status = '请输入统一身份认证学号和密码';
      });
      return;
    }
    setState(() {
      _busy = true;
      _isError = false;
      _status = '正在向学校官方 CAS 验证凭据…';
    });
    try {
      await widget.appState.enableAutoLogin(u, p);
      _passCtrl.clear();
      if (mounted) {
        setState(() {
          _isError = false;
          _status = '验证通过，已加密保存至 iOS Keychain 并开启自动恢复';
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isError = true;
          _status = '验证未通过：$e';
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.appState,
      builder: (context, _) {
        final enabled = widget.appState.autoLoginEnabled;
        return CampusBackdrop(
          child: Scaffold(
            appBar: AppBar(title: const Text('自动登录（可选）')),
            body: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
              children: [
                GlassCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        enabled ? '当前状态：已开启（${widget.appState.autoLoginUsername}）' : '当前状态：未开启（默认不保存密码）',
                        style: const TextStyle(
                          fontSize: 15.5,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        '开启后仅保存在本机 iOS Keychain 中，会话过期时先尝试静默换票，失败才自动提交官方 CAS 表单；遇到验证码或二次验证会立即停止并回退手动登录。关闭后会立即删除密文。',
                        style: TextStyle(
                          fontSize: 12.5,
                          height: 1.5,
                          color: GlassColors.muted(context),
                        ),
                      ),
                      if (enabled) ...[
                        const SizedBox(height: 12),
                        OutlinedButton.icon(
                          onPressed: () async {
                            await widget.appState.disableAutoLogin();
                            setState(() {
                              _isError = false;
                              _status = '已关闭并清除 Keychain 中的凭据';
                            });
                          },
                          icon: const Icon(Icons.delete_outline_rounded,
                              color: GlassColors.danger),
                          label: const Text(
                            '关闭并删除已保存密码',
                            style: TextStyle(color: GlassColors.danger),
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
                      TextField(
                        controller: _userCtrl,
                        decoration: const InputDecoration(
                          labelText: '统一身份认证学号 / 账号',
                          border: OutlineInputBorder(),
                          isDense: true,
                        ),
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        controller: _passCtrl,
                        obscureText: true,
                        decoration: const InputDecoration(
                          labelText: '密码',
                          border: OutlineInputBorder(),
                          isDense: true,
                        ),
                      ),
                      if (_status != null) ...[
                        const SizedBox(height: 10),
                        Text(
                          _status!,
                          style: TextStyle(
                            fontSize: 12.5,
                            color: _isError
                                ? GlassColors.danger
                                : GlassColors.primary,
                          ),
                        ),
                      ],
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          style: FilledButton.styleFrom(
                            backgroundColor: GlassColors.primary,
                          ),
                          onPressed: _busy ? null : _enable,
                          child: Text(_busy ? '验证中…' : '验证并启用自动登录'),
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
