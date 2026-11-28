import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app_state.dart';
import '../../core/schedule_adjustments.dart';
import '../../core/schedule_text_parser.dart';
import '../../core/semester_config.dart';
import '../../data/models.dart';
import '../../data/platform_bridges.dart';
import '../theme/glass_theme.dart';

const _dayLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

/// 点击「+」后的快捷入口：剪贴板智能识别 / iOS Vision 离线截图识图 / 手动添加课程 / 手动添加考试。
Future<void> showAddScheduleEntrySheet({
  required BuildContext context,
  required CampusAppState appState,
  int? defaultWeek,
  ParsedItemKind? defaultKindHint,
}) async {
  final messenger = ScaffoldMessenger.of(context);
  await showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    builder: (sheetCtx) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 18),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.only(left: 4, bottom: 6),
              child: Text(
                '添加课表 / 考试',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(left: 4, bottom: 12),
              child: Text(
                '支持离线智能解析剪贴板通知或群聊截图，解析结果会先交给你核对修改后再保存。',
                style: TextStyle(fontSize: 12.5, color: GlassColors.muted(sheetCtx)),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.content_paste_go_rounded, color: GlassColors.primary),
              title: const Text('从剪贴板智能识别（加课 / 考试）'),
              subtitle: const Text('自动提取时间、周次、教室或考场，审核后加入'),
              onTap: () async {
                Navigator.of(sheetCtx).pop();
                final clip = await Clipboard.getData(Clipboard.kTextPlain);
                final text = (clip?.text ?? '').trim();
                if (!context.mounted) return;
                if (text.isEmpty) {
                  messenger.showSnackBar(
                    const SnackBar(content: Text('剪贴板为空，请先复制加课或考试通知消息')),
                  );
                  return;
                }
                await showSmartImportReviewDialog(
                  context: context,
                  appState: appState,
                  sourceLabel: '剪贴板消息',
                  initialRawText: text,
                  defaultKindHint: defaultKindHint,
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.document_scanner_outlined, color: GlassColors.primary),
              title: const Text('从截图离线识别（iOS Vision OCR）'),
              subtitle: const Text('本机离线识别截图文字，不上传图片，审核后加入'),
              onTap: () async {
                Navigator.of(sheetCtx).pop();
                final text = await OfflineOcrBridge.pickImageAndRecognizeText();
                if (!context.mounted) return;
                if (text == null) return; // 用户取消
                if (text.isEmpty) {
                  messenger.showSnackBar(
                    const SnackBar(content: Text('未从截图中识别到文字，请换清晰截图或改用剪贴板')),
                  );
                  return;
                }
                await showSmartImportReviewDialog(
                  context: context,
                  appState: appState,
                  sourceLabel: '截图离线识别',
                  initialRawText: text,
                  defaultKindHint: defaultKindHint,
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.add_box_outlined, color: Color(0xFF3B82F6)),
              title: const Text('手动添加自定义课程'),
              subtitle: const Text('教务同步时自动保留，不会覆盖'),
              onTap: () {
                Navigator.of(sheetCtx).pop();
                showAddOrEditCourseDialog(
                  context: context,
                  appState: appState,
                  defaultWeek: defaultWeek ?? appState.currentWeek,
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.event_note_outlined, color: Color(0xFFD93848)),
              title: const Text('手动添加自定义考试'),
              subtitle: const Text('自动同步显示在对应周课表与首页今日安排'),
              onTap: () {
                Navigator.of(sheetCtx).pop();
                showAddOrEditExamDialog(
                  context: context,
                  appState: appState,
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.swap_horiz_rounded, color: Color(0xFFF59E0B)),
              title: const Text('节假日调休 / 调课'),
              subtitle: const Text('设置放假停课或某日改上另一天的课，课表与今日同步生效'),
              onTap: () {
                Navigator.of(sheetCtx).pop();
                showHolidayAdjustmentDialog(
                  context: context,
                  appState: appState,
                );
              },
            ),
          ],
        ),
      ),
    ),
  );
}

Future<void> showAddOrEditCourseDialog({
  required BuildContext context,
  required CampusAppState appState,
  JwCourse? initial,
  int? defaultWeek,
}) async {
  final week = defaultWeek ?? appState.currentWeek;
  final nameCtrl = TextEditingController(text: initial?.name ?? '');
  final spCtrl = TextEditingController(text: '${initial?.startPeriod ?? 1}');
  final epCtrl = TextEditingController(text: '${initial?.endPeriod ?? 2}');
  final weeksCtrl = TextEditingController(
    text: (initial?.weeksSpec.isNotEmpty ?? false) ? initial!.weeksSpec : '第$week周',
  );
  final campusCtrl = TextEditingController(text: initial?.campus ?? '');
  final roomCtrl = TextEditingController(text: initial?.room ?? '');
  final teacherCtrl = TextEditingController(text: initial?.teacher ?? '');
  var dayOfWeek = initial?.dayOfWeek ?? (DateTime.now().weekday - 1);
  String? error;

  await showDialog<void>(
    context: context,
    builder: (ctx) => StatefulBuilder(
      builder: (ctx, setState) => AlertDialog(
        title: Text(initial == null ? '添加自定义课程' : '编辑自定义课程'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(labelText: '课程名称 *'),
              ),
              const SizedBox(height: 10),
              const Text('星期', style: TextStyle(fontSize: 12, color: Colors.grey)),
              const SizedBox(height: 6),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  for (var i = 0; i < 7; i++)
                    ChoiceChip(
                      label: Text(_dayLabels[i].substring(1)),
                      selected: dayOfWeek == i,
                      onSelected: (_) => setState(() => dayOfWeek = i),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: spCtrl,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: '开始节次 (1-12)'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: epCtrl,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: '结束节次'),
                    ),
                  ),
                ],
              ),
              TextField(
                controller: weeksCtrl,
                decoration: const InputDecoration(
                  labelText: '周次（如 1-16周 / 13-16周(单) / 第12周）',
                ),
              ),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: campusCtrl,
                      decoration: const InputDecoration(labelText: '校区（可选）'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: roomCtrl,
                      decoration: const InputDecoration(labelText: '教室'),
                    ),
                  ),
                ],
              ),
              TextField(
                controller: teacherCtrl,
                decoration: const InputDecoration(labelText: '教师（可选）'),
              ),
              if (error != null) ...[
                const SizedBox(height: 8),
                Text(error!, style: const TextStyle(color: Colors.red, fontSize: 12)),
              ],
            ],
          ),
        ),
        actions: [
          if (initial != null && initial.isCustom)
            TextButton(
              onPressed: () async {
                await appState.deleteCustomCourse(initial.id);
                if (ctx.mounted) Navigator.of(ctx).pop();
              },
              child: const Text('删除', style: TextStyle(color: Colors.red)),
            ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () async {
              final title = nameCtrl.text.trim();
              final sp = int.tryParse(spCtrl.text.trim()) ?? 0;
              final ep = int.tryParse(epCtrl.text.trim()) ?? 0;
              if (title.isEmpty) {
                setState(() => error = '请填写课程名称');
                return;
              }
              if (sp < 1 || sp > 12 || ep < sp || ep > 12) {
                setState(() => error = '节次需在 1-12 之间且结束节次不小于开始节次');
                return;
              }
              await appState.upsertCustomCourse(
                existingId: initial?.id,
                name: title,
                dayOfWeek: dayOfWeek,
                startPeriod: sp,
                endPeriod: ep,
                weeksSpec: weeksCtrl.text.trim().isEmpty ? '第$week周' : weeksCtrl.text.trim(),
                room: roomCtrl.text.trim(),
                campus: campusCtrl.text.trim(),
                teacher: teacherCtrl.text.trim(),
              );
              if (ctx.mounted) Navigator.of(ctx).pop();
            },
            child: const Text('保存'),
          ),
        ],
      ),
    ),
  );
}

Future<void> showAddOrEditExamDialog({
  required BuildContext context,
  required CampusAppState appState,
  JwExam? initial,
}) async {
  final now = DateTime.now();
  final todayIso =
      '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
  final nameCtrl = TextEditingController(text: initial?.courseName ?? '');
  final dateCtrl = TextEditingController(
    text: (initial?.examDate.isNotEmpty ?? false) ? initial!.examDate : todayIso,
  );
  final startCtrl = TextEditingController(
    text: (initial?.startClock.isNotEmpty ?? false) ? initial!.startClock : '14:00',
  );
  final endCtrl = TextEditingController(
    text: (initial?.endClock.isNotEmpty ?? false) ? initial!.endClock : '16:00',
  );
  final campusCtrl = TextEditingController(text: initial?.campus ?? '');
  final roomCtrl = TextEditingController(text: initial?.location ?? '');
  final typeCtrl = TextEditingController(
    text: (initial?.examType.isNotEmpty ?? false) ? initial!.examType : '期末考试',
  );
  final seatCtrl = TextEditingController(text: initial?.seatNo ?? '');
  final noteCtrl = TextEditingController(text: initial?.note ?? '');
  String? error;

  await showDialog<void>(
    context: context,
    builder: (ctx) => StatefulBuilder(
      builder: (ctx, setState) => AlertDialog(
        title: Text(initial == null ? '添加自定义考试' : '编辑自定义考试'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(labelText: '考试科目 *'),
              ),
              TextField(
                controller: dateCtrl,
                decoration: const InputDecoration(
                  labelText: '考试日期 *（yyyy-MM-dd）',
                ),
              ),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: startCtrl,
                      decoration: const InputDecoration(labelText: '开始时间'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: endCtrl,
                      decoration: const InputDecoration(labelText: '结束时间'),
                    ),
                  ),
                ],
              ),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: campusCtrl,
                      decoration: const InputDecoration(labelText: '校区（可选）'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: roomCtrl,
                      decoration: const InputDecoration(labelText: '考场 / 教室'),
                    ),
                  ),
                ],
              ),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: typeCtrl,
                      decoration: const InputDecoration(labelText: '考试类型'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: seatCtrl,
                      decoration: const InputDecoration(labelText: '座位号（可选）'),
                    ),
                  ),
                ],
              ),
              TextField(
                controller: noteCtrl,
                decoration: const InputDecoration(labelText: '备注（如 闭卷）'),
              ),
              if (error != null) ...[
                const SizedBox(height: 8),
                Text(error!, style: const TextStyle(color: Colors.red, fontSize: 12)),
              ],
            ],
          ),
        ),
        actions: [
          if (initial != null && initial.isCustom)
            TextButton(
              onPressed: () async {
                await appState.deleteCustomExam(initial.key);
                if (ctx.mounted) Navigator.of(ctx).pop();
              },
              child: const Text('删除', style: TextStyle(color: Colors.red)),
            ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () async {
              final title = nameCtrl.text.trim();
              final dateStr = dateCtrl.text.trim();
              if (title.isEmpty) {
                setState(() => error = '请填写考试科目');
                return;
              }
              if (!RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(dateStr)) {
                setState(() => error = '日期格式需为 yyyy-MM-dd，例如 2026-12-15');
                return;
              }
              await appState.upsertCustomExam(
                existingKey: initial?.key,
                courseName: title,
                examDate: dateStr,
                startClock: startCtrl.text.trim(),
                endClock: endCtrl.text.trim(),
                location: roomCtrl.text.trim(),
                campus: campusCtrl.text.trim(),
                examType: typeCtrl.text.trim(),
                seatNo: seatCtrl.text.trim(),
                note: noteCtrl.text.trim(),
              );
              if (ctx.mounted) Navigator.of(ctx).pop();
            },
            child: const Text('保存'),
          ),
        ],
      ),
    ),
  );
}

/// 智能识别审核与编辑弹窗（必须经本人确认后才落库）。
Future<void> showSmartImportReviewDialog({
  required BuildContext context,
  required CampusAppState appState,
  required String sourceLabel,
  required String initialRawText,
  ParsedItemKind? defaultKindHint,
}) async {
  final messenger = ScaffoldMessenger.of(context);
  final rawCtrl = TextEditingController(text: initialRawText);
  var showRawEditor = false;
  var drafts = ScheduleTextParser.parse(
    initialRawText,
    defaultKindHint: defaultKindHint,
  );

  await showDialog<void>(
    context: context,
    barrierDismissible: false,
    builder: (ctx) => StatefulBuilder(
      builder: (ctx, setState) {
        final selectedCount =
            drafts.where((d) => d.selected && d.title.trim().isNotEmpty).length;
        return AlertDialog(
          insetPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 20),
          title: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '智能识别审核',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                maxLines: 1,
              ),
              const SizedBox(height: 2),
              Text(
                '$sourceLabel · 请核对修改后再加入',
                style: TextStyle(fontSize: 12, color: GlassColors.muted(ctx)),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
          content: SizedBox(
            width: double.maxFinite,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          drafts.isEmpty
                              ? '暂未自动提取到条目'
                              : '识别出 ${drafts.length} 条待审核',
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: GlassColors.primary,
                          ),
                        ),
                      ),
                      TextButton(
                        onPressed: () =>
                            setState(() => showRawEditor = !showRawEditor),
                        child: Text(showRawEditor ? '收起原文' : '编辑原文重解析'),
                      ),
                    ],
                  ),
                  if (drafts.length >= 2)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Row(
                        children: [
                          ActionChip(
                            label: Text(
                              drafts.every((d) => d.selected) ? '取消全选' : '全选',
                              style: const TextStyle(fontSize: 12),
                            ),
                            visualDensity: VisualDensity.compact,
                            onPressed: () {
                              final next = !drafts.every((d) => d.selected);
                              setState(() {
                                drafts = [
                                  for (final d in drafts) d.copyWith(selected: next),
                                ];
                              });
                            },
                          ),
                          const SizedBox(width: 6),
                          ActionChip(
                            label: const Text('全设考试', style: TextStyle(fontSize: 12)),
                            visualDensity: VisualDensity.compact,
                            onPressed: () => setState(() {
                              drafts = [
                                for (final d in drafts)
                                  d.copyWith(kind: ParsedItemKind.exam),
                              ];
                            }),
                          ),
                          const SizedBox(width: 6),
                          ActionChip(
                            label: const Text('全设加课', style: TextStyle(fontSize: 12)),
                            visualDensity: VisualDensity.compact,
                            onPressed: () => setState(() {
                              drafts = [
                                for (final d in drafts)
                                  d.copyWith(kind: ParsedItemKind.course),
                              ];
                            }),
                          ),
                        ],
                      ),
                    ),
                  if (showRawEditor || drafts.isEmpty) ...[
                    TextField(
                      controller: rawCtrl,
                      minLines: 3,
                      maxLines: 6,
                      decoration: const InputDecoration(
                        labelText: '识别文本（可修改错字后重新解析）',
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () => setState(() {
                              drafts = ScheduleTextParser.parse(
                                rawCtrl.text,
                                defaultKindHint: defaultKindHint,
                              );
                            }),
                            child: const Text('重新识别'),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () {
                              final now = DateTime.now();
                              final todayIso =
                                  '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
                              setState(() {
                                drafts = [
                                  ...drafts,
                                  ParsedScheduleDraft(
                                    id: 'manual_${DateTime.now().microsecondsSinceEpoch}',
                                    kind: defaultKindHint ?? ParsedItemKind.exam,
                                    title: '',
                                    dayOfWeek: now.weekday - 1,
                                    startPeriod: 1,
                                    endPeriod: 2,
                                    weeksSpec: '第${SemesterConfig.weekFor(now)}周',
                                    examDate: todayIso,
                                    startClock: '14:00',
                                    endClock: '16:00',
                                  ),
                                ];
                              });
                            },
                            child: const Text('+ 手动补一条'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                  ],
                  for (var i = 0; i < drafts.length; i++)
                    _DraftEditCard(
                      key: ValueKey(drafts[i].id),
                      index: i,
                      draft: drafts[i],
                      onChanged: (updated) => setState(() {
                        final copy = [...drafts];
                        copy[i] = updated;
                        drafts = copy;
                      }),
                      onRemove: () => setState(() {
                        final copy = [...drafts]..removeAt(i);
                        drafts = copy;
                      }),
                    ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: selectedCount == 0
                  ? null
                  : () async {
                      final msg = await appState.saveReviewedDrafts(drafts);
                      if (ctx.mounted) Navigator.of(ctx).pop();
                      messenger.showSnackBar(SnackBar(content: Text(msg)));
                    },
              child: Text('确认加入 ($selectedCount)'),
            ),
          ],
        );
      },
    ),
  );
}

class _DraftEditCard extends StatelessWidget {
  final int index;
  final ParsedScheduleDraft draft;
  final ValueChanged<ParsedScheduleDraft> onChanged;
  final VoidCallback onRemove;

  const _DraftEditCard({
    super.key,
    required this.index,
    required this.draft,
    required this.onChanged,
    required this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest.withValues(alpha: 0.45),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              SizedBox(
                width: 32,
                height: 32,
                child: Checkbox(
                  value: draft.selected,
                  onChanged: (v) => onChanged(draft.copyWith(selected: v ?? true)),
                ),
              ),
              const SizedBox(width: 4),
              Text(
                '#${index + 1}',
                style: const TextStyle(fontWeight: FontWeight.w700),
                maxLines: 1,
                softWrap: false,
              ),
              const Spacer(),
              ChoiceChip(
                label: const Text('加课', style: TextStyle(fontSize: 12)),
                visualDensity: VisualDensity.compact,
                selected: draft.kind == ParsedItemKind.course,
                onSelected: (_) => onChanged(draft.copyWith(kind: ParsedItemKind.course)),
              ),
              const SizedBox(width: 4),
              ChoiceChip(
                label: const Text('考试', style: TextStyle(fontSize: 12)),
                visualDensity: VisualDensity.compact,
                selected: draft.kind == ParsedItemKind.exam,
                onSelected: (_) => onChanged(draft.copyWith(kind: ParsedItemKind.exam)),
              ),
              IconButton(
                icon: const Icon(Icons.delete_outline, color: Colors.red, size: 20),
                visualDensity: VisualDensity.compact,
                onPressed: onRemove,
              ),
            ],
          ),
          if (draft.note.isNotEmpty)
            Container(
              width: double.infinity,
              margin: const EdgeInsets.only(bottom: 6),
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: GlassColors.primary.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                draft.note,
                style: const TextStyle(
                  fontSize: 11.5,
                  color: GlassColors.primary,
                  fontWeight: FontWeight.w600,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          TextFormField(
            initialValue: draft.title,
            decoration: InputDecoration(
              labelText: draft.kind == ParsedItemKind.exam ? '考试科目 *' : '课程名称 *',
              isDense: true,
            ),
            onChanged: (v) => onChanged(draft.copyWith(title: v)),
          ),
          const SizedBox(height: 6),
          if (draft.kind == ParsedItemKind.course) ...[
            Wrap(
              spacing: 4,
              children: [
                for (var d = 0; d < 7; d++)
                  ChoiceChip(
                    label: Text(_dayLabels[d].substring(1)),
                    selected: draft.dayOfWeek == d,
                    onSelected: (_) => onChanged(draft.copyWith(dayOfWeek: d)),
                  ),
              ],
            ),
            Row(
              children: [
                Expanded(
                  child: TextFormField(
                    initialValue: '${draft.startPeriod}',
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: '开始节', isDense: true),
                    onChanged: (v) {
                      final n = int.tryParse(v);
                      if (n != null && n >= 1 && n <= 12) {
                        onChanged(draft.copyWith(
                          startPeriod: n,
                          endPeriod: n > draft.endPeriod ? n : draft.endPeriod,
                        ));
                      }
                    },
                  ),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: TextFormField(
                    initialValue: '${draft.endPeriod}',
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: '结束节', isDense: true),
                    onChanged: (v) {
                      final n = int.tryParse(v);
                      if (n != null && n >= draft.startPeriod && n <= 12) {
                        onChanged(draft.copyWith(endPeriod: n));
                      }
                    },
                  ),
                ),
                const SizedBox(width: 6),
                Expanded(
                  flex: 2,
                  child: TextFormField(
                    initialValue: draft.weeksSpec,
                    decoration: const InputDecoration(labelText: '周次', isDense: true),
                    onChanged: (v) => onChanged(draft.copyWith(weeksSpec: v)),
                  ),
                ),
              ],
            ),
            Row(
              children: [
                Expanded(
                  child: TextFormField(
                    initialValue: draft.room,
                    decoration: const InputDecoration(labelText: '教室', isDense: true),
                    onChanged: (v) => onChanged(draft.copyWith(room: v)),
                  ),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: TextFormField(
                    initialValue: draft.teacher,
                    decoration: const InputDecoration(labelText: '教师', isDense: true),
                    onChanged: (v) => onChanged(draft.copyWith(teacher: v)),
                  ),
                ),
              ],
            ),
          ] else ...[
            TextFormField(
              initialValue: draft.examDate,
              decoration: const InputDecoration(
                labelText: '考试日期（yyyy-MM-dd）',
                isDense: true,
              ),
              onChanged: (v) => onChanged(draft.copyWith(examDate: v)),
            ),
            Row(
              children: [
                Expanded(
                  child: TextFormField(
                    initialValue: draft.startClock,
                    decoration: const InputDecoration(labelText: '开始时间', isDense: true),
                    onChanged: (v) => onChanged(draft.copyWith(startClock: v)),
                  ),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: TextFormField(
                    initialValue: draft.endClock,
                    decoration: const InputDecoration(labelText: '结束时间', isDense: true),
                    onChanged: (v) => onChanged(draft.copyWith(endClock: v)),
                  ),
                ),
              ],
            ),
            Row(
              children: [
                Expanded(
                  child: TextFormField(
                    initialValue: draft.campus,
                    decoration: const InputDecoration(labelText: '校区', isDense: true),
                    onChanged: (v) => onChanged(draft.copyWith(campus: v)),
                  ),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: TextFormField(
                    initialValue: draft.room,
                    decoration: const InputDecoration(labelText: '考场地点', isDense: true),
                    onChanged: (v) => onChanged(draft.copyWith(room: v)),
                  ),
                ),
              ],
            ),
            Row(
              children: [
                Expanded(
                  child: TextFormField(
                    initialValue: draft.examType,
                    decoration: const InputDecoration(labelText: '考试类型', isDense: true),
                    onChanged: (v) => onChanged(draft.copyWith(examType: v)),
                  ),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: TextFormField(
                    initialValue: draft.seatNo,
                    decoration: const InputDecoration(labelText: '座位号', isDense: true),
                    onChanged: (v) => onChanged(draft.copyWith(seatNo: v)),
                  ),
                ),
              ],
            ),
            TextFormField(
              initialValue: draft.note,
              decoration: const InputDecoration(labelText: '备注 / 班级', isDense: true),
              onChanged: (v) => onChanged(draft.copyWith(note: v)),
            ),
          ],
        ],
      ),
    );
  }
}

/// 节假日调休 / 放假停课管理弹窗（支持调休补课、连续放假停课、剪贴板识别通知与一键撤销）。
Future<void> showHolidayAdjustmentDialog({
  required BuildContext context,
  required CampusAppState appState,
}) async {
  final today = DateTime.now();
  final todayIso = ScheduleAdjustments.formatIso(today);
  final tomorrowIso =
      ScheduleAdjustments.formatIso(today.add(const Duration(days: 1)));

  final targetCtrl = TextEditingController(text: todayIso);
  final sourceCtrl = TextEditingController(text: tomorrowIso);
  final swapLabelCtrl = TextEditingController(text: '节假日调休');
  final restStartCtrl = TextEditingController(text: todayIso);
  final restEndCtrl = TextEditingController(text: todayIso);
  final restLabelCtrl = TextEditingController(text: '节假日放假');

  var mode = 0; // 0 = 调休补课, 1 = 放假停课
  var markSourceAsRest = true;
  String? error;
  String? noticeInfo;
  var pendingNoticeRules = <ScheduleDayAdjustment>[];

  await showDialog<void>(
    context: context,
    builder: (ctx) => StatefulBuilder(
      builder: (ctx, setState) {
        final rules = appState.scheduleAdjustments;
        return AlertDialog(
          insetPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 20),
          title: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '节假日调休 / 调课',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 2),
              Text(
                '无损覆盖课表与今日安排，教务同步不丢失、可随时撤销',
                style: TextStyle(fontSize: 12, color: GlassColors.muted(ctx)),
              ),
            ],
          ),
          content: SizedBox(
            width: double.maxFinite,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SegmentedButton<int>(
                    segments: const [
                      ButtonSegment(value: 0, label: Text('调休补课')),
                      ButtonSegment(value: 1, label: Text('放假停课')),
                    ],
                    selected: {mode},
                    onSelectionChanged: (v) => setState(() {
                      mode = v.first;
                      error = null;
                    }),
                  ),
                  const SizedBox(height: 10),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton(
                      onPressed: () async {
                        final clip = await Clipboard.getData(Clipboard.kTextPlain);
                        final raw = (clip?.text ?? '').trim();
                        if (raw.isEmpty) {
                          setState(() {
                            noticeInfo = '剪贴板为空，请先复制学校放假调休通知';
                            pendingNoticeRules = const [];
                          });
                          return;
                        }
                        final parsed = ScheduleAdjustments.parseHolidayNotice(
                          raw,
                          referenceDate: today,
                        );
                        final merged = <String, ScheduleDayAdjustment>{
                          for (final r in parsed.rests) r.targetDate: r,
                          for (final s in parsed.swaps) s.targetDate: s,
                        }.values.toList()
                          ..sort((a, b) => a.targetDate.compareTo(b.targetDate));
                        setState(() {
                          if (merged.isEmpty) {
                            noticeInfo = '未识别到标准放假/调休句式，可直接在下方手动填写';
                            pendingNoticeRules = const [];
                          } else {
                            pendingNoticeRules = merged;
                            noticeInfo =
                                '识别出 ${parsed.swaps.length} 天调休补课、${parsed.rests.length} 天放假停课，核对后点下方应用';
                          }
                        });
                      },
                      child: const Text('从剪贴板智能识别放假/调休通知'),
                    ),
                  ),
                  if (noticeInfo != null) ...[
                    const SizedBox(height: 8),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: GlassColors.primary.withValues(alpha: 0.08),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            noticeInfo!,
                            style: const TextStyle(
                              fontSize: 12,
                              color: GlassColors.primary,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          for (final r in pendingNoticeRules.take(8))
                            Padding(
                              padding: const EdgeInsets.only(top: 3),
                              child: Text(
                                r.type == ScheduleAdjustmentType.swap
                                    ? '• 调休：${ScheduleAdjustments.shortDateWithWeekday(r.targetDate)} → 上 ${ScheduleAdjustments.shortDateWithWeekday(r.sourceDate)} 课'
                                    : '• 放假：${ScheduleAdjustments.shortDateWithWeekday(r.targetDate)} 停课（${r.label}）',
                                style: const TextStyle(fontSize: 12),
                              ),
                            ),
                          if (pendingNoticeRules.isNotEmpty) ...[
                            const SizedBox(height: 8),
                            Row(
                              children: [
                                FilledButton(
                                  onPressed: () async {
                                    await appState.upsertScheduleAdjustments(pendingNoticeRules);
                                    setState(() {
                                      pendingNoticeRules = const [];
                                      noticeInfo = '已应用全部识别到的调休规则';
                                    });
                                  },
                                  child: Text('确认应用 (${pendingNoticeRules.length})'),
                                ),
                                const SizedBox(width: 8),
                                TextButton(
                                  onPressed: () => setState(() {
                                    pendingNoticeRules = const [];
                                    noticeInfo = null;
                                  }),
                                  child: const Text('放弃'),
                                ),
                              ],
                            ),
                          ],
                        ],
                      ),
                    ),
                  ],
                  const SizedBox(height: 8),
                  if (mode == 0) ...[
                    TextField(
                      controller: targetCtrl,
                      onChanged: (_) => setState(() => error = null),
                      decoration: InputDecoration(
                        labelText: '补课日期（哪天去上课，yyyy-MM-dd）',
                        helperText: '预览：${ScheduleAdjustments.formatDateWithWeek(targetCtrl.text)}',
                      ),
                    ),
                    TextField(
                      controller: sourceCtrl,
                      onChanged: (_) => setState(() => error = null),
                      decoration: InputDecoration(
                        labelText: '改上哪天的课（原课程日期，yyyy-MM-dd）',
                        helperText: '预览：${ScheduleAdjustments.formatDateWithWeek(sourceCtrl.text)}',
                      ),
                    ),
                    TextField(
                      controller: swapLabelCtrl,
                      decoration: const InputDecoration(labelText: '调休备注（如 国庆调休）'),
                    ),
                    CheckboxListTile(
                      value: markSourceAsRest,
                      onChanged: (v) => setState(() => markSourceAsRest = v ?? true),
                      contentPadding: EdgeInsets.zero,
                      controlAffinity: ListTileControlAffinity.leading,
                      title: Text(
                        '同时将原日期（${sourceCtrl.text}）设为放假停课',
                        style: const TextStyle(fontSize: 13),
                      ),
                    ),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton(
                        onPressed: () async {
                          final t = ScheduleAdjustments.parseIso(targetCtrl.text);
                          final s = ScheduleAdjustments.parseIso(sourceCtrl.text);
                          if (t == null || s == null) {
                            setState(() => error = '请输入有效日期，格式如 2026-10-10');
                            return;
                          }
                          if (t == s) {
                            setState(() => error = '补课日期与原课程日期不能相同');
                            return;
                          }
                          await appState.saveSwapAdjustment(
                            targetDate: targetCtrl.text.trim(),
                            sourceDate: sourceCtrl.text.trim(),
                            label: swapLabelCtrl.text.trim(),
                            markSourceAsRest: markSourceAsRest,
                          );
                          setState(() => error = null);
                        },
                        child: const Text('保存此条调休补课'),
                      ),
                    ),
                  ] else ...[
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: restStartCtrl,
                            onChanged: (_) => setState(() => error = null),
                            decoration: InputDecoration(
                              labelText: '开始日期',
                              helperText: ScheduleAdjustments.shortDateWithWeekday(restStartCtrl.text),
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: TextField(
                            controller: restEndCtrl,
                            onChanged: (_) => setState(() => error = null),
                            decoration: InputDecoration(
                              labelText: '结束日期',
                              helperText: ScheduleAdjustments.shortDateWithWeekday(restEndCtrl.text),
                            ),
                          ),
                        ),
                      ],
                    ),
                    TextField(
                      controller: restLabelCtrl,
                      decoration: const InputDecoration(labelText: '放假名称（如 国庆放假）'),
                    ),
                    const SizedBox(height: 10),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton(
                        onPressed: () async {
                          final s = ScheduleAdjustments.parseIso(restStartCtrl.text);
                          final e = ScheduleAdjustments.parseIso(restEndCtrl.text);
                          if (s == null || e == null) {
                            setState(() => error = '请输入有效日期，格式如 2026-10-01');
                            return;
                          }
                          await appState.saveRestRangeAdjustment(
                            startDate: restStartCtrl.text.trim(),
                            endDate: restEndCtrl.text.trim(),
                            label: restLabelCtrl.text.trim(),
                          );
                          setState(() => error = null);
                        },
                        child: const Text('保存放假停课（单日或连休）'),
                      ),
                    ),
                  ],
                  if (error != null) ...[
                    const SizedBox(height: 6),
                    Text(error!, style: const TextStyle(color: Colors.red, fontSize: 12)),
                  ],
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '已生效调休（${rules.length}）',
                          style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
                        ),
                      ),
                      if (rules.isNotEmpty)
                        TextButton(
                          onPressed: () async {
                            await appState.clearAllScheduleAdjustments();
                            setState(() {});
                          },
                          child: const Text('清空全部', style: TextStyle(color: Colors.red)),
                        ),
                    ],
                  ),
                  if (rules.isEmpty)
                    Text(
                      '暂无节假日调休记录。',
                      style: TextStyle(fontSize: 12.5, color: GlassColors.muted(ctx)),
                    )
                  else
                    for (final rule in rules)
                      Container(
                        margin: const EdgeInsets.only(bottom: 6),
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                        decoration: BoxDecoration(
                          color: Theme.of(ctx)
                              .colorScheme
                              .surfaceContainerHighest
                              .withValues(alpha: 0.45),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: (rule.type == ScheduleAdjustmentType.swap
                                        ? GlassColors.primary
                                        : const Color(0xFFF59E0B))
                                    .withValues(alpha: 0.16),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                rule.type == ScheduleAdjustmentType.swap ? '调' : '休',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                  color: rule.type == ScheduleAdjustmentType.swap
                                      ? GlassColors.primary
                                      : const Color(0xFFF59E0B),
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    ScheduleAdjustments.formatDateWithWeek(rule.targetDate),
                                    style: const TextStyle(
                                      fontSize: 12.5,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  Text(
                                    rule.type == ScheduleAdjustmentType.swap
                                        ? '改上 ${ScheduleAdjustments.shortDateWithWeekday(rule.sourceDate)} 的课${rule.label.isNotEmpty ? ' · ${rule.label}' : ''}'
                                        : (rule.label.isEmpty ? '放假停课' : rule.label),
                                    style: TextStyle(
                                      fontSize: 11.5,
                                      color: GlassColors.muted(ctx),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            TextButton(
                              onPressed: () async {
                                await appState.deleteScheduleAdjustment(rule.targetDate);
                                setState(() {});
                              },
                              child: const Text('撤销', style: TextStyle(color: Colors.red, fontSize: 12)),
                            ),
                          ],
                        ),
                      ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('完成'),
            ),
          ],
        );
      },
    ),
  );
}
