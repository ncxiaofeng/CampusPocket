class CookieRecord {
  final String domain;
  final String name;
  final String value;
  final String path;
  final int expiresAt;
  final bool secure;
  final int updatedAt;

  const CookieRecord({
    required this.domain,
    required this.name,
    required this.value,
    this.path = '/',
    this.expiresAt = 0,
    this.secure = false,
    this.updatedAt = 0,
  });

  Map<String, dynamic> toJson() => {
        'domain': domain,
        'name': name,
        'value': value,
        'path': path,
        'expiresAt': expiresAt,
        'secure': secure,
        'updatedAt': updatedAt,
      };

  factory CookieRecord.fromJson(Map<String, dynamic> json) => CookieRecord(
        domain: (json['domain'] ?? '').toString(),
        name: (json['name'] ?? '').toString(),
        value: (json['value'] ?? '').toString(),
        path: (json['path'] ?? '/').toString().isEmpty
            ? '/'
            : (json['path'] ?? '/').toString(),
        expiresAt: (json['expiresAt'] is int)
            ? json['expiresAt'] as int
            : int.tryParse('${json['expiresAt']}') ?? 0,
        secure: json['secure'] == true,
        updatedAt: (json['updatedAt'] is int)
            ? json['updatedAt'] as int
            : int.tryParse('${json['updatedAt']}') ?? 0,
      );
}

class AuthCredential {
  final String systemId;
  final String domain;
  final String cookie;
  final int updatedAt;

  const AuthCredential({
    required this.systemId,
    required this.domain,
    required this.cookie,
    required this.updatedAt,
  });

  Map<String, dynamic> toJson() => {
        'systemId': systemId,
        'domain': domain,
        'cookie': cookie,
        'updatedAt': updatedAt,
      };

  factory AuthCredential.fromJson(Map<String, dynamic> json) => AuthCredential(
        systemId: (json['systemId'] ?? '').toString(),
        domain: (json['domain'] ?? '').toString(),
        cookie: (json['cookie'] ?? '').toString(),
        updatedAt: (json['updatedAt'] is int)
            ? json['updatedAt'] as int
            : int.tryParse('${json['updatedAt']}') ?? 0,
      );
}

class JwCourse {
  final String name;
  final String teacher;
  final String room;
  final String campus;
  final int dayOfWeek; // 0 = 周一 .. 6 = 周日
  final int startSlot;
  final int slotSpan;
  final int startPeriod;
  final int endPeriod;
  final Set<int> weeks;
  final String weeksSpec;
  final String courseCode;

  const JwCourse({
    required this.name,
    required this.teacher,
    required this.room,
    required this.campus,
    required this.dayOfWeek,
    required this.startSlot,
    required this.slotSpan,
    required this.startPeriod,
    required this.endPeriod,
    required this.weeks,
    required this.weeksSpec,
    required this.courseCode,
  });

  Map<String, dynamic> toJson() => {
        'name': name,
        'teacher': teacher,
        'room': room,
        'campus': campus,
        'dayOfWeek': dayOfWeek,
        'startSlot': startSlot,
        'slotSpan': slotSpan,
        'startPeriod': startPeriod,
        'endPeriod': endPeriod,
        'weeks': weeks.toList()..sort(),
        'weeksSpec': weeksSpec,
        'courseCode': courseCode,
      };

  factory JwCourse.fromJson(Map<String, dynamic> json) => JwCourse(
        name: (json['name'] ?? '').toString(),
        teacher: (json['teacher'] ?? '').toString(),
        room: (json['room'] ?? '').toString(),
        campus: (json['campus'] ?? '').toString(),
        dayOfWeek: (json['dayOfWeek'] as num?)?.toInt() ?? 0,
        startSlot: (json['startSlot'] as num?)?.toInt() ?? 0,
        slotSpan: (json['slotSpan'] as num?)?.toInt() ?? 1,
        startPeriod: (json['startPeriod'] as num?)?.toInt() ?? 1,
        endPeriod: (json['endPeriod'] as num?)?.toInt() ?? 2,
        weeks: ((json['weeks'] as List?) ?? const [])
            .map((e) => (e as num).toInt())
            .toSet(),
        weeksSpec: (json['weeksSpec'] ?? '').toString(),
        courseCode: (json['courseCode'] ?? '').toString(),
      );
}

class JwScheduleResult {
  final String xnm;
  final String xqm;
  final List<JwCourse> courses;

  const JwScheduleResult({
    required this.xnm,
    required this.xqm,
    required this.courses,
  });
}

class JwGrade {
  final String key;
  final String courseName;
  final String score;
  final double credit;
  final double gpa;
  final String term;
  final String courseCode;
  final String nature;
  final String examType;
  final bool passed;

  const JwGrade({
    required this.key,
    required this.courseName,
    required this.score,
    required this.credit,
    required this.gpa,
    required this.term,
    required this.courseCode,
    required this.nature,
    required this.examType,
    required this.passed,
  });

  Map<String, dynamic> toJson() => {
        'key': key,
        'courseName': courseName,
        'score': score,
        'credit': credit,
        'gpa': gpa,
        'term': term,
        'courseCode': courseCode,
        'nature': nature,
        'examType': examType,
        'passed': passed,
      };

  factory JwGrade.fromJson(Map<String, dynamic> json) => JwGrade(
        key: (json['key'] ?? '').toString(),
        courseName: (json['courseName'] ?? '').toString(),
        score: (json['score'] ?? '').toString(),
        credit: (json['credit'] as num?)?.toDouble() ?? 0.0,
        gpa: (json['gpa'] as num?)?.toDouble() ?? 0.0,
        term: (json['term'] ?? '').toString(),
        courseCode: (json['courseCode'] ?? '').toString(),
        nature: (json['nature'] ?? '').toString(),
        examType: (json['examType'] ?? '').toString(),
        passed: json['passed'] != false,
      );
}

class JwExam {
  final String key;
  final String courseName;
  final String courseCode;
  final String examTime;
  final String examDate;
  final String startClock;
  final String endClock;
  final String examName;
  final String examType;
  final String location;
  final String campus;
  final String seatNo;
  final String ticketNo;
  final String note;
  final String term;

  const JwExam({
    required this.key,
    required this.courseName,
    required this.courseCode,
    required this.examTime,
    required this.examDate,
    required this.startClock,
    required this.endClock,
    required this.examName,
    required this.examType,
    required this.location,
    required this.campus,
    required this.seatNo,
    required this.ticketNo,
    required this.note,
    required this.term,
  });

  Map<String, dynamic> toJson() => {
        'key': key,
        'courseName': courseName,
        'courseCode': courseCode,
        'examTime': examTime,
        'examDate': examDate,
        'startClock': startClock,
        'endClock': endClock,
        'examName': examName,
        'examType': examType,
        'location': location,
        'campus': campus,
        'seatNo': seatNo,
        'ticketNo': ticketNo,
        'note': note,
        'term': term,
      };

  factory JwExam.fromJson(Map<String, dynamic> json) => JwExam(
        key: (json['key'] ?? '').toString(),
        courseName: (json['courseName'] ?? '').toString(),
        courseCode: (json['courseCode'] ?? '').toString(),
        examTime: (json['examTime'] ?? '').toString(),
        examDate: (json['examDate'] ?? '').toString(),
        startClock: (json['startClock'] ?? '').toString(),
        endClock: (json['endClock'] ?? '').toString(),
        examName: (json['examName'] ?? '').toString(),
        examType: (json['examType'] ?? '').toString(),
        location: (json['location'] ?? '').toString(),
        campus: (json['campus'] ?? '').toString(),
        seatNo: (json['seatNo'] ?? '').toString(),
        ticketNo: (json['ticketNo'] ?? '').toString(),
        note: (json['note'] ?? '').toString(),
        term: (json['term'] ?? '').toString(),
      );
}

class JwCampus {
  final String id;
  final String name;

  const JwCampus({required this.id, required this.name});
}

class JwClassroomBuilding {
  final String id;
  final String name;

  const JwClassroomBuilding({required this.id, required this.name});
}

class JwClassroomFilters {
  final String campusId;
  final String campusName;
  final List<JwCampus> campuses;
  final List<JwClassroomBuilding> buildings;
  final String xnm;
  final String xqm;

  const JwClassroomFilters({
    required this.campusId,
    required this.campusName,
    required this.campuses,
    required this.buildings,
    required this.xnm,
    required this.xqm,
  });

  JwClassroomFilters copyWith({
    String? campusId,
    String? campusName,
    List<JwClassroomBuilding>? buildings,
  }) =>
      JwClassroomFilters(
        campusId: campusId ?? this.campusId,
        campusName: campusName ?? this.campusName,
        campuses: campuses,
        buildings: buildings ?? this.buildings,
        xnm: xnm,
        xqm: xqm,
      );
}

class JwClassroom {
  final String id;
  final String name;
  final String building;
  final String campus;
  final String type;
  final int seats;
  final int examSeats;
  final bool borrowable;

  const JwClassroom({
    required this.id,
    required this.name,
    required this.building,
    required this.campus,
    required this.type,
    required this.seats,
    required this.examSeats,
    required this.borrowable,
  });
}

class JwNotice {
  final String id;
  final String title;
  final String publisher;
  final String publishedAt;
  final String contentHtml;

  const JwNotice({
    required this.id,
    required this.title,
    required this.publisher,
    required this.publishedAt,
    required this.contentHtml,
  });
}

class CampusCardOverview {
  final double balance;
  final String cardState;
  final String cardNumber;
  final double monthSpend;
  final double monthRecharge;
  final int monthSpendCount;
  final int monthRechargeCount;

  const CampusCardOverview({
    required this.balance,
    required this.cardState,
    required this.cardNumber,
    required this.monthSpend,
    required this.monthRecharge,
    required this.monthSpendCount,
    required this.monthRechargeCount,
  });
}

class CampusCardBill {
  final String time;
  final String location;
  final double amount;
  final bool isRecharge;
  final String machine;
  final String typeCode;

  const CampusCardBill({
    required this.time,
    required this.location,
    required this.amount,
    required this.isRecharge,
    required this.machine,
    required this.typeCode,
  });
}

class CampusCardBillPage {
  final List<CampusCardBill> items;
  final int total;

  const CampusCardBillPage({required this.items, required this.total});
}

class LibrarySeatArea {
  final String id;
  final String name;
  final int free;
  final int total;
  final int sort;
  final List<LibrarySeatArea> children;

  const LibrarySeatArea({
    required this.id,
    required this.name,
    required this.free,
    required this.total,
    required this.sort,
    required this.children,
  });
}

class LibrarySeatSnapshot {
  final List<String> dates;
  final List<LibrarySeatArea> campuses;

  const LibrarySeatSnapshot({required this.dates, required this.campuses});
}

class WeatherLocation {
  final String id;
  final String name;
  final double latitude;
  final double longitude;

  const WeatherLocation({
    required this.id,
    required this.name,
    required this.latitude,
    required this.longitude,
  });
}

class WeatherNow {
  final DateTime time;
  final double temperature;
  final double apparentTemperature;
  final int humidity;
  final double windSpeed;
  final double precipitation;
  final int weatherCode;

  const WeatherNow({
    required this.time,
    required this.temperature,
    required this.apparentTemperature,
    required this.humidity,
    required this.windSpeed,
    required this.precipitation,
    required this.weatherCode,
  });
}

class WeatherHourly {
  final DateTime time;
  final double temperature;
  final int precipitationProbability;
  final int weatherCode;

  const WeatherHourly({
    required this.time,
    required this.temperature,
    required this.precipitationProbability,
    required this.weatherCode,
  });
}

class WeatherDaily {
  final DateTime date;
  final int weatherCode;
  final double maxTemperature;
  final double minTemperature;
  final int precipitationProbabilityMax;

  const WeatherDaily({
    required this.date,
    required this.weatherCode,
    required this.maxTemperature,
    required this.minTemperature,
    required this.precipitationProbabilityMax,
  });
}

class WeatherSnapshot {
  final WeatherLocation location;
  final WeatherNow? now;
  final List<WeatherHourly> hourly;
  final List<WeatherDaily> daily;
  final int fetchedAt;

  const WeatherSnapshot({
    required this.location,
    required this.now,
    required this.hourly,
    required this.daily,
    required this.fetchedAt,
  });
}

class AppUpdateInfo {
  final String version;
  final String tagName;
  final String releaseNotes;
  final String releasePageUrl;
  final String? ipaDownloadUrl;

  const AppUpdateInfo({
    required this.version,
    required this.tagName,
    required this.releaseNotes,
    required this.releasePageUrl,
    required this.ipaDownloadUrl,
  });
}
