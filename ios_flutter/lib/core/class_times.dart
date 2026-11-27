/// 东华理工大学南昌校区（广兰 / 枫林）作息时间（1-10 节）。
class ClassPeriodTime {
  final String start;
  final String end;

  const ClassPeriodTime(this.start, this.end);
}

class ClassTimes {
  static const List<ClassPeriodTime> periods = [
    ClassPeriodTime('08:00', '08:45'),
    ClassPeriodTime('08:55', '09:40'),
    ClassPeriodTime('10:00', '10:45'),
    ClassPeriodTime('10:55', '11:40'),
    ClassPeriodTime('14:00', '14:45'),
    ClassPeriodTime('14:55', '15:40'),
    ClassPeriodTime('16:00', '16:45'),
    ClassPeriodTime('16:55', '17:40'),
    ClassPeriodTime('19:00', '19:45'),
    ClassPeriodTime('19:55', '20:40'),
  ];

  static const int regularPeriods = 10;

  static String? startTime(int period) {
    if (period < 1 || period > periods.length) return null;
    return periods[period - 1].start;
  }

  static String? endTime(int period) {
    if (period < 1 || period > periods.length) return null;
    return periods[period - 1].end;
  }
}
