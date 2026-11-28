import 'package:flutter/services.dart';

import 'models.dart';

/// iOS WKHTTPCookieStore 原生通道封装。
///
/// 解决 iOS 上 `document.cookie` 无法读取 `HttpOnly` Cookie（如 CASTGC、JSESSIONID）的问题。
class WkCookieBridge {
  static const MethodChannel _channel =
      MethodChannel('com.ecut.campuspocket/wk_cookies');

  static Future<List<CookieRecord>> getAllCookies() async {
    try {
      final raw = await _channel.invokeMethod<List<dynamic>>('getAllCookies');
      if (raw == null) return const [];
      final now = DateTime.now().millisecondsSinceEpoch;
      return raw.whereType<Map<dynamic, dynamic>>().map((item) {
        return CookieRecord(
          domain: (item['domain'] ?? '').toString(),
          name: (item['name'] ?? '').toString(),
          value: (item['value'] ?? '').toString(),
          path: (item['path'] ?? '/').toString().isEmpty
              ? '/'
              : (item['path'] ?? '/').toString(),
          expiresAt: (item['expiresAt'] as num?)?.toInt() ?? 0,
          secure: item['secure'] == true,
          updatedAt: now,
        );
      }).toList();
    } on MissingPluginException {
      return const [];
    } catch (_) {
      return const [];
    }
  }

  static Future<void> setCookies(List<CookieRecord> cookies) async {
    if (cookies.isEmpty) return;
    try {
      await _channel.invokeMethod<void>(
        'setCookies',
        cookies.map((e) => e.toJson()).toList(),
      );
    } on MissingPluginException {
      // 非 iOS 或测试环境忽略
    } catch (_) {}
  }

  static Future<void> clearAllCookies() async {
    try {
      await _channel.invokeMethod<void>('clearAllCookies');
    } on MissingPluginException {
      // 非 iOS 或测试环境忽略
    } catch (_) {}
  }
}

/// iOS CoreLocation 轻量单次定位通道封装。
class DeviceLocationBridge {
  static const MethodChannel _channel =
      MethodChannel('com.ecut.campuspocket/location');

  static Future<({double latitude, double longitude})?>
      getCurrentLocation() async {
    try {
      final raw = await _channel
          .invokeMethod<Map<dynamic, dynamic>>('getCurrentLocation');
      if (raw == null) return null;
      final lat = (raw['latitude'] as num?)?.toDouble();
      final lng = (raw['longitude'] as num?)?.toDouble();
      if (lat == null || lng == null) return null;
      return (latitude: lat, longitude: lng);
    } on MissingPluginException {
      return null;
    } catch (_) {
      return null;
    }
  }
}

/// iOS 原生 AVFoundation 离线二维码扫描器桥接。
class QrScannerBridge {
  static const MethodChannel _channel =
      MethodChannel('com.ecut.campuspocket/qr_scanner');

  static Future<String?> scanQr({String title = '扫描二维码'}) async {
    try {
      final result = await _channel.invokeMethod<String>(
        'scanQr',
        {'title': title},
      );
      if (result == null || result.trim().isEmpty) return null;
      return result.trim();
    } on MissingPluginException {
      return null;
    } catch (_) {
      return null;
    }
  }
}

/// iOS 原生 PHPickerViewController + Vision 离线中英文截图文字识别桥接。
///
/// 零第三方依赖、零相册全局权限申请、所有识别均在设备本地离线完成。
class OfflineOcrBridge {
  static const MethodChannel _channel =
      MethodChannel('com.ecut.campuspocket/ocr');

  static Future<String?> pickImageAndRecognizeText() async {
    try {
      final result =
          await _channel.invokeMethod<String>('pickImageAndRecognizeText');
      if (result == null) return null;
      return result.trim();
    } on MissingPluginException {
      return null;
    }
  }
}
