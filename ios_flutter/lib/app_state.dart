import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'core/campus_endpoints.dart';
import 'core/coordinate_converter.dart';
import 'core/exceptions.dart';
import 'core/schedule_adjustments.dart';
import 'core/schedule_exam_fusion.dart';
import 'core/schedule_text_parser.dart';
import 'core/semester_config.dart';
import 'data/auth_repository.dart';
import 'data/models.dart';
import 'data/platform_bridges.dart';
import 'data/runtime_config.dart';
import 'data/secure_vault.dart';
import 'network/campus_card_client.dart';
import 'network/campus_cookie_jar.dart';
import 'network/campus_http_client.dart';
import 'network/campus_network_status.dart';
import 'network/cas_auto_login.dart';
import 'network/cas_sso_manager.dart';
import 'network/chaoxing_client.dart';
import 'network/electricity_client.dart';
import 'network/huilife_client.dart';
import 'network/jw_adapter.dart';
import 'network/library_seat_client.dart';
import 'network/ujing_client.dart';
import 'network/update_checker.dart';
import 'network/weather_client.dart';

/// 全局应用状态容器：统一管理加密登录态、运行期配置、本地缓存与全量校园业务。
class CampusAppState extends ChangeNotifier {
  CampusAppState({SecureVault? vault})
      : _vault = vault ?? KeychainSecureVault() {
    authRepository = AuthRepository(_vault);
    runtimeConfigStore = RuntimeConfigStore(_vault);
    credentialVault = CredentialVault(_vault);
    cookieJar = CampusCookieJar(authRepository);
    httpClient = CampusHttpClient(cookieJar: cookieJar);
    networkStatus = CampusNetworkStatus(httpClient);
    ssoManager = CasSsoManager(authRepository, httpClient);
    casAutoLogin = CasAutoLogin(
      httpClient: httpClient,
      authRepository: authRepository,
    );
    remoteClaimClient = RemoteConfigClaimClient(httpClient);
    jwAdapter = JwAdapter(
      httpClient: httpClient,
      ssoManager: ssoManager,
      baseUrlsProvider: _resolveJwBaseUrls,
      onDirectReachable: _markOnCampusFromDirectSuccess,
      sessionRecovery: tryAutoRecoverCas,
    );
    cardClient = CampusCardClient(
      httpClient: httpClient,
      ssoManager: ssoManager,
    );
    electricityClient = ElectricityClient(
      httpClient: httpClient,
      operatorPwdProvider: () => runtimeConfig.electricityOperatorPwd,
      ssoManager: ssoManager,
      clearSessionCookies: () =>
          authRepository.clearCookiesForHost('xgwx.ecut.edu.cn'),
      onSessionRecovery: tryAutoRecoverCas,
    );
    ujingClient = UjingClient(
      httpClient: httpClient,
      hmacKeyProvider: () => runtimeConfig.ujingHmacKey,
    );
    huiLifeClient = HuiLifeClient(
      httpClient: httpClient,
      appSignSaltProvider: () => runtimeConfig.huilifeAppSignSalt,
    );
    libraryClient = LibrarySeatClient(httpClient: httpClient);
    weatherClient = WeatherClient(httpClient);
    updateChecker = UpdateChecker(httpClient);
  }

  static const String appVersion = '0.6.6';
  static const int appBuildNumber = 19;

  static const String _kCoursesCache = 'cp_cache_courses_v1';
  static const String _kGradesCache = 'cp_cache_grades_v1';
  static const String _kExamsCache = 'cp_cache_exams_v1';
  static const String _kScheduleAdjustments = 'cp_schedule_adjustments_v1';
  static const String _kElectricityCache = 'cp_cache_electricity_v1';
  static const String _kWeatherPref = 'cp_pref_weather_loc_v1';
  static const String _kLastSyncTime = 'cp_last_sync_at_v1';
  static const String _kChaoxingUid = 'cp_chaoxing_unique_id_v1';
  static const String _kHuiLifeScanned = 'cp_huilife_scanned_devices_v1';
  static const String _kHuiLifeUid = 'cp_huilife_uid_v1';

  final SecureVault _vault;
  late final AuthRepository authRepository;
  late final RuntimeConfigStore runtimeConfigStore;
  late final CredentialVault credentialVault;
  late final CampusCookieJar cookieJar;
  late final CampusHttpClient httpClient;
  late final CampusNetworkStatus networkStatus;
  late final CasSsoManager ssoManager;
  late final CasAutoLogin casAutoLogin;
  late final RemoteConfigClaimClient remoteClaimClient;
  late final JwAdapter jwAdapter;
  late final CampusCardClient cardClient;
  late final ElectricityClient electricityClient;
  late final UjingClient ujingClient;
  late final HuiLifeClient huiLifeClient;
  late final LibrarySeatClient libraryClient;
  late final WeatherClient weatherClient;
  late final UpdateChecker updateChecker;
  ChaoxingClient? _chaoxingClient;

  ChaoxingClient get chaoxingClient {
    _chaoxingClient ??= ChaoxingClient(
      httpClient: httpClient,
      authRepository: authRepository,
      uniqueId: _chaoxingUniqueId,
    );
    return _chaoxingClient!;
  }

  SharedPreferences? _prefs;
  String _chaoxingUniqueId = 'cpios000001';

  // 运行期配置与自动登录
  RuntimeConfig get runtimeConfig => runtimeConfigStore.current;
  bool autoLoginEnabled = false;
  String autoLoginUsername = '';

  // 网络与会话状态
  CampusNetworkState netState = CampusNetworkState.unknown;
  bool hasCasMaster = false;
  bool hasJwSession = false;
  bool hasProxySession = false;
  bool hasVpnSession = false;
  bool hasUjingSession = false;
  bool hasChaoxingSession = false;
  bool hasHuiLifeAccount = false;
  bool hasHuiLifeApp = false;
  bool sessionExpired = false;
  String? sessionExpiredMessage;
  int? lastSyncAt;

  // 教务数据
  List<JwCourse> courses = const [];
  List<JwGrade> grades = const [];
  List<JwExam> exams = const [];
  List<ScheduleDayAdjustment> scheduleAdjustments = const [];
  bool syncingJw = false;
  String? jwError;

  Map<String, ScheduleDayAdjustment> get scheduleAdjustmentsByDate => {
        for (final a in scheduleAdjustments) a.targetDate: a,
      };

  // 校园卡数据
  CampusCardOverview? cardOverview;
  List<CampusCardBill> cardBills = const [];
  int cardBillsTotal = 0;
  int _cardBillsPage = 1;
  bool loadingCard = false;
  String? cardError;

  // 电费数据
  ElectricitySnapshot? electricity;
  bool loadingElectricity = false;
  String? electricityError;

  // U净数据
  List<UjingStore> ujingStores = const [];
  List<UjingOrder> ujingRunning = const [];
  List<UjingOrder> ujingHistory = const [];
  (String storeId, String storeName)? ujingRecentStore;
  String ujingCampusPref = 'auto'; // auto | guanglan | qinglan | fuzhou
  String ujingLocationLabel = '当前位置';
  bool loadingUjing = false;
  String? ujingError;

  // 慧生活798 数据
  HuiLifeWallet? huiLifeWallet;
  List<HuiLifeDevice> huiLifeDevices = const [];
  List<HuiLifeBill> huiLifeBills = const [];
  final Map<String, HuiLifeDeviceStatus> huiLifeStatuses = {};
  final Set<String> huiLifeScannedIds = {};
  final Map<String, int> huiLifeLocalStartedAt = {};
  String huiLifeUid = '';
  bool loadingHuiLife = false;
  String? huiLifeError;

  // 学习通数据
  ChaoxingSnapshot? chaoxing;
  bool loadingChaoxing = false;
  String? chaoxingError;

  // 天气 & 图书馆
  WeatherSnapshot? weather;
  String weatherLocationPref = 'auto';
  bool loadingWeather = false;
  String? weatherError;
  LibrarySeatSnapshot? librarySnapshot;
  bool loadingLibrary = false;
  String? libraryError;

  int get currentWeek => SemesterConfig.weekFor();

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    await runtimeConfigStore.load();
    final autoCreds = await credentialVault.load();
    autoLoginEnabled = autoCreds?.enabled == true;
    autoLoginUsername = autoCreds?.username ?? '';
    _loadCaches();
    await refreshSessionFlags();
    notifyListeners();

    refreshNetwork();
    refreshWeather();
    if (hasCasMaster) {
      refreshCard(silent: true);
      if (runtimeConfig.hasElectricity) {
        refreshElectricity(silent: true);
      }
    }
    if (hasChaoxingSession) {
      refreshChaoxing(silent: true);
    }
    if (hasHuiLifeAccount || hasHuiLifeApp) {
      refreshHuiLife(silent: true);
    }
  }

  void _loadCaches() {
    final prefs = _prefs;
    if (prefs == null) return;
    weatherLocationPref = prefs.getString(_kWeatherPref) ?? 'auto';
    lastSyncAt = prefs.getInt(_kLastSyncTime);
    huiLifeUid = prefs.getString(_kHuiLifeUid) ?? '';
    huiLifeScannedIds
      ..clear()
      ..addAll(prefs.getStringList(_kHuiLifeScanned) ?? const []);

    var uid = prefs.getString(_kChaoxingUid);
    if (uid == null || uid.isEmpty) {
      final rnd = Random.secure();
      uid = List.generate(16, (_) => rnd.nextInt(16).toRadixString(16)).join();
      prefs.setString(_kChaoxingUid, uid);
    }
    _chaoxingUniqueId = uid;

    final rawCourses = prefs.getString(_kCoursesCache);
    if (rawCourses != null && rawCourses.isNotEmpty) {
      try {
        courses = (jsonDecode(rawCourses) as List)
            .whereType<Map<String, dynamic>>()
            .map(JwCourse.fromJson)
            .toList();
      } catch (_) {}
    }

    final rawAdjustments = prefs.getString(_kScheduleAdjustments);
    if (rawAdjustments != null && rawAdjustments.isNotEmpty) {
      try {
        scheduleAdjustments = (jsonDecode(rawAdjustments) as List)
            .whereType<Map<String, dynamic>>()
            .map(ScheduleDayAdjustment.fromJson)
            .where((a) => a.targetDate.isNotEmpty)
            .toList()
          ..sort((a, b) => a.targetDate.compareTo(b.targetDate));
      } catch (_) {}
    }

    final rawGrades = prefs.getString(_kGradesCache);
    if (rawGrades != null && rawGrades.isNotEmpty) {
      try {
        grades = (jsonDecode(rawGrades) as List)
            .whereType<Map<String, dynamic>>()
            .map(JwGrade.fromJson)
            .toList();
      } catch (_) {}
    }

    final rawExams = prefs.getString(_kExamsCache);
    if (rawExams != null && rawExams.isNotEmpty) {
      try {
        exams = (jsonDecode(rawExams) as List)
            .whereType<Map<String, dynamic>>()
            .map(JwExam.fromJson)
            .toList();
      } catch (_) {}
    }

    final rawElec = prefs.getString(_kElectricityCache);
    if (rawElec != null && rawElec.isNotEmpty) {
      try {
        electricity = ElectricitySnapshot.fromJson(
          jsonDecode(rawElec) as Map<String, dynamic>,
        );
      } catch (_) {}
    }
  }

  Future<void> refreshSessionFlags() async {
    hasCasMaster = await ssoManager.hasMasterSession();
    hasJwSession =
        (await authRepository.loadCookiesForHost('172.20.130.13')).isNotEmpty;
    hasProxySession =
        (await authRepository.loadCookiesForHost(CampusEndpoints.jwProxyHost))
            .isNotEmpty;
    hasVpnSession =
        (await authRepository.getCredential(CampusEndpoints.systemCas)) != null;
    hasUjingSession =
        (await authRepository.getCredential(CampusEndpoints.systemUjing)) !=
            null;
    hasChaoxingSession = await chaoxingClient.hasSession();
    hasHuiLifeAccount = (await authRepository
            .getCredential(CampusEndpoints.systemHuiLifeAccount)) !=
        null;
    hasHuiLifeApp =
        (await authRepository.getCredential(CampusEndpoints.systemHuiLifeApp)) !=
            null;

    if (hasCasMaster) {
      sessionExpired = false;
      sessionExpiredMessage = null;
    }
    notifyListeners();
  }

  Future<bool> refreshNetwork() async {
    netState = CampusNetworkState.checking;
    notifyListeners();
    final onCampus = await networkStatus.refresh();
    netState = networkStatus.state;
    notifyListeners();
    return onCampus;
  }

  void _markOnCampusFromDirectSuccess() {
    if (netState != CampusNetworkState.onCampus) {
      netState = CampusNetworkState.onCampus;
      notifyListeners();
    }
  }

  Future<bool> tryAutoRecoverCas() async {
    final creds = await credentialVault.load();
    if (creds == null || !creds.enabled) return false;
    try {
      await casAutoLogin.login(creds.username, creds.password);
      await refreshSessionFlags();
      return true;
    } catch (_) {
      return false;
    }
  }

  /// 彻底修复真机校内误报零信任门户的问题：
  /// 1. 如果尚未明确为校内，同步前先实时复探一次；
  /// 2. 绝不直接一刀切拒绝直连——即使首次因 iOS 本地网络弹窗未放行被判为校外，
  ///    仍把直连 `172.20.130.13` 放进候选（若已有代理会话则校外时优先代理、回退直连），
  ///    直连只要能连通会自动回标 `onCampus`。
  Future<List<JwBaseUrl>> _resolveJwBaseUrls() async {
    if (netState != CampusNetworkState.onCampus) {
      await refreshNetwork();
    }
    final hasProxy =
        (await authRepository.loadCookiesForHost(CampusEndpoints.jwProxyHost))
            .isNotEmpty;
    if (netState == CampusNetworkState.onCampus) {
      return [
        const JwBaseUrl(CampusEndpoints.jwBaseUrl, isProxy: false),
        if (hasProxy)
          const JwBaseUrl(CampusEndpoints.jwProxyBaseUrl, isProxy: true),
      ];
    }
    if (hasProxy) {
      return const [
        JwBaseUrl(CampusEndpoints.jwProxyBaseUrl, isProxy: true),
        JwBaseUrl(CampusEndpoints.jwBaseUrl, isProxy: false),
      ];
    }
    return const [JwBaseUrl(CampusEndpoints.jwBaseUrl, isProxy: false)];
  }

  Future<String> syncJwAll() async {
    if (syncingJw) return '正在同步中…';
    syncingJw = true;
    jwError = null;
    notifyListeners();

    try {
      await refreshSessionFlags();
      final scheduleRes = await jwAdapter.fetchSchedule();
      final preservedCustomCourses = courses.where((c) => c.isCustom).toList();
      courses = [...scheduleRes.courses, ...preservedCustomCourses];
      await _persistCourses();

      final fetchedGrades = await jwAdapter.fetchGrades();
      grades = fetchedGrades;
      await _prefs?.setString(
        _kGradesCache,
        jsonEncode(grades.map((e) => e.toJson()).toList()),
      );

      try {
        final fetchedExams = await jwAdapter.fetchExams();
        final preservedCustomExams = exams.where((e) => e.isCustom).toList();
        exams = [...fetchedExams, ...preservedCustomExams]
          ..sort(_compareExams);
        await _persistExams();
      } catch (_) {}

      lastSyncAt = DateTime.now().millisecondsSinceEpoch;
      await _prefs?.setInt(_kLastSyncTime, lastSyncAt!);
      sessionExpired = false;
      sessionExpiredMessage = null;
      syncingJw = false;
      notifyListeners();
      return '同步完成：${courses.length} 节课程、${grades.length} 门成绩、${exams.length} 场考试';
    } on ProxySessionRequiredException catch (e) {
      jwError = e.message;
      syncingJw = false;
      notifyListeners();
      return e.message;
    } on SessionExpiredException catch (e) {
      jwError = e.message;
      sessionExpired = true;
      sessionExpiredMessage = e.message;
      syncingJw = false;
      notifyListeners();
      return e.message;
    } catch (e) {
      jwError = '同步失败：$e';
      syncingJw = false;
      notifyListeners();
      return jwError!;
    }
  }

  // ---- 自定义课表 / 自定义考试（教务同步永不覆盖）----

  Future<void> _persistCourses() async {
    await _prefs?.setString(
      _kCoursesCache,
      jsonEncode(courses.map((e) => e.toJson()).toList()),
    );
  }

  Future<void> _persistExams() async {
    await _prefs?.setString(
      _kExamsCache,
      jsonEncode(exams.map((e) => e.toJson()).toList()),
    );
  }

  int _compareExams(JwExam a, JwExam b) {
    final c1 = a.examDate.compareTo(b.examDate);
    if (c1 != 0) return c1;
    final c2 = a.startClock.compareTo(b.startClock);
    if (c2 != 0) return c2;
    return a.courseName.compareTo(b.courseName);
  }

  Future<void> upsertCustomCourse({
    String? existingId,
    required String name,
    required int dayOfWeek,
    required int startPeriod,
    required int endPeriod,
    required String weeksSpec,
    String room = '',
    String campus = '',
    String teacher = '',
  }) async {
    final item = ScheduleExamFusion.buildCustomCourse(
      existingId: existingId,
      name: name,
      dayOfWeek: dayOfWeek,
      startPeriod: startPeriod,
      endPeriod: endPeriod,
      weeksSpec: weeksSpec,
      room: room,
      campus: campus,
      teacher: teacher,
    );
    final next = courses.where((c) => !(c.isCustom && c.id == item.id)).toList()
      ..add(item);
    courses = next;
    await _persistCourses();
    notifyListeners();
  }

  Future<void> deleteCustomCourse(String id) async {
    courses = courses.where((c) => !(c.isCustom && c.id == id)).toList();
    await _persistCourses();
    notifyListeners();
  }

  Future<void> upsertCustomExam({
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
  }) async {
    final item = ScheduleExamFusion.buildCustomExam(
      existingKey: existingKey,
      courseName: courseName,
      examDate: examDate,
      startClock: startClock,
      endClock: endClock,
      location: location,
      campus: campus,
      examType: examType,
      seatNo: seatNo,
      note: note,
    );
    final next = exams.where((e) => e.key != item.key).toList()
      ..add(item)
      ..sort(_compareExams);
    exams = next;
    await _persistExams();
    notifyListeners();
  }

  Future<void> deleteCustomExam(String key) async {
    exams = exams.where((e) => e.key != key).toList();
    await _persistExams();
    notifyListeners();
  }

  Future<String> saveReviewedDrafts(List<ParsedScheduleDraft> drafts) async {
    final active =
        drafts.where((d) => d.selected && d.title.trim().isNotEmpty).toList();
    if (active.isEmpty) return '未选中任何条目';
    var courseCount = 0;
    var examCount = 0;
    for (final d in active) {
      if (d.kind == ParsedItemKind.course) {
        await upsertCustomCourse(
          name: d.title,
          dayOfWeek: d.dayOfWeek,
          startPeriod: d.startPeriod,
          endPeriod: d.endPeriod,
          weeksSpec: d.weeksSpec,
          room: d.room,
          campus: d.campus,
          teacher: d.teacher,
        );
        courseCount++;
      } else {
        await upsertCustomExam(
          courseName: d.title,
          examDate: d.examDate,
          startClock: d.startClock,
          endClock: d.endClock,
          location: d.room,
          campus: d.campus,
          examType: d.examType,
          seatNo: d.seatNo,
          note: d.note,
        );
        examCount++;
      }
    }
    final parts = <String>[
      if (courseCount > 0) '$courseCount 节课程',
      if (examCount > 0) '$examCount 场考试',
    ];
    return '审核确认完成，已加入 ${parts.join('、')}';
  }

  // ---- 校园卡 ----

  Future<void> refreshCard({bool silent = false}) async {
    if (loadingCard) return;
    loadingCard = true;
    if (!silent) cardError = null;
    notifyListeners();
    try {
      final overview = await cardClient.fetchOverview();
      final page1 = await cardClient.fetchBills(page: 1, pageSize: 30);
      cardOverview = overview;
      cardBills = page1.items;
      cardBillsTotal = page1.total;
      _cardBillsPage = 1;
      cardError = null;
    } on SessionExpiredException catch (e) {
      if (!silent) cardError = e.message;
    } catch (e) {
      if (!silent) cardError = '校园卡同步失败：$e';
    } finally {
      loadingCard = false;
      notifyListeners();
    }
  }

  Future<void> loadMoreCardBills() async {
    if (loadingCard || cardBills.length >= cardBillsTotal) return;
    loadingCard = true;
    notifyListeners();
    try {
      final nextPage = _cardBillsPage + 1;
      final pageData = await cardClient.fetchBills(page: nextPage, pageSize: 30);
      cardBills = [...cardBills, ...pageData.items];
      cardBillsTotal = pageData.total;
      _cardBillsPage = nextPage;
    } catch (e) {
      cardError = '加载更多流水失败：$e';
    } finally {
      loadingCard = false;
      notifyListeners();
    }
  }

  // ---- 宿舍电费 ----

  Future<void> refreshElectricity({bool silent = false}) async {
    if (loadingElectricity) return;
    loadingElectricity = true;
    if (!silent) electricityError = null;
    notifyListeners();
    try {
      final snap = await electricityClient.fetchSnapshot();
      electricity = snap;
      await _prefs?.setString(_kElectricityCache, jsonEncode(snap.toJson()));
      electricityError = null;
    } on ConfigurationRequiredException catch (e) {
      if (!silent) electricityError = e.message;
    } on SessionExpiredException catch (e) {
      if (!silent) electricityError = e.message;
    } catch (e) {
      if (!silent) electricityError = '电费同步失败：$e';
    } finally {
      loadingElectricity = false;
      notifyListeners();
    }
  }

  // ---- U净洗衣 ----

  Future<void> saveUjingToken(String token) async {
    await authRepository.saveOpaqueToken(
      systemId: CampusEndpoints.systemUjing,
      domain: 'phoenix.ujing.online',
      token: token,
    );
    await refreshSessionFlags();
  }

  Future<void> loginUjing(String mobile, String captcha) async {
    final token = await ujingClient.login(mobile, captcha);
    await saveUjingToken(token);
  }

  Future<void> setUjingCampusPref(String pref) async {
    ujingCampusPref = pref;
    notifyListeners();
    await refreshUjing();
  }

  Future<void> refreshUjing() async {
    final cred =
        await authRepository.getCredential(CampusEndpoints.systemUjing);
    if (cred == null || cred.cookie.trim().isEmpty) {
      hasUjingSession = false;
      notifyListeners();
      return;
    }
    final token = cred.cookie.trim();
    loadingUjing = true;
    ujingError = null;
    notifyListeners();
    try {
      double lat = kUjingCampuses.first.latitude;
      double lng = kUjingCampuses.first.longitude;
      if (ujingCampusPref == 'auto') {
        final pos = await DeviceLocationBridge.getCurrentLocation();
        if (pos != null) {
          final gcj =
              CoordinateConverter.wgs84ToGcj02(pos.latitude, pos.longitude);
          lat = gcj.$1;
          lng = gcj.$2;
          ujingLocationLabel = '当前位置（已纠偏）';
        } else {
          ujingLocationLabel = '${kUjingCampuses.first.name}（定位未授权回退）';
        }
      } else {
        final found = kUjingCampuses
                .where((c) => c.id == ujingCampusPref)
                .firstOrNull ??
            kUjingCampuses.first;
        lat = found.latitude;
        lng = found.longitude;
        ujingLocationLabel = found.name;
      }

      ujingStores = await ujingClient.nearbyStores(lat, lng, token);
      ujingRunning = await ujingClient.runningOrders(token);
      ujingHistory = await ujingClient.historyOrders(token, limit: 10);
      if (ujingHistory.isNotEmpty) {
        try {
          ujingRecentStore = await ujingClient.orderDetailStore(
            ujingHistory.first.orderId,
            token,
          );
        } catch (_) {}
      }
      ujingError = null;
    } on SessionExpiredException catch (e) {
      ujingError = e.message;
      await authRepository.clearSystem(CampusEndpoints.systemUjing, const []);
      hasUjingSession = false;
    } catch (e) {
      ujingError = 'U净刷新失败：$e';
    } finally {
      loadingUjing = false;
      notifyListeners();
    }
  }

  Future<void> logoutUjing() async {
    await authRepository.clearSystem(CampusEndpoints.systemUjing, const []);
    ujingStores = const [];
    ujingRunning = const [];
    ujingHistory = const [];
    ujingRecentStore = null;
    hasUjingSession = false;
    notifyListeners();
  }

  // ---- 慧生活798 ----

  Future<void> loginHuiLife({
    required String phone,
    required String smsCode,
    required HuiLifePlatform platform,
  }) async {
    final session = await huiLifeClient.login(
      phone: phone,
      smsCode: smsCode,
      platform: platform,
    );
    final sysId = platform == HuiLifePlatform.app
        ? CampusEndpoints.systemHuiLifeApp
        : CampusEndpoints.systemHuiLifeAccount;
    await authRepository.saveOpaqueToken(
      systemId: sysId,
      domain: 'i.ilife798.com',
      token: session.token,
    );
    if (session.uid.isNotEmpty) {
      huiLifeUid = session.uid;
      await _prefs?.setString(_kHuiLifeUid, huiLifeUid);
    }
    await refreshSessionFlags();
    await refreshHuiLife();
  }

  Future<String?> getHuiLifeToken(HuiLifePlatform platform) async {
    final sysId = platform == HuiLifePlatform.app
        ? CampusEndpoints.systemHuiLifeApp
        : CampusEndpoints.systemHuiLifeAccount;
    final cred = await authRepository.getCredential(sysId);
    return cred?.cookie.trim().isEmpty == true ? null : cred?.cookie.trim();
  }

  Future<void> addHuiLifeScannedDevice(HuiLifeDevice device) async {
    huiLifeScannedIds.add(device.id);
    await _prefs?.setStringList(
      _kHuiLifeScanned,
      huiLifeScannedIds.toList(),
    );
    if (!huiLifeDevices.any((d) => d.id == device.id)) {
      huiLifeDevices = [device, ...huiLifeDevices];
    }
    notifyListeners();
    await refreshHuiLifeDeviceStatuses();
  }

  Future<void> refreshHuiLife({bool silent = false}) async {
    final appToken = await getHuiLifeToken(HuiLifePlatform.app);
    final accountToken = await getHuiLifeToken(HuiLifePlatform.account);
    final activeToken = appToken ?? accountToken;
    final activePlatform =
        appToken != null ? HuiLifePlatform.app : HuiLifePlatform.account;
    if (activeToken == null) return;

    loadingHuiLife = true;
    if (!silent) huiLifeError = null;
    notifyListeners();
    try {
      final mergedDevices = <String, HuiLifeDevice>{};
      for (final d in huiLifeDevices) {
        if (huiLifeScannedIds.contains(d.id)) mergedDevices[d.id] = d;
      }

      try {
        huiLifeWallet =
            await huiLifeClient.fetchWallet(activeToken, activePlatform);
      } catch (_) {}

      try {
        final master =
            await huiLifeClient.fetchMaster(activeToken, activePlatform);
        for (final d in master) {
          mergedDevices[d.id] = d;
        }
      } catch (_) {}

      try {
        huiLifeBills =
            await huiLifeClient.fetchBills(activeToken, activePlatform);
        for (final b in huiLifeBills) {
          if (b.deviceId.isNotEmpty && !mergedDevices.containsKey(b.deviceId)) {
            mergedDevices[b.deviceId] = HuiLifeDevice(
              id: b.deviceId,
              name: b.deviceName.isNotEmpty ? b.deviceName : '最近使用设备',
              detail: '来自消费记录',
            );
          }
        }
      } catch (_) {}

      huiLifeDevices = mergedDevices.values.toList();
      huiLifeError = null;
      await refreshHuiLifeDeviceStatuses();
    } on HuiLifeTokenExpiredException catch (e) {
      if (!silent) huiLifeError = e.message;
    } catch (e) {
      if (!silent) huiLifeError = '慧生活刷新失败：$e';
    } finally {
      loadingHuiLife = false;
      notifyListeners();
    }
  }

  Future<void> refreshHuiLifeDeviceStatuses() async {
    final appToken = await getHuiLifeToken(HuiLifePlatform.app);
    final accountToken = await getHuiLifeToken(HuiLifePlatform.account);
    final token = appToken ?? accountToken;
    final platform =
        appToken != null ? HuiLifePlatform.app : HuiLifePlatform.account;
    if (token == null || huiLifeDevices.isEmpty) return;

    for (final dev in huiLifeDevices) {
      try {
        final status = await huiLifeClient.fetchDeviceStatus(
          token,
          platform,
          dev.id,
          more: dev.name == '扫码设备' || dev.name == '未命名设备',
        );
        huiLifeStatuses[dev.id] = status;
      } catch (_) {}
    }
    notifyListeners();
  }

  Future<void> startHuiLifeDevice(String deviceId) async {
    final appToken = await getHuiLifeToken(HuiLifePlatform.app);
    if (appToken == null) {
      throw const HuiLifeTokenExpiredException('请先完成「设备控制端」短信登录');
    }
    await huiLifeClient.startDevice(appToken, deviceId);
    huiLifeLocalStartedAt[deviceId] = DateTime.now().millisecondsSinceEpoch;
    await refreshHuiLifeDeviceStatuses();
  }

  Future<void> stopHuiLifeDevice(String deviceId) async {
    final appToken = await getHuiLifeToken(HuiLifePlatform.app);
    if (appToken == null) {
      throw const HuiLifeTokenExpiredException('请先完成「设备控制端」短信登录');
    }
    await huiLifeClient.stopDevice(appToken, deviceId);
    huiLifeLocalStartedAt.remove(deviceId);
    await refreshHuiLifeDeviceStatuses();
  }

  // ---- 超星学习通 ----

  Future<void> refreshChaoxing({bool silent = false}) async {
    if (loadingChaoxing) return;
    if (!await chaoxingClient.hasSession()) {
      hasChaoxingSession = false;
      notifyListeners();
      return;
    }
    loadingChaoxing = true;
    if (!silent) chaoxingError = null;
    notifyListeners();
    try {
      chaoxing = await chaoxingClient.fetchSnapshot();
      chaoxingError = null;
    } on ChaoxingSessionExpiredException catch (e) {
      if (!silent) chaoxingError = e.message;
    } catch (e) {
      if (!silent) chaoxingError = '学习通同步失败：$e';
    } finally {
      loadingChaoxing = false;
      notifyListeners();
    }
  }

  Future<void> _persistScheduleAdjustments() async {
    await _prefs?.setString(
      _kScheduleAdjustments,
      jsonEncode(scheduleAdjustments.map((e) => e.toJson()).toList()),
    );
  }

  Future<void> upsertScheduleAdjustments(List<ScheduleDayAdjustment> rules) async {
    if (rules.isEmpty) return;
    final map = {for (final a in scheduleAdjustments) a.targetDate: a};
    for (final r in rules) {
      map[r.targetDate] = r;
    }
    scheduleAdjustments = map.values.toList()
      ..sort((a, b) => a.targetDate.compareTo(b.targetDate));
    await _persistScheduleAdjustments();
    notifyListeners();
  }

  Future<void> saveSwapAdjustment({
    required String targetDate,
    required String sourceDate,
    required String label,
    required bool markSourceAsRest,
  }) async {
    final cleanLabel = label.trim().isEmpty ? '节假日调休' : label.trim();
    final rules = <ScheduleDayAdjustment>[
      ScheduleDayAdjustment(
        targetDate: targetDate.trim(),
        type: ScheduleAdjustmentType.swap,
        sourceDate: sourceDate.trim(),
        label: cleanLabel,
      ),
    ];
    if (markSourceAsRest && sourceDate.trim() != targetDate.trim()) {
      rules.add(
        ScheduleDayAdjustment(
          targetDate: sourceDate.trim(),
          type: ScheduleAdjustmentType.rest,
          label: cleanLabel.replaceAll('调休', '放假'),
        ),
      );
    }
    await upsertScheduleAdjustments(rules);
  }

  Future<int> saveRestRangeAdjustment({
    required String startDate,
    required String endDate,
    required String label,
  }) async {
    final rules = ScheduleAdjustments.expandRestRange(startDate, endDate, label);
    await upsertScheduleAdjustments(rules);
    return rules.length;
  }

  Future<void> deleteScheduleAdjustment(String targetDate) async {
    scheduleAdjustments =
        scheduleAdjustments.where((a) => a.targetDate != targetDate).toList();
    await _persistScheduleAdjustments();
    notifyListeners();
  }

  Future<void> clearAllScheduleAdjustments() async {
    scheduleAdjustments = const [];
    await _persistScheduleAdjustments();
    notifyListeners();
  }

  // ---- 高级配置 & 自动登录 ----

  Future<void> updateRuntimeConfig(RuntimeConfig config) async {
    await runtimeConfigStore.update(config);
    notifyListeners();
  }

  Future<void> clearRuntimeConfig() async {
    await runtimeConfigStore.clear();
    notifyListeners();
  }

  Future<void> enableAutoLogin(String username, String password) async {
    await casAutoLogin.login(username, password);
    await credentialVault.save(username, password, enabled: true);
    autoLoginEnabled = true;
    autoLoginUsername = username.trim();
    await refreshSessionFlags();
  }

  Future<void> disableAutoLogin() async {
    await credentialVault.clear();
    autoLoginEnabled = false;
    autoLoginUsername = '';
    notifyListeners();
  }

  // ---- 天气 & 图书馆 ----

  Future<void> setWeatherLocationPref(String pref) async {
    weatherLocationPref = pref;
    await _prefs?.setString(_kWeatherPref, pref);
    notifyListeners();
    await refreshWeather(force: true);
  }

  Future<void> refreshWeather({bool force = false}) async {
    if (loadingWeather) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    if (!force &&
        weather != null &&
        now - weather!.fetchedAt < 20 * 60 * 1000) {
      return;
    }
    loadingWeather = true;
    weatherError = null;
    notifyListeners();
    try {
      var target = WeatherClient.guanglan;
      if (weatherLocationPref == 'auto') {
        final pos = await DeviceLocationBridge.getCurrentLocation();
        if (pos != null) {
          target = WeatherLocation(
            id: 'auto',
            name: '当前位置',
            latitude: pos.latitude,
            longitude: pos.longitude,
          );
        }
      } else {
        target = WeatherClient.presetLocations
                .where((l) => l.id == weatherLocationPref)
                .firstOrNull ??
            WeatherClient.guanglan;
      }
      weather = await weatherClient.fetch(target);
      weatherError = null;
    } catch (e) {
      weatherError = '天气获取失败：$e';
    } finally {
      loadingWeather = false;
      notifyListeners();
    }
  }

  Future<void> refreshLibrary({
    required String day,
    String? startTime,
    String? endTime,
  }) async {
    loadingLibrary = true;
    libraryError = null;
    notifyListeners();
    try {
      librarySnapshot = await libraryClient.fetchAvailability(
        day: day,
        startTime: startTime,
        endTime: endTime,
      );
    } catch (e) {
      libraryError = '图书馆座位获取失败：$e';
    } finally {
      loadingLibrary = false;
      notifyListeners();
    }
  }

  Future<void> logoutAndClearAll() async {
    await authRepository.clearAll();
    await credentialVault.clear();
    await WkCookieBridge.clearAllCookies();
    await _prefs?.remove(_kCoursesCache);
    await _prefs?.remove(_kGradesCache);
    await _prefs?.remove(_kExamsCache);
    await _prefs?.remove(_kElectricityCache);
    await _prefs?.remove(_kLastSyncTime);
    courses = const [];
    grades = const [];
    exams = const [];
    cardOverview = null;
    cardBills = const [];
    cardBillsTotal = 0;
    electricity = null;
    ujingStores = const [];
    ujingRunning = const [];
    ujingHistory = const [];
    ujingRecentStore = null;
    huiLifeWallet = null;
    huiLifeDevices = const [];
    huiLifeBills = const [];
    huiLifeStatuses.clear();
    huiLifeLocalStartedAt.clear();
    chaoxing = null;
    autoLoginEnabled = false;
    autoLoginUsername = '';
    hasCasMaster = false;
    hasJwSession = false;
    hasProxySession = false;
    hasVpnSession = false;
    hasUjingSession = false;
    hasChaoxingSession = false;
    hasHuiLifeAccount = false;
    hasHuiLifeApp = false;
    sessionExpired = false;
    sessionExpiredMessage = null;
    notifyListeners();
  }
}
