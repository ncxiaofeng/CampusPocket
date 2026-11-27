import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../theme/glass_theme.dart';

/// 更多页：账号状态、自动登录、高级配置、全部生活与教务服务、关于与退出清理。
class MoreScreen extends StatelessWidget {
  final CampusAppState appState;
  final VoidCallback onOpenCasLogin;
  final VoidCallback onOpenJwLogin;
  final VoidCallback onOpenVpnLogin;
  final VoidCallback onOpenPortal;
  final VoidCallback onOpenEhall;
  final VoidCallback onOpenGrades;
  final VoidCallback onOpenExams;
  final VoidCallback onOpenClassrooms;
  final VoidCallback onOpenNotices;
  final VoidCallback onOpenLibrary;
  final VoidCallback onOpenElectricity;
  final VoidCallback onOpenUjing;
  final VoidCallback onOpenHuiLife;
  final VoidCallback onOpenChaoxing;
  final VoidCallback onOpenAutoLogin;
  final VoidCallback onOpenRuntimeConfig;
  final VoidCallback onOpenAbout;
  final VoidCallback onSync;

  const MoreScreen({
    super.key,
    required this.appState,
    required this.onOpenCasLogin,
    required this.onOpenJwLogin,
    required this.onOpenVpnLogin,
    required this.onOpenPortal,
    required this.onOpenEhall,
    required this.onOpenGrades,
    required this.onOpenExams,
    required this.onOpenClassrooms,
    required this.onOpenNotices,
    required this.onOpenLibrary,
    required this.onOpenElectricity,
    required this.onOpenUjing,
    required this.onOpenHuiLife,
    required this.onOpenChaoxing,
    required this.onOpenAutoLogin,
    required this.onOpenRuntimeConfig,
    required this.onOpenAbout,
    required this.onSync,
  });

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(
        16,
        12,
        16,
        LiquidGlassBottomBar.contentBottomInset,
      ),
      children: [
        Text(
          '更多',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w800,
            color: GlassColors.ink(context),
          ),
        ),
        const SizedBox(height: 12),
        GlassCard(
          child: Column(
            children: [
              _MenuRow(
                icon: Icons.verified_user_outlined,
                color: GlassColors.primary,
                title: '统一身份认证',
                subtitle: appState.hasCasMaster
                    ? '已登录（CASTGC 已加密保存至 Keychain）'
                    : '未登录 · 点击登录一次全站共用',
                onTap: onOpenCasLogin,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.school_outlined,
                color: const Color(0xFF3B82F6),
                title: '教务系统直连登录（校内）',
                subtitle: appState.hasJwSession
                    ? '已建立教务会话'
                    : '校内网环境可直接授权教务',
                onTap: onOpenJwLogin,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.vpn_lock_rounded,
                color: const Color(0xFF8B5CF6),
                title: '零信任 WebVPN 门户（校外）',
                subtitle: appState.hasProxySession
                    ? '代理会话已就绪 · 校外免 VPN 可同步教务'
                    : '校外同步：先认证并在门户打开一次“新教务管理系统”',
                onTap: appState.hasVpnSession ? onOpenPortal : onOpenVpnLogin,
                trailing: TextButton(
                  onPressed: onOpenPortal,
                  child: const Text('开门户'),
                ),
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.key_rounded,
                color: const Color(0xFF0EA5E9),
                title: '自动登录（可选）',
                subtitle: appState.autoLoginEnabled
                    ? '已开启（${appState.autoLoginUsername}）'
                    : '默认关闭，不保存账号密码',
                onTap: onOpenAutoLogin,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.tune_rounded,
                color: const Color(0xFFF59E0B),
                title: '高级配置（电费 / 798 设备盐）',
                subtitle: '支持扫码导入与一次性安全配置码',
                onTap: onOpenRuntimeConfig,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.sync_rounded,
                color: const Color(0xFF0D9488),
                title: '立即同步教务数据',
                subtitle: appState.syncingJw
                    ? '正在同步课表、成绩与考试…'
                    : '当前缓存：${appState.courses.length} 课程 · ${appState.grades.length} 成绩',
                onTap: onSync,
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        GlassCard(
          child: Column(
            children: [
              _MenuRow(
                icon: Icons.bolt_rounded,
                color: const Color(0xFFF59E0B),
                title: '宿舍电费',
                subtitle: appState.runtimeConfig.hasElectricity
                    ? '已配置，点击查看余额与 30 天用量'
                    : '未配置参数 · 可去高级配置或用今日校园',
                onTap: onOpenElectricity,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.local_laundry_service_rounded,
                color: const Color(0xFFEC4899),
                title: 'U净洗衣',
                subtitle: appState.hasUjingSession
                    ? '已登录 · 查看附近洗衣房空闲与价格'
                    : '官方网页免配置登录，开箱即用',
                onTap: onOpenUjing,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.water_drop_outlined,
                color: const Color(0xFF0EA5E9),
                title: '慧生活798',
                subtitle: '查询免配置 · 支持扫码启动与本人安全停止',
                onTap: onOpenHuiLife,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.assignment_outlined,
                color: const Color(0xFF6366F1),
                title: '超星学习通',
                subtitle: appState.hasChaoxingSession
                    ? '已登录 · 查看进行中签到、作业与考试'
                    : '只读待办提醒，不代签不代交',
                onTap: onOpenChaoxing,
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        GlassCard(
          child: Column(
            children: [
              _MenuRow(
                icon: Icons.bar_chart_rounded,
                color: const Color(0xFF6366F1),
                title: '成绩查询',
                onTap: onOpenGrades,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.event_note_rounded,
                color: const Color(0xFFEC4899),
                title: '考试安排',
                onTap: onOpenExams,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.meeting_room_outlined,
                color: const Color(0xFF3B82F6),
                title: '空闲教室',
                onTap: onOpenClassrooms,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.campaign_outlined,
                color: const Color(0xFFF97316),
                title: '通知公告',
                onTap: onOpenNotices,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.menu_book_rounded,
                color: const Color(0xFF8B5CF6),
                title: '图书馆空位',
                onTap: onOpenLibrary,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.public_rounded,
                color: const Color(0xFF0EA5E9),
                title: '网上办事大厅（ehall）',
                onTap: onOpenEhall,
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        GlassCard(
          child: Column(
            children: [
              _MenuRow(
                icon: Icons.info_outline_rounded,
                color: GlassColors.primary,
                title: '关于 冻小梨（iOS 自签版）',
                subtitle: 'v${CampusAppState.appVersion} · 检查更新 / 自签说明 / 交流群',
                onTap: onOpenAbout,
              ),
              const Divider(height: 1),
              _MenuRow(
                icon: Icons.logout_rounded,
                color: GlassColors.danger,
                title: '退出登录并清空本地数据',
                subtitle: '清除 Keychain 凭据、自动登录密码、WKWebView Cookie 与缓存',
                onTap: () => _confirmLogout(context),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Future<void> _confirmLogout(BuildContext context) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('确认退出并清空数据？'),
        content: const Text('将删除本机 Keychain 中的全部会话 Cookie、已保存自动登录密码以及本地缓存。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: GlassColors.danger),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('清空退出'),
          ),
        ],
      ),
    );
    if (ok == true) {
      await appState.logoutAndClearAll();
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('已清空本机凭据与缓存')),
        );
      }
    }
  }
}

class _MenuRow extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String title;
  final String? subtitle;
  final VoidCallback onTap;
  final Widget? trailing;

  const _MenuRow({
    required this.icon,
    required this.color,
    required this.title,
    this.subtitle,
    required this.onTap,
    this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: IconBadge(icon: icon, color: color, size: 38),
      title: Text(
        title,
        style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600),
      ),
      subtitle: subtitle != null
          ? Text(
              subtitle!,
              style: TextStyle(fontSize: 12, color: GlassColors.muted(context)),
            )
          : null,
      trailing: trailing ?? const Icon(Icons.chevron_right_rounded),
      onTap: onTap,
    );
  }
}
