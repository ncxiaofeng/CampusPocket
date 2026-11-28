import 'dart:convert';
import 'dart:io';

import '../core/campus_endpoints.dart';
import 'campus_cookie_jar.dart';

class CampusHttpResponse {
  final int statusCode;
  final Uri finalUri;
  final String body;
  final Map<String, List<String>> headers;
  final bool isRedirect;

  const CampusHttpResponse({
    required this.statusCode,
    required this.finalUri,
    required this.body,
    required this.headers,
    required this.isRedirect,
  });

  bool get isSuccessful => statusCode >= 200 && statusCode < 300;
}

/// 统一 HTTP 客户端：基于 dart:io `HttpClient`，支持自动 CookieJar 注入与回写、
/// 可控重定向跟随（并自动把 authserver http:// 升级为 https://）。
class CampusHttpClient {
  CampusHttpClient({
    CampusCookieJar? cookieJar,
    Duration timeout = const Duration(seconds: 15),
  })  : _cookieJar = cookieJar,
        _timeout = timeout;

  final CampusCookieJar? _cookieJar;
  final Duration _timeout;

  Future<CampusHttpResponse> get(
    String url, {
    Map<String, String> headers = const {},
    bool followRedirects = true,
    int maxRedirects = 10,
    bool useCookies = true,
  }) {
    return _send(
      method: 'GET',
      uri: Uri.parse(url),
      headers: headers,
      followRedirects: followRedirects,
      maxRedirects: maxRedirects,
      useCookies: useCookies,
    );
  }

  Future<CampusHttpResponse> head(
    String url, {
    Map<String, String> headers = const {},
    Duration? timeout,
  }) {
    return _send(
      method: 'HEAD',
      uri: Uri.parse(url),
      headers: headers,
      followRedirects: false,
      useCookies: false,
      timeoutOverride: timeout,
    );
  }

  Future<CampusHttpResponse> delete(
    String url, {
    Map<String, String> headers = const {},
    bool useCookies = false,
  }) {
    return _send(
      method: 'DELETE',
      uri: Uri.parse(url),
      headers: headers,
      followRedirects: false,
      useCookies: useCookies,
    );
  }

  Future<List<int>> getBytes(
    String url, {
    Map<String, String> headers = const {},
  }) async {
    final client = HttpClient()..connectionTimeout = _timeout;
    try {
      final request = await client.getUrl(Uri.parse(url)).timeout(_timeout);
      headers.forEach((k, v) => request.headers.set(k, v));
      final response = await request.close().timeout(_timeout);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw HttpException('请求返回 HTTP ${response.statusCode}');
      }
      return await response
          .fold<List<int>>(<int>[], (prev, chunk) => prev..addAll(chunk))
          .timeout(_timeout);
    } finally {
      client.close(force: true);
    }
  }

  Future<CampusHttpResponse> postForm(
    String url,
    Map<String, String> fields, {
    Map<String, String> headers = const {},
    bool followRedirects = false,
    bool useCookies = true,
  }) {
    final encoded = fields.entries
        .map(
          (e) =>
              '${Uri.encodeQueryComponent(e.key)}=${Uri.encodeQueryComponent(e.value)}',
        )
        .join('&');
    return _send(
      method: 'POST',
      uri: Uri.parse(url),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
        ...headers,
      },
      bodyBytes: utf8.encode(encoded),
      followRedirects: followRedirects,
      useCookies: useCookies,
    );
  }

  Future<CampusHttpResponse> postJson(
    String url,
    Object payload, {
    Map<String, String> headers = const {},
    bool useCookies = false,
  }) {
    return _send(
      method: 'POST',
      uri: Uri.parse(url),
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Accept': 'application/json',
        ...headers,
      },
      bodyBytes: utf8.encode(jsonEncode(payload)),
      followRedirects: true,
      useCookies: useCookies,
    );
  }

  Future<CampusHttpResponse> _send({
    required String method,
    required Uri uri,
    required Map<String, String> headers,
    List<int>? bodyBytes,
    bool followRedirects = true,
    int maxRedirects = 10,
    bool useCookies = true,
    Duration? timeoutOverride,
  }) async {
    final client = HttpClient()
      ..connectionTimeout = timeoutOverride ?? _timeout;
    try {
      var currentUri = uri;
      var currentMethod = method;
      var currentBody = bodyBytes;

      for (var hop = 0; hop <= maxRedirects; hop++) {
        final request = await client
            .openUrl(currentMethod, currentUri)
            .timeout(timeoutOverride ?? _timeout);
        request.followRedirects = false;

        headers.forEach((k, v) {
          if (currentMethod == 'GET' && k.toLowerCase() == 'content-type') {
            return;
          }
          request.headers.set(k, v, preserveHeaderCase: true);
        });

        if (useCookies && _cookieJar != null) {
          final cookies = await _cookieJar.loadForRequest(currentUri);
          if (cookies.isNotEmpty) {
            final header =
                cookies.map((c) => '${c.name}=${c.value}').join('; ');
            request.headers.set(HttpHeaders.cookieHeader, header);
          }
        }

        if (currentBody != null && currentMethod != 'GET' && currentMethod != 'HEAD') {
          request.add(currentBody);
        }

        final response =
            await request.close().timeout(timeoutOverride ?? _timeout);
        if (useCookies && _cookieJar != null && response.cookies.isNotEmpty) {
          await _cookieJar.saveFromResponse(currentUri, response.cookies);
        }

        final isRedirect = response.statusCode >= 300 &&
            response.statusCode < 400 &&
            response.headers.value(HttpHeaders.locationHeader) != null;

        if (isRedirect && followRedirects && hop < maxRedirects) {
          await response.drain<void>();
          final location = response.headers.value(HttpHeaders.locationHeader)!;
          var nextUri = currentUri.resolve(location);
          // 学校部分旧入口会把 CAS 302 到 http://authserver.ecut.edu.cn（80 端口不可用），自动升级 https。
          if (nextUri.scheme == 'http' &&
              nextUri.host.toLowerCase() == CampusEndpoints.casHost) {
            nextUri = nextUri.replace(scheme: 'https');
          }
          currentUri = nextUri;
          if (response.statusCode == 301 ||
              response.statusCode == 302 ||
              response.statusCode == 303) {
            currentMethod = 'GET';
            currentBody = null;
          }
          continue;
        }

        final rawBytes = currentMethod == 'HEAD'
            ? const <int>[]
            : await response
                .fold<List<int>>(<int>[], (prev, chunk) => prev..addAll(chunk))
                .timeout(timeoutOverride ?? _timeout);
        final body = utf8.decode(rawBytes, allowMalformed: true);
        final headerMap = <String, List<String>>{};
        response.headers.forEach((name, values) {
          headerMap[name.toLowerCase()] = List<String>.from(values);
        });

        return CampusHttpResponse(
          statusCode: response.statusCode,
          finalUri: currentUri,
          body: body,
          headers: headerMap,
          isRedirect: isRedirect,
        );
      }

      throw const HttpException('重定向次数过多');
    } finally {
      client.close(force: true);
    }
  }
}
