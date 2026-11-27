import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import '../core/campus_endpoints.dart';
import '../data/auth_repository.dart';
import '../data/secure_vault.dart';
import 'campus_http_client.dart';
import 'cas_sso_manager.dart';

/// 可选自动登录凭据箱（默认关闭、默认不保存密码，仅用户主动开启后写入 iOS Keychain）。
class AutoLoginCredentials {
  final String username;
  final String password;
  final bool enabled;

  const AutoLoginCredentials({
    required this.username,
    required this.password,
    required this.enabled,
  });
}

class CredentialVault {
  CredentialVault(this._vault);

  static const String _kVaultKey = 'cp_auto_login_vault_v1';
  final SecureVault _vault;

  Future<AutoLoginCredentials?> load() async {
    final raw = await _vault.read(_kVaultKey);
    if (raw == null || raw.trim().isEmpty) return null;
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      final u = '${map['u'] ?? ''}'.trim();
      final p = '${map['p'] ?? ''}';
      final e = map['e'] == true;
      if (u.isEmpty || p.isEmpty) return null;
      return AutoLoginCredentials(username: u, password: p, enabled: e);
    } catch (_) {
      return null;
    }
  }

  Future<void> save(String username, String password, {bool enabled = true}) async {
    await _vault.write(
      _kVaultKey,
      jsonEncode({'u': username.trim(), 'p': password, 'e': enabled}),
    );
  }

  Future<void> clear() => _vault.delete(_kVaultKey);
}

/// 纯 Dart FIPS-197 AES-128-CBC/PKCS7 加密，用于复刻 Apereo CAS `encrypt.js`。
class _Aes128Cbc {
  static const List<int> _sbox = [
    0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
    0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
    0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
    0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
    0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
    0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
    0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
    0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
    0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
    0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
    0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
    0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
    0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
    0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
    0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
    0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16,
  ];
  static const List<int> _rcon = [
    0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36,
  ];

  static Uint8List encrypt(Uint8List plain, Uint8List key, Uint8List iv) {
    final roundKeys = _expandKey(key);
    final padLen = 16 - (plain.length % 16);
    final padded = Uint8List(plain.length + padLen);
    padded.setRange(0, plain.length, plain);
    for (var i = plain.length; i < padded.length; i++) {
      padded[i] = padLen;
    }

    final out = Uint8List(padded.length);
    var prev = Uint8List.fromList(iv);
    for (var offset = 0; offset < padded.length; offset += 16) {
      final block = Uint8List(16);
      for (var i = 0; i < 16; i++) {
        block[i] = padded[offset + i] ^ prev[i];
      }
      final enc = _encryptBlock(block, roundKeys);
      out.setRange(offset, offset + 16, enc);
      prev = enc;
    }
    return out;
  }

  static Uint8List _expandKey(Uint8List key) {
    final w = Uint8List(176);
    w.setRange(0, 16, key);
    var bytesGenerated = 16;
    var rconIdx = 1;
    final temp = Uint8List(4);
    while (bytesGenerated < 176) {
      for (var i = 0; i < 4; i++) {
        temp[i] = w[bytesGenerated - 4 + i];
      }
      if (bytesGenerated % 16 == 0) {
        final t = temp[0];
        temp[0] = _sbox[temp[1]] ^ _rcon[rconIdx++];
        temp[1] = _sbox[temp[2]];
        temp[2] = _sbox[temp[3]];
        temp[3] = _sbox[t];
      }
      for (var i = 0; i < 4; i++) {
        w[bytesGenerated] = w[bytesGenerated - 16] ^ temp[i];
        bytesGenerated++;
      }
    }
    return w;
  }

  static int _xtime(int a) => ((a << 1) ^ (((a >> 7) & 1) * 0x1b)) & 0xff;

  static Uint8List _encryptBlock(Uint8List input, Uint8List rk) {
    final s = Uint8List.fromList(input);
    for (var i = 0; i < 16; i++) {
      s[i] ^= rk[i];
    }
    for (var round = 1; round <= 10; round++) {
      for (var i = 0; i < 16; i++) {
        s[i] = _sbox[s[i]];
      }
      // ShiftRows
      var t = s[1]; s[1] = s[5]; s[5] = s[9]; s[9] = s[13]; s[13] = t;
      t = s[2]; s[2] = s[10]; s[10] = t;
      t = s[6]; s[6] = s[14]; s[14] = t;
      t = s[15]; s[15] = s[11]; s[11] = s[7]; s[7] = s[3]; s[3] = t;

      if (round < 10) {
        // MixColumns
        for (var c = 0; c < 4; c++) {
          final i = c * 4;
          final a0 = s[i], a1 = s[i + 1], a2 = s[i + 2], a3 = s[i + 3];
          final xor = a0 ^ a1 ^ a2 ^ a3;
          s[i] = a0 ^ xor ^ _xtime(a0 ^ a1);
          s[i + 1] = a1 ^ xor ^ _xtime(a1 ^ a2);
          s[i + 2] = a2 ^ xor ^ _xtime(a2 ^ a3);
          s[i + 3] = a3 ^ xor ^ _xtime(a3 ^ a0);
        }
      }
      final base = round * 16;
      for (var i = 0; i < 16; i++) {
        s[i] ^= rk[base + i];
      }
    }
    return s;
  }
}

class CasLoginForm {
  final String action;
  final String encryptSalt;
  final Map<String, String> hiddenFields;

  const CasLoginForm({
    required this.action,
    required this.encryptSalt,
    required this.hiddenFields,
  });
}

/// 统一身份认证（直连 CAS + 零信任 CAS）可选自动登录客户端。
class CasAutoLogin {
  CasAutoLogin({
    required CampusHttpClient httpClient,
    required AuthRepository authRepository,
  })  : _httpClient = httpClient,
        _authRepository = authRepository;

  static const String _randomChars =
      'ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678';
  static final Random _random = Random.secure();

  final CampusHttpClient _httpClient;
  final AuthRepository _authRepository;

  Future<void> login(String username, String password) => _loginTarget(
        username: username,
        password: password,
        loginUrl:
            CasSsoManager.buildServiceLoginUrl(CampusEndpoints.ehallCardServiceUrl),
        casHost: CampusEndpoints.casHost,
      );

  Future<void> _loginTarget({
    required String username,
    required String password,
    required String loginUrl,
    required String casHost,
  }) async {
    await _authRepository.clearCookiesForHost(casHost);
    final page = await _httpClient.get(
      loginUrl,
      headers: const {
        'User-Agent': CampusEndpoints.desktopUserAgent,
        'Accept': 'text/html,application/xhtml+xml,*/*;q=0.8',
      },
      followRedirects: true,
      useCookies: true,
    );
    if (_isAwayFromCas(page.finalUri, casHost)) {
      await _saveMasterCookies(casHost);
      return;
    }

    final form = parseLoginForm(page.body);
    final encrypted = encryptCasPassword(password, form.encryptSalt);
    final actionUri = form.action.isEmpty
        ? page.finalUri
        : page.finalUri.resolve(form.action);
    final fields = <String, String>{
      'username': username,
      'password': encrypted,
      ...form.hiddenFields,
    };
    fields.putIfAbsent('_eventId', () => 'submit');
    if (!fields.containsKey('dllt') && !fields.containsKey('cllt')) {
      fields['dllt'] = 'userNamePasswordLogin';
    }
    fields.putIfAbsent('rmShown', () => '1');
    fields['rememberMe'] = 'on';

    final resp = await _httpClient.postForm(
      actionUri.toString(),
      fields,
      headers: {
        'User-Agent': CampusEndpoints.desktopUserAgent,
        'Accept': 'text/html,application/xhtml+xml,*/*;q=0.8',
        'Origin': '${page.finalUri.scheme}://${page.finalUri.authority}',
        'Referer': page.finalUri.toString(),
      },
      followRedirects: true,
      useCookies: true,
    );

    if (!_isAwayFromCas(resp.finalUri, casHost)) {
      throw StateError(
        loginErrorMessage(resp.body) ?? '自动登录未完成，可能需要验证码或二次验证',
      );
    }
    await _saveMasterCookies(casHost);
  }

  bool _isAwayFromCas(Uri uri, String casHost) =>
      uri.host.toLowerCase() != casHost.toLowerCase() &&
      !uri.path.contains('/authserver/');

  Future<void> _saveMasterCookies(String casHost) async {
    final rows = await _authRepository.loadCookiesForHost(casHost);
    final raw = rows
        .where((r) => r.domain.toLowerCase() == casHost.toLowerCase())
        .map((r) => '${r.name}=${r.value}')
        .join('; ');
    if (!CasSsoManager.hasCasTicket(raw)) {
      throw StateError('登录成功但未拿到 CASTGC，请手动登录一次');
    }
    await _authRepository.saveCasMasterCookies(raw, fromCasHost: true);
  }

  static CasLoginForm parseLoginForm(String html) {
    final formStart = _findLoginFormStart(html);
    var section = html;
    if (formStart >= 0) {
      final formEnd = html.toLowerCase().indexOf('</form>', formStart);
      final end = formEnd > formStart
          ? formEnd + 7
          : min(html.length, formStart + 25000);
      section = html.substring(formStart, end);
    }

    final formTag = RegExp(r'<form\b[^>]*>', caseSensitive: false)
            .firstMatch(section)
            ?.group(0) ??
        '';
    final action = _parseAttrs(formTag)['action'] ?? '';

    final hidden = <String, String>{};
    var salt = '';
    for (final m in RegExp(r'<input\b[^>]*>', caseSensitive: false)
        .allMatches(section)) {
      final attrs = _parseAttrs(m.group(0)!);
      final id = (attrs['id'] ?? '').toLowerCase();
      final name = attrs['name'] ?? '';
      final type = (attrs['type'] ?? '').toLowerCase();
      final value = attrs['value'] ?? '';
      if (id == 'pwddefaultencryptsalt' ||
          id == 'pwdencryptsalt' ||
          name.toLowerCase() == 'pwddefaultencryptsalt' ||
          name.toLowerCase() == 'pwdencryptsalt') {
        if (value.isNotEmpty) salt = value;
      }
      if (type == 'hidden' &&
          name.isNotEmpty &&
          name.toLowerCase() != 'username' &&
          name.toLowerCase() != 'password') {
        hidden[name] = value;
      }
    }
    if (salt.isEmpty) {
      salt = RegExp(
            r'''(?:pwdDefaultEncryptSalt|pwdEncryptSalt)\s*=\s*["']([^"']{16})["']''',
          ).firstMatch(html)?.group(1) ??
          '';
    }
    if ((hidden['execution'] ?? '').isEmpty || salt.isEmpty) {
      throw StateError('登录页格式已变化，无法自动登录，请手动登录');
    }
    return CasLoginForm(action: action, encryptSalt: salt, hiddenFields: hidden);
  }

  static int _findLoginFormStart(String html) {
    final lower = html.toLowerCase();
    for (final marker in const [
      'id="casloginform"',
      "id='casloginform'",
      'id="pwdfromid"',
      "id='pwdfromid'",
    ]) {
      final idx = lower.indexOf(marker);
      if (idx >= 0) {
        final start = html.lastIndexOf('<', idx);
        if (start >= 0) return start;
      }
    }
    return -1;
  }

  static Map<String, String> _parseAttrs(String tag) {
    final map = <String, String>{};
    final re = RegExp(
      r'''([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))''',
    );
    for (final m in re.allMatches(tag)) {
      final key = m.group(1)!.toLowerCase();
      final raw = m.group(2) ?? m.group(3) ?? m.group(4) ?? '';
      map[key] = raw
          .replaceAll('&amp;', '&')
          .replaceAll('&quot;', '"')
          .replaceAll('&#39;', "'");
    }
    return map;
  }

  static String? loginErrorMessage(String html) {
    if (html.contains('用户名或者密码有误') ||
        html.contains('用户名或密码') ||
        html.contains('密码错误') ||
        html.contains('用户名不存在')) {
      return '账号或密码不正确，请检查后重试';
    }
    if (html.contains('账号已锁定') || html.contains('已被锁定')) {
      return '账号已被临时锁定，请稍后手动登录';
    }
    if (html.contains('验证码错误') ||
        html.contains('请输入验证码') ||
        RegExp(r'''id=["']captchaImg["'][^>]*src=["'][^"']+["']''',
                caseSensitive: false)
            .hasMatch(html)) {
      return '需要图形验证码，请手动登录一次';
    }
    if (html.contains('二次验证') || html.contains('动态码验证')) {
      return '需要二次验证，请手动登录一次';
    }
    return null;
  }

  /// 复刻 CAS `encrypt.js`：AES-128-CBC/PKCS7，明文 = 64 位随机串 + 密码。
  static String encryptCasPassword(
    String password,
    String salt, {
    String? fixedPrefix,
    String? fixedIv,
  }) {
    final keyBytes = Uint8List.fromList(utf8.encode(salt));
    if (keyBytes.length != 16) {
      throw ArgumentError('CAS 密码加密盐长度异常');
    }
    final prefix = fixedPrefix ?? _randomStr(64);
    final ivStr = fixedIv ?? _randomStr(16);
    final ivBytes = Uint8List.fromList(utf8.encode(ivStr));
    final plainBytes = Uint8List.fromList(utf8.encode('$prefix$password'));
    final cipher = _Aes128Cbc.encrypt(plainBytes, keyBytes, ivBytes);
    return base64Encode(cipher);
  }

  static String _randomStr(int len) => List.generate(
        len,
        (_) => _randomChars[_random.nextInt(_randomChars.length)],
      ).join();
}
