import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../data/models.dart';
import '../theme/glass_theme.dart';

/// 成绩查询：按学期分组展示学分、绩点与加权 GPA，不及格标红。
class GradesScreen extends StatelessWidget {
  final CampusAppState appState;
  final VoidCallback onSync;

  const GradesScreen({
    super.key,
    required this.appState,
    required this.onSync,
  });

  @override
  Widget build(BuildContext context) {
    final grades = appState.grades;
    final totalCredit = grades.fold<double>(0, (s, g) => s + g.credit);
    final weightedGpa = totalCredit > 0
        ? grades.fold<double>(0, (s, g) => s + g.credit * g.gpa) / totalCredit
        : 0.0;

    final grouped = <String, List<JwGrade>>{};
    for (final g in grades) {
      final key = g.term.isEmpty ? '其他学期' : g.term;
      grouped.putIfAbsent(key, () => []).add(g);
    }
    final terms = grouped.keys.toList()..sort((a, b) => b.compareTo(a));

    return CampusBackdrop(
      child: Scaffold(
        appBar: AppBar(
          title: const Text('成绩查询'),
          actions: [
            IconButton(
              onPressed: appState.syncingJw ? null : onSync,
              icon: const Icon(Icons.sync_rounded),
            ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
          children: [
            GlassCard(
              child: Row(
                children: [
                  Expanded(
                    child: _SummaryItem(
                      label: '总门数',
                      value: '${grades.length}',
                    ),
                  ),
                  Expanded(
                    child: _SummaryItem(
                      label: '总学分',
                      value: totalCredit.toStringAsFixed(1),
                    ),
                  ),
                  Expanded(
                    child: _SummaryItem(
                      label: '加权绩点',
                      value: weightedGpa.toStringAsFixed(2),
                      highlight: true,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),
            if (grades.isEmpty)
              GlassCard(
                child: Text(
                  '暂无成绩缓存，点击右上角同步按钮从教务系统拉取全部学期成绩。',
                  style: TextStyle(color: GlassColors.muted(context)),
                ),
              ),
            for (final term in terms) ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(4, 6, 4, 8),
                child: Text(
                  term,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: GlassColors.muted(context),
                  ),
                ),
              ),
              GlassCard(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                child: Column(
                  children: [
                    for (var i = 0; i < grouped[term]!.length; i++) ...[
                      if (i > 0) const Divider(height: 1),
                      _GradeRow(grade: grouped[term]![i]),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 10),
            ],
          ],
        ),
      ),
    );
  }
}

class _SummaryItem extends StatelessWidget {
  final String label;
  final String value;
  final bool highlight;

  const _SummaryItem({
    required this.label,
    required this.value,
    this.highlight = false,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(
          value,
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w800,
            color: highlight ? GlassColors.primary : GlassColors.ink(context),
          ),
        ),
        const SizedBox(height: 2),
        Text(
          label,
          style: TextStyle(fontSize: 12, color: GlassColors.muted(context)),
        ),
      ],
    );
  }
}

class _GradeRow extends StatelessWidget {
  final JwGrade grade;

  const _GradeRow({required this.grade});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(
        grade.courseName,
        style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600),
      ),
      subtitle: Text(
        '学分 ${grade.credit.toStringAsFixed(1)} · 绩点 ${grade.gpa.toStringAsFixed(2)}'
        '${grade.nature.isNotEmpty ? ' · ${grade.nature}' : ''}',
        style: TextStyle(fontSize: 12, color: GlassColors.muted(context)),
      ),
      trailing: Text(
        grade.score,
        style: TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w800,
          color: grade.passed ? GlassColors.primary : GlassColors.danger,
        ),
      ),
    );
  }
}
