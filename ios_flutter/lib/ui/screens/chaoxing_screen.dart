import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../core/campus_endpoints.dart';
import '../../network/chaoxing_client.dart';
import '../theme/glass_theme.dart';
import 'campus_web_page_screen.dart';
import 'login_webview_screen.dart';

/// 超星学习通页面：只读展示进行中签到、作业、考试（按本学期/待完成优先排序）。
class ChaoxingScreen extends StatefulWidget {
  final CampusAppState appState;

  const ChaoxingScreen({super.key, required this.appState});

  @override
  State<ChaoxingScreen> createState() => _ChaoxingScreenState();
}

class _ChaoxingScreenState extends State<ChaoxingScreen> {
  int _tab = 0; // 0 签到 / 1 作业 / 2 考试

  @override
  void initState() {
    super.initState();
    if (widget.appState.hasChaoxingSession &&
        widget.appState.chaoxing == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        widget.appState.refreshChaoxing();
      });
    }
  }

  Future<void> _openLogin() async {
    final ok = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => LoginWebViewScreen(
          appState: widget.appState,
          target: CampusEndpoints.chaoxingLogin,
        ),
      ),
    );
    if (ok == true && mounted) {
      await widget.appState.refreshChaoxing();
    }
  }

  void _openUrl(String title, String url) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => CampusWebPageScreen(
          appState: widget.appState,
          title: title,
          url: url,
          desktopUserAgent: false,
          helperText: '学习通官方页面 · App 严格只读，不提供代签或代交作业。',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.appState,
      builder: (context, _) {
        final app = widget.appState;
        final snap = app.chaoxing;

        return CampusBackdrop(
          child: Scaffold(
            appBar: AppBar(
              title: const Text('学习通'),
              actions: [
                TextButton(onPressed: _openLogin, child: const Text('登录')),
                IconButton(
                  onPressed: app.loadingChaoxing
                      ? null
                      : () => app.refreshChaoxing(),
                  icon: app.loadingChaoxing
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.refresh_rounded),
                ),
              ],
            ),
            body: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
              children: [
                if (!app.hasChaoxingSession)
                  GlassCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '尚未登录超星学习通',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          '在内嵌官方页登录后，可统一查看进行中的课堂签到、待办作业与考试；App 只读展示，不会代签或代交。',
                          style: TextStyle(
                            fontSize: 13,
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
                          label: const Text('去登录学习通'),
                        ),
                      ],
                    ),
                  )
                else ...[
                  if (app.chaoxingError != null) ...[
                    GlassCard(
                      child: Text(
                        app.chaoxingError!,
                        style: const TextStyle(color: GlassColors.danger),
                      ),
                    ),
                    const SizedBox(height: 10),
                  ],
                  SegmentedButton<int>(
                    segments: [
                      ButtonSegment(
                        value: 0,
                        label: Text('签到 (${snap?.signs.length ?? 0})'),
                      ),
                      ButtonSegment(
                        value: 1,
                        label: Text('作业 (${snap?.pendingWorkCount ?? 0}待办)'),
                      ),
                      ButtonSegment(
                        value: 2,
                        label: Text('考试 (${snap?.pendingExamCount ?? 0}待办)'),
                      ),
                    ],
                    selected: {_tab},
                    onSelectionChanged: (s) => setState(() => _tab = s.first),
                  ),
                  const SizedBox(height: 12),
                  if (snap == null)
                    const Padding(
                      padding: EdgeInsets.all(24),
                      child: Center(child: CircularProgressIndicator()),
                    )
                  else if (_tab == 0) ...[
                    if (snap.signs.isEmpty)
                      GlassCard(
                        child: Text(
                          '当前没有检测到进行中的签到活动。',
                          style: TextStyle(color: GlassColors.muted(context)),
                        ),
                      )
                    else
                      for (final sign in snap.signs) ...[
                        GlassCard(
                          onTap: () => _openUrl(sign.title, sign.url),
                          child: ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: Text(
                              sign.title,
                              style: const TextStyle(fontWeight: FontWeight.w700),
                            ),
                            subtitle: Text(sign.courseName),
                            trailing: Chip(
                              label: Text(sign.isOngoing ? '进行中' : '活动'),
                              backgroundColor:
                                  GlassColors.danger.withValues(alpha: 0.16),
                            ),
                          ),
                        ),
                        const SizedBox(height: 10),
                      ],
                  ] else ...[
                    for (final task
                        in (_tab == 1 ? snap.works : snap.exams)) ...[
                      GlassCard(
                        onTap: () => _openUrl(task.title, task.url),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    task.title,
                                    style: const TextStyle(
                                      fontSize: 15,
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                ),
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 8,
                                    vertical: 3,
                                  ),
                                  decoration: BoxDecoration(
                                    color: (task.needsAction
                                            ? GlassColors.warning
                                            : GlassColors.primary)
                                        .withValues(alpha: 0.16),
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                  child: Text(
                                    task.statusText.isEmpty
                                        ? (task.needsAction ? '待完成' : '已完成')
                                        : task.statusText,
                                    style: TextStyle(
                                      fontSize: 11.5,
                                      fontWeight: FontWeight.w700,
                                      color: task.needsAction
                                          ? GlassColors.warning
                                          : GlassColors.primary,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 4),
                            Text(
                              [
                                ChaoxingTerm.label(task.termRank),
                                task.courseName,
                                if (task.detail.isNotEmpty) task.detail,
                              ].where((s) => s.isNotEmpty).join(' · '),
                              style: TextStyle(
                                fontSize: 12,
                                color: GlassColors.muted(context),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 10),
                    ],
                  ],
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}
