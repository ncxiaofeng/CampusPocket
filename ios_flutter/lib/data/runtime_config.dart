import 'dart:convert';
import 'dart:io';

import '../network/campus_http_client.dart';
import 'secure_vault.dart';

/// 运行期第三方参数配置（绝不内置在 IPA 或公开代码中，仅保存在本机 Keychain）。
class RuntimeConfig {
  final String electricityOperatorPwd;
  final String ujingHmacKey;
  final String huilifeAppSignSalt;
  final Set<String> sealedFields;

  const RuntimeConfig({
    this.electricityOperatorPwd = '',
    this.ujingHmacKey = '',
    this.huilifeAppSignSalt = '',
    this.sealedFields = const {},
  });

  bool get hasElectricity => electricityOperatorPwd.trim().isNotEmpty;
  bool get hasUjing => ujingHmacKey.trim().isNotEmpty;
  bool get hasHuiLife => huilifeAppSignSalt.trim().isNotEmpty;

  bool isSealed(String shortKey) => sealedFields.contains(shortKey);

  RuntimeConfig copyWith({
    String? electricityOperatorPwd,
    String? ujingHmacKey,
    String? huilifeAppSignSalt,
    Set<String>? sealedFields,
  }) =>
      RuntimeConfig(
        electricityOperatorPwd:
            electricityOperatorPwd ?? this.electricityOperatorPwd,
        ujingHmacKey: ujingHmacKey ?? this.ujingHmacKey,
        huilifeAppSignSalt: huilifeAppSignSalt ?? this.huilifeAppSignSalt,
        sealedFields: sealedFields ?? this.sealedFields,
      );
}

class RuntimeConfigStore {
  RuntimeConfigStore(this._vault);

  static const String _kStorageKey = 'cp_runtime_config_v1';

  final SecureVault _vault;
  RuntimeConfig _cached = const RuntimeConfig();
  bool _loaded = false;

  RuntimeConfig get current => _cached;

  Future<RuntimeConfig> load() async {
    if (_loaded) return _cached;
    _loaded = true;
    final raw = await _vault.read(_kStorageKey);
    if (raw == null || raw.trim().isEmpty) return _cached;
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final sealed = '${map['sealed'] ?? ''}'
          .split(',')
          .map((e) => e.trim())
          .where((e) => e.isNotEmpty)
          .toSet();
      _cached = RuntimeConfig(
        electricityOperatorPwd: '${map['e'] ?? ''}',
        ujingHmacKey: '${map['u'] ?? ''}',
        huilifeAppSignSalt: '${map['h'] ?? ''}',
        sealedFields: sealed,
      );
    } catch (_) {
      _cached = const RuntimeConfig();
    }
    return _cached;
  }

  Future<void> update(RuntimeConfig config) async {
    final normalizedSealed = <String>{
      if (config.sealedFields.contains('e') && config.hasElectricity) 'e',
      if (config.sealedFields.contains('u') && config.hasUjing) 'u',
      if (config.sealedFields.contains('h') && config.hasHuiLife) 'h',
    };
    _cached = config.copyWith(sealedFields: normalizedSealed);
    _loaded = true;
    await _vault.write(
      _kStorageKey,
      jsonEncode({
        'e': _cached.electricityOperatorPwd,
        'u': _cached.ujingHmacKey,
        'h': _cached.huilifeAppSignSalt,
        'sealed': normalizedSealed.join(','),
      }),
    );
  }

  Future<void> clear() async {
    _cached = const RuntimeConfig();
    _loaded = true;
    await _vault.delete(_kStorageKey);
  }
}

/// 解析三行 `key=value` 或 JSON `{"e","u","h"}` 批量配置文本。
class RuntimeConfigTextParser {
  static const String keyElectricity = 'electricity.operator.pwd';
  static const String keyUjing = 'ujing.hmac.key';
  static const String keyHuiLife = 'huilife.app.sign.salt';

  static Map<String, String> parse(String text) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return const {};
    if (trimmed.startsWith('{')) return _parseJson(trimmed);
    return _parseLines(trimmed);
  }

  static Map<String, String> _parseLines(String text) {
    final values = <String, String>{};
    for (final line in const LineSplitter().convert(text)) {
      final idx = line.indexOf('=');
      if (idx <= 0) continue;
      final key = _canonical(line.substring(0, idx).trim());
      final val = line.substring(idx + 1).trim();
      if (key != null && val.isNotEmpty) values[key] = val;
    }
    return values;
  }

  static Map<String, String> _parseJson(String text) {
    try {
      final obj = jsonDecode(text);
      if (obj is! Map<String, dynamic>) return const {};
      final values = <String, String>{};
      obj.forEach((rawKey, rawVal) {
        if (rawVal == null || rawVal is Map || rawVal is List) return;
        final key = _canonical(rawKey.trim());
        final val = rawVal.toString().trim();
        if (key != null && val.isNotEmpty) values[key] = val;
      });
      return values;
    } catch (_) {
      return const {};
    }
  }

  static String? _canonical(String raw) => switch (raw.toLowerCase()) {
        keyElectricity || 'e' => keyElectricity,
        keyUjing || 'u' => keyUjing,
        keyHuiLife || 'h' => keyHuiLife,
        _ => null,
      };
}

enum RemoteClaimMode { once, group }

class RemoteConfigClaim {
  final String getUrl;
  final String? deleteUrl;
  final RemoteClaimMode mode;
  final int expiresAtEpochSec;

  const RemoteConfigClaim({
    required this.getUrl,
    required this.deleteUrl,
    required this.mode,
    this.expiresAtEpochSec = 0,
  });
}

class RemoteClaimOutcome {
  final Map<String, String> values;
  final RemoteClaimMode mode;
  final bool burned;

  const RemoteClaimOutcome({
    required this.values,
    required this.mode,
    required this.burned,
  });
}

/// 一次性 / 限时远程安全配置码解析器（严格白名单防 SSRF）。
class RemoteConfigClaimParser {
  static RemoteConfigClaim? parse(String raw) {
    final text = raw.trim();
    if (!text.startsWith('{')) return null;
    try {
      final obj = jsonDecode(text);
      if (obj is! Map<String, dynamic>) return null;
      if (obj['cp_claim'] != 1) return null;

      final getUrl = '${obj['g'] ?? ''}'.trim();
      if (!isAllowedClaimUrl(getUrl)) return null;

      final modeText = '${obj['m'] ?? ''}'.trim().toLowerCase();
      final mode =
          modeText == 'group' ? RemoteClaimMode.group : RemoteClaimMode.once;
      final exp = (obj['exp'] is num)
          ? (obj['exp'] as num).toInt()
          : int.tryParse('${obj['exp'] ?? ''}') ?? 0;

      final explicitDel = '${obj['d'] ?? ''}'.trim();
      final delSig = '${obj['ds'] ?? ''}'.trim();
      String? deleteUrl;
      if (mode == RemoteClaimMode.once) {
        if (explicitDel.isNotEmpty && isAllowedClaimUrl(explicitDel)) {
          deleteUrl = explicitDel;
        } else if (RegExp(r'^[0-9a-fA-F]{40}$').hasMatch(delSig)) {
          deleteUrl = deriveDeleteUrl(getUrl, delSig);
        }
      }
      return RemoteConfigClaim(
        getUrl: getUrl,
        deleteUrl: deleteUrl,
        mode: mode,
        expiresAtEpochSec: exp,
      );
    } catch (_) {
      return null;
    }
  }

  static bool isAllowedClaimUrl(String url) {
    final uri = Uri.tryParse(url);
    if (uri == null || uri.scheme.toLowerCase() != 'https') return false;
    final host = uri.host.toLowerCase();
    if (!host.endsWith('.myqcloud.com') || host.length <= '.myqcloud.com'.length) {
      return false;
    }
    if (!uri.path.startsWith('/claim/')) return false;
    final sig = uri.queryParameters['q-signature'];
    return sig != null && sig.trim().isNotEmpty;
  }

  static String? deriveDeleteUrl(String getUrl, String deleteSig) {
    final uri = Uri.tryParse(getUrl);
    if (uri == null) return null;
    final sig = uri.queryParameters['q-signature'];
    if (sig == null || sig.trim().isEmpty) return null;
    final params = Map<String, String>.from(uri.queryParameters);
    params['q-signature'] = deleteSig;
    return uri.replace(queryParameters: params).toString();
  }
}

class RemoteConfigClaimClient {
  RemoteConfigClaimClient(this._httpClient);

  static const int _maxBodyBytes = 4096;
  final CampusHttpClient _httpClient;

  Future<RemoteClaimOutcome> claim(RemoteConfigClaim claim) async {
    final resp = await _httpClient.get(
      claim.getUrl,
      followRedirects: false,
      useCookies: false,
    );
    if (resp.statusCode == 404) {
      throw const HttpException('该配置码已被领取或已失效');
    }
    if (resp.statusCode == 403) {
      throw const HttpException('该配置码已过期或无访问权限，请向分享者重新获取');
    }
    if (!resp.isSuccessful) {
      throw HttpException('配置码拉取失败（HTTP ${resp.statusCode}）');
    }
    if (utf8.encode(resp.body).length > _maxBodyBytes) {
      throw const HttpException('配置码内容异常（体积超限）');
    }
    final values = RuntimeConfigTextParser.parse(resp.body);
    if (values.isEmpty) {
      throw const HttpException('配置码内没有识别到有效参数');
    }
    var burned = false;
    if (claim.mode == RemoteClaimMode.once &&
        claim.deleteUrl != null &&
        claim.deleteUrl!.isNotEmpty) {
      try {
        final del = await _httpClient.delete(
          claim.deleteUrl!,
          useCookies: false,
        );
        burned = del.isSuccessful || del.statusCode == 404;
      } catch (_) {}
    }
    return RemoteClaimOutcome(
      values: values,
      mode: claim.mode,
      burned: burned,
    );
  }
}
