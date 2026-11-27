/// 学期起点（第 1 周的周一）：2026 秋季第 1 周 = 2026-09-07。
class SemesterConfig {
  static final DateTime startDate = DateTime(2026, 9, 7);
  static const int totalWeeks = 20;

  static int weekFor([DateTime? date]) {
    final now = date ?? DateTime.now();
    final dayOnly = DateTime(now.year, now.month, now.day);
    if (dayOnly.isBefore(startDate)) return 1;
    final days = dayOnly.difference(startDate).inDays;
    final week = days ~/ 7 + 1;
    if (week < 1) return 1;
    if (week > totalWeeks) return totalWeeks;
    return week;
  }

  static DateTime weekStart(int week) {
    final clamped = week.clamp(1, totalWeeks);
    return startDate.add(Duration(days: (clamped - 1) * 7));
  }
}
