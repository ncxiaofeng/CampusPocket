import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../core/campus_endpoints.dart';
import '../../network/campus_network_status.dart';
import '../theme/glass_theme.dart';
import 'about_screen.dart';
import 'campus_card_screen.dart';
import 'campus_web_page_screen.dart';
import 'classrooms_screen.dart';
import 'exams_screen.dart';
import 'grades_screen.dart';
import 'library_screen.dart';
import 'login_webview_screen.dart';
import 'more_screen.dart';
import 'notices_screen.dart';
import 'schedule_screen.dart';
import 'today_screen.dart';

/// 主框架：4 Tab 悬浮液态玻璃底栏 + 中央加号快捷面板。
class ShellScreen extends StatefulWidget {
  final CampusAppState appState;

  const ShellScreen({super.key, required this.appState});

  @override
  State<ShellScreen> createState() => _ShellScreenState();
}

class _ShellScreenState extends State<ShellScreen> {
  int _tabIndex = 0;

  static const _tabs = [
    NavTabItem('首页', Icons.home_outlined, Icons.home_rounded),
    NavTabItem('课表', Icons.calendar_month_outlined, Icons.calendar_month_rounded),
    NavTabItem('校园卡', Icons.credit_card_outlined, Icons.credit_card_rounded),
    NavTabItem('更多', Icons.menu_rounded, Icons.menu_open_rounded),
  ];

  void _push(Widget page) {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => page));
  }

  Future<void> _openLogin(LoginTarget target) async {
    final ok = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => LoginWebViewScreen(
          appState: widget.appState,
          target: target,
        ),
      ),
    );
    if (ok == true && mounted) {
      await widget.appState.refreshSessionFlags();
      if (target.systemId == CampusEndpoints.systemCasMaster ||
          target.systemId == CampusEndpoints.systemJw) {
        _syncJw();
      }
    }
  }

  void _openSmartLogin() {
    if (widget.appState.netState == CampusNetworkState.onCampus) {
      _openLogin(CampusEndpoints.jwLogin);
    } else {
      _openLogin(CampusEndpoints.casLogin);
    }
  }

  void _openPortal() {
    _push(
      CampusWebPageScreen(
        appState: widget.appState,
        title: '零信任 WebVPN 门户',
        url: CampusEndpoints.atrustPortalUrl,
        desktopUserAgent: true,
        allowUserAgentSwitch: true,
        saveProxySession: true,
        helperText: '校外使用：登录后点击「新教务管理系统」进入任意页面一次，即可自动捕获代理会话并回 App 原生同步课表成绩。',
      ),
    );
  }

  void _openCardOfficialPage() {
    _push(
      CampusWebPageScreen(
        appState: widget.appState,
        title: '校园卡官方页',
        url: CampusEndpoints.ehallCardUrl,
        saveSessionHost: 'ehall.ecut.edu.cn',
        saveSessionSystemId: CampusEndpoints.systemCard,
        helperText: '登录后返回校园卡页会自动同步余额与本月流水。',
      ),
    );
  }

  void _openEhall() {
    _push(
      CampusWebPageScreen(
        appState: widget.appState,
        title: '网上办事大厅',
        url: CampusEndpoints.ehallUrl,
        desktopUserAgent: true,
        allowUserAgentSwitch: true,
        saveSessionHost: 'ehall.ecut.edu.cn',
        saveSessionSystemId: CampusEndpoints.systemCard,
      ),
    );
  }

  Future<void> _syncJw() async {
    final msg = await widget.appState.syncJwAll();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  void _showQuickActions() {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        final actions = [
          ('立即同步', Icons.sync_rounded, GlassColors.primary, _syncJw),
          (
            '成绩查询',
            Icons.bar_chart_rounded,
            const Color(0xFF6366F1),
            () => _push(GradesScreen(appState: widget.appState, onSync: _syncJw)),
          ),
          (
            '考试安排',
            Icons.event_note_rounded,
            const Color(0xFFEC4899),
            () => _push(ExamsScreen(appState: widget.appState, onSync: _syncJw)),
          ),
          (
            '空闲教室',
            Icons.meeting_room_outlined,
            const Color(0xFF3B82F6),
            () => _push(ClassroomsScreen(appState: widget.appState)),
          ),
          (
            '图书馆空位',
            Icons.menu_book_rounded,
            const Color(0xFF8B5CF6),
            () => _push(LibraryScreen(appState: widget.appState)),
          ),
          (
            '零信任门户',
            Icons.vpn_lock_rounded,
            const Color(0xFF0D9488),
            _openPortal,
          ),
        ];

        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: GlassCard(
              radius: 30,
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 22),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 38,
                    height: 4,
                    decoration: BoxDecoration(
                      color: GlassColors.muted(context).withValues(alpha: 0.35),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '快捷操作',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w800,
                            color: GlassColors.ink(context),
                          ),
                        ),
                      ),
                      IconButton(
                        onPressed: () => Navigator.of(ctx).pop(),
                        icon: const Icon(Icons.close_rounded),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  GridView.count(
                    crossAxisCount: 3,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    mainAxisSpacing: 10,
                    crossAxisSpacing: 10,
                    childAspectRatio: 1.12,
                    children: [
                      for (final a in actions)
                        InkWell(
                          onTap: () {
                            Navigator.of(ctx).pop();
                            a.$4();
                          },
                          borderRadius: BorderRadius.circular(18),
                          child: Container(
                            decoration: BoxDecoration(
                              color: a.$3.withValues(alpha: 0.10),
                              borderRadius: BorderRadius.circular(18),
                            ),
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                IconBadge(icon: a.$2, color: a.$3, size: 38),
                                const SizedBox(height: 6),
                                Text(
                                  a.$1,
                                  style: TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600,
                                    color: GlassColors.ink(context),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.appState,
      builder: (context, _) {
        final pages = [
          TodayScreen(
            appState: widget.appState,
            onOpenLogin: _openSmartLogin,
            onOpenPortal: _openPortal,
            onOpenSchedule: () => setState(() => _tabIndex = 1),
            onOpenCard: () => setState(() => _tabIndex = 2),
            onOpenGrades: () =>
                _push(GradesScreen(appState: widget.appState, onSync: _syncJw)),
            onOpenExams: () =>
                _push(ExamsScreen(appState: widget.appState, onSync: _syncJw)),
            onOpenClassrooms: () =>
                _push(ClassroomsScreen(appState: widget.appState)),
            onOpenNotices: () =>
                _push(NoticesScreen(appState: widget.appState)),
            onOpenLibrary: () =>
                _push(LibraryScreen(appState: widget.appState)),
            onSync: _syncJw,
          ),
          ScheduleScreen(appState: widget.appState, onSync: _syncJw),
          CampusCardScreen(
            appState: widget.appState,
            onOpenOfficialPage: _openCardOfficialPage,
          ),
          MoreScreen(
            appState: widget.appState,
            onOpenCasLogin: () => _openLogin(CampusEndpoints.casLogin),
            onOpenJwLogin: () => _openLogin(CampusEndpoints.jwLogin),
            onOpenVpnLogin: () => _openLogin(CampusEndpoints.atrustLogin),
            onOpenPortal: _openPortal,
            onOpenEhall: _openEhall,
            onOpenGrades: () =>
                _push(GradesScreen(appState: widget.appState, onSync: _syncJw)),
            onOpenExams: () =>
                _push(ExamsScreen(appState: widget.appState, onSync: _syncJw)),
            onOpenClassrooms: () =>
                _push(ClassroomsScreen(appState: widget.appState)),
            onOpenNotices: () =>
                _push(NoticesScreen(appState: widget.appState)),
            onOpenLibrary: () =>
                _push(LibraryScreen(appState: widget.appState)),
            onOpenAbout: () => _push(AboutScreen(appState: widget.appState)),
            onSync: _syncJw,
          ),
        ];

        return CampusBackdrop(
          child: Scaffold(
            extendBody: true,
            backgroundColor: Colors.transparent,
            body: SafeArea(
              bottom: false,
              child: IndexedStack(index: _tabIndex, children: pages),
            ),
            bottomNavigationBar: LiquidGlassBottomBar(
              items: _tabs,
              currentIndex: _tabIndex,
              onTabSelected: (idx) => setState(() => _tabIndex = idx),
              onAddPressed: _showQuickActions,
            ),
          ),
        );
      },
    );
  }
}
