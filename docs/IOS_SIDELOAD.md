# 冻小梨 iOS 未越狱免费自签安装指南

本指南适用于**未越狱 iPhone / iPad**，**不需要 688 元/年的苹果付费开发者账号**，只需使用普通免费 Apple ID 即可通过个人自签安装「冻小梨」。

> 安全提示：冻小梨 iOS 版为纯本地无后端 App，**不包含** Push 推送、App Groups、桌面小组件等付费开发者扩展，仅占用免费 Apple ID 的 **1 个 App 名额与 1 个 App ID**。

---

## 第一步：获取未签名 IPA 安装包

你可以通过以下任一方式下载最新的 `CampusPocket-iOS-<版本>-unsigned.ipa`：

1. **GitHub Releases**：<https://github.com/ncxiaofeng/CampusPocket/releases>（查找标题含「iOS 自签版」的发布页）
2. **GitHub Actions 构建产物**：进入仓库 `Actions → Build iOS Unsigned IPA` 下载最新构建的 Artifact 压缩包并解压得到 `.ipa`。
3. **蓝奏云镜像**（如作者同步上传）：<https://ncxiaofeng.lanzoub.com/b0j1u822d>（提取码 `c3bc`）

---

## 第二步：选择一种免费自签安装工具

免费 Apple ID 自签的证书有效期为 **7 天**，到期前续签一次即可继续使用（同一 Apple ID 覆盖安装不会丢失登录态和课表缓存）。

### 方案 A：SideStore（强烈推荐 —— 之后无需连电脑，手机连 Wi-Fi 即可续签）

1. 首次在 Windows / macOS 电脑上使用 AltServer 按 [SideStore 官方指南](https://sidestore.io/) 将 **SideStore** 装到手机上，并配好配对文件与 StosVPN / WireGuard 回路。
2. 在手机上下载 `CampusPocket-iOS-<版本>-unsigned.ipa` 到「文件」App。
3. 打开 **SideStore**，点击左上角 `+` 选择该 `.ipa`，按提示完成签名安装（选择 **Remove App Extensions** 如出现提示）。
4. **日常续签**：每 7 天内打开一次 SideStore（连任意 Wi-Fi + 开启回路 VPN）点「刷新」即可，全程不再需要电脑。

### 方案 B：Sideloadly（最简单 —— 适合有 Windows / macOS 电脑的用户）

1. 在电脑上安装 [Sideloadly](https://sideloadly.io/)（Windows 用户需同时安装苹果官网独立版 iTunes 和 iCloud，勿用微软商店版）。
2. 用数据线将 iPhone 连接电脑，在手机上点「信任此电脑」。
3. 打开 Sideloadly：
   - 将 `CampusPocket-iOS-<版本>-unsigned.ipa` 拖入 IPA 图标框；
   - 在 `Apple ID` 栏输入你的免费 Apple ID（建议可使用备用小号）；
   - 点击 **Start**，按提示输入密码和双重认证验证码，等待进度条显示 `Done`。
4. **7 天续签**：到期前再连电脑（或开启 Sideloadly Wi-Fi 自动刷新）重签一次即可。

### 方案 C：AltStore

1. 在电脑上安装 [AltServer](https://altstore.io/) 并安装 AltStore 到 iPhone。
2. 将 `.ipa` 通过隔空投送或「文件」导入 AltStore 安装。
3. 电脑与手机在同一 Wi-Fi 下即可自动后台续签。

---

## 第三步：首次在 iPhone 上信任并启用（必做）

1. **信任开发者证书**：打开 iPhone「设置 → 通用 → VPN 与设备管理」，点你的 Apple ID → 点击「信任」。
2. **开启开发者模式（iOS 16 及以上）**：打开「设置 → 隐私与安全性 → 开发者模式」→ 打开并按提示重启一次手机。
3. **允许「本地网络」权限**：首次打开冻小梨并在校园网内点击登录/同步时，系统会弹出「冻小梨想查找并连接到本地网络上的设备」，**请务必点击「允许」**，否则 iOS 会拦截对学校内网教务 `172.20.130.13` 的直连请求。

---

## 常见问题

### 1. 免费 Apple ID 有什么限制？
- 每个免费 Apple ID 同时最多激活 **3 个**自签 App（含 SideStore / AltStore 本身算 1 个，所以还能装 2 个，冻小梨只占 1 个）。
- 证书有效期 **7 天**，到期打不开时**不要卸载**！直接用同一个 Apple ID 覆盖重签即可立即恢复，Keychain 登录态和课表数据全部保留。

### 2. 在校外没有连接校园 Wi-Fi 怎么查课表和成绩？
- 打开冻小梨 →「更多 → 零信任 WebVPN 门户」登录零信任并在门户里进入一次「新教务管理系统」。
- 页面顶部提示「已捕获校外 WebVPN 教务代理会话」后，返回首页点击「同步」即可走学校 WebVPN 代理原生同步课表、成绩、考试、空闲教室与公告，无需额外装系统 VPN。

### 3. 为什么提示“无法验证应用”？
- 请确认手机处于联网状态，且已在「设置 → 通用 → VPN 与设备管理」信任当前 Apple ID；若超过 7 天未续签，重新自签覆盖安装即可。
