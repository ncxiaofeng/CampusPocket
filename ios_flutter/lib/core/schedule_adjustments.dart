import '../data/models.dart';
import 'semester_config.dart';

enum ScheduleAdjustmentType { rest, swap }

/// 单日节假日调休/停课规则（按自然日 yyyy-MM-dd 存储，教务同步时不覆盖、删除即可一键复原）。
class ScheduleDayAdjustment {
  final String targetDate; // yyyy-MM-dd
  final ScheduleAdjustmentType type;
  final String sourceDate; // swap 时必填 yyyy-MM-dd
  final String label;

  const ScheduleDayAdjustment({
    required this.targetDate,
    required this.type,
    this.sourceDate = '',
    this.label = '',
  });

  Map<String, dynamic> toJson() => {
        'targetDate': targetDate,
        'type': type.name,
        'sourceDate': sourceDate,
        'label': label,
      };

  factory ScheduleDayAdjustment.fromJson(Map<String, dynamic> json) {
    final typeRaw = (json['type'] as String? ?? 'rest').toLowerCase();
    return ScheduleDayAdjustment(
      targetDate: (json['targetDate'] as String? ?? '').trim(),
      type: typeRaw == 'swap'
          ? ScheduleAdjustmentType.swap
          : ScheduleAdjustmentType.rest,
      sourceDate: (json['sourceDate'] as String? ?? '').trim(),
      label: (json['label'] as String? ?? '').trim(),
    );
  }
}

class ResolvedDayCourse {
  final JwCourse course;
  final int displayDayOfWeek;
  final String adjustedTag;
  final String adjustedDetail;

  const ResolvedDayCourse({
    required this.course,
    required this.displayDayOfWeek,
    this.adjustedTag = '',
    this.adjustedDetail = '',
  });
}

class ParsedHolidayNotice {
  final List<ScheduleDayAdjustment> swaps;
  final List<ScheduleDayAdjustment> rests;

  const ParsedHolidayNotice({
    this.swaps = const [],
    this.rests = const [],
  });

  bool get isEmpty => swaps.isEmpty && rests.isEmpty;
}

class ScheduleAdjustments {
  static const _weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

  static String formatIso(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  static DateTime? parseIso(String iso) {
    final m = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$').firstMatch(iso.trim());
    if (m == null) return null;
    final y = int.parse(m.group(1)!);
    final mo = int.parse(m.group(2)!);
    final d = int.parse(m.group(3)!);
    if (mo < 1 || mo > 12 || d < 1 || d > 31) return null;
    final dt = DateTime(y, mo, d);
    if (dt.year != y || dt.month != mo || dt.day != d) return null;
    return dt;
  }

  static String formatDateWithWeek(String isoDate) {
    final d = parseIso(isoDate);
    if (d == null) return isoDate;
    final w = SemesterConfig.weekFor(d);
    final wd = _weekdays[d.weekday - 1];
    return '${d.month}/${d.day} $wd (第$w周)';
  }

  static String shortDateWithWeekday(String isoDate) {
    final d = parseIso(isoDate);
    if (d == null) return isoDate;
    return '${d.month}/${d.day} ${_weekdays[d.weekday - 1]}';
  }

  /// 解析单个自然日实际应上的课程（叠加放假停课或调休补课规则）。
  static List<ResolvedDayCourse> resolveDayCourses(
    List<JwCourse> allCourses,
    DateTime date,
    Map<String, ScheduleDayAdjustment> adjustmentsByDate,
  ) {
    final iso = formatIso(date);
    final displayDay = date.weekday - 1;
    final adj = adjustmentsByDate[iso];

    if (adj != null && adj.type == ScheduleAdjustmentType.rest) {
      return const [];
    }
    if (adj != null && adj.type == ScheduleAdjustmentType.swap) {
      final src = parseIso(adj.sourceDate);
      if (src == null) return const [];
      final srcWeek = SemesterConfig.weekFor(src);
      final srcDay = src.weekday - 1;
      final detail =
          '原 ${shortDateWithWeekday(adj.sourceDate)} 课表${adj.label.isNotEmpty ? '（${adj.label}）' : ''}';
      return allCourses
          .where((c) => c.dayOfWeek == srcDay && (c.weeks.isEmpty || c.weeks.contains(srcWeek)))
          .map(
            (c) => ResolvedDayCourse(
              course: c,
              displayDayOfWeek: displayDay,
              adjustedTag: '调休',
              adjustedDetail: detail,
            ),
          )
          .toList();
    }

    final week = SemesterConfig.weekFor(date);
    return allCourses
        .where((c) => c.dayOfWeek == displayDay && (c.weeks.isEmpty || c.weeks.contains(week)))
        .map(
          (c) => ResolvedDayCourse(
            course: c,
            displayDayOfWeek: displayDay,
          ),
        )
        .toList();
  }

  /// 解析整周（周一至周日）的实际课程列表。
  static List<ResolvedDayCourse> resolveWeekCourses(
    List<JwCourse> allCourses,
    DateTime weekStart,
    Map<String, ScheduleDayAdjustment> adjustmentsByDate,
  ) {
    final out = <ResolvedDayCourse>[];
    for (var offset = 0; offset < 7; offset++) {
      final date = weekStart.add(Duration(days: offset));
      out.addAll(resolveDayCourses(allCourses, date, adjustmentsByDate));
    }
    return out;
  }

  static Map<int, ScheduleDayAdjustment> adjustmentsInWeek(
    DateTime weekStart,
    Map<String, ScheduleDayAdjustment> adjustmentsByDate,
  ) {
    if (adjustmentsByDate.isEmpty) return const {};
    final out = <int, ScheduleDayAdjustment>{};
    for (var offset = 0; offset < 7; offset++) {
      final iso = formatIso(weekStart.add(Duration(days: offset)));
      final adj = adjustmentsByDate[iso];
      if (adj != null) out[offset] = adj;
    }
    return out;
  }

  static List<ScheduleDayAdjustment> expandRestRange(
    String startIso,
    String endIso,
    String label,
  ) {
    final s = parseIso(startIso);
    if (s == null) return const [];
    final e = parseIso(endIso) ?? s;
    final from = s.isAfter(e) ? e : s;
    final to = s.isAfter(e) ? s : e;
    final cleanLabel = label.trim().isEmpty ? '节假日放假' : label.trim();
    final out = <ScheduleDayAdjustment>[];
    var cur = from;
    var guard = 0;
    while (!cur.isAfter(to) && guard < 31) {
      out.add(
        ScheduleDayAdjustment(
          targetDate: formatIso(cur),
          type: ScheduleAdjustmentType.rest,
          label: cleanLabel,
        ),
      );
      cur = cur.add(const Duration(days: 1));
      guard++;
    }
    return out;
  }

  /// 离线解析常见高校及国务院节假日放假/调休通知（兼容正向/反向/成对分别/按星期/国务院上班简明格式）。
  static ParsedHolidayNotice parseHolidayNotice(
    String rawText, {
    DateTime? referenceDate,
  }) {
    final ref = referenceDate ?? DateTime.now();
    final text = rawText.replaceAll('\r\n', '\n').replaceAll('\r', '\n').trim();
    if (text.isEmpty) return const ParsedHolidayNotice();

    final docYearMatch = RegExp(r'(20\d{2})\s*年').firstMatch(text);
    final docYear = docYearMatch == null ? null : int.tryParse(docYearMatch.group(1)!);
    const holidays = ['元旦', '春节', '清明', '五一', '劳动节', '端午', '中秋', '国庆', '寒假', '暑假'];

    String holidayNameNear(int pos) {
      final start = (pos - 100) < 0 ? 0 : (pos - 100);
      final end = pos > text.length ? text.length : pos;
      final before = text.substring(start, end);
      final breakIdx = before.lastIndexOf('\n') > before.lastIndexOf('；')
          ? before.lastIndexOf('\n')
          : before.lastIndexOf('；');
      final seg = breakIdx >= 0 ? before.substring(breakIdx + 1) : before;

      String pickLast(String s) {
        var bestPos = -1;
        var best = '';
        for (final kw in holidays) {
          final idx = s.lastIndexOf(kw);
          if (idx > bestPos) {
            bestPos = idx;
            best = kw;
          }
        }
        return best;
      }

      final inSeg = pickLast(seg);
      return inSeg.isNotEmpty ? inSeg : pickLast(before);
    }

    const dateToken = r'(?:(?:20\d{2})\s*[-/.年]\s*)?\d{1,2}\s*[月/.]\s*\d{1,2}\s*[日号]?';
    const optWeekday = r'(?:\s*[（(]?(?:第\d{1,2}周\s*)?(?:星期|礼拜|周)[一二三四五六日天][)）]?)?';
    const dateWithOptWd = '(?:$dateToken)$optWeekday';
    const dateList = '$dateWithOptWd(?:\\s*[、,，和与及]\\s*$dateWithOptWd)*';
    const weekdayToken = r'(?:第\d{1,2}周\s*)?(?:单周|双周)?(?:星期|礼拜|周)[一二三四五六日天]';
    const weekdayList = '$weekdayToken(?:\\s*[、,，和与及]\\s*$weekdayToken)*';
    const optWorkPrefix = r'(?:(?:正常|调休)?(?:上班|上课|补班|补课|行课)[，,、\s]*)?';

    final rests = <ScheduleDayAdjustment>[];
    final restBlocks = <({int matchStart, String holiday, List<DateTime> dates})>[];
    final restSeen = <String>{};

    // 1. 放假区间与单日放假
    final rangeRe = RegExp(
      '($dateToken)$optWeekday\\s*[-~～—至到]\\s*(?:($dateToken)|(\\d{1,2})\\s*[日号])$optWeekday\\s*(?:放假|停课|调休放假)',
    );
    for (final m in rangeRe.allMatches(text)) {
      final start = _parseLooseDate(m.group(1)!, ref, defaultYear: docYear);
      if (start == null) continue;
      DateTime? end;
      if ((m.group(2) ?? '').isNotEmpty) {
        end = _parseLooseDate(m.group(2)!, ref, defaultYear: docYear);
      } else if ((m.group(3) ?? '').isNotEmpty) {
        final d = int.tryParse(m.group(3)!);
        if (d != null && d >= 1 && d <= 31) {
          end = DateTime(start.year, start.month, d);
        }
      }
      final hName = holidayNameNear(m.start);
      final expanded = expandRestRange(
        formatIso(start),
        formatIso(end ?? start),
        hName.isNotEmpty ? '$hName放假' : '节假日放假',
      );
      final blockDates = <DateTime>[];
      for (final r in expanded) {
        final dt = parseIso(r.targetDate);
        if (dt != null) blockDates.add(dt);
        if (restSeen.add(r.targetDate)) rests.add(r);
      }
      if (blockDates.isNotEmpty) {
        restBlocks.add((matchStart: m.start, holiday: hName, dates: blockDates));
      }
    }

    final singleRestRe = RegExp('($dateToken)$optWeekday\\s*(?:放假|停课|不上课)');
    for (final m in singleRestRe.allMatches(text)) {
      final d = _parseLooseDate(m.group(1)!, ref, defaultYear: docYear);
      if (d == null) continue;
      final iso = formatIso(d);
      if (restSeen.add(iso)) {
        final hName = holidayNameNear(m.start);
        rests.add(
          ScheduleDayAdjustment(
            targetDate: iso,
            type: ScheduleAdjustmentType.rest,
            label: hName.isNotEmpty ? '$hName放假' : '放假停课',
          ),
        );
        restBlocks.add((matchStart: m.start, holiday: hName, dates: [d]));
      }
    }

    final allRestDates = rests
        .map((r) => parseIso(r.targetDate))
        .whereType<DateTime>()
        .toList();
    final swapsByTarget = <String, ScheduleDayAdjustment>{};

    void addSwap(DateTime target, DateTime source, int matchPos, {bool inferred = false}) {
      if (formatIso(target) == formatIso(source)) return;
      final key = formatIso(target);
      if (swapsByTarget.containsKey(key)) return;
      final hName = holidayNameNear(matchPos);
      final base = hName.isNotEmpty ? '$hName调休' : '节假日调休';
      swapsByTarget[key] = ScheduleDayAdjustment(
        targetDate: key,
        type: ScheduleAdjustmentType.swap,
        sourceDate: formatIso(source),
        label: inferred ? '$base·待核对' : base,
      );
    }

    List<DateTime> extractDates(String chunk) => RegExp(dateToken)
        .allMatches(chunk)
        .map((m) => _parseLooseDate(m.group(0)!, ref, defaultYear: docYear))
        .whereType<DateTime>()
        .toList();

    DateTime? resolveWeekdaySource(
      DateTime target,
      String wdChar,
      int? weekNum,
      int matchPos,
    ) {
      final dow = _parseChineseWeekday(wdChar);
      if (dow == null) return null;
      if (weekNum != null && weekNum >= 1 && weekNum <= 30) {
        return SemesterConfig.weekStart(weekNum).add(Duration(days: dow - 1));
      }
      final preferredBlocks = restBlocks.where((b) => b.matchStart <= matchPos).toList();
      if (preferredBlocks.isNotEmpty) {
        for (final d in preferredBlocks.last.dates) {
          if (d.weekday == dow && formatIso(d) != formatIso(target)) return d;
        }
      }
      DateTime? bestRest;
      var bestDist = 999999;
      for (final d in allRestDates) {
        if (d.weekday != dow || formatIso(d) == formatIso(target)) continue;
        final dist = d.difference(target).inDays.abs();
        if (dist < bestDist) {
          bestDist = dist;
          bestRest = d;
        }
      }
      if (bestRest != null && bestDist <= 25) return bestRest;

      DateTime? bestNear;
      var nearDist = 999999;
      for (var delta = -7; delta <= 7; delta++) {
        if (delta == 0) continue;
        final cand = target.add(Duration(days: delta));
        if (cand.weekday == dow && delta.abs() < nearDist) {
          nearDist = delta.abs();
          bestNear = cand;
        }
      }
      return bestNear;
    }

    // 2a. 成对“分别”正向调课
    final pairedFwdRe = RegExp(
      '($dateList)\\s*$optWorkPrefix\\s*分别\\s*(?:补上?|改上|上|执行|按照?)\\s*($dateList|$weekdayList)\\s*(?:的)?(?:课|课程|课表|教学安排)',
    );
    for (final m in pairedFwdRe.allMatches(text)) {
      final targets = extractDates(m.group(1)!);
      final rhs = m.group(2)!;
      final rhsDates = extractDates(rhs);
      if (rhsDates.isNotEmpty) {
        final count = targets.length < rhsDates.length ? targets.length : rhsDates.length;
        for (var i = 0; i < count; i++) {
          addSwap(targets[i], rhsDates[i], m.start);
        }
      } else {
        final wdMatches = RegExp(r'(?:第(\d{1,2})周\s*)?(?:单周|双周)?(?:星期|礼拜|周)([一二三四五六日天])')
            .allMatches(rhs)
            .toList();
        final count = targets.length < wdMatches.length ? targets.length : wdMatches.length;
        for (var i = 0; i < count; i++) {
          final wn = wdMatches[i].group(1) == null ? null : int.tryParse(wdMatches[i].group(1)!);
          final src = resolveWeekdaySource(targets[i], wdMatches[i].group(2)!, wn, m.start);
          if (src != null) addSwap(targets[i], src, m.start);
        }
      }
    }

    // 2b. 成对“分别”反向调课
    final pairedRevRe = RegExp(
      '(?:原|将|把)?($dateList)\\s*(?:的)?(?:课|课程|课表|教学安排)?\\s*分别\\s*(?:调至|调到|调整至|调整到|移至|改至|改到|安排至|安排到)\\s*($dateList)',
    );
    for (final m in pairedRevRe.allMatches(text)) {
      final sources = extractDates(m.group(1)!);
      final targets = extractDates(m.group(2)!);
      final count = targets.length < sources.length ? targets.length : sources.length;
      for (var i = 0; i < count; i++) {
        addSwap(targets[i], sources[i], m.start);
      }
    }

    // 2c. 单条正向明确补课
    final fwdSwapRe = RegExp(
      '($dateToken)$optWeekday\\s*$optWorkPrefix(?:调休)?\\s*(?:补上?|改上|上|执行|按照?|调整为上?)\\s*(?:第\\d{1,2}周\\s*)?(?:(?:星期|礼拜|周)[一二三四五六日天]\\s*)?[（(]?($dateToken)[)）]?$optWeekday\\s*(?:的)?(?:课|课程|课表|教学安排)',
    );
    for (final m in fwdSwapRe.allMatches(text)) {
      final target = _parseLooseDate(m.group(1)!, ref, defaultYear: docYear);
      final source = _parseLooseDate(m.group(2)!, ref, defaultYear: docYear);
      if (target != null && source != null) addSwap(target, source, m.start);
    }

    // 2d. 单条反向调课
    final revSwapRe = RegExp(
      '(?:原|将|把)?($dateToken)$optWeekday\\s*(?:的)?(?:课|课程|课表|教学安排)?\\s*(?:调至|调到|调整至|调整到|移至|改至|改到|挪至|挪到|安排至|安排到)\\s*($dateToken)$optWeekday',
    );
    for (final m in revSwapRe.allMatches(text)) {
      final source = _parseLooseDate(m.group(1)!, ref, defaultYear: docYear);
      final target = _parseLooseDate(m.group(2)!, ref, defaultYear: docYear);
      if (target != null && source != null) addSwap(target, source, m.start);
    }

    // 2e. 单条按星期补课
    final wdSwapRe = RegExp(
      '($dateToken)$optWeekday\\s*$optWorkPrefix(?:调休)?\\s*(?:补上?|改上|上|执行|按照?|调整为上?)\\s*(?:第(\\d{1,2})周\\s*)?(?:单周|双周)?(?:星期|礼拜|周)([一二三四五六日天])\\s*(?:的)?(?:课|课程|课表|教学安排)',
    );
    for (final m in wdSwapRe.allMatches(text)) {
      final target = _parseLooseDate(m.group(1)!, ref, defaultYear: docYear);
      if (target == null) continue;
      final wn = m.group(2) == null ? null : int.tryParse(m.group(2)!);
      final source = resolveWeekdaySource(target, m.group(3)!, wn, m.start);
      if (source != null) addSwap(target, source, m.start);
    }

    // 2f. 国务院/简明放假通知兜底：“9月20日（周日）、10月10日（周六）上班”
    final bareWorkRe = RegExp('($dateList)\\s*(?:正常|调休)?(?:上班|补班|补课|上课|行课)');
    for (final m in bareWorkRe.allMatches(text)) {
      final seenLocal = <String>{};
      final workDates = <DateTime>[];
      for (final d in extractDates(m.group(1)!)) {
        final iso = formatIso(d);
        if (seenLocal.add(iso) && !swapsByTarget.containsKey(iso) && !restSeen.contains(iso)) {
          workDates.add(d);
        }
      }
      if (workDates.isEmpty || restBlocks.isEmpty) continue;

      final priorBlocks = restBlocks.where((b) => b.matchStart <= m.start).toList();
      final block = priorBlocks.isNotEmpty
          ? priorBlocks.last
          : (List.of(restBlocks)
                ..sort((a, b) => a.dates.first
                    .difference(workDates.first)
                    .inDays
                    .abs()
                    .compareTo(b.dates.first.difference(workDates.first).inDays.abs())))
              .first;
      final candidates = _pickSwapSourceWeekdays(block.dates);
      final count = workDates.length < candidates.length ? workDates.length : candidates.length;
      for (var i = 0; i < count; i++) {
        addSwap(workDates[i], candidates[i], m.start, inferred: true);
      }
    }

    final finalSwaps = swapsByTarget.values.toList()
      ..sort((a, b) => a.targetDate.compareTo(b.targetDate));
    final restMap = <String, ScheduleDayAdjustment>{};
    for (final r in rests) {
      if (!swapsByTarget.containsKey(r.targetDate)) {
        restMap[r.targetDate] = r;
      }
    }
    final finalRests = restMap.values.toList()
      ..sort((a, b) => a.targetDate.compareTo(b.targetDate));

    return ParsedHolidayNotice(swaps: finalSwaps, rests: finalRests);
  }

  static List<DateTime> _pickSwapSourceWeekdays(List<DateTime> blockDates) {
    final byIso = {for (final d in blockDates) formatIso(d): d};
    final sorted = byIso.values.toList()..sort((a, b) => a.compareTo(b));
    final weekdays = sorted.where((d) => d.weekday >= 1 && d.weekday <= 5).toList();
    if (weekdays.isEmpty) return const [];
    final firstWeekendIdx = sorted.indexWhere((d) => d.weekday >= 6);
    final afterWeekend = firstWeekendIdx >= 0
        ? sorted.sublist(firstWeekendIdx + 1).where((d) => d.weekday >= 1 && d.weekday <= 5).toList()
        : const <DateTime>[];
    final nonFirst = weekdays.length > 1 ? weekdays.sublist(1) : weekdays;
    final seen = <String>{};
    final out = <DateTime>[];
    for (final d in [...afterWeekend, ...nonFirst, ...weekdays]) {
      if (seen.add(formatIso(d))) out.add(d);
    }
    return out;
  }

  static int? _parseChineseWeekday(String ch) => switch (ch) {
        '一' => 1,
        '二' => 2,
        '三' => 3,
        '四' => 4,
        '五' => 5,
        '六' => 6,
        '日' || '天' => 7,
        _ => null,
      };

  static DateTime? _parseLooseDate(
    String token,
    DateTime ref, {
    int? defaultYear,
  }) {
    final full = RegExp(r'(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})')
        .firstMatch(token);
    if (full != null) {
      final y = int.tryParse(full.group(1)!);
      final mo = int.tryParse(full.group(2)!);
      final d = int.tryParse(full.group(3)!);
      if (y != null && mo != null && d != null && mo >= 1 && mo <= 12 && d >= 1 && d <= 31) {
        return DateTime(y, mo, d);
      }
      return null;
    }
    final md = RegExp(r'(\d{1,2})\s*[月/.]\s*(\d{1,2})').firstMatch(token);
    if (md == null) return null;
    final mo = int.tryParse(md.group(1)!);
    final d = int.tryParse(md.group(2)!);
    if (mo == null || d == null || mo < 1 || mo > 12 || d < 1 || d > 31) return null;
    final year = defaultYear ?? ((ref.month >= 9 && mo <= 2) ? ref.year + 1 : ref.year);
    return DateTime(year, mo, d);
  }
}
