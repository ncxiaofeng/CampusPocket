import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../core/class_times.dart';
import '../../core/semester_config.dart';
import '../../data/models.dart';
import '../theme/glass_theme.dart';

/// 周课表页面：高饱和课程块、1-10 节真实作息时间、今日高亮、点击查看详情。
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
    final weekCourses = widget.appState.courses
        .where((c) => c.weeks.contains(_selectedWeek))
        .toList();
    final multiCampus =
        weekCourses.map((c) => c.campus).where((c) => c.isNotEmpty).toSet().length > 1;

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 12, 6),
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
                      '${weekStart.month}/${weekStart.day} - ${weekEnd.month}/${weekEnd.day} · 本周 ${weekCourses.length} 节',
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
        _buildDayHeader(context, weekStart, today),
        const SizedBox(height: 4),
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.only(
              bottom: LiquidGlassBottomBar.contentBottomInset,
            ),
            child: _buildGrid(context, weekCourses, multiCampus),
          ),
        ),
      ],
    );
  }

  Widget _buildDayHeader(
    BuildContext context,
    DateTime weekStart,
    DateTime today,
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
    List<JwCourse> weekCourses,
    bool showCampus,
  ) {
    const double rowHeight = 56;
    const double timeColWidth = 42;
    final totalHeight = rowHeight * ClassTimes.regularPeriods;
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
                // 左侧节次与时间 + 水平网格线
                for (var p = 1; p <= ClassTimes.regularPeriods; p++)
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
                                  ClassTimes.startTime(p) ?? '',
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
                // 课程块
                for (final course in weekCourses)
                  _buildCourseBlock(
                    context,
                    course,
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
    JwCourse course, {
    required double timeColWidth,
    required double dayWidth,
    required double rowHeight,
    required bool showCampus,
  }) {
    final start = course.startPeriod.clamp(1, ClassTimes.regularPeriods);
    final end = course.endPeriod.clamp(start, ClassTimes.regularPeriods);
    final span = end - start + 1;
    final color = GlassColors.coursePalette[
        course.name.hashCode.abs() % GlassColors.coursePalette.length];

    return Positioned(
      left: timeColWidth + course.dayOfWeek * dayWidth + 2,
      top: (start - 1) * rowHeight + 2,
      width: dayWidth - 4,
      height: span * rowHeight - 4,
      child: GestureDetector(
        onTap: () => _showCourseDetail(context, course, color),
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

  void _showCourseDetail(BuildContext context, JwCourse course, Color color) {
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
