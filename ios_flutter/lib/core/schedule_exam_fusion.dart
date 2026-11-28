import '../data/models.dart';
import 'class_times.dart';
import 'semester_config.dart';

/// 自定义课表/考试周次解析、格式化与考试融合到课表的纯函数工具（与 Android 端算法完全对齐）。
class ScheduleExamFusion {
  static Set<int> parseWeeksSpec(
    String spec, {
    int maxWeeks = SemesterConfig.totalWeeks,
  }) {
    final cleaned = spec
        .trim()
        .replaceAll('，', ',')
        .replaceAll('、', ',')
        .replaceAll('～', '-')
        .replaceAll('~', '-')
        .replaceAll('—', '-')
        .replaceAll('至', '-')
        .replaceAll('到', '-');
    if (cleaned.isEmpty) return {};

    final result = <int>{};
    final segments = cleaned
        .split(',')
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty);
    final rangeRe = RegExp(r'(\d{1,2})\s*-\s*(\d{1,2})');
    final numRe = RegExp(r'\d{1,2}');

    for (final segment in segments) {
      final isOdd = segment.contains('单');
      final isEven = segment.contains('双');
      final m = rangeRe.firstMatch(segment);
      if (m != null) {
        final a = int.tryParse(m.group(1)!) ?? 0;
        final b = int.tryParse(m.group(2)!) ?? 0;
        if (a <= 0 || b <= 0) continue;
        final start = (a < b ? a : b).clamp(1, maxWeeks);
        final end = (a > b ? a : b).clamp(start, maxWeeks);
        for (var w = start; w <= end; w++) {
          if (isOdd && w % 2 == 0) continue;
          if (isEven && w % 2 != 0) continue;
          result.add(w);
        }
      } else {
        for (final nm in numRe.allMatches(segment)) {
          final w = int.tryParse(nm.group(0)!) ?? 0;
          if (w >= 1 && w <= maxWeeks) {
            result.add(w);
          }
        }
      }
    }
    return result;
  }

  static String formatWeeksSpec(Set<int> weeks) {
    final sorted = weeks.where((w) => w > 0).toList()..sort();
    if (sorted.isEmpty) return '';
    if (sorted.length == 1) return '第${sorted.first}周';

    final step = sorted[1] - sorted[0];
    var isUniform = step == 1 || step == 2;
    if (isUniform) {
      for (var i = 1; i < sorted.length; i++) {
        if (sorted[i] - sorted[i - 1] != step) {
          isUniform = false;
          break;
        }
      }
    }
    if (isUniform) {
      if (step == 1) return '${sorted.first}-${sorted.last}周';
      return sorted.first % 2 == 1
          ? '${sorted.first}-${sorted.last}周(单)'
          : '${sorted.first}-${sorted.last}周(双)';
    }

    final parts = <String>[];
    var rangeStart = sorted.first;
    var prev = sorted.first;
    for (var i = 1; i < sorted.length; i++) {
      final cur = sorted[i];
      if (cur == prev + 1) {
        prev = cur;
      } else {
        parts.add(rangeStart == prev ? '$rangeStart' : '$rangeStart-$prev');
        rangeStart = cur;
        prev = cur;
      }
    }
    parts.add(rangeStart == prev ? '$rangeStart' : '$rangeStart-$prev');
    return '${parts.join(',')}周';
  }

  static String normalizeClock(String raw) {
    final m = RegExp(r'(\d{1,2})\s*[:：点時时]\s*(\d{1,2})?').firstMatch(raw.trim());
    if (m == null) return '';
    final h = (int.tryParse(m.group(1)!) ?? -1);
    if (h < 0 || h > 23) return '';
    final min = (int.tryParse(m.group(2) ?? '0') ?? 0).clamp(0, 59);
    return '${h.toString().padLeft(2, '0')}:${min.toString().padLeft(2, '0')}';
  }

  static int? _clockToMinutes(String raw) {
    final norm = normalizeClock(raw);
    if (norm.isEmpty) return null;
    final parts = norm.split(':');
    return int.parse(parts[0]) * 60 + int.parse(parts[1]);
  }

  /// 将考试起止时钟映射到课表节次区间 (startPeriod, endPeriod)。
  static (int, int) clockRangeToPeriods(String startClock, String endClock) {
    final startMin = _clockToMinutes(startClock);
    if (startMin == null) return (1, 2);
    final parsedEnd = _clockToMinutes(endClock);
    final endMin =
        (parsedEnd != null && parsedEnd >= startMin) ? parsedEnd : startMin + 100;

    final overlapped = <int>[];
    for (var i = 0; i < ClassTimes.periods.length; i++) {
      final p = ClassTimes.periods[i];
      final pStart = _clockToMinutes(p.start)!;
      final pEnd = _clockToMinutes(p.end)!;
      final oStart = startMin > pStart ? startMin : pStart;
      final oEnd = endMin < pEnd ? endMin : pEnd;
      if (oEnd - oStart >= 5) {
        overlapped.add(i + 1);
      }
    }
    if (overlapped.isNotEmpty) {
      return (overlapped.first, overlapped.last);
    }

    var bestPeriod = 1;
    var bestDiff = 1 << 30;
    for (var i = 0; i < ClassTimes.periods.length; i++) {
      final pStart = _clockToMinutes(ClassTimes.periods[i].start)!;
      final diff = (pStart - startMin).abs();
      if (diff < bestDiff) {
        bestDiff = diff;
        bestPeriod = i + 1;
      }
    }
    final endPeriod = (bestPeriod + 1).clamp(bestPeriod, ClassTimes.regularPeriods);
    return (bestPeriod, endPeriod);
  }

  /// 判断考试是否落在 [weekStart, weekStart+6]，命中返回 (dayOfWeek, startPeriod, endPeriod)。
  static (int, int, int)? examSlotInWeek(JwExam exam, DateTime weekStart) {
    final parsed = _parseIsoDate(exam.examDate);
    if (parsed == null) return null;
    final ws = DateTime(weekStart.year, weekStart.month, weekStart.day);
    final offset = parsed.difference(ws).inDays;
    if (offset < 0 || offset > 6) return null;
    final (sp, ep) = clockRangeToPeriods(exam.startClock, exam.endClock);
    return (offset, sp, ep);
  }

  static DateTime? _parseIsoDate(String raw) {
    final m = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$').firstMatch(raw.trim());
    if (m == null) return null;
    return DateTime(
      int.parse(m.group(1)!),
      int.parse(m.group(2)!),
      int.parse(m.group(3)!),
    );
  }

  static JwCourse buildCustomCourse({
    String? existingId,
    required String name,
    required int dayOfWeek,
    required int startPeriod,
    required int endPeriod,
    required String weeksSpec,
    String room = '',
    String campus = '',
    String teacher = '',
  }) {
    final cleanName = name.trim();
    final safeDay = dayOfWeek.clamp(0, 6);
    final safeStart = startPeriod.clamp(1, 12);
    final safeEnd = endPeriod.clamp(safeStart, 12);
    final weeks = parseWeeksSpec(weeksSpec);
    final resolvedWeeks =
        weeks.isEmpty ? {SemesterConfig.weekFor()} : weeks;
    final startSlot = ((safeStart - 1) ~/ 2).clamp(0, 6);
    final endSlot = ((safeEnd - 1) ~/ 2).clamp(startSlot, 6);
    final id = (existingId != null && existingId.isNotEmpty)
        ? existingId
        : 'custom_course_${DateTime.now().microsecondsSinceEpoch}_${cleanName.hashCode.abs()}';

    return JwCourse(
      id: id,
      name: cleanName,
      teacher: teacher.trim(),
      room: room.trim(),
      campus: campus.trim(),
      dayOfWeek: safeDay,
      startSlot: startSlot,
      slotSpan: (endSlot - startSlot + 1).clamp(1, 6),
      startPeriod: safeStart,
      endPeriod: safeEnd,
      weeks: resolvedWeeks,
      weeksSpec: formatWeeksSpec(resolvedWeeks),
      courseCode: '',
      isCustom: true,
    );
  }

  static JwExam buildCustomExam({
    String? existingKey,
    required String courseName,
    required String examDate,
    required String startClock,
    required String endClock,
    String location = '',
    String campus = '',
    String examType = '自定义考试',
    String seatNo = '',
    String note = '',
  }) {
    final cleanName = courseName.trim();
    final cleanDate = examDate.trim();
    final cleanStart = normalizeClock(startClock);
    final cleanEnd = normalizeClock(endClock);
    final key = (existingKey != null && existingKey.isNotEmpty)
        ? existingKey
        : 'custom_exam_${DateTime.now().microsecondsSinceEpoch}_${(cleanName + cleanDate + cleanStart).hashCode.abs()}';
    final buf = StringBuffer();
    if (cleanDate.isNotEmpty) buf.write(cleanDate);
    if (cleanStart.isNotEmpty) {
      if (buf.isNotEmpty) buf.write('(');
      buf.write(cleanStart);
      if (cleanEnd.isNotEmpty) buf.write('-$cleanEnd');
      if (cleanDate.isNotEmpty) buf.write(')');
    }
    final typeText = examType.trim().isEmpty ? '自定义考试' : examType.trim();
    return JwExam(
      key: key,
      courseName: cleanName,
      courseCode: '',
      examTime: buf.toString(),
      examDate: cleanDate,
      startClock: cleanStart,
      endClock: cleanEnd,
      examName: typeText,
      examType: typeText,
      location: location.trim(),
      campus: campus.trim(),
      seatNo: seatNo.trim(),
      ticketNo: '',
      note: note.trim(),
      term: '',
      isCustom: true,
    );
  }
}
