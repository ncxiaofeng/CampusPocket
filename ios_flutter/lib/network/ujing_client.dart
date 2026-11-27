import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';

import '../core/campus_endpoints.dart';
import '../core/exceptions.dart';
import 'campus_http_client.dart';

class UjingCampus {
  final String id;
  final String name;
  final double latitude;
  final double longitude;

  const UjingCampus(this.id, this.name, this.latitude, this.longitude);
}

const List<UjingCampus> kUjingCampuses = [
  UjingCampus('guanglan', '广兰校区', 28.718795, 115.825926),
  UjingCampus('qinglan', '青岚校区', 28.7202, 115.8203),
  UjingCampus('fuzhou', '抚州校区', 27.94705, 116.41202),
];

class UjingStore {
  final String id;
  final String name;
  final double distanceKm;
  final int deviceNum;
  final String serviceSubjectName;
  final String address;
  final int washerCount;
  final int dryerCount;

  const UjingStore({
    required this.id,
    required this.name,
    required this.distanceKm,
    required this.deviceNum,
    required this.serviceSubjectName,
    required this.address,
    required this.washerCount,
    required this.dryerCount,
  });
}

class UjingDeviceTag {
  final int deviceTypeId;
  final String name;
  final int total;
  final int idle;

  const UjingDeviceTag({
    required this.deviceTypeId,
    required this.name,
    required this.total,
    required this.idle,
  });
}

class UjingStoreInfo {
  final String storeId;
  final String storeName;
  final String serviceSubjectName;
  final List<UjingDeviceTag> devices;

  const UjingStoreInfo({
    required this.storeId,
    required this.storeName,
    required this.serviceSubjectName,
    required this.devices,
  });
}

class UjingProgram {
  final int workModelId;
  final String name;
  final int priceCents;
  final int minutes;
  final String description;

  const UjingProgram({
    required this.workModelId,
    required this.name,
    required this.priceCents,
    required this.minutes,
    required this.description,
  });
}

class UjingOrder {
  final int orderId;
  final String deviceTypeName;
  final String deviceNo;
  final String storeName;
  final String createAt;
  final String status;
  final String statusRemark;
  final int remainTime;

  const UjingOrder({
    required this.orderId,
    required this.deviceTypeName,
    required this.deviceNo,
    required this.storeName,
    required this.createAt,
    required this.status,
    required this.statusRemark,
    required this.remainTime,
  });
}

/// 从 U净 官方 H5 返回值里安全提取并校验 JWT。
class UjingTokenExtractor {
  static final RegExp _jwtRegex =
      RegExp(r'^[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}$');

  static String? extractFromJsResult(Object? raw) {
    if (raw == null) return null;
    var text = raw.toString().trim();
    if (text.isEmpty ||
        text == 'null' ||
        text == '""' ||
        text == '"null"' ||
        text == '"undefined"') {
      return null;
    }
    if (text.startsWith('"') && text.endsWith('"') && text.length >= 2) {
      try {
        final decoded = jsonDecode(text);
        if (decoded is String) text = decoded;
      } catch (_) {
        text = text.substring(1, text.length - 1);
      }
    }
    return sanitizeJwt(text);
  }

  static String? sanitizeJwt(String? candidate) {
    if (candidate == null) return null;
    var cleaned = candidate.trim();
    if (cleaned.toLowerCase().startsWith('bearer ')) {
      cleaned = cleaned.substring(7).trim();
    }
    if (cleaned.length < 30 || cleaned.length > 4096) return null;
    return _jwtRegex.hasMatch(cleaned) ? cleaned : null;
  }
}

/// U净洗衣只读接口（只查门店空闲数、等待时间、方案与订单，不下单/支付）。
class UjingClient {
  UjingClient({
    required CampusHttpClient httpClient,
    required String Function() hmacKeyProvider,
  })  : _httpClient = httpClient,
        _hmacKeyProvider = hmacKeyProvider;

  static const String _appCode = 'BO';
  static const String _appVersion = '1.1.4';
  static final Random _random = Random.secure();

  final CampusHttpClient _httpClient;
  final String Function() _hmacKeyProvider;

  Future<void> sendCaptcha(String mobile) async {
    final hmacKey = _hmacKeyProvider().trim();
    if (hmacKey.isEmpty) {
      throw const ConfigurationRequiredException(
        '未配置 U净 HMAC key：请使用「官方网页免配置登录」，或在「更多 → 高级配置」导入',
      );
    }
    final nonce = List.generate(
      16,
      (_) => _random.nextInt(256).toRadixString(16).padLeft(2, '0'),
    ).join();
    final timestamp = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    final mac = Hmac(sha256, utf8.encode(hmacKey));
    final sig = base64Encode(mac.convert(utf8.encode('$nonce$timestamp')).bytes);

    final uri = Uri.parse('${CampusEndpoints.ujingBaseUrl}/wechat/captcha/create')
        .replace(queryParameters: {
      'mobile': mobile,
      'type': '1',
      'nonce': nonce,
      'timestamp': '$timestamp',
      'signature': sig,
    });
    await _get(uri.toString(), null);
  }

  Future<String> login(String mobile, String captcha) async {
    final root = await _post(
      '${CampusEndpoints.ujingBaseUrl}/login',
      {'mobile': mobile, 'captcha': captcha},
      null,
    );
    final data = (root['data'] as Map<String, dynamic>?) ?? const {};
    final token = '${data['token'] ?? ''}'.trim();
    if (token.isEmpty) throw const HttpException('U净登录返回缺少 token');
    return token;
  }

  Future<List<UjingStore>> nearbyStores(
    double lat,
    double lng,
    String token,
  ) async {
    final uri = Uri.parse('${CampusEndpoints.ujingBaseUrl}/stores/near')
        .replace(queryParameters: {
      'lat': lat.toString(),
      'lont': lng.toString(),
      'skip': '0',
      'limit': '20',
    });
    final root = await _get(uri.toString(), token);
    final data = (root['data'] as Map<String, dynamic>?) ?? const {};
    final list = (data['storeList'] as List?) ?? const [];
    final stores = <UjingStore>[];
    for (final item in list) {
      if (item is! Map<String, dynamic>) continue;
      var washer = 0;
      var dryer = 0;
      for (final info in (item['storeInfo'] as List?) ?? const []) {
        if (info is! Map<String, dynamic>) continue;
        final cnt = _int(info['access']);
        final cat = _int(info['category']);
        if (cat == 1) washer += cnt;
        if (cat == 2) dryer += cnt;
      }
      final subj = (item['serviceSubject'] as Map<String, dynamic>?) ?? const {};
      stores.add(
        UjingStore(
          id: '${item['id'] ?? ''}',
          name: '${item['name'] ?? ''}',
          distanceKm: _double(item['distance']),
          deviceNum: _int(item['deviceNum']),
          serviceSubjectName: '${subj['name'] ?? ''}',
          address: '${subj['address'] ?? ''}',
          washerCount: washer,
          dryerCount: dryer,
        ),
      );
    }
    stores.sort((a, b) => a.distanceKm.compareTo(b.distanceKm));
    return stores;
  }

  Future<UjingStoreInfo> storeInfo(String storeId, String token) async {
    final uri = Uri.parse('${CampusEndpoints.ujingBaseUrl}/wechat/washer/store/info')
        .replace(queryParameters: {'storeId': storeId});
    final root = await _get(uri.toString(), token);
    final data = (root['data'] as Map<String, dynamic>?) ?? const {};
    final tags = ((data['deviceTags'] as List?) ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(
          (e) => UjingDeviceTag(
            deviceTypeId: _int(e['deviceTypeId']),
            name: '${e['deviceTypeName'] ?? ''}',
            total: _int(e['totalNum']),
            idle: _int(e['idleNum']),
          ),
        )
        .toList();
    return UjingStoreInfo(
      storeId: '${data['storeId'] ?? storeId}',
      storeName: '${data['storeName'] ?? ''}',
      serviceSubjectName: '${data['serviceSubjectName'] ?? ''}',
      devices: tags,
    );
  }

  Future<List<UjingProgram>> programInfo(
    String storeId,
    int deviceTypeId,
    String token,
  ) async {
    final uri = Uri.parse(
      '${CampusEndpoints.ujingBaseUrl}/wechat/washer/devices/program/info',
    ).replace(queryParameters: {
      'storeId': storeId,
      'deviceTypeId': '$deviceTypeId',
    });
    final root = await _get(uri.toString(), token);
    final data = (root['data'] as Map<String, dynamic>?) ?? const {};
    return ((data['deviceWashModel'] as List?) ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(
          (e) => UjingProgram(
            workModelId: _int(e['workModelId']),
            name: '${e['workModelName'] ?? ''}',
            priceCents: _int(e['basePrice']),
            minutes: _int(e['time']),
            description: '${e['description'] ?? ''}',
          ),
        )
        .toList();
  }

  Future<int> waitTime(String storeId, int deviceTypeId, String token) async {
    final uri = Uri.parse(
      '${CampusEndpoints.ujingBaseUrl}/wechat/washer/store/waitTime',
    ).replace(queryParameters: {
      'storeId': storeId,
      'deviceTypeId': '$deviceTypeId',
    });
    final root = await _get(uri.toString(), token);
    final data = (root['data'] as Map<String, dynamic>?) ?? const {};
    return _int(data['waitTime']);
  }

  Future<List<UjingOrder>> runningOrders(String token) async {
    final root =
        await _get('${CampusEndpoints.ujingBaseUrl}/orders/running', token);
    return ((root['data'] as List?) ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(_parseOrder)
        .toList();
  }

  Future<List<UjingOrder>> historyOrders(String token, {int limit = 10}) async {
    final uri = Uri.parse('${CampusEndpoints.ujingBaseUrl}/orders/history')
        .replace(queryParameters: {'skip': '0', 'limit': '$limit'});
    final root = await _get(uri.toString(), token);
    return ((root['data'] as List?) ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(_parseOrder)
        .toList();
  }

  Future<(String storeId, String storeName)?> orderDetailStore(
    int orderId,
    String token,
  ) async {
    final root = await _get(
      '${CampusEndpoints.ujingBaseUrl}/orders/$orderId/detail',
      token,
    );
    final data = (root['data'] as Map<String, dynamic>?) ?? const {};
    final storeId = '${data['storeId'] ?? ''}'.trim();
    if (storeId.isEmpty) return null;
    return (storeId, '${data['storeName'] ?? ''}'.trim());
  }

  static UjingOrder _parseOrder(Map<String, dynamic> e) => UjingOrder(
        orderId: _int(e['orderId']),
        deviceTypeName: '${e['deviceTypeName'] ?? ''}',
        deviceNo: '${e['deviceNo'] ?? ''}',
        storeName: '${e['storeName'] ?? ''}',
        createAt: '${e['createAt'] ?? ''}',
        status: '${e['status'] ?? ''}',
        statusRemark: '${e['statusRemark'] ?? ''}',
        remainTime: _int(e['remainTime']),
      );

  Future<Map<String, dynamic>> _get(String url, String? token) async {
    final resp = await _httpClient.get(
      url,
      headers: {
        'Accept': 'application/json',
        'x-app-code': _appCode,
        'x-app-version': _appVersion,
        if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
      },
      useCookies: false,
    );
    return _unwrap(resp);
  }

  Future<Map<String, dynamic>> _post(
    String url,
    Object payload,
    String? token,
  ) async {
    final resp = await _httpClient.postJson(
      url,
      payload,
      headers: {
        'x-app-code': _appCode,
        'x-app-version': _appVersion,
        if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
      },
      useCookies: false,
    );
    return _unwrap(resp);
  }

  Map<String, dynamic> _unwrap(CampusHttpResponse resp) {
    if (resp.statusCode == 401) {
      throw const SessionExpiredException('U净登录已过期，请重新登录');
    }
    final root = jsonDecode(resp.body);
    if (root is! Map<String, dynamic>) {
      throw const FormatException('U净返回数据无法解析');
    }
    final code = _int(root['code']);
    if (code == 401) throw const SessionExpiredException('U净登录已过期，请重新登录');
    if (code != 0) {
      throw HttpException('${root['message'] ?? 'U净返回错误码 $code'}');
    }
    return root;
  }

  static int _int(Object? v) => v is num ? v.toInt() : int.tryParse('$v') ?? 0;
  static double _double(Object? v) =>
      v is num ? v.toDouble() : double.tryParse('$v') ?? 0.0;
}
