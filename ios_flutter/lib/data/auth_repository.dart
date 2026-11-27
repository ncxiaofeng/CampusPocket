import 'dart:convert';

import '../core/campus_endpoints.dart';
import 'models.dart';
import 'secure_vault.dart';

/// 凭据与 Cookie 仓库：所有登录态仅保存在 iOS Keychain，不写入明文 SharedPreferences。
class AuthRepository {
  AuthRepository(this._vault);

  static const String _kCredentialsKey = 'cp_auth_credentials_v1';
  static const String _kCookiesKey = 'cp_auth_cookies_v1';

  final SecureVault _vault;
  bool _loaded = false;
  final Map<String, AuthCredential> _credentials = {};
  final List<CookieRecord> _cookies = [];

  Future<void> _ensureLoaded() async {
    if (_loaded) return;
    _loaded = true;

    final rawCreds = await _vault.read(_kCredentialsKey);
    if (rawCreds != null && rawCreds.isNotEmpty) {
      try {
        final decoded = jsonDecode(rawCreds) as List<dynamic>;
        for (final item in decoded) {
          if (item is Map<String, dynamic>) {
            final cred = AuthCredential.fromJson(item);
            if (cred.systemId.isNotEmpty) {
              _credentials[cred.systemId] = cred;
            }
          }
        }
      } catch (_) {}
    }

    final rawCookies = await _vault.read(_kCookiesKey);
    if (rawCookies != null && rawCookies.isNotEmpty) {
      try {
        final decoded = jsonDecode(rawCookies) as List<dynamic>;
        for (final item in decoded) {
          if (item is Map<String, dynamic>) {
            final rec = CookieRecord.fromJson(item);
            if (rec.domain.isNotEmpty && rec.name.isNotEmpty) {
              _cookies.add(rec);
            }
          }
        }
      } catch (_) {}
    }
  }

  Future<void> _persist() async {
    await _vault.write(
      _kCredentialsKey,
      jsonEncode(_credentials.values.map((e) => e.toJson()).toList()),
    );
    await _vault.write(
      _kCookiesKey,
      jsonEncode(_cookies.map((e) => e.toJson()).toList()),
    );
  }

  Future<AuthCredential?> getCredential(String systemId) async {
    await _ensureLoaded();
    return _credentials[systemId];
  }

  Future<List<AuthCredential>> getCredentialsForHost(String host) async {
    await _ensureLoaded();
    final lower = host.toLowerCase();
    return _credentials.values
        .where((e) => e.domain.toLowerCase() == lower)
        .toList();
  }

  Future<List<CookieRecord>> loadCookiesForHost(String host) async {
    await _ensureLoaded();
    final lower = host.toLowerCase();
    return _cookies.where((c) {
      final dom = c.domain.replaceFirst(RegExp(r'^\.+'), '').toLowerCase();
      return lower == dom || lower.endsWith('.$dom');
    }).toList();
  }

  Future<List<CookieRecord>> allCookies() async {
    await _ensureLoaded();
    return List.unmodifiable(_cookies);
  }

  Future<void> upsertCookies(List<CookieRecord> incoming) async {
    if (incoming.isEmpty) return;
    await _ensureLoaded();
    for (final item in incoming) {
      _cookies.removeWhere(
        (old) =>
            old.domain.toLowerCase() == item.domain.toLowerCase() &&
            old.name == item.name &&
            old.path == item.path,
      );
      _cookies.add(item);
    }
    await _persist();
  }

  Future<void> deleteCookie(String host, String name, String path) async {
    await _ensureLoaded();
    _cookies.removeWhere(
      (old) =>
          old.domain.toLowerCase() == host.toLowerCase() &&
          old.name == name &&
          old.path == path,
    );
    await _persist();
  }

  /// 保存从 WebView 捕获的完整 Cookie 对象（保留真实 domain 和 path）。
  Future<void> saveStructuredCookies({
    required String systemId,
    required String primaryHost,
    required List<CookieRecord> records,
  }) async {
    if (records.isEmpty) return;
    await _ensureLoaded();
    final now = DateTime.now().millisecondsSinceEpoch;
    final primaryHeader = records
        .where((c) => _domainMatches(primaryHost, c.domain))
        .map((c) => '${c.name}=${c.value}')
        .join('; ');
    final fallbackHeader =
        records.map((c) => '${c.name}=${c.value}').join('; ');

    _credentials[systemId] = AuthCredential(
      systemId: systemId,
      domain: primaryHost,
      cookie: primaryHeader.isNotEmpty ? primaryHeader : fallbackHeader,
      updatedAt: now,
    );

    for (final rec in records) {
      final normalizedDomain =
          rec.domain.replaceFirst(RegExp(r'^\.+'), '').toLowerCase();
      final path = rec.path.isEmpty ? '/' : rec.path;
      // 同一 host 下同名且路径重叠（或相同 path）替换，/sso 与 /jwglxt 各自保留。
      _cookies.removeWhere(
        (old) =>
            old.domain.toLowerCase() == normalizedDomain &&
            old.name == rec.name &&
            old.path == path,
      );
      _cookies.add(
        CookieRecord(
          domain: normalizedDomain,
          name: rec.name,
          value: rec.value,
          path: path,
          expiresAt: rec.expiresAt,
          secure: rec.secure,
          updatedAt: now,
        ),
      );
    }
    await _persist();
  }

  /// 按 `domain -> rawHeader` 方式保存登录态（与 Android 端保持一致）。
  Future<void> saveLoginCookies({
    required String systemId,
    required String primaryHost,
    required Map<String, String> cookieByHost,
    Map<String, String> pathByHost = const {},
  }) async {
    await _ensureLoaded();
    final now = DateTime.now().millisecondsSinceEpoch;
    final merged = dedupeCookiePairs(
      cookieByHost.values.where((v) => v.trim().isNotEmpty).join('; '),
    );
    if (merged.isEmpty) return;

    final primaryRaw = cookieByHost[primaryHost];
    _credentials[systemId] = AuthCredential(
      systemId: systemId,
      domain: primaryHost,
      cookie: (primaryRaw != null && primaryRaw.trim().isNotEmpty)
          ? primaryRaw
          : merged,
      updatedAt: now,
    );

    for (final entry in cookieByHost.entries) {
      final host = entry.key.toLowerCase();
      final raw = entry.value;
      if (raw.trim().isEmpty) continue;
      final candidatePath = pathByHost[entry.key];
      final path = (candidatePath != null && candidatePath.startsWith('/'))
          ? candidatePath
          : '/';
      final pairs = parseCookiePairs(raw);
      if (pairs.isEmpty) continue;

      final names = pairs.map((e) => e.$1).toSet();
      _cookies.removeWhere(
        (old) =>
            old.domain.toLowerCase() == host && names.contains(old.name),
      );
      for (final (name, value) in pairs) {
        _cookies.add(
          CookieRecord(
            domain: host,
            name: name,
            value: value,
            path: path,
            expiresAt: 0,
            secure: false,
            updatedAt: now,
          ),
        );
      }
    }
    await _persist();
  }

  /// 保存 CAS 主凭据（CASTGC）。
  ///
  /// [fromCasHost]=true 时整套保留（CASTGC + route + JSESSIONID），防止 CAS 集群节点漂移；
  /// [fromCasHost]=false 时只把 CASTGC 镜像合并进主域，保留主域已有的 route。
  Future<void> saveCasMasterCookies(
    String raw, {
    bool fromCasHost = true,
  }) async {
    await _ensureLoaded();
    final incoming = parseCookiePairs(raw);
    final ticketPairs = incoming
        .where(
          (p) => CampusEndpoints.casTicketCookieNames
              .contains(p.$1.toLowerCase()),
        )
        .toList();
    if (ticketPairs.isEmpty) return;

    String header;
    if (fromCasHost) {
      header = dedupeCookiePairs(raw);
    } else {
      final existing = _cookies
          .where((c) => c.domain.toLowerCase() == CampusEndpoints.casHost)
          .map((c) => '${c.name}=${c.value}')
          .join('; ');
      header = mergeCookiePairs(existing, ticketPairs);
    }
    if (header.isEmpty) return;

    await saveLoginCookies(
      systemId: CampusEndpoints.systemCasMaster,
      primaryHost: CampusEndpoints.casHost,
      cookieByHost: {CampusEndpoints.casHost: header},
      pathByHost: {CampusEndpoints.casHost: '/authserver'},
    );
  }

  Future<void> clearSystem(String systemId, Iterable<String> hosts) async {
    await _ensureLoaded();
    _credentials.remove(systemId);
    final lowerHosts = hosts.map((e) => e.toLowerCase()).toSet();
    _cookies.removeWhere((c) => lowerHosts.contains(c.domain.toLowerCase()));
    await _persist();
  }

  Future<void> clearAll() async {
    _loaded = true;
    _credentials.clear();
    _cookies.clear();
    await _vault.clearAll();
  }

  static bool _domainMatches(String host, String cookieDomain) {
    final h = host.toLowerCase();
    final d = cookieDomain.replaceFirst(RegExp(r'^\.+'), '').toLowerCase();
    return d.isNotEmpty && (h == d || h.endsWith('.$d'));
  }

  static String dedupeCookiePairs(String raw) {
    final seen = <String, String>{};
    for (final (name, value) in parseCookiePairs(raw)) {
      seen[name] = value;
    }
    return seen.entries.map((e) => '${e.key}=${e.value}').join('; ');
  }

  static String mergeCookiePairs(
    String base,
    List<(String, String)> overrides,
  ) {
    final merged = <String, String>{};
    for (final (name, value) in parseCookiePairs(base)) {
      merged[name] = value;
    }
    for (final (name, value) in overrides) {
      merged[name] = value;
    }
    return merged.entries.map((e) => '${e.key}=${e.value}').join('; ');
  }

  static List<(String, String)> parseCookiePairs(String raw) {
    final result = <(String, String)>[];
    for (final part in raw.split(';')) {
      final index = part.indexOf('=');
      if (index <= 0) continue;
      final name = part.substring(0, index).trim();
      final value = part.substring(index + 1).trim();
      if (name.isNotEmpty) {
        result.add((name, value));
      }
    }
    return result;
  }
}
