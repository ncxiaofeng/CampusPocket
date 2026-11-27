import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'core/campus_endpoints.dart';
import 'core/exceptions.dart';
import 'core/semester_config.dart';
import 'data/auth_repository.dart';
import 'data/models.dart';
import 'data/platform_bridges.dart';
import 'data/secure_vault.dart';
import 'network/campus_card_client.dart';
import 'network/campus_cookie_jar.dart';
import 'network/campus_http_client.dart';
import 'network/campus_network_status.dart';
import 'network/cas_sso_manager.dart';
import 'network/jw_adapter.dart';
import 'network/library_seat_client.dart';
import 'network/update_checker.dart';
import 'network/weather_client.dart';

/// 全局应用状态容器：统一管理加密登录态、本地缓存与各业务同步。
class CampusAppState extends ChangeNotifier {
  CampusAppState({SecureVault? vault})
      : authRepository = AuthRepository(vault ?? KeychainSecureVault()) {
    cookieJar = CampusCookieJar(authRepository);
    httpClient = CampusHttpClient(cookieJar: cookieJar);
    networkStatus = CampusNetworkStatus(httpClient);
    ssoManager = CasSsoManager(authRepository, httpClient);
    jwAdapter = JwAdapter(
      httpClient: httpClient,
      ssoManager: ssoManager,
      baseUrlsProvider: _resolveJwBaseUrls,
    );
    cardClient = CampusCardClient(
      httpClient: httpClient,
      ssoManager: ssoManager,
    );
    libraryClient = LibrarySeatClient(httpClient: httpClient);
    weatherClient = WeatherClient(httpClient);
    updateChecker = UpdateChecker(httpClient);
  }

  static const String appVersion = '0.6.4';
  static const int appBuildNumber = 17;

  static const String _kCoursesCache = 'cp_cache_courses_v1';
  static const String _kGradesCache = 'cp_cache_grades_v1';
  static const String _kExamsCache = 'cp_cache_exams_v1';
  static const String _kWeatherPref = 'cp_pref_weather_loc_v1';
  static const String _kLastSyncTime = 'cp_last_sync_at_v1';

  final AuthRepository authRepository;
  late final CampusCookieJar cookieJar;
  late final CampusHttpClient httpClient;
  late final CampusNetworkStatus networkStatus;
  late final CasSsoManager ssoManager;
  late final JwAdapter jwAdapter;
  late final CampusCardClient cardClient;
  late final LibrarySeatClient libraryClient;
  late final WeatherClient weatherClient;
  late final UpdateChecker updateChecker;

  SharedPreferences? _prefs;

  // 网络与会话状态
  CampusNetworkState netState = CampusNetworkState.unknown;
  bool hasCasMaster = false;
  bool hasJwSession = false;
  bool hasProxySession = false;
  bool hasVpnSession = false;
  bool sessionExpired = false;
  String? sessionExpiredMessage;
  int? lastSyncAt;

  // 业务数据状态
  List<JwCourse> courses = const [];
  List<JwGrade> grades = const [];
  List<JwExam> exams = const [];
  bool syncingJw = false;
  String? jwError;

  CampusCardOverview? cardOverview;
  List<CampusCardBill> cardBills = const [];
  int cardBillsTotal = 0;
  int _cardBillsPage = 1;
  bool loadingCard = false;
  String? cardError;

  WeatherSnapshot? weather;
  String weatherLocationPref = 'auto'; // auto | guanglan | qinglan | fuzhou
  bool loadingWeather = false;
  String? weatherError;

  LibrarySeatSnapshot? librarySnapshot;
  bool loadingLibrary = false;
  String? libraryError;

  int get currentWeek => SemesterConfig.weekFor();

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    _loadCaches();
    await refreshSessionFlags();
    notifyListeners();

    // 启动后异步探测校园网并刷新天气与校园卡（若已有登录态）。
    refreshNetwork();
    refreshWeather();
    if (hasCasMaster) {
      refreshCard(silent: true);
    }
  }

  void _loadCaches() {
    final prefs = _prefs;
    if (prefs == null) return;
    weatherLocationPref = prefs.getString(_kWeatherPref) ?? 'auto';
    lastSyncAt = prefs.getInt(_kLastSyncTime);

    final rawCourses = prefs.getString(_kCoursesCache);
    if (rawCourses != null && rawCourses.isNotEmpty) {
      try {
        courses = (jsonDecode(rawCourses) as List)
            .whereType<Map<String, dynamic>>()
            .map(JwCourse.fromJson)
            .toList();
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
  }

  Future<void> refreshSessionFlags() async {
    hasCasMaster = await ssoManager.hasMasterSession();
    final jwCookies = await authRepository.loadCookiesForHost('172.20.130.13');
    hasJwSession = jwCookies.isNotEmpty;
    final proxyCookies =
        await authRepository.loadCookiesForHost(CampusEndpoints.jwProxyHost);
    hasProxySession = proxyCookies.isNotEmpty;
    final vpnCred = await authRepository.getCredential(CampusEndpoints.systemCas);
    hasVpnSession = vpnCred != null;
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

  Future<List<JwBaseUrl>> _resolveJwBaseUrls() async {
    final proxyCookies =
        await authRepository.loadCookiesForHost(CampusEndpoints.jwProxyHost);
    final hasProxy = proxyCookies.isNotEmpty;
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
    if (netState == CampusNetworkState.offCampus) {
      throw const ProxySessionRequiredException();
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
      courses = scheduleRes.courses;
      await _prefs?.setString(
        _kCoursesCache,
        jsonEncode(courses.map((e) => e.toJson()).toList()),
      );

      final fetchedGrades = await jwAdapter.fetchGrades();
      grades = fetchedGrades;
      await _prefs?.setString(
        _kGradesCache,
        jsonEncode(grades.map((e) => e.toJson()).toList()),
      );

      try {
        final fetchedExams = await jwAdapter.fetchExams();
        exams = fetchedExams;
        await _prefs?.setString(
          _kExamsCache,
          jsonEncode(exams.map((e) => e.toJson()).toList()),
        );
      } catch (_) {
        // 考试未开放时不阻断课表与成绩保存
      }

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
      if (!silent) {
        cardError = e.message;
      }
    } catch (e) {
      if (!silent) {
        cardError = '校园卡同步失败：$e';
      }
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
    await WkCookieBridge.clearAllCookies();
    await _prefs?.remove(_kCoursesCache);
    await _prefs?.remove(_kGradesCache);
    await _prefs?.remove(_kExamsCache);
    await _prefs?.remove(_kLastSyncTime);
    courses = const [];
    grades = const [];
    exams = const [];
    cardOverview = null;
    cardBills = const [];
    cardBillsTotal = 0;
    cardError = null;
    jwError = null;
    lastSyncAt = null;
    hasCasMaster = false;
    hasJwSession = false;
    hasProxySession = false;
    hasVpnSession = false;
    sessionExpired = false;
    sessionExpiredMessage = null;
    notifyListeners();
  }
}
