import 'dart:convert';
import 'dart:io';

import '../core/campus_endpoints.dart';
import '../core/exceptions.dart';
import 'campus_http_client.dart';
import 'cas_sso_manager.dart';

class ElectricityDayUsage {
  final String date; // yyyy-MM-dd
  final double kwh;

  const ElectricityDayUsage({required this.date, required this.kwh});

  Map<String, dynamic> toJson() => {'date': date, 'kwh': kwh};

  factory ElectricityDayUsage.fromJson(Map<String, dynamic> json) =>
      ElectricityDayUsage(
        date: '${json['date'] ?? ''}',
        kwh: (json['kwh'] as num?)?.toDouble() ?? 0.0,
      );
}

class ElectricitySnapshot {
  final String userId;
  final String houseLabel;
  final String houseAddress;
  final String meterId;
  final double? balance;
  final List<ElectricityDayUsage> dailyUsage;
  final int updatedAt;

  const ElectricitySnapshot({
    required this.userId,
    required this.houseLabel,
    required this.houseAddress,
    required this.meterId,
    required this.balance,
    required this.dailyUsage,
    required this.updatedAt,
  });

  Map<String, dynamic> toJson() => {
        'userId': userId,
        'houseLabel': houseLabel,
        'houseAddress': houseAddress,
        'meterId': meterId,
        'balance': balance,
        'dailyUsage': dailyUsage.map((e) => e.toJson()).toList(),
        'updatedAt': updatedAt,
      };

  factory ElectricitySnapshot.fromJson(Map<String, dynamic> json) =>
      ElectricitySnapshot(
        userId: '${json['userId'] ?? ''}',
        houseLabel: '${json['houseLabel'] ?? ''}',
        houseAddress: '${json['houseAddress'] ?? ''}',
        meterId: '${json['meterId'] ?? ''}',
        balance: (json['balance'] as num?)?.toDouble(),
        dailyUsage: ((json['dailyUsage'] as List?) ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(ElectricityDayUsage.fromJson)
            .toList(),
        updatedAt: (json['updatedAt'] as num?)?.toInt() ?? 0,
      );
}

/// 今日校园“宿舍缴费”水电只读接口（xgwx.ecut.edu.cn）。
class ElectricityClient {
  ElectricityClient({
    required CampusHttpClient httpClient,
    required String Function() operatorPwdProvider,
    CasSsoManager? ssoManager,
    Future<void> Function()? clearSessionCookies,
    Future<bool> Function()? onSessionRecovery,
  })  : _httpClient = httpClient,
        _operatorPwdProvider = operatorPwdProvider,
        _ssoManager = ssoManager,
        _clearSessionCookies = clearSessionCookies,
        _onSessionRecovery = onSessionRecovery;

  final CampusHttpClient _httpClient;
  final String Function() _operatorPwdProvider;
  final CasSsoManager? _ssoManager;
  final Future<void> Function()? _clearSessionCookies;
  final Future<bool> Function()? _onSessionRecovery;

  Future<ElectricitySnapshot> fetchSnapshot() async {
    final operatorPwd = _operatorPwdProvider().trim();
    if (operatorPwd.isEmpty) {
      throw const ConfigurationRequiredException(
        '未配置电费参数：公开版不内置；请前往「更多 → 高级配置」扫码/粘贴导入，或直接使用今日校园查询',
      );
    }
    final indexUrl = CampusEndpoints.electricityIndexUrl(operatorPwd);
    final resolved = await _loadIndexUserId(indexUrl);
    var userId = resolved.$1;
    var referer = resolved.$2;

    String houseJson;
    try {
      houseJson = await _fetchBindHouses(userId, referer);
    } on SessionExpiredException {
      final renewed = await _renewSession(indexUrl);
      if (!renewed) rethrow;
      final retried = await _loadIndexUserId(indexUrl, allowRecovery: false);
      userId = retried.$1;
      referer = retried.$2;
      houseJson = await _fetchBindHouses(userId, referer);
    }

    final root = _parseObject(houseJson);
    if ('${root['ret'] ?? ''}' != '1') {
      final msg = '${root['msg'] ?? '宿舍绑定信息读取失败'}';
      if (looksLikeSessionError(msg)) {
        throw SessionExpiredException('宿舍缴费会话已失效：$msg');
      }
      throw HttpException(msg);
    }

    final houses = (root['data'] as List?) ?? const [];
    final selection = _selectMeter(houses);
    if (selection == null) {
      throw const HttpException('没有找到可查询的电表');
    }

    final today = DateTime.now();
    final start = DateTime(today.year, today.month, today.day)
        .subtract(const Duration(days: 29));
    final end = DateTime(today.year, today.month, today.day);
    final usage = await _queryDosageChunked(
      houseId: selection.houseId,
      meterId: selection.meterId,
      start: start,
      end: end,
    );

    return ElectricitySnapshot(
      userId: userId,
      houseLabel: selection.houseLabel,
      houseAddress: selection.houseAddress,
      meterId: selection.meterId,
      balance: selection.balance,
      dailyUsage: usage,
      updatedAt: DateTime.now().millisecondsSinceEpoch,
    );
  }

  Future<(String, String)> _loadIndexUserId(
    String indexUrl, {
    bool allowRecovery = true,
  }) async {
    Future<(String, String)?> tryOnce() async {
      final resp = await _httpClient.get(
        indexUrl,
        headers: const {
          'User-Agent': CampusEndpoints.desktopUserAgent,
          'Accept': 'text/html,application/xhtml+xml,*/*;q=0.8',
        },
        followRedirects: true,
        useCookies: true,
      );
      if (!resp.isSuccessful) return null;
      final uid = extractUserId(resp.body);
      if (uid != null) return (uid, resp.finalUri.toString());
      return null;
    }

    final first = await tryOnce();
    if (first != null) return first;

    if (allowRecovery && await _renewSession(indexUrl)) {
      final second = await tryOnce();
      if (second != null) return second;
    }
    throw const SessionExpiredException('统一认证或宿舍缴费会话已过期，请重新登录');
  }

  Future<bool> _renewSession(String indexUrl) async {
    try {
      await _clearSessionCookies?.call();
    } catch (_) {}
    if (await _ssoManager?.ensureServiceSession(indexUrl) == true) {
      return true;
    }
    if (await _onSessionRecovery?.call() == true) {
      try {
        await _clearSessionCookies?.call();
      } catch (_) {}
      return await _ssoManager?.ensureServiceSession(indexUrl) == true;
    }
    return false;
  }

  Future<String> _fetchBindHouses(String userId, String referer) => _postForm(
        '/app/app/api/user/searchBindHouseListForMoney',
        {'userid': userId},
        referer,
      );

  Future<List<ElectricityDayUsage>> _queryDosageChunked({
    required String houseId,
    required String meterId,
    required DateTime start,
    required DateTime end,
  }) async {
    final merged = <String, double>{};
    var cursor = start;
    while (!cursor.isAfter(end)) {
      final next = cursor.add(const Duration(days: 6));
      final chunkEnd = next.isAfter(end) ? end : next;
      try {
        final json = await _postForm(
          '/app/app/api/data/dosage',
          {
            'houseId': houseId,
            'meterId': meterId,
            'startTime': formatQueryDate(cursor),
            'endTime': formatQueryDate(chunkEnd),
          },
          CampusEndpoints.electricitySurplusUrl,
        );
        for (final item in parseDosageRangeJson(json)) {
          merged[item.date] = item.kwh;
        }
      } on SessionExpiredException {
        rethrow;
      } catch (_) {}
      cursor = chunkEnd.add(const Duration(days: 1));
    }

    // 对最近 15 天内缺失的日期逐日补查（最多到昨天）
    final yesterday = end.subtract(const Duration(days: 1));
    final recentStart = yesterday.subtract(const Duration(days: 14));
    var check = recentStart.isBefore(start) ? start : recentStart;
    while (!check.isAfter(yesterday)) {
      final key = formatQueryDate(check);
      if (!merged.containsKey(key)) {
        try {
          final json = await _postForm(
            '/app/app/api/data/dosage',
            {
              'houseId': houseId,
              'meterId': meterId,
              'startTime': key,
              'endTime': key,
            },
            CampusEndpoints.electricitySurplusUrl,
          );
          final single = parseSingleDayDosage(json);
          if (single != null && single > 0) {
            merged[key] = single;
          }
        } on SessionExpiredException {
          rethrow;
        } catch (_) {}
      }
      check = check.add(const Duration(days: 1));
    }

    final keys = merged.keys.toList()..sort();
    return keys
        .map((k) => ElectricityDayUsage(date: k, kwh: merged[k]!))
        .toList();
  }

  Future<String> _postForm(
    String path,
    Map<String, String> fields,
    String referer,
  ) async {
    final resp = await _httpClient.postForm(
      CampusEndpoints.electricityH5Base + path,
      fields,
      headers: {
        'User-Agent': CampusEndpoints.desktopUserAgent,
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': referer,
      },
      followRedirects: false,
      useCookies: true,
    );
    if (resp.statusCode == 401 || resp.statusCode == 403 || resp.isRedirect) {
      throw const SessionExpiredException('宿舍缴费会话已失效');
    }
    if (!resp.isSuccessful) {
      throw HttpException('宿舍缴费系统返回 ${resp.statusCode}');
    }
    if (!resp.body.trimLeft().startsWith('{')) {
      throw const SessionExpiredException('宿舍缴费会话已失效');
    }
    return resp.body;
  }

  Map<String, dynamic> _parseObject(String json) {
    final decoded = jsonDecode(json);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('宿舍缴费返回数据无法解析');
    }
    return decoded;
  }

  static _MeterSelection? _selectMeter(List<dynamic> houses) {
    _MeterSelection? best;
    var bestScore = -100;
    for (final h in houses) {
      if (h is! Map<String, dynamic>) continue;
      final houseId = '${h['house_id'] ?? ''}'.trim();
      final meters = (h['meter'] as List?) ?? const [];
      for (final m in meters) {
        if (m is! Map<String, dynamic>) continue;
        final meterId = '${m['meter_id'] ?? ''}'.trim();
        if (houseId.isEmpty || meterId.isEmpty) continue;
        final balance = double.tryParse('${m['meter_balance'] ?? ''}');
        final hint =
            '${m['meter_type'] ?? ''} ${m['meter_name'] ?? ''} ${m['meter_no'] ?? ''} ${m['remark'] ?? ''}'
                .toLowerCase();
        var score = 0;
        if (hint.contains('电') || hint.contains('elec')) score += 10;
        if (hint.contains('水') || hint.contains('water')) score -= 5;
        if (balance != null) score += 2;
        if (score > bestScore) {
          bestScore = score;
          best = _MeterSelection(
            houseId: houseId,
            meterId: meterId,
            houseLabel: '${h['house_no'] ?? h['customer_no'] ?? ''}'.trim(),
            houseAddress: '${m['house_addr'] ?? h['house_addr'] ?? ''}'.trim(),
            balance: balance,
          );
        }
      }
    }
    return best;
  }

  static String formatQueryDate(DateTime date) =>
      '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';

  static String? extractUserId(String html) {
    const invalid = {'null', 'undefined', 'none', 'false', 'true'};
    final patterns = [
      RegExp(r'''userid\s*=\s*['"]?([A-Za-z0-9_-]{4,32})''',
          caseSensitive: false),
      RegExp(r'''"userid"\s*:\s*"([A-Za-z0-9_-]{4,32})"''',
          caseSensitive: false),
    ];
    for (final p in patterns) {
      final v = p.firstMatch(html)?.group(1);
      if (v != null && v.isNotEmpty && !invalid.contains(v.toLowerCase())) {
        return v;
      }
    }
    return null;
  }

  static bool looksLikeSessionError(String msg) {
    final lower = msg.toLowerCase();
    return const ['登录', '失效', '过期', '超时', '认证', 'session', 'login']
        .any(lower.contains);
  }

  static List<ElectricityDayUsage> parseDosageRangeJson(String json) {
    try {
      final root = jsonDecode(json);
      if (root is! Map<String, dynamic>) return const [];
      if ('${root['ret'] ?? ''}' != '1') {
        final msg = '${root['msg'] ?? ''}';
        if (looksLikeSessionError(msg)) {
          throw SessionExpiredException('宿舍缴费会话已失效：$msg');
        }
        return const [];
      }
      final data = root['data'];
      if (data is! Map<String, dynamic>) return const [];
      final list = data['list'];
      if (list is! List) return const [];
      final result = <ElectricityDayUsage>[];
      for (final item in list) {
        if (item is! Map<String, dynamic>) continue;
        final rawDate =
            '${item['data_date'] ?? item['dataDate'] ?? item['date'] ?? ''}'
                .trim();
        final normDate = normalizeDate(rawDate);
        if (normDate == null) continue;
        final usage = double.tryParse(
          '${item['energy_used'] ?? item['energyUsed'] ?? item['dosage'] ?? item['usage'] ?? ''}',
        );
        if (usage == null || usage <= 0) continue;
        result.add(ElectricityDayUsage(date: normDate, kwh: usage));
      }
      return result;
    } on SessionExpiredException {
      rethrow;
    } catch (_) {
      return const [];
    }
  }

  static double? parseSingleDayDosage(String json) {
    try {
      final root = jsonDecode(json);
      if (root is! Map<String, dynamic>) return null;
      if ('${root['ret'] ?? ''}' != '1') {
        final msg = '${root['msg'] ?? ''}';
        if (looksLikeSessionError(msg)) {
          throw SessionExpiredException('宿舍缴费会话已失效：$msg');
        }
        return null;
      }
      final data = root['data'];
      if (data is Map<String, dynamic>) {
        final list = (data['list'] as List?) ?? const [];
        var sum = 0.0;
        for (final item in list) {
          if (item is Map<String, dynamic>) {
            sum += double.tryParse(
                  '${item['energy_used'] ?? item['energyUsed'] ?? item['dosage'] ?? 0}',
                ) ??
                0.0;
          }
        }
        if (sum > 0) return sum;
        final total = double.tryParse('${data['totle'] ?? data['total'] ?? ''}');
        return (total != null && total > 0) ? total : null;
      }
      final direct = double.tryParse('$data');
      return (direct != null && direct > 0) ? direct : null;
    } on SessionExpiredException {
      rethrow;
    } catch (_) {
      return null;
    }
  }

  static String? normalizeDate(String raw) {
    final cleaned = raw.trim().split(' ').first.split('T').first;
    if (cleaned.isEmpty) return null;
    if (RegExp(r'^\d{8}$').hasMatch(cleaned)) {
      return '${cleaned.substring(0, 4)}-${cleaned.substring(4, 6)}-${cleaned.substring(6, 8)}';
    }
    final m = RegExp(r'^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$').firstMatch(cleaned);
    if (m == null) return null;
    return '${m.group(1)}-${m.group(2)!.padLeft(2, '0')}-${m.group(3)!.padLeft(2, '0')}';
  }
}

class _MeterSelection {
  final String houseId;
  final String meterId;
  final String houseLabel;
  final String houseAddress;
  final double? balance;

  const _MeterSelection({
    required this.houseId,
    required this.meterId,
    required this.houseLabel,
    required this.houseAddress,
    required this.balance,
  });
}
