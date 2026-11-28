import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../core/schedule_text_parser.dart';
import '../../data/models.dart';
import '../theme/glass_theme.dart';
import '../widgets/schedule_import_dialogs.dart';

/// 考试安排：按即将考试 / 已结束分组，支持手动添加与离线智能识别（剪贴板 / Vision 截图）。
class ExamsScreen extends StatelessWidget {
  final CampusAppState appState;
  final VoidCallback onSync;

  const ExamsScreen({
    super.key,
    required this.appState,
    required this.onSync,
  });

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final todayStr =
        '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';

    final upcoming = <JwExam>[];
    final finished = <JwExam>[];
    for (final exam in appState.exams) {
      if (exam.examDate.isEmpty || exam.examDate.compareTo(todayStr) >= 0) {
        upcoming.add(exam);
      } else {
        finished.add(exam);
      }
    }

    return CampusBackdrop(
      child: Scaffold(
        appBar: AppBar(
          title: const Text('考试安排'),
          actions: [
            IconButton(
              tooltip: '添加或智能识别考试',
              onPressed: () => showAddScheduleEntrySheet(
                context: context,
                appState: appState,
                defaultKindHint: ParsedItemKind.exam,
              ),
              icon: const Icon(Icons.add_rounded),
            ),
            IconButton(
              onPressed: appState.syncingJw ? null : onSync,
              icon: const Icon(Icons.sync_rounded),
            ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
          children: [
            if (appState.exams.isEmpty)
              GlassCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '暂无本学期考试安排',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: GlassColors.ink(context),
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      '可点击右上角刷新同步教务，或手动添加 / 通过剪贴板与截图离线识别考试通知，自动融合显示在课表上。',
                      style: TextStyle(
                        fontSize: 13,
                        color: GlassColors.muted(context),
                      ),
                    ),
                    const SizedBox(height: 12),
                    FilledButton.icon(
                      onPressed: () => showAddScheduleEntrySheet(
                        context: context,
                        appState: appState,
                        defaultKindHint: ParsedItemKind.exam,
                      ),
                      icon: const Icon(Icons.add_rounded),
                      label: const Text('添加 / 智能识别考试'),
                    ),
                  ],
                ),
              ),
            if (upcoming.isNotEmpty) ...[
              _SectionTitle('即将考试 (${upcoming.length})'),
              for (final exam in upcoming) ...[
                _ExamCard(
                  exam: exam,
                  finished: false,
                  onTap: exam.isCustom
                      ? () => showAddOrEditExamDialog(
                            context: context,
                            appState: appState,
                            initial: exam,
                          )
                      : null,
                ),
                const SizedBox(height: 10),
              ],
            ],
            if (finished.isNotEmpty) ...[
              _SectionTitle('已结束 (${finished.length})'),
              for (final exam in finished) ...[
                _ExamCard(
                  exam: exam,
                  finished: true,
                  onTap: exam.isCustom
                      ? () => showAddOrEditExamDialog(
                            context: context,
                            appState: appState,
                            initial: exam,
                          )
                      : null,
                ),
                const SizedBox(height: 10),
              ],
            ],
          ],
        ),
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 6, 4, 8),
      child: Text(
        text,
        style: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w700,
          color: GlassColors.muted(context),
        ),
      ),
    );
  }
}

class _ExamCard extends StatelessWidget {
  final JwExam exam;
  final bool finished;
  final VoidCallback? onTap;

  const _ExamCard({
    required this.exam,
    required this.finished,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  exam.courseName,
                  style: TextStyle(
                    fontSize: 15.5,
                    fontWeight: FontWeight.w700,
                    color: finished
                        ? GlassColors.muted(context)
                        : GlassColors.ink(context),
                  ),
                ),
              ),
              if (exam.isCustom) ...[
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                  decoration: BoxDecoration(
                    color: const Color(0xFFD93848).withValues(alpha: 0.14),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Text(
                    '自定义',
                    style: TextStyle(
                      fontSize: 11,
                      color: Color(0xFFD93848),
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                const SizedBox(width: 6),
              ],
              if (exam.examType.isNotEmpty)
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: GlassColors.primary.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    exam.examType,
                    style: const TextStyle(
                      fontSize: 11,
                      color: GlassColors.primary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            '时间：${exam.examTime.isEmpty ? '待定' : exam.examTime}',
            style: TextStyle(fontSize: 13, color: GlassColors.ink(context)),
          ),
          if (exam.location.isNotEmpty || exam.campus.isNotEmpty)
            Text(
              '地点：${[exam.campus, exam.location].where((s) => s.isNotEmpty).join(' · ')}',
              style: TextStyle(fontSize: 13, color: GlassColors.muted(context)),
            ),
          if (exam.seatNo.isNotEmpty)
            Text(
              '座位号：${exam.seatNo}',
              style: TextStyle(fontSize: 13, color: GlassColors.muted(context)),
            ),
          if (exam.note.isNotEmpty || exam.isCustom)
            Text(
              [if (exam.note.isNotEmpty) '备注：${exam.note}', if (exam.isCustom) '点此编辑或删除']
                  .join(' · '),
              style: TextStyle(fontSize: 12, color: GlassColors.muted(context)),
            ),
        ],
      ),
    );
  }
}
