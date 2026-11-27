/// 登录会话失效异常（可触发统一认证重新登录）。
class SessionExpiredException implements Exception {
  final String message;

  const SessionExpiredException([this.message = '登录状态已过期，请重新登录']);

  @override
  String toString() => message;
}

/// 校外 WebVPN 代理会话缺失或已失效异常。
class ProxySessionRequiredException implements Exception {
  final String message;

  const ProxySessionRequiredException([
    this.message = '当前在校外网络，请先打开「零信任门户」并进入一次“新教务管理系统”以建立代理会话',
  ]);

  @override
  String toString() => message;
}

/// 运行期高级配置缺失异常。
class ConfigurationRequiredException implements Exception {
  final String message;

  const ConfigurationRequiredException([
    this.message = '缺少必要参数，请前往「更多 → 高级配置」导入',
  ]);

  @override
  String toString() => message;
}

/// 学习通登录态失效异常。
class ChaoxingSessionExpiredException implements Exception {
  final String message;

  const ChaoxingSessionExpiredException([this.message = '学习通登录已过期，请重新登录']);

  @override
  String toString() => message;
}

/// 慧生活798 Token 失效异常。
class HuiLifeTokenExpiredException implements Exception {
  final String message;

  const HuiLifeTokenExpiredException([this.message = '慧生活登录已过期，请重新登录']);

  @override
  String toString() => message;
}
