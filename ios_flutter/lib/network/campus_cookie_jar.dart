import 'dart:io';

import '../data/auth_repository.dart';
import '../data/models.dart';

/// 校内 CookieJar：按 domain + path 精准筛选，与 Android `CampusCookieJar` 规则完全一致。
class CampusCookieJar {
  CampusCookieJar(this._authRepository);

  final AuthRepository _authRepository;

  Future<List<CookieRecord>> loadForRequest(Uri uri) async {
    final host = uri.host;
    final requestPath = uri.path.isEmpty ? '/' : uri.path;
    final now = DateTime.now().millisecondsSinceEpoch;
    final rows = await _authRepository.loadCookiesForHost(host);
    final selected = selectCookies(rows, host, requestPath, now);
    if (selected.isNotEmpty) return selected;

    // 兜底：若尚无拆分 Cookie，回退到原始凭据串。
    final creds = await _authRepository.getCredentialsForHost(host);
    final fallback = <String, CookieRecord>{};
    for (final cred in creds) {
      for (final (name, value) in AuthRepository.parseCookiePairs(cred.cookie)) {
        fallback.putIfAbsent(
          name,
          () => CookieRecord(
            domain: host.toLowerCase(),
            name: name,
            value: value,
            path: '/',
            updatedAt: cred.updatedAt,
          ),
        );
      }
    }
    return fallback.values.toList();
  }

  Future<void> saveFromResponse(Uri uri, List<Cookie> cookies) async {
    if (cookies.isEmpty) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    final host = uri.host.toLowerCase();
    final existing = (await _authRepository.loadCookiesForHost(host))
        .where((c) => c.domain.toLowerCase() == host)
        .toList();

    final incoming = <CookieRecord>[];
    for (final cookie in cookies) {
      final path = (cookie.path == null || cookie.path!.trim().isEmpty)
          ? '/'
          : cookie.path!;
      final expiresAt = cookie.expires?.millisecondsSinceEpoch ?? 0;
      // 过期 cookie（maxAge == 0）或已被删除则移除
      if (cookie.maxAge != null && cookie.maxAge! <= 0) {
        await _authRepository.deleteCookie(host, cookie.name, path);
        continue;
      }
      final rec = CookieRecord(
        domain: host,
        name: cookie.name,
        value: cookie.value,
        path: path,
        expiresAt: expiresAt,
        secure: cookie.secure,
        updatedAt: now,
      );
      incoming.add(rec);

      // 清理互为父子前缀的旧路径副本；/sso 与 /jwglxt 互不为前缀，不受影响。
      for (final old in existing) {
        if (old.name == rec.name &&
            old.path != rec.path &&
            pathsOverlap(old.path, rec.path)) {
          await _authRepository.deleteCookie(host, old.name, old.path);
        }
      }
    }
    await _authRepository.upsertCookies(incoming);
  }

  static bool hostMatches(String requestHost, String cookieDomain) {
    final req = requestHost.toLowerCase();
    final dom = cookieDomain.replaceFirst(RegExp(r'^\.+'), '').toLowerCase();
    if (dom.isEmpty) return false;
    return req == dom || req.endsWith('.$dom');
  }

  static bool pathMatches(String requestPath, String cookiePath) {
    if (cookiePath.trim().isEmpty || cookiePath == '/') return true;
    final normalizedCookie = cookiePath.replaceFirst(RegExp(r'/+$'), '');
    final normalizedReq = requestPath.trim().isEmpty ? '/' : requestPath;
    if (normalizedReq == normalizedCookie ||
        normalizedReq == '$normalizedCookie/') {
      return true;
    }
    return normalizedReq.startsWith('$normalizedCookie/');
  }

  /// 两条路径存在父子前缀关系（含一方为 `/`）时返回 true；`/sso` 与 `/jwglxt` 返回 false。
  static bool pathsOverlap(String first, String second) {
    final a = first.trim().isEmpty ? '/' : first;
    final b = second.trim().isEmpty ? '/' : second;
    return pathMatches(a, b) || pathMatches(b, a);
  }

  static List<CookieRecord> selectCookies(
    List<CookieRecord> rows,
    String host,
    String requestPath,
    int now,
  ) {
    final chosen = <String, CookieRecord>{};
    for (final row in rows) {
      if (!hostMatches(host, row.domain)) continue;
      if (row.expiresAt != 0 && row.expiresAt <= now) continue;
      if (!pathMatches(requestPath, row.path)) continue;
      final current = chosen[row.name];
      if (current == null || _shouldReplace(current, row, host)) {
        chosen[row.name] = row;
      }
    }
    return chosen.values.toList();
  }

  static bool _shouldReplace(
    CookieRecord current,
    CookieRecord candidate,
    String host,
  ) {
    final currentExact = current.domain.toLowerCase() == host.toLowerCase();
    final candidateExact = candidate.domain.toLowerCase() == host.toLowerCase();
    if (candidateExact != currentExact) return candidateExact;

    final timeDiff = candidate.updatedAt - current.updatedAt;
    if (timeDiff > 5000) return true;
    if (timeDiff < -5000) return false;

    final currentLen = (current.path.trim().isEmpty ? '/' : current.path).length;
    final candidateLen =
        (candidate.path.trim().isEmpty ? '/' : candidate.path).length;
    if (candidateLen != currentLen) return candidateLen > currentLen;
    return candidate.updatedAt >= current.updatedAt;
  }
}
