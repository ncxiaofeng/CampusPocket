import 'dart:convert';
import 'dart:io';

import '../core/campus_endpoints.dart';
import '../data/models.dart';
import 'campus_http_client.dart';

/// 图书馆空间预约（libspace）免登录空位只读客户端。
class LibrarySeatClient {
  LibrarySeatClient({
    required CampusHttpClient httpClient,
    this.baseUrl = CampusEndpoints.libraryBaseUrl,
  }) : _httpClient = httpClient;

  final CampusHttpClient _httpClient;
  final String baseUrl;

  Future<LibrarySeatSnapshot> fetchAvailability({
    required String day,
    String? startTime,
    String? endTime,
  }) async {
    final payload = <String, dynamic>{
      'day': day,
      if (startTime != null) 'start_time': startTime,
      if (endTime != null) 'end_time': endTime,
    };
    final resp = await _httpClient.postJson(
      '$baseUrl/v4/space/pcTopFor',
      payload,
      useCookies: false,
    );
    if (!resp.isSuccessful) {
      throw HttpException('图书馆系统返回 ${resp.statusCode}');
    }
    return parseSnapshot(resp.body);
  }

  static LibrarySeatSnapshot parseSnapshot(String body) {
    final root = jsonDecode(body);
    if (root is! Map<String, dynamic>) {
      throw const FormatException('图书馆返回数据无法解析');
    }
    final code = (root['code'] is num)
        ? (root['code'] as num).toInt()
        : int.tryParse('${root['code']}') ?? -1;
    if (code != 0) {
      throw HttpException('${root['message'] ?? '图书馆系统返回错误'}');
    }
    final data = (root['data'] is Map<String, dynamic>)
        ? root['data'] as Map<String, dynamic>
        : const <String, dynamic>{};
    final dates = ((data['date'] as List?) ?? const [])
        .where((e) => e != null)
        .map((e) => e.toString())
        .toList();
    final campuses = ((data['list'] as List?) ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(_toArea)
        .toList();
    return LibrarySeatSnapshot(dates: dates, campuses: campuses);
  }

  static LibrarySeatArea _toArea(Map<String, dynamic> item) {
    final children = ((item['children'] as List?) ?? const [])
        .whereType<Map<String, dynamic>>()
        .map(_toArea)
        .toList();
    return LibrarySeatArea(
      id: '${item['id'] ?? ''}',
      name: '${item['name'] ?? ''}',
      free: _asInt(item['free_num']),
      total: _asInt(item['total_num']),
      sort: _asInt(item['sort']),
      children: children,
    );
  }

  static int _asInt(Object? raw) {
    if (raw is num) return raw.toInt();
    return int.tryParse('$raw') ?? 0;
  }
}
