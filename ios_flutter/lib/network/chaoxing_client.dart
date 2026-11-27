import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';

import '../core/campus_endpoints.dart';
import '../core/exceptions.dart';
import '../data/auth_repository.dart';
import 'campus_http_client.dart';

class ChaoxingTerm {
  static const int current = 0;
  static const int previous = 1;
  static const int next = 2;
  static const int other = 3;
  static const int unknown = 4;

  static int currentStartYear([DateTime? today]) {
    final now = today ?? DateTime.now();
    return now.month >= 8 ? now.year : now.year - 1;
  }

  static int rank(String text, [DateTime? today]) {
    if (text.trim().isEmpty) return unknown;
    final target = currentStartYear(today);
    final starts = <int>[];
    for (final m
        in RegExp(r'(20\d{2})\s*[-–—~至]\s*(20\d{2})').allMatches(text)) {
      final s = int.tryParse(m.group(1)!);
      final e = int.tryParse(m.group(2)!);
      if (s != null && e != null && e >= s && e <= s + 2) starts.add(s);
    }
    if (starts.isEmpty) return unknown;
    starts.sort((a, b) => (a - target).abs().compareTo((b - target).abs()));
    final best = starts.first;
    if (best == target) return current;
    if (best == target - 1) return previous;
    if (best == target + 1) return next;
    return other;
  }

  static String label(int r) => switch (r) {
        current => '本学期',
        previous || other => '往期',
        next => '下学期',
        _ => '',
      };
}

class ChaoxingCourse {
  final String courseId;
  final String classId;
  final String name;
  final String className;
  final String teacher;
  final int termRank;

  const ChaoxingCourse({
    required this.courseId,
    required this.classId,
    required this.name,
    required this.className,
    required this.teacher,
    required this.termRank,
  });
}

class ChaoxingTask {
  final String type; // work | exam
  final String title;
  final String courseName;
  final String courseId;
  final String classId;
  final String url;
  final String statusText;
  final String detail;
  final int status;
  final int termRank;

  const ChaoxingTask({
    required this.type,
    required this.title,
    required this.courseName,
    required this.courseId,
    required this.classId,
    required this.url,
    required this.statusText,
    required this.detail,
    required this.status,
    required this.termRank,
  });

  bool get needsAction {
    final s = statusText;
    if (s.contains('待批阅') ||
        s.contains('已完成') ||
        s.contains('已提交') ||
        s.contains('已过期') ||
        s.contains('已结束') ||
        s.contains('已评分')) {
      return false;
    }
    return s.contains('待完成') ||
        s.contains('未完成') ||
        s.contains('未提交') ||
        s.contains('待提交') ||
        s.contains('做作业') ||
        s.contains('待考试') ||
        s.contains('待参加') ||
        s.contains('进行中') ||
        status == 0;
  }
}

class ChaoxingSign {
  final String activeId;
  final String title;
  final String courseName;
  final String courseId;
  final String classId;
  final int startTimeMillis;
  final int status;
  final int activeType;
  final String url;
  final int termRank;

  const ChaoxingSign({
    required this.activeId,
    required this.title,
    required this.courseName,
    required this.courseId,
    required this.classId,
    required this.startTimeMillis,
    required this.status,
    required this.activeType,
    required this.url,
    required this.termRank,
  });

  bool get isOngoing => status == 1;
}

class ChaoxingSnapshot {
  final List<ChaoxingCourse> courses;
  final List<ChaoxingTask> works;
  final List<ChaoxingTask> exams;
  final List<ChaoxingSign> signs;
  final int fetchedAt;

  const ChaoxingSnapshot({
    required this.courses,
    required this.works,
    required this.exams,
    required this.signs,
    required this.fetchedAt,
  });

  int get pendingWorkCount => works.where((w) => w.needsAction).length;
  int get pendingExamCount => exams.where((e) => e.needsAction).length;
}

class ChaoxingUserAgent {
  static const String _salt = r'ipL$TkeiEmfy1gTXb2XHrdLN0a@7c^vu';

  static String build(String uniqueId) {
    const httpAgent =
        'Dalvik/2.1.0 (Linux; U; Android 14; Pixel 6 Build/UQ1A.240205.004)';
    final suffix =
        ' (device:Pixel 6) Language/zh_CN com.chaoxing.mobile/ChaoXingStudy'
        '_3_7.0.2_android_phone_10993_264 (@Kalimdor)_$uniqueId';
    final sig = md5.convert(utf8.encode('$suffix$_salt')).toString();
    return '$httpAgent (schild:$sig)$suffix';
  }
}

class ChaoxingHtmlParser {
  static const List<String> _statusHints = [
    '待完成', '未完成', '未提交', '待提交', '做作业', '去完成',
    '未开始', '进行中', '待参加', '待考试',
    '已完成', '已提交', '待批阅', '已批阅', '已过期', '已结束', '已评分',
  ];

  static List<ChaoxingTask> parseWorkList(String html, ChaoxingCourse course) {
    final dtRows = _parseWorkDt(html, course);
    if (dtRows.isNotEmpty) return dtRows;
    return _parseWorkLi(html, course);
  }

  static List<ChaoxingTask> _parseWorkDt(String html, ChaoxingCourse course) {
    final result = <ChaoxingTask>[];
    final re = RegExp(r'<dt\b([^>]*)>([\s\S]*?)</dt>', caseSensitive: false);
    for (final m in re.allMatches(html)) {
      final attrs = m.group(1)!;
      final inner = m.group(2)!;
      final data = _attr(attrs, 'data');
      if (data == null || data.isEmpty) continue;
      final title = _cleanHtml(_innerTag(inner, 'h3'));
      result.add(
        ChaoxingTask(
          type: 'work',
          title: title.isEmpty ? '未命名作业' : title,
          courseName: course.name,
          courseId: course.courseId,
          classId: course.classId,
          url: _absoluteUrl(data),
          statusText: _workStatus(inner),
          detail: _cleanHtml(_innerTag(inner, 'p')),
          status: int.tryParse(_attr(attrs, 'status') ?? '') ?? 0,
          termRank: course.termRank,
        ),
      );
    }
    return result;
  }

  static List<ChaoxingTask> _parseWorkLi(String html, ChaoxingCourse course) {
    final result = <ChaoxingTask>[];
    final re = RegExp(r'<li\b([^>]*)>([\s\S]*?)</li>', caseSensitive: false);
    for (final m in re.allMatches(html)) {
      final attrs = m.group(1)!;
      final inner = m.group(2)!;
      final data = _attr(attrs, 'data');
      if (data == null || data.isEmpty) continue;
      final title = _cleanHtml(_innerTag(inner, 'p'));
      result.add(
        ChaoxingTask(
          type: 'work',
          title: title.isEmpty ? '未命名作业' : title,
          courseName: course.name,
          courseId: course.courseId,
          classId: course.classId,
          url: _absoluteUrl(data),
          statusText: _workStatus(inner),
          detail: _cleanHtml(_innerTag(inner, 'dd')),
          status: int.tryParse(_attr(attrs, 'status') ?? '') ?? 0,
          termRank: course.termRank,
        ),
      );
    }
    return result;
  }

  static List<ChaoxingTask> parseExamList(String html, ChaoxingCourse course) {
    final result = <ChaoxingTask>[];
    final rowRe = RegExp(
      r'<tr\b([^>]*\bdata="[^"]*"[^>]*)>([\s\S]*?)</tr>',
      caseSensitive: false,
    );
    final tdRe = RegExp(r'<td\b[^>]*>([\s\S]*?)</td>', caseSensitive: false);
    for (final m in rowRe.allMatches(html)) {
      final attrs = m.group(1)!;
      final inner = m.group(2)!;
      final data = _attr(attrs, 'data');
      if (data == null || data.isEmpty) continue;
      final cells = tdRe.allMatches(inner).map((c) => _cleanHtml(c.group(1)!)).toList();
      final titleAttr = _cleanHtml(_attr(attrs, 'title') ?? '');
      final title = titleAttr.isNotEmpty
          ? titleAttr
          : (cells.isNotEmpty && cells.first.isNotEmpty ? cells.first : '未命名考试');
      final statusText = cells
              .where((c) => _statusHints.any(c.contains))
              .firstOrNull ??
          (cells.length > 2 ? cells[2] : '');
      final detail = cells.length > 1 ? cells[1] : '';
      result.add(
        ChaoxingTask(
          type: 'exam',
          title: title,
          courseName: course.name,
          courseId: course.courseId,
          classId: course.classId,
          url: _absoluteUrl(data),
          statusText: statusText,
          detail: detail,
          status: int.tryParse(_attr(attrs, 'status') ?? '') ?? 0,
          termRank: course.termRank,
        ),
      );
    }
    return result;
  }

  static String _workStatus(String inner) {
    final spans = RegExp(r'<span\b[^>]*>([\s\S]*?)</span>', caseSensitive: false)
        .allMatches(inner)
        .map((m) => _cleanHtml(m.group(1)!))
        .where((s) => s.isNotEmpty)
        .toList();
    final matchedSpan =
        spans.where((s) => _statusHints.any(s.contains)).firstOrNull;
    if (matchedSpan != null) return matchedSpan;
    final bold = _cleanHtml(_innerTag(inner, 'b'));
    if (bold.isNotEmpty) return bold;
    return spans.firstOrNull ?? '';
  }

  static String _absoluteUrl(String path) {
    if (path.startsWith('http://') || path.startsWith('https://')) return path;
    if (path.startsWith('/')) return CampusEndpoints.chaoxingMoocApi + path;
    return '${CampusEndpoints.chaoxingMoocApi}/$path';
  }

  static String? _attr(String attrs, String name) {
    final m = RegExp('$name\\s*=\\s*"([^"]*)"', caseSensitive: false)
        .firstMatch(attrs);
    return m?.group(1)?.replaceAll('&amp;', '&');
  }

  static String _innerTag(String html, String tag) =>
      RegExp('<$tag\\b[^>]*>([\\s\\S]*?)</$tag>', caseSensitive: false)
          .firstMatch(html)
          ?.group(1) ??
      '';

  static String _cleanHtml(String s) => s
      .replaceAll('&amp;', '&')
      .replaceAll('&lt;', '<')
      .replaceAll('&gt;', '>')
      .replaceAll('&quot;', '"')
      .replaceAll('&#39;', "'")
      .replaceAll('&nbsp;', ' ')
      .replaceAll(RegExp(r'<[^>]+>'), '')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
}

/// 超星学习通只读客户端（绝不代签、绝不代交作业）。
class ChaoxingClient {
  ChaoxingClient({
    required CampusHttpClient httpClient,
    required AuthRepository authRepository,
    required String uniqueId,
  })  : _httpClient = httpClient,
        _authRepository = authRepository,
        _uniqueId = uniqueId;

  final CampusHttpClient _httpClient;
  final AuthRepository _authRepository;
  final String _uniqueId;

  Future<bool> hasSession() async => (await _uidOrNull()) != null;

  Future<ChaoxingSnapshot> fetchSnapshot() async {
    final courses = await listCourses();
    final works = <ChaoxingTask>[];
    final exams = <ChaoxingTask>[];

    // 只并发拉取前 20 门（优先本学期）防止过多请求卡顿
    final sorted = [...courses]
      ..sort((a, b) => a.termRank.compareTo(b.termRank));
    final toFetch = sorted.take(20).toList();
    for (var i = 0; i < toFetch.length; i += 4) {
      final batch = toFetch.skip(i).take(4);
      final results = await Future.wait(
        batch.map((c) async {
          final w = await _listWorksSafe(c);
          final e = await _listExamsSafe(c);
          return (w, e);
        }),
      );
      for (final (w, e) in results) {
        works.addAll(w);
        exams.addAll(e);
      }
    }

    works.sort(_compareTasks);
    exams.sort(_compareTasks);

    final signs = await _listActiveSignsSafe(sorted);
    return ChaoxingSnapshot(
      courses: sorted,
      works: works,
      exams: exams,
      signs: signs.where((s) => s.isOngoing || s.status == 0).toList(),
      fetchedAt: DateTime.now().millisecondsSinceEpoch,
    );
  }

  static int _compareTasks(ChaoxingTask a, ChaoxingTask b) {
    final t = a.termRank.compareTo(b.termRank);
    if (t != 0) return t;
    if (a.needsAction != b.needsAction) return a.needsAction ? -1 : 1;
    return a.courseName.compareTo(b.courseName);
  }

  Future<List<ChaoxingCourse>> listCourses() async {
    const url =
        '${CampusEndpoints.chaoxingMoocApi}/mycourse/backclazzdata?view=json&getTchClazzType=1&mcode=';
    final text = await _get(url, CampusEndpoints.mobileBrowserUserAgent);
    final root = jsonDecode(text) as Map<String, dynamic>;
    if ((root['result'] as num?)?.toInt() != 1) {
      throw ChaoxingSessionExpiredException(
        '${root['msg'] ?? '学习通课程列表读取失败'}',
      );
    }
    final channels = (root['channelList'] as List?) ?? const [];
    final courses = <ChaoxingCourse>[];
    final seen = <String>{};
    for (final ch in channels) {
      if (ch is! Map<String, dynamic>) continue;
      final rawContent = ch['content'];
      Map<String, dynamic>? content;
      if (rawContent is Map<String, dynamic>) {
        content = rawContent;
      } else if (rawContent is String) {
        try {
          content = jsonDecode(rawContent) as Map<String, dynamic>;
        } catch (_) {}
      }
      if (content == null) continue;
      final courseWrap = content['course'] as Map<String, dynamic>?;
      final courseData = courseWrap?['data'] as List?;
      final classId = '${content['id'] ?? ch['key'] ?? ''}'.trim();
      if (courseData != null && classId.isNotEmpty) {
        for (final item in courseData) {
          if (item is! Map<String, dynamic>) continue;
          final cid = '${item['id'] ?? ''}'.trim();
          if (cid.isEmpty || !seen.add('$cid|$classId')) continue;
          final termText = '${jsonEncode(content)} ${jsonEncode(item)}';
          courses.add(
            ChaoxingCourse(
              courseId: cid,
              classId: classId,
              name: '${item['name'] ?? '未命名课程'}'.trim(),
              className: '${content['name'] ?? ''}'.trim(),
              teacher: '${item['teacherfactor'] ?? ''}'.trim(),
              termRank: ChaoxingTerm.rank(termText),
            ),
          );
        }
      }
    }
    return courses;
  }

  Future<List<ChaoxingTask>> _listWorksSafe(ChaoxingCourse course) async {
    try {
      final url =
          '${CampusEndpoints.chaoxingMoocApi}/phone/moocAnalysis/workList?courseId=${Uri.encodeQueryComponent(course.courseId)}&classId=${Uri.encodeQueryComponent(course.classId)}';
      final html = await _get(url, CampusEndpoints.mobileBrowserUserAgent);
      return ChaoxingHtmlParser.parseWorkList(html, course);
    } catch (_) {
      return const [];
    }
  }

  Future<List<ChaoxingTask>> _listExamsSafe(ChaoxingCourse course) async {
    try {
      final url =
          '${CampusEndpoints.chaoxingMoocApi}/phone/moocAnalysis/examList?courseId=${Uri.encodeQueryComponent(course.courseId)}&classId=${Uri.encodeQueryComponent(course.classId)}';
      final html = await _get(url, CampusEndpoints.mobileBrowserUserAgent);
      return ChaoxingHtmlParser.parseExamList(html, course);
    } catch (_) {
      return const [];
    }
  }

  Future<List<ChaoxingSign>> _listActiveSignsSafe(
    List<ChaoxingCourse> courses,
  ) async {
    final uid = await _uidOrNull();
    if (uid == null) return const [];
    final ua = ChaoxingUserAgent.build(_uniqueId);
    try {
      final url =
          '${CampusEndpoints.chaoxingMobileLearn}/ppt/activeAPI/courseactivelist?puid=${Uri.encodeQueryComponent(uid)}&status=1&page=1&pageSize=20';
      final root = jsonDecode(await _get(url, ua)) as Map<String, dynamic>;
      return _parseActiveList(root, courses);
    } catch (_) {
      return const [];
    }
  }

  static List<ChaoxingSign> _parseActiveList(
    Map<String, dynamic> root,
    List<ChaoxingCourse> courses,
  ) {
    final data = root['data'];
    List<dynamic> list = const [];
    if (root['activeList'] is List) {
      list = root['activeList'] as List;
    } else if (data is Map<String, dynamic> && data['activeList'] is List) {
      list = data['activeList'] as List;
    } else if (data is List) {
      list = data;
    }
    final byId = {for (final c in courses) c.courseId: c};
    final result = <ChaoxingSign>[];
    for (final item in list) {
      if (item is! Map<String, dynamic>) continue;
      final activeId = '${item['id'] ?? item['activeId'] ?? ''}'.trim();
      if (activeId.isEmpty) continue;
      final cid = '${item['courseId'] ?? ''}'.trim();
      final course = byId[cid];
      final activeType =
          int.tryParse('${item['activeType'] ?? item['type'] ?? 0}') ?? 0;
      final title =
          '${item['nameOne'] ?? item['name'] ?? item['title'] ?? '课堂活动'}'.trim();
      if (activeType != 2 && !title.contains('签到') && !title.contains('手势')) {
        continue;
      }
      final rawTime = int.tryParse('${item['startTime'] ?? 0}') ?? 0;
      final ms = rawTime > 0 && rawTime < 10000000000 ? rawTime * 1000 : rawTime;
      final url = '${item['url'] ?? ''}'.trim();
      result.add(
        ChaoxingSign(
          activeId: activeId,
          title: title,
          courseName: course?.name ?? '${item['nameTwo'] ?? '学习通课程'}',
          courseId: cid,
          classId: '${item['classId'] ?? course?.classId ?? ''}',
          startTimeMillis: ms,
          status: int.tryParse('${item['status'] ?? 0}') ?? 0,
          activeType: activeType,
          url: url.isNotEmpty
              ? url
              : '${CampusEndpoints.chaoxingMobileLearn}/newsign/studentSign?activeId=$activeId',
          termRank: course?.termRank ?? ChaoxingTerm.unknown,
        ),
      );
    }
    return result;
  }

  Future<String> _get(String url, String ua) async {
    final resp = await _httpClient.get(
      url,
      headers: {
        'User-Agent': ua,
        'Accept': 'application/json,text/html,application/xhtml+xml,*/*;q=0.8',
        'Accept-Language': 'zh-CN,zh;q=0.9',
      },
      followRedirects: false,
      useCookies: true,
    );
    if (resp.isRedirect) {
      throw const ChaoxingSessionExpiredException();
    }
    if (!resp.isSuccessful) {
      throw HttpException('学习通返回 HTTP ${resp.statusCode}');
    }
    if (resp.body.contains('请重新登录') ||
        resp.body.contains('passport2.chaoxing.com/login')) {
      throw const ChaoxingSessionExpiredException();
    }
    return resp.body;
  }

  Future<String?> _uidOrNull() async {
    for (final host in const [
      CampusEndpoints.chaoxingHost,
      'passport2.chaoxing.com',
      'mooc1-api.chaoxing.com',
      'mobilelearn.chaoxing.com',
    ]) {
      final rows = await _authRepository.loadCookiesForHost(host);
      for (final r in rows) {
        final k = r.name.toLowerCase();
        if ((k == '_uid' || k == 'uid') && r.value.trim().isNotEmpty) {
          return r.value.trim();
        }
      }
    }
    return null;
  }
}
