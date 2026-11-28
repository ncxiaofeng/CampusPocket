import 'package:flutter_test/flutter_test.dart';

import 'package:campuspocket_ios/core/schedule_adjustments.dart';
import 'package:campuspocket_ios/core/schedule_exam_fusion.dart';
import 'package:campuspocket_ios/core/schedule_text_parser.dart';
import 'package:campuspocket_ios/core/semester_config.dart';

void main() {
  group('ScheduleExamFusion', () {
    test('parses and formats weeks specs including odd/even/discrete', () {
      expect(
        ScheduleExamFusion.parseWeeksSpec('1-16周'),
        {for (var i = 1; i <= 16; i++) i},
      );
      expect(ScheduleExamFusion.parseWeeksSpec('1-8周(单)'), {1, 3, 5, 7});
      expect(ScheduleExamFusion.parseWeeksSpec('1-8周(双)'), {2, 4, 6, 8});
      expect(ScheduleExamFusion.parseWeeksSpec('12、14、16周'), {12, 14, 16});
      expect(ScheduleExamFusion.formatWeeksSpec({1, 2, 3, 4}), '1-4周');
      expect(ScheduleExamFusion.formatWeeksSpec({1, 3, 5, 7}), '1-7周(单)');
      expect(ScheduleExamFusion.formatWeeksSpec({15}), '第15周');
    });

    test('maps exam clock ranges to class periods', () {
      expect(ScheduleExamFusion.clockRangeToPeriods('08:30', '10:30'), (1, 3));
      expect(ScheduleExamFusion.clockRangeToPeriods('14:00', '16:00'), (5, 6));
      expect(ScheduleExamFusion.clockRangeToPeriods('19:00', '21:00'), (9, 10));
      expect(ScheduleExamFusion.clockRangeToPeriods('', ''), (1, 2));
    });

    test('maps exam into matching week only', () {
      final week12Start = SemesterConfig.weekStart(12);
      final wed = week12Start.add(const Duration(days: 2));
      final wedIso =
          '${wed.year}-${wed.month.toString().padLeft(2, '0')}-${wed.day.toString().padLeft(2, '0')}';
      final exam = ScheduleExamFusion.buildCustomExam(
        courseName: '高等数学',
        examDate: wedIso,
        startClock: '14:00',
        endClock: '16:00',
        location: '3-201',
      );
      expect(ScheduleExamFusion.examSlotInWeek(exam, week12Start), (2, 5, 6));
      expect(
        ScheduleExamFusion.examSlotInWeek(exam, SemesterConfig.weekStart(13)),
        isNull,
      );
    });
  });

  group('ScheduleTextParser', () {
    test('parses single exam notification from clipboard', () {
      const raw = '【期末考试通知】《高等数学》 12月15日 14:00-16:00 地点：广兰 3-201 座位号：18 闭卷';
      final drafts = ScheduleTextParser.parse(
        raw,
        referenceDate: DateTime(2026, 11, 28),
      );
      expect(drafts.length, 1);
      final d = drafts.first;
      expect(d.kind, ParsedItemKind.exam);
      expect(d.title, '高等数学');
      expect(d.examDate, '2026-12-15');
      expect(d.startClock, '14:00');
      expect(d.endClock, '16:00');
      expect(d.campus, '广兰校园');
      expect(d.room, '3-201');
      expect(d.seatNo, '18');
      expect(d.note, contains('闭卷'));
    });

    test('rolls january exam date into next year when received in autumn', () {
      const raw = '1月8日 上午9点-11点 大学物理期末考试 广兰 阶18';
      final drafts = ScheduleTextParser.parse(
        raw,
        referenceDate: DateTime(2026, 11, 28),
      );
      expect(drafts.length, 1);
      final d = drafts.first;
      expect(d.kind, ParsedItemKind.exam);
      expect(d.examDate, '2027-01-08');
      expect(d.startClock, '09:00');
      expect(d.endClock, '11:00');
      expect(d.room, '阶18');
    });

    test('parses makeup course notice accurately', () {
      const raw = '【补课通知】《大地测量学基础》 第13-16周 周五 第3-4节 广兰 阶18 王老师';
      final drafts = ScheduleTextParser.parse(
        raw,
        referenceDate: DateTime(2026, 11, 28),
      );
      expect(drafts.length, 1);
      final d = drafts.first;
      expect(d.kind, ParsedItemKind.course);
      expect(d.title, '大地测量学基础');
      expect(d.dayOfWeek, 4);
      expect(d.startPeriod, 3);
      expect(d.endPeriod, 4);
      expect(d.weeksSpec, '13-16周');
      expect(d.campus, '广兰校园');
      expect(d.room, '阶18');
      expect(d.teacher, '王老师');
    });

    test('splits multi-line OCR exam table and skips header', () {
      const ocr = '''
课程名称 考试时间 考场
数据结构 2026-12-20 08:30-10:30 教3-105
线性代数 2026-12-22 14:00-16:00 3-201
大学英语 2026-12-24 19:00-21:00 实验楼302
''';
      final drafts = ScheduleTextParser.parse(
        ocr,
        referenceDate: DateTime(2026, 11, 28),
      );
      expect(drafts.length, 3);
      expect(drafts[0].title, '数据结构');
      expect(drafts[0].examDate, '2026-12-20');
      expect(drafts[0].room, '教3-105');
      expect(drafts[1].title, '线性代数');
      expect(drafts[2].title, '大学英语');
    });

    test('resolves relative weekday and oral afternoon clock', () {
      const raw = '下周三下午2点-4点 《概率论与数理统计》随堂测验 教3-105';
      final drafts = ScheduleTextParser.parse(
        raw,
        referenceDate: DateTime(2026, 11, 28),
      );
      expect(drafts.length, 1);
      final d = drafts.first;
      expect(d.kind, ParsedItemKind.exam);
      expect(d.title, '概率论与数理统计');
      expect(d.examDate, '2026-12-02');
      expect(d.startClock, '14:00');
      expect(d.endClock, '16:00');
    });

    test('parses wrapped ECUT exam table OCR screenshot accurately', () {
      const realOcr = '''
工程力学 | 3教-阶|14周周四7-8节(12月10日
24205012, 2420502 69 16:00-17:40)
10
3教-阶|14周周四7-8节(12月10日
2420503, 2420504, 2425093 工程力学 82
15 16:00-17:40)
工程力学 |3教-阶|14周周四7-8节(12月10日
3 2425901, 2425902 75
16 16:00-17:40)
''';
      final drafts = ScheduleTextParser.parse(
        realOcr,
        referenceDate: DateTime(2026, 11, 28),
        defaultKindHint: ParsedItemKind.course,
      );
      expect(drafts.length, 3);

      expect(drafts[0].kind, ParsedItemKind.exam);
      expect(drafts[0].title, '工程力学');
      expect(drafts[0].examDate, '2026-12-10');
      expect(drafts[0].startClock, '16:00');
      expect(drafts[0].endClock, '17:40');
      expect(drafts[0].room, '3教-阶10');
      expect(drafts[0].note, contains('24205012'));

      expect(drafts[1].kind, ParsedItemKind.exam);
      expect(drafts[1].title, '工程力学');
      expect(drafts[1].room, '3教-阶15');
      expect(drafts[1].note, contains('2420503'));

      expect(drafts[2].kind, ParsedItemKind.exam);
      expect(drafts[2].title, '工程力学');
      expect(drafts[2].room, '3教-阶16');
      expect(drafts[2].note, contains('2425901'));
    });

    test('handles katakana dash periods and avoids date-day false week match', () {
      const realOcr = '''
2420501Z |3-602、三教地理信息系统原|4十天早基 李大军、吴静、王建
2026/12/2 周三3ー4节 13 |阶16 理(B) |强、刘向鋼 128
2420502-4
''';
      final drafts = ScheduleTextParser.parse(
        realOcr,
        referenceDate: DateTime(2026, 11, 28),
        defaultKindHint: ParsedItemKind.course,
      );
      expect(drafts.length, 2);

      final d1 = drafts[0];
      expect(d1.kind, ParsedItemKind.exam);
      expect(d1.title, '地理信息系统原');
      expect(d1.examDate, '2026-12-02');
      expect(d1.weeksSpec, '第13周');
      expect(d1.dayOfWeek, 2);
      expect(d1.startPeriod, 3);
      expect(d1.endPeriod, 4);
      expect(d1.startClock, '10:00');
      expect(d1.endClock, '11:40');
      expect(d1.room, '3-602');
      expect(d1.note, '班级 2420501Z');

      final d2 = drafts[1];
      expect(d2.kind, ParsedItemKind.exam);
      expect(d2.title, '地理信息系统原');
      expect(d2.examDate, '2026-12-02');
      expect(d2.room, '阶16');
      expect(d2.note, '班级 2420502-4');
    });

    test('does not confuse class code suffix before weekday with week range', () {
      const realOcr = '''
2026/12/9 24205017-2 周三3-4节 14 3-622 数字图像处理
2026/12/9 2420503-4 周三3-4节 14 3-623 数字图像处理
''';
      final drafts = ScheduleTextParser.parse(
        realOcr,
        referenceDate: DateTime(2026, 11, 28),
        defaultKindHint: ParsedItemKind.course,
      );
      expect(drafts.length, 2);

      expect(drafts[0].kind, ParsedItemKind.exam);
      expect(drafts[0].title, '数字图像处理');
      expect(drafts[0].examDate, '2026-12-09');
      expect(drafts[0].weeksSpec, '第14周');
      expect(drafts[0].startClock, '10:00');
      expect(drafts[0].endClock, '11:40');
      expect(drafts[0].room, '3-622');
      expect(drafts[0].note, '班级 24205017-2');

      expect(drafts[1].kind, ParsedItemKind.exam);
      expect(drafts[1].title, '数字图像处理');
      expect(drafts[1].examDate, '2026-12-09');
      expect(drafts[1].weeksSpec, '第14周');
      expect(drafts[1].room, '3-623');
      expect(drafts[1].note, '班级 2420503-4');
    });
  });

  group('ScheduleAdjustments', () {
    test('resolves holiday swap and rest days accurately', () {
      final monCourse = ScheduleExamFusion.buildCustomCourse(
        name: '高等数学',
        dayOfWeek: 0,
        startPeriod: 1,
        endPeriod: 2,
        weeksSpec: '1-16周(单)',
        room: '3-201',
      );
      final satCourse = ScheduleExamFusion.buildCustomCourse(
        name: '周末选修',
        dayOfWeek: 5,
        startPeriod: 5,
        endPeriod: 6,
        weeksSpec: '1-16周',
        room: '阶10',
      );
      const adjustments = {
        '2026-10-05': ScheduleDayAdjustment(
          targetDate: '2026-10-05',
          type: ScheduleAdjustmentType.rest,
          label: '国庆放假',
        ),
        '2026-10-10': ScheduleDayAdjustment(
          targetDate: '2026-10-10',
          type: ScheduleAdjustmentType.swap,
          sourceDate: '2026-10-05',
          label: '国庆调休',
        ),
      };

      final oct5 = ScheduleAdjustments.resolveDayCourses(
        [monCourse, satCourse],
        DateTime(2026, 10, 5),
        adjustments,
      );
      expect(oct5, isEmpty);

      final oct10 = ScheduleAdjustments.resolveDayCourses(
        [monCourse, satCourse],
        DateTime(2026, 10, 10),
        adjustments,
      );
      expect(oct10.length, 1);
      expect(oct10.first.course.name, '高等数学');
      expect(oct10.first.displayDayOfWeek, 5);
      expect(oct10.first.adjustedTag, '调休');
      expect(oct10.first.adjustedDetail, contains('10/5'));
    });

    test('parses chinese university holiday rest and swap notice', () {
      const notice =
          '【国庆放假通知】10月1日至10月7日放假调休，共7天。9月27日（周日）上10月2日（周五）的课，10月10日（周六）上10月5日（周一）的课。';
      final parsed = ScheduleAdjustments.parseHolidayNotice(
        notice,
        referenceDate: DateTime(2026, 9, 20),
      );
      expect(parsed.rests.length, 7);
      expect(parsed.rests.first.targetDate, '2026-10-01');
      expect(parsed.rests.last.targetDate, '2026-10-07');
      expect(parsed.swaps.length, 2);
      expect(parsed.swaps[0].targetDate, '2026-09-27');
      expect(parsed.swaps[0].sourceDate, '2026-10-02');
      expect(parsed.swaps[1].targetDate, '2026-10-10');
      expect(parsed.swaps[1].sourceDate, '2026-10-05');
    });

    test('parses 2026 mid-autumn and national day state council notice with per-holiday labels and inferred swaps', () {
      const notice = '''
2026年放假安排：
中秋节：9月25日（周五）至27日（周日）放假，共3天。
国庆节：10月1日（周四）至7日（周三）放假调休，共7天。9月20日（周日）、10月10日（周六）上班。
''';
      final parsed = ScheduleAdjustments.parseHolidayNotice(
        notice,
        referenceDate: DateTime(2026, 11, 28),
      );
      expect(parsed.rests.length, 10);
      expect(
        parsed.rests.firstWhere((r) => r.targetDate == '2026-09-25').label,
        '中秋放假',
      );
      expect(
        parsed.rests.firstWhere((r) => r.targetDate == '2026-10-01').label,
        '国庆放假',
      );
      expect(parsed.swaps.length, 2);
      expect(parsed.swaps[0].targetDate, '2026-09-20');
      expect(parsed.swaps[0].sourceDate, '2026-10-05');
      expect(parsed.swaps[0].label, contains('国庆调休'));
      expect(parsed.swaps[1].targetDate, '2026-10-10');
      expect(parsed.swaps[1].sourceDate, '2026-10-06');
    });

    test('parses reversed, paired, and weekday-only university holiday swap notices', () {
      const reversed =
          '国庆节：10月1日至7日放假。将10月5日（周一）的课调至9月20日（周日）上，原10月6日（周二）课程调整至10月10日（周六）。';
      final p1 = ScheduleAdjustments.parseHolidayNotice(
        reversed,
        referenceDate: DateTime(2026, 9, 15),
      );
      expect(p1.swaps.length, 2);
      expect(p1.swaps[0].targetDate, '2026-09-20');
      expect(p1.swaps[0].sourceDate, '2026-10-05');
      expect(p1.swaps[1].targetDate, '2026-10-10');
      expect(p1.swaps[1].sourceDate, '2026-10-06');

      const pairedWd =
          '国庆节：10月1日至7日放假调休。9月20日（周日）、10月10日（周六）上班，分别补上星期一、星期二的课。';
      final p2 = ScheduleAdjustments.parseHolidayNotice(
        pairedWd,
        referenceDate: DateTime(2026, 9, 15),
      );
      expect(p2.swaps.length, 2);
      expect(p2.swaps[0].targetDate, '2026-09-20');
      expect(p2.swaps[0].sourceDate, '2026-10-05');
      expect(p2.swaps[1].targetDate, '2026-10-10');
      expect(p2.swaps[1].sourceDate, '2026-10-06');
    });
  });
}
