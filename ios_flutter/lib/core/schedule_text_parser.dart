import 'class_times.dart';
import 'schedule_exam_fusion.dart';
import 'semester_config.dart';

enum ParsedItemKind { course, exam }

/// 智能识别草稿（来自剪贴板通知或 iOS Vision 离线 OCR），必须经用户核对修改后再保存。
class ParsedScheduleDraft {
  final String id;
  final bool selected;
  final ParsedItemKind kind;
  final String title;
  final String campus;
  final String room;
  final String teacher;
  final int dayOfWeek;
  final int startPeriod;
  final int endPeriod;
  final String weeksSpec;
  final String examDate;
  final String startClock;
  final String endClock;
  final String examType;
  final String seatNo;
  final String note;
  final String rawSnippet;

  const ParsedScheduleDraft({
    required this.id,
    this.selected = true,
    required this.kind,
    required this.title,
    this.campus = '',
    this.room = '',
    this.teacher = '',
    this.dayOfWeek = 0,
    this.startPeriod = 1,
    this.endPeriod = 2,
    this.weeksSpec = '',
    this.examDate = '',
    this.startClock = '',
    this.endClock = '',
    this.examType = '考试',
    this.seatNo = '',
    this.note = '',
    this.rawSnippet = '',
  });

  ParsedScheduleDraft copyWith({
    bool? selected,
    ParsedItemKind? kind,
    String? title,
    String? campus,
    String? room,
    String? teacher,
    int? dayOfWeek,
    int? startPeriod,
    int? endPeriod,
    String? weeksSpec,
    String? examDate,
    String? startClock,
    String? endClock,
    String? examType,
    String? seatNo,
    String? note,
  }) =>
      ParsedScheduleDraft(
        id: id,
        selected: selected ?? this.selected,
        kind: kind ?? this.kind,
        title: title ?? this.title,
        campus: campus ?? this.campus,
        room: room ?? this.room,
        teacher: teacher ?? this.teacher,
        dayOfWeek: dayOfWeek ?? this.dayOfWeek,
        startPeriod: startPeriod ?? this.startPeriod,
        endPeriod: endPeriod ?? this.endPeriod,
        weeksSpec: weeksSpec ?? this.weeksSpec,
        examDate: examDate ?? this.examDate,
        startClock: startClock ?? this.startClock,
        endClock: endClock ?? this.endClock,
        examType: examType ?? this.examType,
        seatNo: seatNo ?? this.seatNo,
        note: note ?? this.note,
        rawSnippet: rawSnippet,
      );
}

/// 纯本地离线智能解析引擎（与 Android `ScheduleTextParser.kt` 保持一致规则）。
class ScheduleTextParser {
  static const _examKeywords = [
    '考试', '期末', '期中', '补考', '缓考', '随堂考', '结课考', '测验',
    '考场', '座位', '监考', '闭卷', '开卷', '笔试', '机考', '答辩',
  ];

  static const _explicitCourseKeywords = [
    '补课', '加课', '调课', '上课', '实验课', '重修', '选修',
  ];

  static const _tableHeaderKeywords = [
    '课程名称', '考试时间', '考场', '考试地点', '教室', '班级', '人数', '序号', '监考', '备注', '星期', '节次',
  ];

  static const _campusKeywords = [
    ('广兰校园', '广兰校园'),
    ('广兰校区', '广兰校园'),
    ('广兰', '广兰校园'),
    ('枫林校园', '枫林校园'),
    ('枫林校区', '枫林校园'),
    ('枫林', '枫林校园'),
    ('青岚校区', '青岚校区'),
    ('青岚', '青岚校区'),
    ('抚州校区', '抚州校区'),
    ('抚州', '抚州校区'),
  ];

  static const _weekdayMap = {
    '一': 0, '二': 1, '三': 2, '四': 3, '五': 4, '六': 5, '日': 6, '天': 6, '末': 6,
    '1': 0, '2': 1, '3': 2, '4': 3, '5': 4, '6': 5, '7': 6,
  };

  static int _seq = 0;

  static List<ParsedScheduleDraft> parse(
    String rawText, {
    DateTime? referenceDate,
    ParsedItemKind? defaultKindHint,
  }) {
    final ref = referenceDate ?? DateTime.now();
    final normalized = rawText
        .replaceAll('\r\n', '\n')
        .replaceAll('\r', '\n')
        .replaceAll(RegExp(r'(?<=\d)[ー–—－―](?=\d)'), '-')
        .trim();
    if (normalized.isEmpty) return const [];

    final chunks = _splitIntoChunks(normalized, ref);
    final drafts = <ParsedScheduleDraft>[];
    for (final chunk in chunks) {
      drafts.addAll(_parseChunkToDrafts(chunk, ref, defaultKindHint));
    }
    return drafts;
  }

  static List<String> _splitIntoChunks(String text, DateTime ref) {
    final paragraphs = text
        .split(RegExp(r'\n\s*\n+'))
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    if (paragraphs.length > 1 &&
        paragraphs.where((p) => _hasScheduleSignal(p, ref)).length >= 2) {
      return [
        for (final p in paragraphs) ..._splitNumberedOrTable(p, ref),
      ];
    }
    return _splitNumberedOrTable(text, ref);
  }

  static List<String> _splitNumberedOrTable(String block, DateTime ref) {
    final numbered = block
        .split(RegExp(
          r'(?=^(?:[（(]?\d{1,2}[)）\.、]|[①②③④⑤⑥⑦⑧⑨⑩])\s*)',
          multiLine: true,
        ))
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    if (numbered.length >= 2 &&
        numbered.where((s) => _hasScheduleSignal(s, ref)).length >= 2) {
      return numbered;
    }

    final lines = block
        .split('\n')
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    if (lines.length >= 2) {
      final grouped = _groupTableRows(lines, ref);
      final validGroups = grouped
          .map((g) => g.join('\n'))
          .where((chunk) => _hasDateOrTimeOrPeriod(chunk, ref))
          .toList();
      if (validGroups.length >= 2) {
        return validGroups;
      }
    }
    return [block];
  }

  static List<List<String>> _groupTableRows(List<String> lines, DateTime ref) {
    final groups = <List<String>>[];
    for (final line in lines) {
      final hasDate = extractDate(line, ref) != null;
      final hasPeriod = extractPeriodRange(line) != null;
      final hasClock = extractClockRange(line) != null;

      if (_isTableHeaderLine(line, hasDate, hasPeriod, hasClock)) continue;

      if (groups.isEmpty) {
        groups.add([line]);
        continue;
      }

      final cur = groups.last;
      final curText = cur.join('\n');
      final curHasDate = extractDate(curText, ref) != null;
      final curHasPeriod = extractPeriodRange(curText) != null;
      final curHasClock = extractClockRange(curText) != null;
      final curOpenParens = _unclosedParenCount(curText);

      final startsNewRow = (curHasDate || curHasPeriod || curHasClock) &&
          ((hasDate && curHasDate) ||
              (hasPeriod && curHasPeriod) ||
              (hasClock && curHasClock && curOpenParens <= 0));

      if (startsNewRow) {
        groups.add([line]);
      } else {
        cur.add(line);
      }
    }
    return groups;
  }

  static bool _isTableHeaderLine(
    String line,
    bool hasDate,
    bool hasPeriod,
    bool hasClock,
  ) {
    if (hasDate || hasPeriod || hasClock) return false;
    return _tableHeaderKeywords.where((k) => line.contains(k)).length >= 2;
  }

  static int _unclosedParenCount(String text) {
    var count = 0;
    for (final ch in text.split('')) {
      if (ch == '(' || ch == '（') count++;
      if (ch == ')' || ch == '）') count--;
    }
    return count;
  }

  static bool _hasDateOrTimeOrPeriod(String text, DateTime ref) =>
      extractDate(text, ref) != null ||
      extractClockRange(text) != null ||
      extractPeriodRange(text) != null;

  static bool _hasScheduleSignal(String text, DateTime ref) =>
      _hasDateOrTimeOrPeriod(text, ref) ||
      extractWeekday(text) != null ||
      extractWeeksSpec(text).isNotEmpty;

  static List<ParsedScheduleDraft> _parseChunkToDrafts(
    String chunk,
    DateTime ref,
    ParsedItemKind? defaultKindHint,
  ) {
    final compact = chunk.trim();
    if (compact.length < 2) return const [];

    final kind = _detectKind(compact, ref, defaultKindHint);
    final date = extractDate(compact, ref);
    final weeksSpecRaw = extractWeeksSpec(compact);
    final weekdayRaw = extractWeekday(compact);
    final clockRange = extractClockRange(compact);
    final periodRange = extractPeriodRange(compact);
    final campus = _extractCampus(compact);
    final rooms = _extractRooms(compact, campus);
    final teacher = _extractTeacher(compact);
    final seatNo = _extractSeatNo(compact);
    final examType = _extractExamType(compact);
    final classCodes = _extractClassCodes(compact);
    final title = _extractTitle(compact, rooms, teacher, campus);

    if (title.isEmpty && date == null && clockRange == null && periodRange == null) {
      return const [];
    }

    late final String resolvedWeeks;
    late final int resolvedDayOfWeek;
    late final String resolvedExamDate;

    if (date != null) {
      final weekNum = SemesterConfig.weekFor(date);
      resolvedExamDate = _fmtIsoDate(date);
      resolvedDayOfWeek = date.weekday - 1;
      resolvedWeeks = weeksSpecRaw.isNotEmpty ? weeksSpecRaw : '第$weekNum周';
    } else {
      final parsedWeeks = ScheduleExamFusion.parseWeeksSpec(weeksSpecRaw);
      final singleWeek =
          parsedWeeks.isNotEmpty ? parsedWeeks.first : SemesterConfig.weekFor(ref);
      resolvedDayOfWeek = weekdayRaw ?? (ref.weekday - 1);
      resolvedWeeks = weeksSpecRaw.isNotEmpty ? weeksSpecRaw : '第$singleWeek周';
      if (weeksSpecRaw.isNotEmpty || weekdayRaw != null) {
        final d = SemesterConfig.weekStart(singleWeek)
            .add(Duration(days: resolvedDayOfWeek));
        resolvedExamDate = _fmtIsoDate(d);
      } else {
        resolvedExamDate = '';
      }
    }

    late final int resolvedStartPeriod;
    late final int resolvedEndPeriod;
    late final String resolvedStartClock;
    late final String resolvedEndClock;

    if (periodRange != null) {
      resolvedStartPeriod = periodRange.$1;
      resolvedEndPeriod = periodRange.$2;
      resolvedStartClock =
          clockRange?.$1 ?? (ClassTimes.startTime(resolvedStartPeriod) ?? '');
      resolvedEndClock =
          clockRange?.$2 ?? (ClassTimes.endTime(resolvedEndPeriod) ?? '');
    } else if (clockRange != null) {
      resolvedStartClock = clockRange.$1;
      resolvedEndClock = clockRange.$2;
      final mapped = ScheduleExamFusion.clockRangeToPeriods(
        clockRange.$1,
        clockRange.$2,
      );
      resolvedStartPeriod = mapped.$1;
      resolvedEndPeriod = mapped.$2;
    } else {
      resolvedStartPeriod = 1;
      resolvedEndPeriod = 2;
      resolvedStartClock = '';
      resolvedEndClock = '';
    }

    final commonNotes = <String>[];
    if (compact.contains('补课')) commonNotes.add('补课');
    if (compact.contains('调课')) commonNotes.add('调课');
    if (compact.contains('闭卷')) commonNotes.add('闭卷');
    if (compact.contains('开卷')) commonNotes.add('开卷');
    if (compact.contains('带学生证') || compact.contains('校园卡')) {
      commonNotes.add('需携带证件');
    }

    final resolvedTitle = title.isNotEmpty
        ? title
        : (kind == ParsedItemKind.exam ? '未命名考试' : '未命名课程');

    // 同一场考试表格行含多个考场（不同班级分考场）时，按考场拆分为独立待选条目，不合并考场
    if (kind == ParsedItemKind.exam && rooms.length >= 2) {
      final out = <ParsedScheduleDraft>[];
      for (var i = 0; i < rooms.length; i++) {
        final perNote = <String>[];
        if (classCodes.length == rooms.length) {
          perNote.add('班级 ${classCodes[i]}');
        } else if (classCodes.isNotEmpty) {
          perNote.add('班级 ${classCodes.join(', ')}');
        }
        perNote.addAll(commonNotes);
        _seq++;
        out.add(
          ParsedScheduleDraft(
            id: 'draft_${DateTime.now().microsecondsSinceEpoch}_$_seq',
            kind: kind,
            title: resolvedTitle,
            campus: campus,
            room: rooms[i],
            teacher: teacher,
            dayOfWeek: resolvedDayOfWeek,
            startPeriod: resolvedStartPeriod,
            endPeriod: resolvedEndPeriod,
            weeksSpec: resolvedWeeks,
            examDate: resolvedExamDate,
            startClock: resolvedStartClock,
            endClock: resolvedEndClock,
            examType: examType,
            seatNo: seatNo,
            note: perNote.toSet().join(' · '),
            rawSnippet: compact.length > 180 ? compact.substring(0, 180) : compact,
          ),
        );
      }
      return out;
    }

    final allNotes = <String>[];
    if (classCodes.isNotEmpty) allNotes.add('班级 ${classCodes.join(', ')}');
    allNotes.addAll(commonNotes);

    _seq++;
    return [
      ParsedScheduleDraft(
        id: 'draft_${DateTime.now().microsecondsSinceEpoch}_$_seq',
        kind: kind,
        title: resolvedTitle,
        campus: campus,
        room: rooms.isNotEmpty ? rooms.first : '',
        teacher: teacher,
        dayOfWeek: resolvedDayOfWeek,
        startPeriod: resolvedStartPeriod,
        endPeriod: resolvedEndPeriod,
        weeksSpec: resolvedWeeks,
        examDate: resolvedExamDate,
        startClock: resolvedStartClock,
        endClock: resolvedEndClock,
        examType: examType,
        seatNo: seatNo,
        note: allNotes.toSet().join(' · '),
        rawSnippet: compact.length > 180 ? compact.substring(0, 180) : compact,
      ),
    ];
  }

  static ParsedItemKind _detectKind(
    String text,
    DateTime ref,
    ParsedItemKind? hint,
  ) {
    final examScore = _examKeywords.where((k) => text.contains(k)).length;
    final explicitCourseScore =
        _explicitCourseKeywords.where((k) => text.contains(k)).length;
    final hasMultiWeek = _hasMultiWeekRange(text);
    final hasConcreteDate = extractDate(text, ref) != null;
    final hasClock = extractClockRange(text) != null;
    final hasPeriod = extractPeriodRange(text) != null;

    if (examScore > 0 && examScore >= explicitCourseScore) {
      return ParsedItemKind.exam;
    }
    if (explicitCourseScore > 0) return ParsedItemKind.course;
    if (hasConcreteDate && (hasClock || hasPeriod) && !hasMultiWeek) {
      return ParsedItemKind.exam;
    }
    if (hasMultiWeek) return ParsedItemKind.course;
    if (hint != null) return hint;
    return ParsedItemKind.course;
  }

  static bool _hasMultiWeekRange(String text) {
    final m = RegExp(
      r'(?<![\d/.\-])第?\s*(\d{1,2})\s*[-~～至到]\s*(\d{1,2})\s*周(?![一二三四五六日天末])',
    ).firstMatch(text);
    if (m == null) return false;
    final a = int.tryParse(m.group(1)!) ?? 0;
    final b = int.tryParse(m.group(2)!) ?? 0;
    return a >= 1 &&
        a <= SemesterConfig.totalWeeks &&
        b >= a &&
        b <= SemesterConfig.totalWeeks;
  }

  static DateTime? extractDate(String text, DateTime ref) {
    final full = RegExp(
      r'(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*日?',
    ).firstMatch(text);
    if (full != null) {
      final y = int.tryParse(full.group(1)!);
      final mo = int.tryParse(full.group(2)!);
      final d = int.tryParse(full.group(3)!);
      if (y != null && mo != null && d != null && mo >= 1 && mo <= 12 && d >= 1 && d <= 31) {
        return DateTime(y, mo, d);
      }
    }

    final mdCn = RegExp(r'(?<!\d)(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]?').firstMatch(text);
    if (mdCn != null) {
      final mo = int.tryParse(mdCn.group(1)!);
      final d = int.tryParse(mdCn.group(2)!);
      if (mo != null && d != null && mo >= 1 && mo <= 12 && d >= 1 && d <= 31) {
        return _resolveMonthDay(mo, d, ref);
      }
    }

    final mdSlash = RegExp(
      r'(?<![\d\-.])(1[0-2]|0?[1-9])\s*[/.]\s*(3[01]|[12]\d|0?[1-9])(?![\d.])',
    ).firstMatch(text);
    if (mdSlash != null) {
      final mo = int.tryParse(mdSlash.group(1)!);
      final d = int.tryParse(mdSlash.group(2)!);
      if (mo != null && d != null) {
        return _resolveMonthDay(mo, d, ref);
      }
    }

    final base = DateTime(ref.year, ref.month, ref.day);
    if (text.contains('大后天')) return base.add(const Duration(days: 3));
    if (text.contains('后天')) return base.add(const Duration(days: 2));
    if (text.contains('明天')) return base.add(const Duration(days: 1));
    if (text.contains('今天')) return base;

    final relWeek = RegExp(r'(本|这|下)周([一二三四五六日天])').firstMatch(text);
    if (relWeek != null) {
      final targetIdx = _weekdayMap[relWeek.group(2)!];
      if (targetIdx != null) {
        final monday = base.subtract(Duration(days: base.weekday - 1));
        final weekOffset = relWeek.group(1) == '下' ? 7 : 0;
        return monday.add(Duration(days: weekOffset + targetIdx));
      }
    }
    return null;
  }

  static DateTime _resolveMonthDay(int month, int day, DateTime ref) {
    final year = (ref.month >= 9 && month <= 2) ? ref.year + 1 : ref.year;
    return DateTime(year, month, day);
  }

  static int? extractWeekday(String text) {
    final m = RegExp(r'(?:星期|礼拜|周)\s*([一二三四五六日天])').firstMatch(text);
    if (m == null) return null;
    return _weekdayMap[m.group(1)!];
  }

  static String extractWeeksSpec(String text) {
    final range = RegExp(
      r'(?<![\d/.\-])第?\s*((\d{1,2})\s*[-~～至到]\s*(\d{1,2})\s*周(?:\s*[（(]?[单双]周?[)）]?)?)(?![一二三四五六日天末])',
    ).firstMatch(text);
    if (range != null) {
      final a = int.tryParse(range.group(2)!) ?? 0;
      final b = int.tryParse(range.group(3)!) ?? 0;
      if (a >= 1 &&
          a <= SemesterConfig.totalWeeks &&
          b >= a &&
          b <= SemesterConfig.totalWeeks) {
        final weeks = ScheduleExamFusion.parseWeeksSpec(range.group(1)!);
        if (weeks.isNotEmpty) return ScheduleExamFusion.formatWeeksSpec(weeks);
      }
    }

    final list = RegExp(
      r'(?<![\d/.\-])第?\s*(\d{1,2}(?:\s*[,，、]\s*\d{1,2})+\s*周)(?![一二三四五六日天末])',
    ).firstMatch(text);
    if (list != null) {
      final weeks = ScheduleExamFusion.parseWeeksSpec(list.group(1)!);
      if (weeks.isNotEmpty) return ScheduleExamFusion.formatWeeksSpec(weeks);
    }

    final single =
        RegExp(r'(?<![\d/.\-])第?\s*(\d{1,2})\s*周(?![一二三四五六日天末])').firstMatch(text);
    if (single != null) {
      final w = int.tryParse(single.group(1)!) ?? 0;
      if (w >= 1 && w <= SemesterConfig.totalWeeks) return '第$w周';
    }
    return '';
  }

  static (String, String)? extractClockRange(String text) {
    final std = RegExp(
      r'(\d{1,2})\s*[:：]\s*(\d{2})\s*[-~～—至到]\s*(\d{1,2})\s*[:：]\s*(\d{2})',
    ).firstMatch(text);
    if (std != null) {
      final sh = int.parse(std.group(1)!);
      final sm = int.parse(std.group(2)!);
      final eh = int.parse(std.group(3)!);
      final em = int.parse(std.group(4)!);
      if (sh <= 23 && eh <= 23 && sm <= 59 && em <= 59) {
        return (
          '${sh.toString().padLeft(2, '0')}:${sm.toString().padLeft(2, '0')}',
          '${eh.toString().padLeft(2, '0')}:${em.toString().padLeft(2, '0')}',
        );
      }
    }

    final oral = RegExp(
      r'(上午|早上|下午|晚上|晚)?\s*(\d{1,2})\s*[点时](半|\d{1,2}分?)?\s*[-~～—至到]\s*(上午|早上|下午|晚上|晚)?\s*(\d{1,2})\s*[点时](半|\d{1,2}分?)?',
    ).firstMatch(text);
    if (oral != null) {
      final p1 = oral.group(1) ?? '';
      final p2 = (oral.group(4) ?? '').isNotEmpty ? oral.group(4)! : p1;
      final sh = _adjustHour(int.parse(oral.group(2)!), p1);
      final sm = _parseOralMin(oral.group(3) ?? '');
      final eh = _adjustHour(int.parse(oral.group(5)!), p2);
      final em = _parseOralMin(oral.group(6) ?? '');
      if (sh <= 23 && eh <= 23) {
        return (
          '${sh.toString().padLeft(2, '0')}:${sm.toString().padLeft(2, '0')}',
          '${eh.toString().padLeft(2, '0')}:${em.toString().padLeft(2, '0')}',
        );
      }
    }
    return null;
  }

  static int _adjustHour(int hour, String period) {
    if ((period == '下午' || period == '晚上' || period == '晚') &&
        hour >= 1 &&
        hour <= 11) {
      return hour + 12;
    }
    return hour;
  }

  static int _parseOralMin(String raw) {
    if (raw == '半') return 30;
    if (raw.isEmpty) return 0;
    return (int.tryParse(raw.replaceAll('分', '')) ?? 0).clamp(0, 59);
  }

  static (int, int)? extractPeriodRange(String text) {
    final range =
        RegExp(r'(?<!\d)第?\s*(\d{1,2})\s*[-~～—–－ー―一至到、,]\s*(\d{1,2})\s*大?节')
            .firstMatch(text);
    if (range != null) {
      final a = int.parse(range.group(1)!);
      final b = int.parse(range.group(2)!);
      if (a >= 1 && a <= 12 && b >= a && b <= 12) return (a, b);
    }
    final single = RegExp(r'第\s*(\d{1,2})\s*节').firstMatch(text);
    if (single != null) {
      final a = int.parse(single.group(1)!);
      if (a >= 1 && a <= 12) return (a, a);
    }
    return null;
  }

  static String _extractCampus(String text) {
    for (final (kw, canonical) in _campusKeywords) {
      if (text.contains(kw)) return canonical;
    }
    return '';
  }

  static List<String> _extractRooms(String text, String campus) {
    final labeled = RegExp(r'(?:考场|地点|教室|课室|位置)\s*[:：]\s*([^\n;；。]+)')
        .firstMatch(text);
    if (labeled != null) {
      final raw = labeled
          .group(1)!
          .trim()
          .replaceFirst(RegExp(r'(?:座[位号]?|老师|教师|时间|日期|备注).*$'), '')
          .trim();
      if (raw.isNotEmpty) {
        final seen = <String>{};
        final pieces = <String>[];
        for (final p in raw.split(RegExp(r'[、,，/]+'))) {
          final cleaned =
              _completeWrappedRoomNumber(_stripCampusPrefix(p.trim(), campus), text);
          if (cleaned.length >= 2 && seen.add(cleaned)) {
            pieces.add(cleaned);
            if (pieces.length >= 4) break;
          }
        }
        if (pieces.isNotEmpty) return pieces;
      }
    }

    final roomRe = RegExp(
      r'(?:(?<!\d)\d{1,2}\s*教\s*[-—]?\s*(?:阶梯?\s*\d{0,3}|[A-Za-z]?\d{2,4})|[一二三四五六七八九十]\s*教\s*[-—]?\s*(?:阶梯?\s*\d{0,3}|[A-Za-z]?\d{2,4})|教\s*\d{1,2}\s*[-—]?\s*(?:阶梯?\s*\d{0,3}|[A-Za-z]?\d{2,4})|(?<![A-Za-z0-9])[A-Za-z]?\d{1,2}[-—]\d{3}(?!\d)|(?:阶梯?|实验楼|实训楼|地学楼|软件楼|经管楼|文法楼|理工楼|教学楼|综合楼|主楼|核工楼|逸夫楼|机房|体育馆|操场|图书馆)\s*[A-Za-z0-9\-—]{1,10}|(?<![A-Za-z0-9])[A-Za-z]\d{3,4}(?!\d))',
    );
    final seen = <String>{};
    final rooms = <String>[];
    for (final m in roomRe.allMatches(text)) {
      final cand = m.group(0)!.replaceAll(RegExp(r'\s+'), '').trim();
      if (RegExp(r'^\d{1,2}-\d{1,2}$').hasMatch(cand)) continue;
      final completed = _completeWrappedRoomNumber(cand, text);
      if (seen.add(completed)) {
        rooms.add(completed);
        if (rooms.length >= 4) break;
      }
    }
    return rooms;
  }

  static String _completeWrappedRoomNumber(String room, String fullText) {
    if (!room.endsWith('阶') &&
        !room.endsWith('阶梯') &&
        !room.endsWith('-') &&
        !room.endsWith('—')) {
      return room;
    }
    final lines = fullText
        .split('\n')
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    final matchIdx = lines.indexWhere((l) => l.replaceAll(' ', '').contains(room));
    final tail = (matchIdx >= 0 && matchIdx + 1 < lines.length)
        ? lines.sublist(matchIdx + 1)
        : const <String>[];
    for (final ln in tail) {
      if (RegExp(r'^\d{1,3}$').hasMatch(ln)) return '$room$ln';
    }
    for (final ln in tail) {
      final m = RegExp(r'^(\d{1,3})\s+\d{1,2}\s*[:：]\s*\d{2}').firstMatch(ln);
      if (m != null) return '$room${m.group(1)}';
    }
    return room;
  }

  static List<String> _extractClassCodes(String text) {
    final seen = <String>{};
    final out = <String>[];
    for (final m
        in RegExp(r'(?<!\d)(\d{6,9}[A-Za-z]?(?:-\d{1,2})?)(?!\d)').allMatches(text)) {
      final code = m.group(1)!;
      if (seen.add(code)) {
        out.add(code);
        if (out.length >= 6) break;
      }
    }
    return out;
  }

  static String _stripCampusPrefix(String raw, String campus) {
    var s = raw;
    for (final (kw, _) in _campusKeywords) {
      if (s.startsWith(kw)) s = s.substring(kw.length).trim();
    }
    if (campus.isNotEmpty && s.startsWith(campus)) {
      s = s.substring(campus.length).trim();
    }
    return s.isEmpty ? raw : s;
  }

  static String _extractTeacher(String text) {
    final labeled = RegExp(r'(?:教师|授课教师|任课教师|老师)\s*[:：]\s*([\u4e00-\u9fa5A-Za-z·]{2,8})')
        .firstMatch(text);
    if (labeled != null) return labeled.group(1)!.trim();
    final suffix = RegExp(r'([\u4e00-\u9fa5]{1,3}老师)').firstMatch(text);
    if (suffix != null) return suffix.group(1)!.trim();
    return '';
  }

  static String _extractSeatNo(String text) {
    final m = RegExp(r'(?:座位号?|座号)\s*[:：]?\s*([A-Za-z0-9\-]{1,8})').firstMatch(text);
    return m?.group(1)?.trim() ?? '';
  }

  static String _extractExamType(String text) {
    if (text.contains('期末')) return '期末考试';
    if (text.contains('期中')) return '期中考试';
    if (text.contains('补考')) return '补考';
    if (text.contains('缓考')) return '缓考';
    if (text.contains('随堂')) return '随堂测验';
    if (text.contains('机考')) return '上机考试';
    if (text.contains('答辩')) return '课程答辩';
    return '自定义考试';
  }

  static String _extractTitle(
    String text,
    List<String> rooms,
    String teacher,
    String campus,
  ) {
    final quoted = RegExp(r'《([^》\n]{1,40})》').firstMatch(text);
    if (quoted != null) return quoted.group(1)!.trim();

    final labeled = RegExp(
      r'(?:课程名称|考试科目|考试课程|课程名|科目|课程)\s*[:：]\s*([^\n,，;；。]+)',
    ).firstMatch(text);
    if (labeled != null) {
      final cleaned = labeled
          .group(1)!
          .replaceFirst(
            RegExp(r'\s+(?:时间|日期|地点|考场|教室|教师|老师|周次|第\d).*$'),
            '',
          )
          .trim();
      if (cleaned.isNotEmpty) {
        return cleaned.length > 32 ? cleaned.substring(0, 32) : cleaned;
      }
    }

    var s = text
        .replaceFirst(RegExp(r'^(?:[（(]?\d{1,2}[)）\.、]|[①②③④⑤⑥⑦⑧⑨⑩])\s*'), '')
        .replaceAll(RegExp(r'【[^】]{1,20}】'), ' ')
        .replaceAll(RegExp(r'\[[^\]]{1,20}\]'), ' ')
        .replaceAll(RegExp(r'(?:关于|通知|提醒|各位同学|请大家|注意)[:：]?'), ' ')
        .replaceAll(RegExp(r'20\d{2}[-/.年]\d{1,2}[-/.月]\d{1,2}日?'), ' ')
        .replaceAll(RegExp(r'\d{1,2}月\d{1,2}[日号]?'), ' ')
        .replaceAll(RegExp(r'\d{1,2}[:：]\d{2}\s*[-~～—至到]\s*\d{1,2}[:：]\d{2}'), ' ')
        .replaceAll(RegExp(r'(?<!\d)第?\d{1,2}[-~～—–－ー―一至到、,]\d{1,2}大?节'), ' ')
        .replaceAll(RegExp(r'(?<![\d/.\-])第?\d{1,2}[-~～至到]\d{1,2}周(?:[（(]?[单双]周?[)）]?)?(?![一二三四五六日天末])'), ' ')
        .replaceAll(RegExp(r'(?<![\d/.\-])第?\d{1,2}周(?![一二三四五六日天末])'), ' ')
        .replaceAll(RegExp(r'(?:本|这|下)?(?:星期|礼拜|周)[一二三四五六日天]'), ' ')
        .replaceAll(RegExp(r'(?:考场|地点|教室|座位号?|教师|老师)\s*[:：]\s*[^\n\s,，;；]+'), ' ')
        .replaceAll(RegExp(r'(?<!\d)\d{5,}[A-Za-z]?(?:-\d{1,2})?'), ' ');

    for (final r in rooms) {
      final part = r.trim();
      if (part.isNotEmpty) {
        s = s.replaceAll(part, ' ');
        final roomPrefix = part.replaceFirst(RegExp(r'\d{1,3}$'), '');
        if (roomPrefix.length >= 2) s = s.replaceAll(roomPrefix, ' ');
      }
    }
    if (teacher.isNotEmpty) s = s.replaceAll(teacher, ' ');
    if (campus.isNotEmpty) s = s.replaceAll(campus, ' ');
    for (final (kw, _) in _campusKeywords) {
      s = s.replaceAll(kw, ' ');
    }

    const stopWords = {'考试', '补课', '加课', '调课', '通知', '闭卷', '开卷', '上午', '下午', '晚上'};
    final parts = s.split(RegExp(r'[\n,，、;；。|｜\t()（）]+|\s{2,}'));
    for (final raw in parts) {
      var tok = raw
          .trim()
          .replaceFirst(RegExp(r'^\d+\s+'), '')
          .replaceFirst(RegExp(r'\s+\d+$'), '')
          .replaceFirst(
            RegExp(r'^(?:[一二三四五六七八九十\d]{1,2}教(?:学楼)?)(?=[\u4e00-\u9fa5]{2,})'),
            '',
          );
      for (final suffix in ['期末考试', '期中考试', '考试', '补课', '加课', '调课']) {
        if (tok.endsWith(suffix) && tok.length > suffix.length) {
          tok = tok.substring(0, tok.length - suffix.length);
        }
      }
      tok = tok.replaceAll(RegExp(r'^[:：\-—、\s]+|[:：\-—、\s]+$'), '');
      if (tok.length >= 2 &&
          RegExp(r'[\u4e00-\u9fa5A-Za-z]').hasMatch(tok) &&
          !stopWords.contains(tok)) {
        return tok.length > 32 ? tok.substring(0, 32) : tok;
      }
    }
    return '';
  }

  static String _fmtIsoDate(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
}
