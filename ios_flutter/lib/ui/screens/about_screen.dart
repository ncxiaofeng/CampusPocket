import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../app_state.dart';
import '../../core/app_links.dart';
import '../theme/glass_theme.dart';

/// 关于页：iOS 未越狱免费自签说明、检查更新、交流群与安全边界。
class AboutScreen extends StatefulWidget {
  final CampusAppState appState;

  const AboutScreen({super.key, required this.appState});

  @override
  State<AboutScreen> createState() => _AboutScreenState();
}

class _AboutScreenState extends State<AboutScreen> {
  bool _checking = false;
  String? _updateMessage;

  Future<void> _checkUpdate() async {
    setState(() {
      _checking = true;
      _updateMessage = null;
    });
    try {
      final info = await widget.appState.updateChecker
          .check(CampusAppState.appVersion);
      if (!mounted) return;
      if (info == null) {
        setState(() => _updateMessage = '当前已是最新 iOS 自签版本');
      } else {
        setState(() => _updateMessage = '发现新版本 ${info.version}');
        await showDialog<void>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: Text('发现新版本 ${info.version}'),
            content: SingleChildScrollView(
              child: Text(
                info.releaseNotes.isEmpty
                    ? '请下载新版未签名 IPA，使用 SideStore / AltStore / Sideloadly 覆盖自签安装（同 Apple ID 会保留数据）。'
                    : info.releaseNotes,
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(),
                child: const Text('稍后'),
              ),
              FilledButton(
                onPressed: () {
                  Navigator.of(ctx).pop();
                  launchUrl(
                    Uri.parse(info.ipaDownloadUrl ?? info.releasePageUrl),
                    mode: LaunchMode.externalApplication,
                  );
                },
                child: const Text('前往下载 IPA'),
              ),
            ],
          ),
        );
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _updateMessage = '检查更新失败，可直接使用蓝奏云或 GitHub 页面查看。');
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return CampusBackdrop(
      child: Scaffold(
        appBar: AppBar(title: const Text('关于 冻小梨')),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 28),
          children: [
            GlassCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '冻小梨 · iOS 免开发者账号自签版',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                      color: GlassColors.ink(context),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    '版本 v${CampusAppState.appVersion} (${CampusAppState.appBuildNumber}) · 纯本地无后端',
                    style: TextStyle(
                      fontSize: 13,
                      color: GlassColors.muted(context),
                    ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    '专为未越狱 iPhone 优化：零付费开发者权限（不含 Push/App Groups/Widget 扩展），支持免费 Apple ID 通过 SideStore / AltStore / Sideloadly 手动自签安装。只要保持同一 Apple ID 续签覆盖安装，Keychain 中的登录态与本地课表不会丢失。',
                    style: TextStyle(
                      fontSize: 13,
                      height: 1.5,
                      color: GlassColors.ink(context),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            GlassCard(
              child: Column(
                children: [
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const IconBadge(
                      icon: Icons.system_update_rounded,
                      color: GlassColors.primary,
                      size: 38,
                    ),
                    title: const Text('检查 iOS 自签版更新'),
                    subtitle: _updateMessage != null
                        ? Text(_updateMessage!)
                        : const Text('通过 GitHub Releases 检测最新无签 IPA'),
                    trailing: _checking
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.chevron_right_rounded),
                    onTap: _checking ? null : _checkUpdate,
                  ),
                  const Divider(height: 1),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const IconBadge(
                      icon: Icons.menu_book_outlined,
                      color: Color(0xFF3B82F6),
                      size: 38,
                    ),
                    title: const Text('iOS 自签与 7 天续签指南'),
                    subtitle: const Text('SideStore / Sideloadly / AltStore 安装说明'),
                    trailing: const Icon(Icons.open_in_new_rounded, size: 18),
                    onTap: () => launchUrl(
                      Uri.parse(AppLinks.iosSideloadGuide),
                      mode: LaunchMode.externalApplication,
                    ),
                  ),
                  const Divider(height: 1),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const IconBadge(
                      icon: Icons.code_rounded,
                      color: Color(0xFF6366F1),
                      size: 38,
                    ),
                    title: const Text('GitHub 开源仓库与 Releases'),
                    subtitle: const Text('ncxiaofeng/CampusPocket'),
                    trailing: const Icon(Icons.open_in_new_rounded, size: 18),
                    onTap: () => launchUrl(
                      Uri.parse(AppLinks.githubReleases),
                      mode: LaunchMode.externalApplication,
                    ),
                  ),
                  const Divider(height: 1),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const IconBadge(
                      icon: Icons.cloud_download_outlined,
                      color: Color(0xFF0D9488),
                      size: 38,
                    ),
                    title: const Text('蓝奏云镜像（提取码 ${AppLinks.lanzouFolderPwd}）'),
                    subtitle: const Text('国内网络加速下载入口'),
                    trailing: const Icon(Icons.open_in_new_rounded, size: 18),
                    onTap: () => launchUrl(
                      Uri.parse(AppLinks.lanzouFolder),
                      mode: LaunchMode.externalApplication,
                    ),
                  ),
                  const Divider(height: 1),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const IconBadge(
                      icon: Icons.forum_outlined,
                      color: Color(0xFFF97316),
                      size: 38,
                    ),
                    title: const Text('QQ 交流群：${AppLinks.qqGroupNumber}'),
                    subtitle: const Text('点击复制群号反馈问题或获取新版自签通知'),
                    trailing: const Icon(Icons.copy_rounded, size: 18),
                    onTap: () async {
                      await Clipboard.setData(
                        const ClipboardData(text: AppLinks.qqGroupNumber),
                      );
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('已复制 QQ 群号 ${AppLinks.qqGroupNumber}'),
                          ),
                        );
                      }
                    },
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            GlassCard(
              child: Text(
                '安全与隐私边界：\n'
                '• 纯本地无后端，不收集任何个人身份或使用日志。\n'
                '• 登录全程在学校官方网页完成，默认不保存账号密码，仅在 iOS Keychain 加密保存会话 Cookie。\n'
                '• 只读优先：不提供代签、代交作业、自动打卡、支付或充值功能。',
                style: TextStyle(
                  fontSize: 12.5,
                  height: 1.55,
                  color: GlassColors.muted(context),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
