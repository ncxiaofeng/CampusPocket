# 校园抓包助手（CampusCapture）

配合 CampusPocket 使用的 H5 抓包工具，独立 APK。

## 能做什么

- 内置 U净（洗衣 / 饮水 / 淋浴）、慧生活798、闪动校园的 H5 入口，也可输入任意网址。
- 在自带 WebView 中打开页面，通过 JS 注入记录：
  - `fetch` / `XMLHttpRequest` / `sendBeacon`
  - 请求方法、URL、请求头、请求体
  - 响应状态、响应头、响应体
  - 页面导航、JS 错误、console.warn/error
- 日志实时显示，可筛选、复制、导出 JSONL。
- 自动脱敏：Cookie、Authorization、密码、验证码、token、手机号、ticket 等字段写日志前替换为 `***`。
- 日志只写应用私有目录，导出时通过系统分享发送。

## 不能做什么

- **不能抓原生 App 的 HTTPS。** Android 7+ 默认不信任用户安装的 CA 证书，且大多数 App 使用证书固定；无 root / 系统证书时无法可靠解密。
- 不能绕过微信 OAuth。提示“请在微信中打开”的页面需要微信内置浏览器。
- 不支持 WebSocket 消息体、HTTP/2 帧级抓包、二进制响应体。

## 构建

```bash
JAVA_HOME=<项目>\_toolchain\jdk-17.0.20.1+1 ./gradlew :capture:assembleDebug
```

产物：`capture/build/outputs/apk/debug/capture-debug.apk`

## 使用

1. 安装 `capture-debug.apk`；
2. 选择 U净 / 慧生活798 / 闪动 H5，或输入自定义网址；
3. 在页面里完成登录和操作；
4. 点右上角“日志 N”查看实时请求；
5. 点“导出”，通过系统分享把 JSONL 发到电脑，交给 CampusPocket 分析。

日志字段：`t`（req/res/err/page/console）、`id`、`ts`、`method`、`url`、`status`、`mime`、`headers`、`body`、`page`、`error`。
