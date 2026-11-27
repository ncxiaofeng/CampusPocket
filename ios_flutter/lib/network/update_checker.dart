import 'dart:convert';
import 'dart:io';

import '../core/app_links.dart';
import '../data/models.dart';
import 'campus_http_client.dart';

/// 检查 GitHub Releases 上的 iOS 自签版更新（优先匹配含 `.ipa` 附件的 Release）。
class UpdateChecker {
  UpdateChecker(this._httpClient);

  final CampusHttpClient _httpClient;

  Future<AppUpdateInfo?> check(String currentVersion) async {
    final resp = await _httpClient.get(
      AppLinks.githubReleasesApi,
      headers: const {
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'CampusPocket-iOS',
      },
      useCookies: false,
    );
    if (!resp.isSuccessful) {
      throw HttpException('更新接口返回 HTTP ${resp.statusCode}');
    }
    return parseLatestIpaRelease(resp.body, currentVersion);
  }

  static AppUpdateInfo? parseLatestIpaRelease(
    String jsonText,
    String currentVersion,
  ) {
    final decoded = jsonDecode(jsonText);
    if (decoded is! List) return null;
    for (final item in decoded) {
      if (item is! Map<String, dynamic>) continue;
      if (item['draft'] == true) continue;
      final tagName = '${item['tag_name'] ?? ''}'.trim();
      final ipaUrl = _findIpaUrl(item['assets']);
      final isIosTag = tagName.toLowerCase().startsWith('ios-');
      if (ipaUrl == null && !isIosTag) continue;
      final remoteVer = normalizeVersion(tagName);
      if (remoteVer.isEmpty) continue;
      if (!isNewer(remoteVer, currentVersion)) return null;
      return AppUpdateInfo(
        version: remoteVer,
        tagName: tagName,
        releaseNotes: '${item['body'] ?? ''}',
        releasePageUrl: '${item['html_url'] ?? AppLinks.githubReleases}',
        ipaDownloadUrl: ipaUrl,
      );
    }
    return null;
  }

  static String? _findIpaUrl(Object? assets) {
    if (assets is! List) return null;
    for (final asset in assets) {
      if (asset is! Map<String, dynamic>) continue;
      final name = '${asset['name'] ?? ''}';
      final url = '${asset['browser_download_url'] ?? ''}';
      if (name.toLowerCase().endsWith('.ipa') && url.isNotEmpty) {
        return url;
      }
    }
    return null;
  }

  static String normalizeVersion(String raw) {
    var s = raw.trim();
    if (s.toLowerCase().startsWith('ios-')) {
      s = s.substring(4);
    }
    if (s.startsWith('v') || s.startsWith('V')) {
      s = s.substring(1);
    }
    s = s.split('-').first.split('+').first.trim();
    return s;
  }

  static bool isNewer(String remote, String local) {
    final remoteParts = _parseParts(remote);
    final localParts = _parseParts(local);
    if (remoteParts.isEmpty || localParts.isEmpty) return false;
    final len = remoteParts.length > localParts.length
        ? remoteParts.length
        : localParts.length;
    for (var i = 0; i < len; i++) {
      final r = i < remoteParts.length ? remoteParts[i] : 0;
      final l = i < localParts.length ? localParts[i] : 0;
      if (r != l) return r > l;
    }
    return false;
  }

  static List<int> _parseParts(String raw) => normalizeVersion(raw)
      .split('.')
      .map(int.tryParse)
      .whereType<int>()
      .toList();
}
