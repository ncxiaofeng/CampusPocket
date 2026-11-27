import '../core/campus_endpoints.dart';
import '../data/auth_repository.dart';
import 'campus_http_client.dart';

/// 统一身份认证（CAS）单点登录换票管理。
class CasSsoManager {
  CasSsoManager(this._authRepository, this._httpClient);

  final AuthRepository _authRepository;
  final CampusHttpClient _httpClient;
  Future<bool>? _inFlight;

  Future<bool> hasMasterSession() async {
    final header = await _loadCasCookieHeader();
    return hasCasTicket(header);
  }

  Future<int> masterSessionUpdatedAt() async {
    final cred =
        await _authRepository.getCredential(CampusEndpoints.systemCasMaster);
    return cred?.updatedAt ?? 0;
  }

  Future<bool> ensureServiceSession(String serviceUrl) async {
    if (!await hasMasterSession()) return false;
    if (_inFlight != null) {
      return _inFlight!;
    }
    final future = _doEnsure(serviceUrl);
    _inFlight = future;
    try {
      return await future;
    } finally {
      _inFlight = null;
    }
  }

  Future<bool> _doEnsure(String serviceUrl) async {
    try {
      final response = await _httpClient.get(
        buildServiceLoginUrl(serviceUrl),
        headers: const {
          'User-Agent': CampusEndpoints.desktopUserAgent,
          'Accept':
              'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        },
        followRedirects: true,
        useCookies: true,
      );
      if (!response.isSuccessful) return false;
      if (response.finalUri.host.toLowerCase() == CampusEndpoints.casHost) {
        return false;
      }
      final path = response.finalUri.path;
      if (path.contains('login_slogin') || path.contains('/authserver/')) {
        return false;
      }
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<void> harvestCasCookies(String raw) async {
    if (!hasCasTicket(raw)) return;
    await _authRepository.saveCasMasterCookies(raw);
  }

  Future<String> _loadCasCookieHeader() async {
    final rows =
        await _authRepository.loadCookiesForHost(CampusEndpoints.casHost);
    return rows.map((e) => '${e.name}=${e.value}').join('; ');
  }

  static bool hasCasTicket(String raw) => casTicketValue(raw) != null;

  static String? casTicketValue(String raw) {
    for (final part in raw.split(';')) {
      final index = part.indexOf('=');
      if (index <= 0) continue;
      final name = part.substring(0, index).trim().toLowerCase();
      if (CampusEndpoints.casTicketCookieNames.contains(name)) {
        final value = part.substring(index + 1).trim();
        if (value.isNotEmpty) return value;
      }
    }
    return null;
  }

  static String buildServiceLoginUrl(String serviceUrl) =>
      '${CampusEndpoints.authServerLoginUrl}?service=${Uri.encodeQueryComponent(serviceUrl)}';
}
