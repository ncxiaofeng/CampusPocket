import 'dart:convert';
import 'dart:io';

import '../core/campus_endpoints.dart';
import '../core/exceptions.dart';
import '../data/models.dart';
import 'campus_http_client.dart';
import 'cas_sso_manager.dart';

/// ehall 一卡通只读接口，支持 401/403 自动用 CASTGC 重建会话并预热 psxykapp 上下文。
class CampusCardClient {
  CampusCardClient({
    required CampusHttpClient httpClient,
    this.baseUrl = CampusEndpoints.ehallBaseUrl,
    CasSsoManager? ssoManager,
  })  : _httpClient = httpClient,
        _ssoManager = ssoManager;

  static const String _cardAppId = '5476902092873882';

  final CampusHttpClient _httpClient;
  final String baseUrl;
  final CasSsoManager? _ssoManager;

  Future<CampusCardOverview> fetchOverview() async {
    final now = DateTime.now();
    final ym = '${now.year}-${now.month.toString().padLeft(2, '0')}';
    final info = await _postData(
      '/psfw/sys/psxykapp/modules/WisCardViewController/getWisCardInformation.do',
      jsonEncode({'XFNY': ym}),
    );
    final infoData = _map(info['data']);
    final csxx = _map(infoData['CSXX']);
    final csdm = _str(csxx, 'CSDM');
    final jkfs = _str(csxx, 'JKFS');

    final base = await _postData(
      '/psfw/sys/psxykapp/modules/CampusCardActionController/queryBaseInfo.do',
      jsonEncode({'CSDM': csdm, 'JKFS': jkfs}),
    );
    final baseData = _map(base['data']);
    final month = _map(infoData['MONTHBILL']);
    return CampusCardOverview(
      balance: _double(baseData, 'BALANCE'),
      cardState: _str(baseData, 'CARD_STATE'),
      cardNumber: _str(baseData, 'CARD_ACCNUM'),
      monthSpend: _double(month, 'XFJE'),
      monthRecharge: _double(month, 'CZJE'),
      monthSpendCount: _int(month, 'XFZS'),
      monthRechargeCount: _int(month, 'CZZS'),
    );
  }

  Future<CampusCardBillPage> fetchBills({
    required int page,
    int pageSize = 30,
  }) async {
    final payload = jsonEncode({
      'pagenum': page < 1 ? 1 : page,
      'pagesize': pageSize.clamp(1, 100),
      'querySetting': '[]',
    });
    final root = await _postData(
      '/psfw/sys/psxykapp/modules/WisCardViewController/getWisCardBill.do',
      payload,
    );
    final data = _map(root['data']);
    final list = data['BILL_ARRAY'];
    final items = <CampusCardBill>[];
    if (list is List) {
      for (final item in list) {
        if (item is Map<String, dynamic>) {
          items.add(
            CampusCardBill(
              time: _str(item, 'SJ'),
              location: _str(item, 'ZDMC'),
              amount: _double(item, 'JE'),
              isRecharge: _str(item, 'XFFS') == '1',
              machine: _str(item, 'MC'),
              typeCode: _str(item, 'LXDM'),
            ),
          );
        }
      }
    }
    final countMap = _map(data['COUNT']);
    final total = _int(countMap, 'COUNT');
    return CampusCardBillPage(
      items: items,
      total: total > 0 ? total : items.length,
    );
  }

  Future<Map<String, dynamic>> _postData(String path, String payload) async {
    var resp = await _postDataRaw(path, payload);
    if (resp.sessionExpired) {
      final renewed = await _ssoManager
              ?.ensureServiceSession(CampusEndpoints.ehallCardServiceUrl) ??
          false;
      if (!renewed) {
        throw const SessionExpiredException('统一认证已过期，请重新登录');
      }
      await _primeCardPage();
      resp = await _postDataRaw(path, payload);
      if (resp.sessionExpired) {
        throw const SessionExpiredException('一卡通接口拒绝了当前会话，请打开校园卡官方页刷新一次');
      }
    }
    final decoded = jsonDecode(resp.body);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('一卡通返回数据无法解析');
    }
    final code = '${decoded['code'] ?? ''}';
    if (code != '0') {
      throw HttpException('${decoded['msg'] ?? '一卡通系统返回错误'}');
    }
    return decoded;
  }

  Future<void> _primeCardPage() async {
    final urls = [
      CampusEndpoints.ehallCardServiceUrl,
      '$baseUrl/psfw/sys/xgpspubapp/indexmenu/getAppConfig.do?appId=$_cardAppId&appName=psxykapp',
      '$baseUrl/psfw/sys/emappagelog/config/psxykapp.do',
    ];
    for (final url in urls) {
      try {
        await _httpClient.get(
          url,
          headers: const {
            'User-Agent': CampusEndpoints.desktopUserAgent,
            'Referer': CampusEndpoints.ehallCardServiceUrl,
          },
          followRedirects: true,
          useCookies: true,
        );
      } catch (_) {}
    }
  }

  Future<_CardRawResponse> _postDataRaw(String path, String payload) async {
    final resp = await _httpClient.postForm(
      baseUrl + path,
      {'data': payload},
      headers: const {
        'User-Agent': CampusEndpoints.desktopUserAgent,
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': CampusEndpoints.ehallCardServiceUrl,
      },
      followRedirects: false,
      useCookies: true,
    );
    if (resp.statusCode == 401 || resp.statusCode == 403 || resp.isRedirect) {
      return _CardRawResponse(resp.body, true);
    }
    if (!resp.isSuccessful) {
      throw HttpException('一卡通系统返回 ${resp.statusCode}');
    }
    final text = resp.body;
    final expired = text.contains('authserver') ||
        text.contains('login_slogin') ||
        text.trimLeft().startsWith('<');
    return _CardRawResponse(text, expired);
  }

  static Map<String, dynamic> _map(Object? raw) =>
      raw is Map<String, dynamic> ? raw : const {};

  static String _str(Map<String, dynamic> map, String key) =>
      map[key]?.toString().trim() ?? '';

  static double _double(Map<String, dynamic> map, String key) {
    final v = map[key];
    if (v is num) return v.toDouble();
    return double.tryParse('$v') ?? 0.0;
  }

  static int _int(Map<String, dynamic> map, String key) {
    final v = map[key];
    if (v is num) return v.toInt();
    return int.tryParse('$v') ?? 0;
  }
}

class _CardRawResponse {
  final String body;
  final bool sessionExpired;

  const _CardRawResponse(this.body, this.sessionExpired);
}
