import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../core/class_times.dart';
import '../../core/schedule_adjustments.dart';
import '../../core/schedule_exam_fusion.dart';
import '../../core/semester_config.dart';
import '../../data/models.dart';
import '../theme/glass_theme.dart';
import '../widgets/schedule_import_dialogs.dart';

const _examHighlightColor = Color(0xFFD93848);

/// 周课表页面：高饱和课程块 + 考试融合高亮卡片 + 自定义课表/考试与离线智能识别。
class ScheduleScreen extends StatefulWidget {
  final CampusAppState appState;
  final VoidCallback onSync;

  const ScheduleScreen({
    super.key,
    required this.appState,
    required this.onSync,
  });

  @override
  State<ScheduleScreen> createState() => _ScheduleScreenState();
}

class _ScheduleScreenState extends State<ScheduleScreen> {
  late int _selectedWeek;
  static const _dayLabels = ['一', '二', '三', '四', '五', '六', '日'];

  @override
  void initState() {
    super.initState();
    _selectedWeek = widget.appState.currentWeek;
  }

  @override
  Widget build(BuildContext context) {
    final weekStart = SemesterConfig.weekStart(_selectedWeek);
    final weekEnd = weekStart.add(const Duration(days: 6));
    final today = DateTime.now();
    final adjustmentsByDate = widget.appState.scheduleAdjustmentsByDate;
    final weekAdjustments =
        ScheduleAdjustments.adjustmentsInWeek(weekStart, adjustmentsByDate);
    final weekCourses = ScheduleAdjustments.resolveWeekCourses(
      widget.appState.courses,
      weekStart,
      adjustmentsByDate,
    );
    final weekExams = <(JwExam, int, int, int)>[];
    for (final exam in widget.appState.exams) {
      final slot = ScheduleExamFusion.examSlotInWeek(exam, weekStart);
      if (slot != null) {
        weekExams.add((exam, slot.$1, slot.$2, slot.$3));
      }
    }
    final multiCampus = {
          ...weekCourses.map((c) => c.course.campus),
          ...weekExams.map((e) => e.$1.campus),
        }.where((c) => c.isNotEmpty).length >
        1;

    final subtitle = weekExams.isEmpty
        ? '${weekStart.month}/${weekStart.day} - ${weekEnd.month}/${weekEnd.day} · 本周 ${weekCourses.length} 课'
        : '${weekStart.month}/${weekStart.day} - ${weekEnd.month}/${weekEnd.day} · ${weekCourses.length} 课 · ${weekExams.length} 考';

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 8, 6),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '第 $_selectedWeek 周',
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        color: GlassColors.ink(context),
                      ),
                    ),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: 12,
                        color: GlassColors.muted(context),
                      ),
                    ),
                  ],
                ),
              ),
              IconButton(
                onPressed: _selectedWeek > 1
                    ? () => setState(() => _selectedWeek--)
                    : null,
                icon: const Icon(Icons.chevron_left_rounded),
              ),
              TextButton(
                onPressed: () => setState(
                  () => _selectedWeek = widget.appState.currentWeek,
                ),
                child: const Text('本周'),
              ),
              IconButton(
                onPressed: _selectedWeek < SemesterConfig.totalWeeks
                    ? () => setState(() => _selectedWeek++)
                    : null,
                icon: const Icon(Icons.chevron_right_rounded),
              ),
              IconButton(
                tooltip: '添加课表/考试或智能识别',
                onPressed: () => showAddScheduleEntrySheet(
                  context: context,
                  appState: widget.appState,
                  defaultWeek: _selectedWeek,
                ),
                icon: const Icon(Icons.add_rounded),
              ),
              IconButton(
                tooltip: '同步课表',
                onPressed: widget.onSync,
                icon: widget.appState.syncingJw
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.sync_rounded),
              ),
            ],
          ),
        ),
        if (weekAdjustments.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 6),
            child: InkWell(
              onTap: () => showHolidayAdjustmentDialog(
                context: context,
                appState: widget.appState,
              ),
              borderRadius: BorderRadius.circular(10),
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: GlassColors.primary.withValues(alpha: 0.09),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        '本周调休：${weekAdjustments.values.map((a) => a.type == ScheduleAdjustmentType.swap ? '${ScheduleAdjustments.shortDateWithWeekday(a.targetDate)} 调上 ${ScheduleAdjustments.shortDateWithWeekday(a.sourceDate)}' : '${ScheduleAdjustments.shortDateWithWeekday(a.targetDate)} 休').join(' · ')}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 11.5,
                          color: GlassColors.primary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                    const Text(
                      '管理',
                      style: TextStyle(
                        fontSize: 11.5,
                        color: GlassColors.primary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        _buildDayHeader(context, weekStart, today, weekAdjustments),
        const SizedBox(height: 4),
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.only(
              bottom: LiquidGlassBottomBar.contentBottomInset,
            ),
            child: _buildGrid(
              context,
              weekCourses,
              weekExams,
              weekAdjustments,
              multiCampus,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildDayHeader(
    BuildContext context,
    DateTime weekStart,
    DateTime today,
    Map<int, ScheduleDayAdjustment> weekAdjustments,
  ) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 6),
      child: Row(
        children: [
          const SizedBox(width: 42),
          for (var i = 0; i < 7; i++)
            Expanded(
              child: Builder(
                builder: (context) {
                  final date = weekStart.add(Duration(days: i));
                  final isToday = date.year == today.year &&
                      date.month == today.month &&
                      date.day == today.day;
                  final adj = weekAdjustments[i];
                  return Container(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    decoration: BoxDecoration(
                      color: isToday
                          ? GlassColors.primary
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Column(
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              _dayLabels[i],
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                                color: isToday
                                    ? Colors.white
                                    : GlassColors.muted(context),
                              ),
                            ),
                            if (adj != null) ...[
                              const SizedBox(width: 2),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 3),
                                decoration: BoxDecoration(
                                  color: isToday
                                      ? Colors.white.withValues(alpha: 0.28)
                                      : (adj.type == ScheduleAdjustmentType.swap
                                              ? GlassColors.primary
                                              : const Color(0xFFF59E0B))
                                          .withValues(alpha: 0.16),
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: Text(
                                  adj.type == ScheduleAdjustmentType.swap ? '调' : '休',
                                  style: TextStyle(
                                    fontSize: 8.5,
                                    fontWeight: FontWeight.w800,
                                    color: isToday
                                        ? Colors.white
                                        : (adj.type == ScheduleAdjustmentType.swap
                                            ? GlassColors.primary
                                            : const Color(0xFFF59E0B)),
                                  ),
                                ),
                              ),
                            ],
                          ],
                        ),
                        const SizedBox(height: 1),
                        Text(
                          '${date.day}',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                            color: isToday
                                ? Colors.white
                                : GlassColors.ink(context),
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildGrid(
    BuildContext context,
    List<ResolvedDayCourse> weekCourses,
    List<(JwExam, int, int, int)> weekExams,
    Map<int, ScheduleDayAdjustment> weekAdjustments,
    bool showCampus,
  ) {
    const double rowHeight = 56;
    const double timeColWidth = 42;
    final maxPeriod = [
      ClassTimes.regularPeriods,
      ...weekCourses.map((c) => c.course.endPeriod),
      ...weekExams.map((e) => e.$4),
    ].reduce((a, b) => a > b ? a : b).clamp(ClassTimes.regularPeriods, 12);
    final totalHeight = rowHeight * maxPeriod;
    final dividerColor = GlassColors.isDark(context)
        ? Colors.white.withValues(alpha: 0.08)
        : Colors.black.withValues(alpha: 0.06);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 6),
      child: SizedBox(
        height: totalHeight,
        child: LayoutBuilder(
          builder: (context, constraints) {
            final dayWidth = (constraints.maxWidth - timeColWidth) / 7;
            return Stack(
              children: [
                for (var p = 1; p <= maxPeriod; p++)
                  Positioned(
                    top: (p - 1) * rowHeight,
                    left: 0,
                    right: 0,
                    height: rowHeight,
                    child: Container(
                      decoration: BoxDecoration(
                        border: Border(top: BorderSide(color: dividerColor)),
                      ),
                      child: Row(
                        children: [
                          SizedBox(
                            width: timeColWidth,
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Text(
                                  '$p',
                                  style: TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w700,
                                    color: GlassColors.ink(context),
                                  ),
                                ),
                                Text(
                                  ClassTimes.startTime(p) ?? '夜间',
                                  style: TextStyle(
                                    fontSize: 9,
                                    color: GlassColors.muted(context),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const Expanded(child: SizedBox()),
                        ],
                      ),
                    ),
                  ),
                // 放假停课列在无卡片时展示柔和「放假停课」提示
                for (final entry in weekAdjustments.entries)
                  if (entry.value.type == ScheduleAdjustmentType.rest &&
                      weekCourses.every((c) => c.displayDayOfWeek != entry.key) &&
                      weekExams.every((e) => e.$2 != entry.key))
                    Positioned(
                      left: timeColWidth + entry.key * dayWidth + 3,
                      top: rowHeight * 2,
                      width: dayWidth - 6,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 3, vertical: 8),
                        decoration: BoxDecoration(
                          color: const Color(0xFFF59E0B).withValues(alpha: 0.14),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          entry.value.label.isEmpty ? '放假停课' : entry.value.label,
                          textAlign: TextAlign.center,
                          maxLines: 4,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 9.5,
                            fontWeight: FontWeight.w700,
                            color: Color(0xFFD97706),
                          ),
                        ),
                      ),
                    ),
                // 先绘制课程块，再绘制考试块（确保考试永不被普通课遮盖）
                for (final resolved in weekCourses)
                  _buildCourseBlock(
                    context,
                    resolved,
                    maxPeriod: maxPeriod,
                    timeColWidth: timeColWidth,
                    dayWidth: dayWidth,
                    rowHeight: rowHeight,
                    showCampus: showCampus,
                  ),
                for (final item in weekExams)
                  _buildExamBlock(
                    context,
                    item.$1,
                    dayOfWeek: item.$2,
                    startPeriod: item.$3,
                    endPeriod: item.$4,
                    maxPeriod: maxPeriod,
                    timeColWidth: timeColWidth,
                    dayWidth: dayWidth,
                    rowHeight: rowHeight,
                    showCampus: showCampus,
                  ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildCourseBlock(
    BuildContext context,
    ResolvedDayCourse resolved, {
    required int maxPeriod,
    required double timeColWidth,
    required double dayWidth,
    required double rowHeight,
    required bool showCampus,
  }) {
    final course = resolved.course;
    final start = course.startPeriod.clamp(1, maxPeriod);
    final end = course.endPeriod.clamp(start, maxPeriod);
    final span = end - start + 1;
    final color = GlassColors.coursePalette[
        course.name.hashCode.abs() % GlassColors.coursePalette.length];

    return Positioned(
      left: timeColWidth + resolved.displayDayOfWeek * dayWidth + 2,
      top: (start - 1) * rowHeight + 2,
      width: dayWidth - 4,
      height: span * rowHeight - 4,
      child: GestureDetector(
        onTap: () => _showCourseDetail(
          context,
          course,
          color,
          adjustedDetail: resolved.adjustedDetail,
        ),
        child: Container(
          padding: const EdgeInsets.all(4),
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.90),
            borderRadius: BorderRadius.circular(10),
            boxShadow: [
              BoxShadow(
                color: color.withValues(alpha: 0.28),
                blurRadius: 8,
                offset: const Offset(0, 3),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (resolved.adjustedTag.isNotEmpty || course.isCustom)
                Container(
                  margin: const EdgeInsets.only(bottom: 1),
                  padding: const EdgeInsets.symmetric(horizontal: 3, vertical: 0.5),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.28),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    resolved.adjustedTag.isNotEmpty ? resolved.adjustedTag : '自定义',
                    style: const TextStyle(
                      fontSize: 7.5,
                      color: Colors.white,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              Text(
                course.name,
                maxLines: span >= 2 ? 3 : 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 10.5,
                  height: 1.15,
                  fontWeight: FontWeight.w700,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 2),
              if (course.room.isNotEmpty)
                Text(
                  showCampus && course.campus.isNotEmpty
                      ? '${course.campus} ${course.room}'
                      : course.room,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 9.5,
                    color: Colors.white.withValues(alpha: 0.92),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildExamBlock(
    BuildContext context,
    JwExam exam, {
    required int dayOfWeek,
    required int startPeriod,
    required int endPeriod,
    required int maxPeriod,
    required double timeColWidth,
    required double dayWidth,
    required double rowHeight,
    required bool showCampus,
  }) {
    final start = startPeriod.clamp(1, maxPeriod);
    final end = endPeriod.clamp(start, maxPeriod);
    final span = end - start + 1;
    final clockRange = exam.startClock.isNotEmpty && exam.endClock.isNotEmpty
        ? '${exam.startClock}-${exam.endClock}'
        : exam.startClock;

    return Positioned(
      left: timeColWidth + dayOfWeek * dayWidth + 2,
      top: (start - 1) * rowHeight + 2,
      width: dayWidth - 4,
      height: span * rowHeight - 4,
      child: GestureDetector(
        onTap: () => _showExamDetail(context, exam),
        child: Container(
          padding: const EdgeInsets.all(4),
          decoration: BoxDecoration(
            color: _examHighlightColor.withValues(alpha: 0.95),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: Colors.white.withValues(alpha: 0.90),
              width: 1.5,
            ),
            boxShadow: [
              BoxShadow(
                color: _examHighlightColor.withValues(alpha: 0.36),
                blurRadius: 10,
                offset: const Offset(0, 3),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                margin: const EdgeInsets.only(bottom: 1),
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 0.5),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.94),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  exam.isCustom ? '考·自' : '考试',
                  style: const TextStyle(
                    fontSize: 8,
                    color: _examHighlightColor,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              Text(
                exam.courseName,
                maxLines: span >= 2 ? 3 : 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 10.5,
                  height: 1.15,
                  fontWeight: FontWeight.w800,
                  color: Colors.white,
                ),
              ),
              if (clockRange.isNotEmpty)
                Text(
                  clockRange,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 8.5,
                    fontWeight: FontWeight.w600,
                    color: Colors.white.withValues(alpha: 0.96),
                  ),
                ),
              if (exam.location.isNotEmpty)
                Text(
                  showCampus && exam.campus.isNotEmpty
                      ? '${exam.campus} ${exam.location}'
                      : exam.location,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 9,
                    color: Colors.white.withValues(alpha: 0.92),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  void _showCourseDetail(
    BuildContext context,
    JwCourse course,
    Color color, {
    String adjustedDetail = '',
  }) {
    final start = ClassTimes.startTime(course.startPeriod) ?? '';
    final end = ClassTimes.endTime(course.endPeriod) ?? '';
    showModalBottomSheet<void>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 12,
                    height: 12,
                    decoration: BoxDecoration(
                      color: color,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      course.name,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              _detailRow('时间', '周${_dayLabels[course.dayOfWeek]} 第${course.startPeriod}-${course.endPeriod}节 ($start-$end)'),
              if (course.room.isNotEmpty) _detailRow('教室', course.room),
              if (course.campus.isNotEmpty) _detailRow('校区', course.campus),
              if (course.teacher.isNotEmpty) _detailRow('教师', course.teacher),
              if (course.weeksSpec.isNotEmpty) _detailRow('周次', course.weeksSpec),
              if (course.courseCode.isNotEmpty) _detailRow('课程号', course.courseCode),
              if (adjustedDetail.isNotEmpty) _detailRow('调休', adjustedDetail),
              if (course.isCustom) ...[
                _detailRow('来源', '自定义课程（教务同步不覆盖）'),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () {
                          Navigator.of(ctx).pop();
                          showAddOrEditCourseDialog(
                            context: context,
                            appState: widget.appState,
                            initial: course,
                            defaultWeek: _selectedWeek,
                          );
                        },
                        child: const Text('编辑'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () async {
                          Navigator.of(ctx).pop();
                          await widget.appState.deleteCustomCourse(course.id);
                        },
                        style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
                        child: const Text('删除'),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  void _showExamDetail(BuildContext context, JwExam exam) {
    final clockRange = exam.startClock.isNotEmpty && exam.endClock.isNotEmpty
        ? '${exam.startClock}-${exam.endClock}'
        : (exam.startClock.isNotEmpty ? exam.startClock : exam.examTime);
    showModalBottomSheet<void>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: _examHighlightColor.withValues(alpha: 0.14),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      exam.isCustom ? '自定义考试' : '考试',
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                        color: _examHighlightColor,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      exam.courseName,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              if (exam.examDate.isNotEmpty) _detailRow('日期', exam.examDate),
              if (clockRange.isNotEmpty) _detailRow('时间', clockRange),
              if (exam.campus.isNotEmpty || exam.location.isNotEmpty)
                _detailRow(
                  '考场',
                  [exam.campus, exam.location].where((s) => s.isNotEmpty).join(' · '),
                ),
              if (exam.examType.isNotEmpty) _detailRow('类型', exam.examType),
              if (exam.seatNo.isNotEmpty) _detailRow('座位', exam.seatNo),
              if (exam.note.isNotEmpty) _detailRow('备注', exam.note),
              _detailRow('来源', exam.isCustom ? '自定义考试（教务同步不覆盖）' : '教务系统'),
              if (exam.isCustom) ...[
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () {
                          Navigator.of(ctx).pop();
                          showAddOrEditExamDialog(
                            context: context,
                            appState: widget.appState,
                            initial: exam,
                          );
                        },
                        child: const Text('编辑'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () async {
                          Navigator.of(ctx).pop();
                          await widget.appState.deleteCustomExam(exam.key);
                        },
                        style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
                        child: const Text('删除'),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _detailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 62,
            child: Text(
              label,
              style: const TextStyle(fontSize: 13, color: Colors.grey),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
            ),
          ),
        ],
      ),
    );
  }
}
