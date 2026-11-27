import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// 敏感凭据加密存储抽象，便于单元测试用内存实现替换 iOS Keychain。
abstract class SecureVault {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
  Future<void> clearAll();
}

/// iOS Keychain 安全存储实现。
///
/// 不设置自定义 `groupId` / Keychain Access Group，确保免费 Apple ID 自签
/// 在无付费 Entitlements 时可正常持久化且覆盖续签不丢数据。
class KeychainSecureVault implements SecureVault {
  KeychainSecureVault()
      : _storage = const FlutterSecureStorage(
          iOptions: IOSOptions(
            accessibility: KeychainAccessibility.first_unlock_this_device,
          ),
        );

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) =>
      _storage.write(key: key, value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);

  @override
  Future<void> clearAll() => _storage.deleteAll();
}

/// 测试用内存安全库。
class InMemorySecureVault implements SecureVault {
  final Map<String, String> _map = {};

  @override
  Future<String?> read(String key) async => _map[key];

  @override
  Future<void> write(String key, String value) async {
    _map[key] = value;
  }

  @override
  Future<void> delete(String key) async {
    _map.remove(key);
  }

  @override
  Future<void> clearAll() async {
    _map.clear();
  }
}
