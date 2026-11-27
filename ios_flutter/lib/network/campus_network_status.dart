import '../core/campus_endpoints.dart';
import 'campus_http_client.dart';

enum CampusNetworkState { unknown, checking, onCampus, offCampus }

/// 校园网连通性检测：探测校内两台主机，收到任意 HTTP 响应即判定在校内。
class CampusNetworkStatus {
  CampusNetworkStatus(this._httpClient);

  final CampusHttpClient _httpClient;

  static const List<String> _probeUrls = [
    '${CampusEndpoints.jwBaseUrl}/jwglxt/',
    'http://172.20.134.7:8080/',
  ];

  CampusNetworkState state = CampusNetworkState.unknown;

  bool get isOnCampus => state == CampusNetworkState.onCampus;

  Future<bool> refresh() async {
    state = CampusNetworkState.checking;
    var reachable = false;
    for (var attempt = 0; attempt < 2; attempt++) {
      reachable = await _probeOnce();
      if (reachable) break;
      if (attempt == 0) {
        await Future<void>.delayed(const Duration(milliseconds: 600));
      }
    }
    state = reachable
        ? CampusNetworkState.onCampus
        : CampusNetworkState.offCampus;
    return reachable;
  }

  Future<bool> _probeOnce() async {
    for (final url in _probeUrls) {
      try {
        await _httpClient.head(
          url,
          headers: const {
            'User-Agent': CampusEndpoints.desktopUserAgent,
            'Cache-Control': 'no-cache',
          },
          timeout: const Duration(seconds: 2),
        );
        return true;
      } catch (_) {
        // 试下一个内网地址
      }
    }
    return false;
  }
}
