import 'dart:convert';
import 'dart:io';

import '../core/campus_endpoints.dart';
import '../core/exceptions.dart';
import '../data/models.dart';
import 'campus_http_client.dart';
import 'cas_sso_manager.dart';
import 'jw_parser.dart';

class JwBaseUrl {
  final String url;
  final bool isProxy;

  const JwBaseUrl(this.url, {required this.isProxy});
}

/// 正方教务（jwglxt）只读适配器：课表、成绩、考试、空闲教室、通知公告。
///
/// 支持校内直连（自动 CASTGC 静默换票）与校外 WebVPN 代理（`172-20-130-13.atrust.ecut.edu.cn`）
/// 双候选切换与错误回退。
class JwAdapter {
  JwAdapter({
    required CampusHttpClient httpClient,
    required Future<List<JwBaseUrl>> Function() baseUrlsProvider,
    CasSsoManager? ssoManager,
  })  : _httpClient = httpClient,
        _baseUrlsProvider = baseUrlsProvider,
        _ssoManager = ssoManager;

  static const int _bootstrapRetryCooldownMs = 30000;
  static const String _schedulePage =
      '/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N253508&layout=default';
  static const String _scheduleApi =
      '/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508';
  static const String _gradePage =
      '/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default';
  static const String _gradeApi =
      '/jwglxt/cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305005';
  static const String _examPage =
      '/jwglxt/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105&layout=default';
  static const String _examApi =
      '/jwglxt/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105';
  static const String _classroomPage =
      '/jwglxt/cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&layout=default';
  static const String _classroomApi =
      '/jwglxt/cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155';
  static const String _classroomBuildingApi = '/jwglxt/cdjy/cdjy_cxXqjc.html';
  static const int _classroomPageSize = 300;
  static const String _noticeListApi =
      '/jwglxt/xtgl/xwglMobile_cxMoreXwList.html';
  static const String _noticeDetailApi =
      '/jwglxt/xtgl/xwglMobile_cxXwDetail.html';
  static const int _noticePageSize = 10;

  final CampusHttpClient _httpClient;
  final Future<List<JwBaseUrl>> Function() _baseUrlsProvider;
  final CasSsoManager? _ssoManager;

  int _lastFailedBootstrapAt = 0;
  String? _preferredBaseUrl;

  Future<List<JwBaseUrl>> _baseCandidates() async {
    final raw = await _baseUrlsProvider();
    final seen = <String>{};
    final all = <JwBaseUrl>[];
    for (final item in raw) {
      if (seen.add(item.url)) all.add(item);
    }
    final preferred = _preferredBaseUrl;
    if (preferred == null) return all;
    all.sort((a, b) {
      final aPref = a.url == preferred ? 0 : 1;
      final bPref = b.url == preferred ? 0 : 1;
      return aPref.compareTo(bPref);
    });
    return all;
  }

  Future<bool> _bootstrapDirectSession(String baseUrl) async {
    final manager = _ssoManager;
    if (manager == null) return false;
    if (baseUrl != CampusEndpoints.jwBaseUrl) return false;
    final now = DateTime.now().millisecondsSinceEpoch;
    final masterUpdatedAt = await manager.masterSessionUpdatedAt();
    if (now - _lastFailedBootstrapAt < _bootstrapRetryCooldownMs &&
        masterUpdatedAt <= _lastFailedBootstrapAt) {
      return false;
    }
    final ok = await manager.ensureServiceSession(CampusEndpoints.jwSsoUrl);
    _lastFailedBootstrapAt = ok ? 0 : now;
    return ok;
  }

  bool get _lastBaseWasProxy =>
      _preferredBaseUrl == CampusEndpoints.jwProxyBaseUrl;

  Exception _terminalError(bool proxyAttempted, Object? lastError) {
    if (proxyAttempted) return const ProxySessionRequiredException();
    if (lastError is Exception) return lastError;
    return const SessionExpiredException();
  }

  Future<JwScheduleResult> fetchSchedule() async {
    final page = await _get(_schedulePage);
    final (xnm, xqm) = _parseSelectedTerm(page);
    final jsonText = await _postForm(
      _scheduleApi,
      {
        'xnm': xnm,
        'xqm': xqm,
        'kzlx': 'ck',
        'xsdm': '',
        'kclbdm': '',
        'kclxdm': '',
      },
      _schedulePage,
    );
    final root = _parseObject(jsonText);
    final kbList = root['kbList'];
    final courses = <JwCourse>[];
    if (kbList is List) {
      for (final item in kbList) {
        if (item is Map<String, dynamic>) {
          final parsed = parseCourseMap(item);
          if (parsed != null) courses.add(parsed);
        }
      }
    }
    return JwScheduleResult(xnm: xnm, xqm: xqm, courses: courses);
  }

  Future<List<JwGrade>> fetchGrades() async {
    final jsonText = await _postForm(
      _gradeApi,
      {
        'xnm': '',
        'xqm': '',
        'sfzgcj': '',
        'cxbkjgcj': '',
        'kcbj': '',
        'pkey': '',
        '_search': 'false',
        'nd': DateTime.now().millisecondsSinceEpoch.toString(),
        'queryModel.showCount': '200',
        'queryModel.currentPage': '1',
        'queryModel.sortName': ' ',
        'queryModel.sortOrder': 'asc',
        'time': '0',
      },
      _gradePage,
    );
    final root = _parseObject(jsonText);
    final items = root['items'];
    final result = <JwGrade>[];
    if (items is List) {
      for (final item in items) {
        if (item is Map<String, dynamic>) {
          final parsed = parseGradeMap(item);
          if (parsed != null) result.add(parsed);
        }
      }
    }
    return result;
  }

  Future<List<JwExam>> fetchExams() async {
    final page = await _get(_examPage);
    final (xnm, xqm) = _parseSelectedTerm(page);
    final jsonText = await _postForm(
      _examApi,
      {
        'xnm': xnm,
        'xqm': xqm,
        'ksmcdmb_id': '',
        'kch': '',
        'kc': '',
        'ksrq': '',
        'kkbm_id': '',
        '_search': 'false',
        'nd': DateTime.now().millisecondsSinceEpoch.toString(),
        'queryModel.showCount': '200',
        'queryModel.currentPage': '1',
        'queryModel.sortName': ' ',
        'queryModel.sortOrder': 'asc',
        'time': '0',
      },
      _examPage,
    );
    final root = _parseObject(jsonText);
    final items = root['items'];
    final term = _termLabel(xnm, xqm);
    final result = <JwExam>[];
    if (items is List) {
      for (final item in items) {
        if (item is Map<String, dynamic>) {
          final parsed = parseExamMap(item, term);
          if (parsed != null) result.add(parsed);
        }
      }
    }
    return result;
  }

  Future<JwClassroomFilters> fetchClassroomFilters() async {
    final html = await _get(_classroomPage);
    final campuses = JwParser.parseSelectOptions(html, 'xqh_id');
    final buildings = JwParser.parseSelectOptions(html, 'lh');
    final campusId = JwParser.selectedOptionValue(html, 'xqh_id') ??
        (campuses.isNotEmpty ? campuses.first.$1 : '05');
    final campusName = campuses
            .where((c) => c.$1 == campusId)
            .map((c) => c.$2)
            .firstOrNull ??
        (campuses.isNotEmpty ? campuses.first.$2 : '广兰校区');
    final fallback = _fallbackTerm();
    return JwClassroomFilters(
      campusId: campusId,
      campusName: campusName,
      campuses: campuses.map((c) => JwCampus(id: c.$1, name: c.$2)).toList(),
      buildings: [
        const JwClassroomBuilding(id: '', name: '全部'),
        ...buildings
            .where((b) => b.$1.isNotEmpty)
            .map((b) => JwClassroomBuilding(id: b.$1, name: b.$2)),
      ],
      xnm: JwParser.parseInputValue(html, 'xnm') ?? fallback.$1,
      xqm: JwParser.parseInputValue(html, 'xqm') ?? fallback.$2,
    );
  }

  Future<List<JwClassroomBuilding>> fetchClassroomBuildings({
    required String xnm,
    required String xqm,
    required String campusId,
  }) async {
    final path =
        '$_classroomBuildingApi?xqh_id=$campusId&xnm=$xnm&xqm=$xqm&gnmkdm=N2155';
    final text = await _get(path);
    final root = _parseObject(text);
    final list = root['lhList'];
    final buildings = <JwClassroomBuilding>[];
    if (list is List) {
      for (final item in list) {
        if (item is Map<String, dynamic>) {
          final id = _str(item, 'JXLDM');
          final name = _str(item, 'JXLMC');
          if (id.isNotEmpty || name.isNotEmpty) {
            buildings.add(JwClassroomBuilding(id: id, name: name));
          }
        }
      }
    }
    return [
      const JwClassroomBuilding(id: '', name: '全部'),
      ...buildings.where((b) => b.id.isNotEmpty),
    ];
  }

  Future<List<JwClassroom>> queryClassrooms({
    required JwClassroomFilters filters,
    required String buildingId,
    required int week,
    required int weekday,
    required int periodMask,
  }) async {
    final weekMask = 1 << (week.clamp(1, 20) - 1);
    final result = <JwClassroom>[];
    var page = 1;
    var total = 1 << 30;
    while (page <= 20 && result.length < total) {
      final text = await _postForm(
        _classroomApi,
        {
          'xqh_id': filters.campusId,
          'xnm': filters.xnm,
          'xqm': filters.xqm,
          'cdlb_id': '',
          'cdejlb_id': '',
          'qszws': '',
          'jszws': '',
          'cdmc': '',
          'lh': buildingId,
          'jyfs': '0',
          'cdjylx': '',
          'sfbhkc': '',
          'zcd': weekMask.toString(),
          'xqj': weekday.clamp(1, 7).toString(),
          'jcd': periodMask.toString(),
          '_search': 'false',
          'nd': DateTime.now().millisecondsSinceEpoch.toString(),
          'queryModel.showCount': _classroomPageSize.toString(),
          'queryModel.currentPage': page.toString(),
          'queryModel.sortName': 'cdbh ',
          'queryModel.sortOrder': 'asc',
          'time': '0',
        },
        _classroomPage,
      );
      final root = _parseObject(text);
      final items = root['items'];
      if (items is! List) break;
      final pageItems = <JwClassroom>[];
      for (final item in items) {
        if (item is Map<String, dynamic>) {
          final parsed = parseClassroomMap(item);
          if (parsed != null) pageItems.add(parsed);
        }
      }
      result.addAll(pageItems);
      final totalParsed = int.tryParse('${root['totalResult'] ?? ''}');
      if (totalParsed != null) total = totalParsed;
      if (items.length < _classroomPageSize || pageItems.isEmpty) break;
      page++;
    }
    return result;
  }

  Future<List<JwNotice>> fetchNotices({
    String keyword = '',
    int page = 0,
    int pageSize = _noticePageSize,
  }) async {
    final start = (page < 0 ? 0 : page) * pageSize;
    final text = await _postFormText(
      _noticeListApi,
      {
        'njdm': start.toString(),
        'bjdm': (start + pageSize).toString(),
        'doType': 'app',
        'xwbt': keyword,
      },
      _noticeListApi,
    );
    final trimmed = text.trimLeft();
    if (!trimmed.startsWith('[') && !trimmed.startsWith('{')) {
      throw _lastBaseWasProxy
          ? const ProxySessionRequiredException()
          : const SessionExpiredException('教务会话已失效');
    }
    final decoded = jsonDecode(trimmed);
    if (decoded is! List) {
      throw const FormatException('通知公告返回格式异常');
    }
    final result = <JwNotice>[];
    for (final item in decoded) {
      if (item is Map<String, dynamic>) {
        final parsed = parseNoticeMap(item);
        if (parsed != null) result.add(parsed);
      }
    }
    return result;
  }

  Future<JwNotice?> fetchNoticeDetail(String xwbh) async {
    if (xwbh.trim().isEmpty) return null;
    final id = Uri.encodeQueryComponent(xwbh);
    final text = await _postForm(
      '$_noticeDetailApi?doType=app&xwbh=$id',
      const {},
      _noticeListApi,
    );
    return parseNoticeMap(_parseObject(text));
  }

  Future<String> _get(String path) async {
    var proxyAttempted = false;
    Object? lastError;
    for (final base in await _baseCandidates()) {
      try {
        final first = await _getRaw(base.url + path, base.url);
        if (!first.sessionExpired) {
          _preferredBaseUrl = base.url;
          return first.body;
        }
        if (base.isProxy) proxyAttempted = true;
        if (!base.isProxy && await _bootstrapDirectSession(base.url)) {
          final second = await _getRaw(base.url + path, base.url);
          if (!second.sessionExpired) {
            _preferredBaseUrl = base.url;
            return second.body;
          }
          lastError = const SessionExpiredException('教务会话已失效');
        } else {
          lastError = const SessionExpiredException('教务会话已失效');
        }
      } catch (e) {
        lastError = e;
        if (base.isProxy) proxyAttempted = true;
      }
    }
    throw _terminalError(proxyAttempted, lastError);
  }

  Future<_RawResponse> _getRaw(String url, String base) async {
    final resp = await _httpClient.get(
      url,
      headers: {
        'User-Agent': CampusEndpoints.desktopUserAgent,
        'Accept':
            'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Referer': '$base/jwglxt/xtgl/index_initMenu.html?jsdm=xs',
      },
      followRedirects: false,
      useCookies: true,
    );
    if (resp.isRedirect) return const _RawResponse('', true);
    if (resp.statusCode == 401 || resp.statusCode == 403) {
      return const _RawResponse('', true);
    }
    if (!resp.isSuccessful) {
      throw HttpException('教务系统返回 ${resp.statusCode}');
    }
    final loginLike = _looksLikeLogin(resp.body);
    return _RawResponse(resp.body, loginLike);
  }

  Future<String> _postForm(
    String path,
    Map<String, String> fields,
    String refererPath,
  ) async {
    final text = await _postFormText(path, fields, refererPath);
    if (!text.trimLeft().startsWith('{')) {
      throw _lastBaseWasProxy
          ? const ProxySessionRequiredException()
          : const SessionExpiredException('教务会话已失效');
    }
    return text;
  }

  Future<String> _postFormText(
    String path,
    Map<String, String> fields,
    String refererPath,
  ) async {
    var proxyAttempted = false;
    Object? lastError;
    for (final base in await _baseCandidates()) {
      try {
        final first = await _postRaw(base.url, path, fields, refererPath);
        if (!first.sessionExpired) {
          _preferredBaseUrl = base.url;
          return first.body;
        }
        if (base.isProxy) proxyAttempted = true;
        if (!base.isProxy && await _bootstrapDirectSession(base.url)) {
          final second = await _postRaw(base.url, path, fields, refererPath);
          if (!second.sessionExpired) {
            _preferredBaseUrl = base.url;
            return second.body;
          }
          lastError = const SessionExpiredException('教务会话已失效');
        } else {
          lastError = const SessionExpiredException('教务会话已失效');
        }
      } catch (e) {
        lastError = e;
        if (base.isProxy) proxyAttempted = true;
      }
    }
    throw _terminalError(proxyAttempted, lastError);
  }

  Future<_RawResponse> _postRaw(
    String base,
    String path,
    Map<String, String> fields,
    String refererPath,
  ) async {
    final resp = await _httpClient.postForm(
      base + path,
      fields,
      headers: {
        'User-Agent': CampusEndpoints.desktopUserAgent,
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Origin': base,
        'Referer': base + refererPath,
      },
      followRedirects: false,
      useCookies: true,
    );
    if (resp.isRedirect) return const _RawResponse('', true);
    if (resp.statusCode == 401 || resp.statusCode == 403) {
      return const _RawResponse('', true);
    }
    if (!resp.isSuccessful) {
      throw HttpException('教务系统返回 ${resp.statusCode}');
    }
    final text = resp.body;
    if (text.trim().isEmpty) return const _RawResponse('', true);
    final trimmed = text.trimLeft();
    final jsonLike = trimmed.startsWith('{') || trimmed.startsWith('[');
    if (!jsonLike && _looksLikeLogin(text)) {
      return _RawResponse(text, true);
    }
    return _RawResponse(text, false);
  }

  Map<String, dynamic> _parseObject(String jsonText) {
    final decoded = jsonDecode(jsonText);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('教务返回数据无法解析');
    }
    return decoded;
  }

  bool _looksLikeLogin(String text) {
    if (text.trim().isEmpty) return true;
    return text.contains('authserver/login') ||
        text.contains('login_slogin') ||
        text.contains('统一身份认证');
  }

  (String, String) _parseSelectedTerm(String html) {
    final fallback = _fallbackTerm();
    final xnm = JwParser.selectedOptionValue(html, 'xnm') ??
        JwParser.selectedOptionValue(html, 'cx_xnm') ??
        fallback.$1;
    final xqm = JwParser.selectedOptionValue(html, 'xqm') ??
        JwParser.selectedOptionValue(html, 'cx_xqm') ??
        fallback.$2;
    return (xnm, xqm);
  }

  (String, String) _fallbackTerm() {
    final now = DateTime.now();
    if (now.month >= 8) {
      return (now.year.toString(), '3');
    }
    return ((now.year - 1).toString(), '12');
  }

  static JwCourse? parseCourseMap(Map<String, dynamic> item) {
    final name = _str(item, 'kcmc');
    if (name.isEmpty) return null;
    final day = int.tryParse(_str(item, 'xqj'));
    if (day == null || day < 1 || day > 7) return null;
    final periods = JwParser.parsePeriods(_str(item, 'jc'));
    if (periods == null) return null;
    final (startPeriod, endPeriod) = periods;
    final slotStart = ((startPeriod - 1) ~/ 2).clamp(0, 5);
    final slotSpan =
        (((endPeriod - startPeriod + 1) / 2.0).ceil()).clamp(1, 6 - slotStart);
    final weeksSpec = _str(item, 'zcd');
    final room = _str(item, 'cdmc').isNotEmpty
        ? _str(item, 'cdmc')
        : '${_str(item, 'lh')}${_str(item, 'cdbh')}';
    final campus = _str(item, 'xqmc').isNotEmpty
        ? _str(item, 'xqmc')
        : _str(item, 'cdxqmc');
    return JwCourse(
      name: name,
      teacher: _str(item, 'xm'),
      room: room,
      campus: campus,
      dayOfWeek: day - 1,
      startSlot: slotStart,
      slotSpan: slotSpan,
      startPeriod: startPeriod,
      endPeriod: endPeriod,
      weeks: JwParser.parseWeeks(weeksSpec),
      weeksSpec: weeksSpec,
      courseCode: _str(item, 'kch'),
    );
  }

  static JwGrade? parseGradeMap(Map<String, dynamic> item) {
    final score =
        _str(item, 'bfzcj').isNotEmpty ? _str(item, 'bfzcj') : _str(item, 'cj');
    final courseName = _str(item, 'kcmc');
    if (score.isEmpty || courseName.isEmpty) return null;
    final xnmmc = _str(item, 'xnmmc');
    final xqmmc = _str(item, 'xqmmc');
    final termLabel =
        xqmmc.isNotEmpty ? '$xnmmc · 第$xqmmc学期'.trim() : xnmmc.trim();
    final key = _str(item, 'row_id').isNotEmpty
        ? _str(item, 'row_id')
        : [
            _str(item, 'kch'),
            xnmmc,
            xqmmc,
            _str(item, 'jxbmc'),
          ].join('_');
    final numericScore = double.tryParse(score);
    final passed = numericScore != null
        ? numericScore >= 60
        : !score.contains('不及格') && !score.contains('未通过');
    return JwGrade(
      key: key,
      courseName: courseName,
      score: score,
      credit: double.tryParse(_str(item, 'xf')) ?? 0.0,
      gpa: double.tryParse(_str(item, 'jd')) ?? 0.0,
      term: termLabel,
      courseCode: _str(item, 'kch'),
      nature: _str(item, 'kcxzmc').isNotEmpty
          ? _str(item, 'kcxzmc')
          : _str(item, 'kcbj'),
      examType: _str(item, 'khfsmc').isNotEmpty
          ? _str(item, 'khfsmc')
          : _str(item, 'ksxz'),
      passed: passed,
    );
  }

  static JwClassroom? parseClassroomMap(Map<String, dynamic> item) {
    final name = _str(item, 'cdmc').isNotEmpty
        ? _str(item, 'cdmc')
        : _str(item, 'cdbh');
    if (name.isEmpty) return null;
    return JwClassroom(
      id: _str(item, 'cd_id').isNotEmpty
          ? _str(item, 'cd_id')
          : _str(item, 'cdxqxx_id'),
      name: name,
      building: _str(item, 'jxlmc'),
      campus: _str(item, 'xqmc'),
      type: _str(item, 'cdlbmc'),
      seats: int.tryParse(_str(item, 'zws')) ?? 0,
      examSeats: int.tryParse(_str(item, 'kszws1')) ?? 0,
      borrowable: _str(item, 'sfkjy') == '是',
    );
  }

  static JwExam? parseExamMap(Map<String, dynamic> item, String fallbackTerm) {
    final courseName = _str(item, 'kcmc').isNotEmpty
        ? _str(item, 'kcmc')
        : _str(item, 'kc');
    final examTime = _str(item, 'kssj');
    if (courseName.isEmpty && examTime.isEmpty) return null;
    final (date, startClock, endClock) = JwParser.parseExamTime(examTime);
    final examName = _str(item, 'ksmc');
    final courseCode = _str(item, 'kch');
    final key = _str(item, 'sjbh').isNotEmpty
        ? _str(item, 'sjbh')
        : [courseCode, examTime, examName].join('_');
    final xnmc = _str(item, 'xnmc');
    final xqmmc = _str(item, 'xqmmc');
    final itemTerm = (xnmc.isEmpty && xqmmc.isEmpty)
        ? fallbackTerm
        : [
            if (xnmc.isNotEmpty) xnmc,
            if (xqmmc.isNotEmpty) '第$xqmmc学期',
          ].join(' · ');
    return JwExam(
      key: key,
      courseName: courseName,
      courseCode: courseCode,
      examTime: examTime,
      examDate: date,
      startClock: startClock,
      endClock: endClock,
      examName: examName,
      examType: _str(item, 'ksfs').isNotEmpty
          ? _str(item, 'ksfs')
          : _str(item, 'ksxz'),
      location: _str(item, 'cdmc').isNotEmpty
          ? _str(item, 'cdmc')
          : _str(item, 'cd'),
      campus: _str(item, 'cdxqmc').isNotEmpty
          ? _str(item, 'cdxqmc')
          : _str(item, 'xqmc'),
      seatNo: _str(item, 'zwh'),
      ticketNo: _str(item, 'zkzh'),
      note: _str(item, 'ksbz'),
      term: itemTerm,
    );
  }

  static JwNotice? parseNoticeMap(Map<String, dynamic> item) {
    final id = _str(item, 'xwbh');
    final title = _str(item, 'xwbt');
    if (id.isEmpty && title.isEmpty) return null;
    return JwNotice(
      id: id,
      title: title.isNotEmpty ? title : '通知公告',
      publisher: _str(item, 'fbr').isNotEmpty
          ? _str(item, 'fbr')
          : _str(item, 'fbrxm'),
      publishedAt: _str(item, 'fbsj'),
      contentHtml: _str(item, 'fbnr'),
    );
  }

  static String _termLabel(String xnm, String xqm) {
    final startYear = int.tryParse(xnm);
    final yearLabel = startYear != null ? '$startYear-${startYear + 1}' : xnm;
    final termText = switch (xqm) {
      '3' => '第一学期',
      '12' => '第二学期',
      '16' => '小学期',
      _ => '',
    };
    return [yearLabel, termText].where((s) => s.isNotEmpty).join(' · ');
  }

  static String _str(Map<String, dynamic> map, String key) {
    final v = map[key];
    if (v == null) return '';
    return v.toString().trim();
  }
}

class _RawResponse {
  final String body;
  final bool sessionExpired;

  const _RawResponse(this.body, this.sessionExpired);
}
