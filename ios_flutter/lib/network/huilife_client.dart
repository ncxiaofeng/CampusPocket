import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';

import '../core/campus_endpoints.dart';
import '../core/exceptions.dart';
import 'campus_http_client.dart';

enum HuiLifePlatform { account, app }

class HuiLifeSession {
  final String token;
  final String uid;
  final String eid;

  const HuiLifeSession({
    required this.token,
    required this.uid,
    required this.eid,
  });
}

class HuiLifeDevice {
  final String id;
  final String name;
  final String detail;
  final String typeCode;

  const HuiLifeDevice({
    required this.id,
    required this.name,
    required this.detail,
    this.typeCode = '',
  });
}

class HuiLifeDeviceStatus {
  final int deviceStatus;
  final int geneStatus;
  final int endTimeMillis;
  final String deviceName;
  final String deviceDetail;
  final String usingUserId;

  const HuiLifeDeviceStatus({
    required this.deviceStatus,
    required this.geneStatus,
    this.endTimeMillis = 0,
    this.deviceName = '',
    this.deviceDetail = '',
    this.usingUserId = '',
  });

  String get label {
    if (deviceStatus == 0) return '离线';
    if (geneStatus == 98) return '禁用';
    if (geneStatus == 99) return '在线';
    if (geneStatus == 1 || geneStatus == 30) return '启动中';
    return '使用中';
  }

  bool get canStart => deviceStatus != 0 && geneStatus == 99;
  bool get isRunning =>
      deviceStatus != 0 && geneStatus != 99 && geneStatus != 98;
}

class HuiLifeWallet {
  final String name;
  final double balance;

  const HuiLifeWallet({required this.name, required this.balance});
}

class HuiLifeBill {
  final String id;
  final String title;
  final double amount;
  final int timeMillis;
  final String deviceId;
  final String deviceName;

  const HuiLifeBill({
    required this.id,
    required this.title,
    required this.amount,
    required this.timeMillis,
    this.deviceId = '',
    this.deviceName = '',
  });
}

/// 慧生活798 二维码/设备编号解析器（对齐 Android `HuiLifeDeviceIdParser`）。
class HuiLifeDeviceIdParser {
  static final RegExp _digits = RegExp(r'^\d+$');
  static final RegExp _devFmt = RegExp(r'^[0-9a-z]{12,16}$');
  static final RegExp _flexId = RegExp(r'^[A-Za-z0-9][A-Za-z0-9_-]{5,63}$');
  static final RegExp _appLink =
      RegExp(r'^ilife798s://i/device/(nav|nfc)\?id=[^&#]+');
  static final RegExp _qrUseLink =
      RegExp(r'^https?://.+?/q/\d+/(b|s|billing|vip|wallet)\?id=[^&#]+');
  static final RegExp _bmLink =
      RegExp(r'^https?://.+?/q/\d+/bm\?/[A-Za-z0-9-]+');
  static final RegExp _genericQrLink =
      RegExp(r'^https?://.+?/q/\d+/[A-Za-z0-9-]+');
  static final RegExp _keyPattern = RegExp(
    r'''(?:[?&#;,\s{]|^)["']?(?:did|deviceId|device_id|id)["']?\s*(?:=|:)\s*["']?([^&#/,}\s"']+)''',
    caseSensitive: false,
  );

  static ({String? deviceId, String? qrId}) parse(String raw) {
    final input = _decode(raw.trim());
    if (input.isEmpty) return (deviceId: null, qrId: null);

    if (_digits.hasMatch(input) || _devFmt.hasMatch(input)) {
      return (deviceId: input, qrId: null);
    }
    if (_appLink.hasMatch(input)) {
      return (deviceId: _queryParam(input, 'id'), qrId: null);
    }
    if (_qrUseLink.hasMatch(input) || _bmLink.hasMatch(input)) {
      var id = _queryParam(input, 'id');
      if (id.isEmpty && input.contains('/bm?/')) {
        id = input.split('/bm?/').last.split('?').first;
      }
      return (deviceId: null, qrId: id.isEmpty ? null : id);
    }
    if (_genericQrLink.hasMatch(input)) {
      final cleaned = input.replaceFirst(RegExp(r'/+$'), '');
      final id = cleaned.split('/').last.split('?').first;
      return (deviceId: id.isEmpty ? null : id, qrId: null);
    }
    final m = _keyPattern.firstMatch(input)?.group(1)?.trim();
    if (m != null && m.isNotEmpty) {
      final clean = m
          .replaceAll(RegExp('^["\']|["\']\$'), '')
          .replaceFirst(RegExp('^did:', caseSensitive: false), '');
      if (clean.isNotEmpty) return (deviceId: clean, qrId: null);
    }
    if (_flexId.hasMatch(input) && RegExp(r'\d').hasMatch(input)) {
      return (deviceId: input, qrId: null);
    }
    return (deviceId: null, qrId: null);
  }

  static String _queryParam(String url, String key) {
    final uri = Uri.tryParse(url);
    if (uri != null) {
      for (final entry in uri.queryParameters.entries) {
        if (entry.key.toLowerCase() == key.toLowerCase()) {
          return _decode(entry.value);
        }
      }
    }
    return '';
  }

  static String _decode(String v) {
    try {
      return Uri.decodeComponent(v);
    } catch (_) {
      return v;
    }
  }
}

/// 慧生活798 客户端：查询端免配置（1,5）+ 设备控制端（1,1，支持扫码启动与本人安全停止）。
class HuiLifeClient {
  HuiLifeClient({
    required CampusHttpClient httpClient,
    required String Function() appSignSaltProvider,
  })  : _httpClient = httpClient,
        _appSignSaltProvider = appSignSaltProvider;

  static const String _uaAccount = 'WaterWidget/5.5.0 (Android)';
  static const String _uaApp = 'Android_ilife798_3.1.9';
  static const String _randChars =
      'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  static final Random _random = Random.secure();

  final CampusHttpClient _httpClient;
  final String Function() _appSignSaltProvider;

  static String newCaptchaKey() => List.generate(
        10,
        (_) => _randChars[_random.nextInt(_randChars.length)],
      ).join();

  static String signAppLogin(String phone, String smsCode, String salt) {
    final raw = 'authCode=$smsCode&un=$phone$salt';
    return md5.convert(utf8.encode(raw)).toString();
  }

  /// 本人安全停止守卫：只有当前设备运行中，且为本机 30 分钟内启动或服务端使用人匹配本人时允许停止。
  static bool canSafelyStop({
    required HuiLifeDeviceStatus status,
    required String currentUid,
    required int? localStartedAtMillis,
    int? nowMillis,
  }) {
    if (!status.isRunning) return false;
    final now = nowMillis ?? DateTime.now().millisecondsSinceEpoch;
    final startedByLocal = localStartedAtMillis != null &&
        now - localStartedAtMillis >= 0 &&
        now - localStartedAtMillis <= 30 * 60 * 1000;
    final uidMatches = currentUid.isNotEmpty &&
        status.usingUserId.isNotEmpty &&
        currentUid == status.usingUserId;
    return startedByLocal || uidMatches;
  }

  Future<List<int>> fetchCaptchaImage(String sessionKey) {
    final url =
        '${CampusEndpoints.huiLifeBaseUrl}/captcha/?s=${Uri.encodeQueryComponent(sessionKey)}&r=${DateTime.now().millisecondsSinceEpoch}';
    return _httpClient.getBytes(
      url,
      headers: const {'User-Agent': _uaAccount},
    );
  }

  Future<void> sendSms({
    required String phone,
    required String graphCode,
    required String sessionKey,
    required HuiLifePlatform platform,
  }) async {
    final json = await _postJson(
      '/acc/login/code',
      {'un': phone, 'authCode': graphCode, 's': sessionKey},
      platform,
      null,
    );
    _requireSuccess(json, '短信发送失败');
  }

  Future<HuiLifeSession> login({
    required String phone,
    required String smsCode,
    required HuiLifePlatform platform,
  }) async {
    final body = <String, dynamic>{
      'authCode': smsCode,
      'un': phone,
      if (platform == HuiLifePlatform.account) ...{
        'openCode': '',
        'cid': CampusEndpoints.huiLifeCid,
      },
    };
    var path = '/acc/login';
    if (platform == HuiLifePlatform.app) {
      final salt = _appSignSaltProvider().trim();
      if (salt.isEmpty) {
        throw const ConfigurationRequiredException(
          '未配置慧生活设备登录盐：请前往「更多 → 高级配置」导入，或使用免配置的查询端登录',
        );
      }
      path = '/acc/login?sign=${signAppLogin(phone, smsCode, salt)}';
    }
    final json = await _postJson(path, body, platform, null);
    _requireSuccess(json, '登录失败');
    final data = (json['data'] as Map<String, dynamic>?) ?? const {};
    final al = (data['al'] as Map<String, dynamic>?) ?? const {};
    final token = '${al['token'] ?? ''}'.trim();
    if (token.isEmpty) throw const HttpException('登录返回缺少 token');
    return HuiLifeSession(
      token: token,
      uid: '${al['uid'] ?? ''}',
      eid: '${al['eid'] ?? ''}',
    );
  }

  Future<HuiLifeWallet?> fetchWallet(
    String token,
    HuiLifePlatform platform,
  ) async {
    final json = await _getJson('/acc/wallet/owner', platform, token);
    _requireSuccess(json, '钱包读取失败');
    final data = (json['data'] as Map<String, dynamic>?) ?? const {};
    Map<String, dynamic>? wallet;
    if (data['aw'] is Map<String, dynamic>) {
      wallet = data['aw'] as Map<String, dynamic>;
    } else if (data['eps'] is List && (data['eps'] as List).isNotEmpty) {
      final first = (data['eps'] as List).first;
      if (first is Map<String, dynamic>) wallet = first;
    }
    if (wallet == null) return null;
    final ep = (wallet['ep'] as Map<String, dynamic>?) ?? const {};
    final bal = double.tryParse(
          '${wallet['total'] ?? wallet['olCash'] ?? wallet['balance'] ?? 0}',
        ) ??
        0.0;
    final name = '${ep['name'] ?? ''}'.trim();
    return HuiLifeWallet(name: name.isEmpty ? '校园钱包' : name, balance: bal);
  }

  Future<List<HuiLifeDevice>> fetchMaster(
    String token,
    HuiLifePlatform platform,
  ) async {
    final json = await _getJson('/ui/app/master', platform, token);
    _requireSuccess(json, '设备列表读取失败');
    final data = (json['data'] as Map<String, dynamic>?) ?? const {};
    final list = (data['favos'] ?? data['devices'] ?? data['list']) as List? ??
        const [];
    final result = <HuiLifeDevice>[];
    for (final item in list) {
      if (item is Map<String, dynamic>) {
        final dev = parseDeviceObject(item, fallbackName: '未命名设备');
        if (dev != null) result.add(dev);
      }
    }
    return result;
  }

  Future<List<HuiLifeBill>> fetchBills(
    String token,
    HuiLifePlatform platform,
  ) async {
    final json =
        await _getJson('/bill/lst-owner?page=0&size=20', platform, token);
    _requireSuccess(json, '消费记录读取失败');
    final data = json['data'];
    List<dynamic> list = const [];
    if (data is List) {
      list = data;
    } else if (data is Map<String, dynamic>) {
      list = (data['list'] ?? data['records']) as List? ?? const [];
    }
    final result = <HuiLifeBill>[];
    for (final item in list) {
      if (item is! Map<String, dynamic>) continue;
      final amt = double.tryParse(
        '${item['payment'] ?? item['amount'] ?? item['total'] ?? ''}',
      );
      if (amt == null) continue;
      final rawTime = int.tryParse('${item['utime'] ?? item['ctime'] ?? 0}') ?? 0;
      final ms = rawTime > 0 && rawTime < 10000000000 ? rawTime * 1000 : rawTime;
      final dev = (item['dev'] as Map<String, dynamic>?) ?? const {};
      final devName = '${dev['name'] ?? ''}'.trim();
      result.add(
        HuiLifeBill(
          id: '${item['id'] ?? '$ms|$amt'}',
          title: devName.isNotEmpty ? devName : '设备消费',
          amount: amt,
          timeMillis: ms,
          deviceId: '${dev['id'] ?? ''}'.trim(),
          deviceName: devName,
        ),
      );
    }
    return result;
  }

  Future<HuiLifeDeviceStatus> fetchDeviceStatus(
    String token,
    HuiLifePlatform platform,
    String deviceId, {
    bool more = false,
  }) async {
    final json = await _getJson(
      '/ui/app/dev/status?did=${Uri.encodeQueryComponent(deviceId)}&more=$more',
      platform,
      token,
    );
    _requireSuccess(json, '设备状态读取失败');
    return parseDeviceStatus(json);
  }

  Future<HuiLifeDevice> resolveQrDevice(String token, String qrId) async {
    final json = await _getJson(
      '/qr/use?id=${Uri.encodeQueryComponent(qrId)}',
      HuiLifePlatform.app,
      token,
    );
    _requireSuccess(json, '二维码解析失败');
    final data = (json['data'] as Map<String, dynamic>?) ?? const {};
    final dev = (data['dev'] ?? data['device'] ?? data) as Map<String, dynamic>;
    final parsed = parseDeviceObject(dev, fallbackName: '扫码设备');
    if (parsed == null) throw const HttpException('二维码里没有设备编号');
    return parsed;
  }

  Future<void> startDevice(String appToken, String deviceId) async {
    final json = await _getJson(
      '/dev/start?did=${Uri.encodeQueryComponent(deviceId)}&upgrade=true&ptype=91&rcp=false&cnt=1',
      HuiLifePlatform.app,
      appToken,
    );
    _requireSuccess(json, '启动失败');
  }

  Future<void> stopDevice(String appToken, String deviceId) async {
    final json = await _getJson(
      '/dev/end?did=${Uri.encodeQueryComponent(deviceId)}&rcp=false',
      HuiLifePlatform.app,
      appToken,
    );
    _requireSuccess(json, '停止失败');
  }

  Future<Map<String, dynamic>> _getJson(
    String path,
    HuiLifePlatform platform,
    String token,
  ) async {
    final resp = await _httpClient.get(
      CampusEndpoints.huiLifeBaseUrl + path,
      headers: _headers(platform, token),
      useCookies: false,
    );
    if (!resp.isSuccessful) {
      throw HttpException('慧生活返回 HTTP ${resp.statusCode}');
    }
    return jsonDecode(resp.body) as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> _postJson(
    String path,
    Object body,
    HuiLifePlatform platform,
    String? token,
  ) async {
    final resp = await _httpClient.postJson(
      CampusEndpoints.huiLifeBaseUrl + path,
      body,
      headers: _headers(platform, token),
      useCookies: false,
    );
    if (!resp.isSuccessful) {
      throw HttpException('慧生活返回 HTTP ${resp.statusCode}');
    }
    return jsonDecode(resp.body) as Map<String, dynamic>;
  }

  Map<String, String> _headers(HuiLifePlatform platform, String? token) => {
        'Content-Type': 'application/json',
        'Accept-Language': 'zh-Hans-CN;q=1',
        'ApplicationType': platform == HuiLifePlatform.app ? '1,1' : '1,5',
        'VersionCode': platform == HuiLifePlatform.app ? '3.1.9' : '2.0.83',
        'User-Agent': platform == HuiLifePlatform.app ? _uaApp : _uaAccount,
        if (token != null && token.isNotEmpty) 'Authorization': token,
      };

  static void _requireSuccess(Map<String, dynamic> json, String fallback) {
    final code = _int(json['code']);
    if (code == -99) throw const HuiLifeTokenExpiredException();
    if (code != 0) {
      final msg = '${json['msg'] ?? ''}'.trim();
      throw HttpException('${msg.isEmpty ? fallback : msg}（code=$code）');
    }
  }

  static HuiLifeDevice? parseDeviceObject(
    Map<String, dynamic> item, {
    String fallbackName = '',
  }) {
    final id = _firstNonEmpty(item, const ['id', 'did', 'deviceId', 'devId']);
    if (id.isEmpty) return null;
    var name = _firstNonEmpty(item, const [
      'name',
      'devName',
      'deviceName',
      'dname',
      'alias',
      'title',
    ]);
    if (name.isEmpty || name == '新注册设备' || name == '未命名设备') {
      final subs = (item['subs'] as List?) ?? const [];
      for (final s in subs) {
        if (s is Map<String, dynamic>) {
          final sn = _firstNonEmpty(s, const ['name', 'dname', 'devName']);
          if (sn.isNotEmpty && sn != '新注册设备') {
            name = sn;
            break;
          }
        }
      }
    }
    final ep = (item['ep'] as Map<String, dynamic>?) ?? const {};
    if (name.isEmpty) name = '${ep['name'] ?? ''}'.trim();
    if (name.isEmpty) name = fallbackName;
    final detail = _firstNonEmpty(
      item,
      const ['addr', 'address', 'position', 'location', 'areaName'],
    );
    return HuiLifeDevice(
      id: id,
      name: name,
      detail: detail.isNotEmpty ? detail : '${ep['name'] ?? ''}'.trim(),
      typeCode: _firstNonEmpty(item, const ['ltype', 'devType', 'type']),
    );
  }

  static HuiLifeDeviceStatus parseDeviceStatus(Map<String, dynamic> json) {
    final data = (json['data'] as Map<String, dynamic>?) ?? const {};
    final dev = (data['device'] ?? data['dev']) as Map<String, dynamic>?;
    if (dev == null) throw const FormatException('设备状态缺少 device');
    final gene = (dev['gene'] as Map<String, dynamic>?) ?? const {};
    final order = (data['order'] as Map<String, dynamic>?) ?? const {};
    final rawTime = _int(gene['time']);
    final endMs =
        rawTime > 0 && rawTime < 100000000000 ? rawTime * 1000 : rawTime;
    var usingUid = '';
    for (final obj in [gene, order, dev, data]) {
      final u = _firstNonEmpty(
        obj,
        const ['uid', 'userId', 'ownerId', 'accountId'],
      );
      if (u.isNotEmpty) {
        usingUid = u;
        break;
      }
    }
    final parsedDev = parseDeviceObject(dev);
    return HuiLifeDeviceStatus(
      deviceStatus: _int(dev['status']),
      geneStatus: _int(gene['status']),
      endTimeMillis: endMs,
      deviceName: parsedDev?.name ?? '',
      deviceDetail: parsedDev?.detail ?? '',
      usingUserId: usingUid,
    );
  }

  static String _firstNonEmpty(Map<String, dynamic> map, List<String> keys) {
    for (final k in keys) {
      final v = map[k];
      if (v != null && v is! Map && v is! List) {
        final s = v.toString().trim();
        if (s.isNotEmpty) return s;
      }
    }
    return '';
  }

  static int _int(Object? v) => v is num ? v.toInt() : int.tryParse('$v') ?? 0;
}
