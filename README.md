# 冻小梨（CampusPocket）

东华理工大学（ECUT）校园服务聚合 App。App 显示名为“冻小梨”，工程 / 包名仍为 CampusPocket。**纯本地、无后端**，所有凭据只保存在手机的加密数据库中。

> 非官方项目。仅限校园网环境使用，请遵守学校相关管理规定。

## 下载

- GitHub Releases：<https://github.com/ncxiaofeng/CampusPocket/releases>
- 蓝奏云固定文件夹（提取码 `c3bc`）：<https://ncxiaofeng.lanzoub.com/b0j1u822d>
  - 内含各版本 ZIP（APK + SHA256 + 安装说明）；免费蓝奏云账号不支持直接分享 APK，所以国内镜像提供 ZIP。

App 内「更多 → 关于 冻小梨」也提供检查更新、项目地址和蓝奏云下载入口。

## 当前进度

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| Phase 1 | 项目骨架、Material 3 静态 UI、三页底部导航 | ✅ |
| Phase 2 | Keystore + SQLCipher、WebView 登录捕获、CookieJar、会话过期异常 | ✅ |
| Phase 3 | 业务 Adapter（教务 / 电费 / 图书馆 / 慧生活798 / U净 / 闪动 / 衣可林）+ WorkManager | 🚧 教务课表/成绩/考试/空闲教室/通知公告、宿舍电费、图书馆空位、U净洗衣已接入并真机验证；慧生活证书固定、闪动需 sign；其余待补 |
| Phase 4 | 真实数据绑定、图表、过期警告条、退出清理 | ✅ 课表 / 成绩 / 电费读取 Room；同步、空状态、过期警告条与退出清理完成 |
| Phase 5 | 混淆、权限清理、说明文档 | 🚧 混淆规则与发布构建已配置，待签发布包 |

## 已实现

- 首页：今日课程、照明/空调用电两列卡片、7 天用电微型柱状图、校园卡入口，全部读取本地数据库
- 课表：登录后同步真实课表，WakeUp 风格逐节课表（每节一行）；左侧显示 1-10 节上下课时间与当前时间红线；课程块显示课程名、校区、教室、教师，显示不全时点课程可看完整信息；周次日期与教务一致（2026 秋季第 1 周 = 09-07）
- 成绩：同步全部学期成绩，按学期分组，展示学分 / 绩点 / 加权绩点，不及格标红
- 考试：同步本学期考试安排，按“即将考试 / 已结束”分组，展示日期、时间、考场、座位与备注；教务未发布时明确提示
- 空闲教室：原生查询 11 个校区、第 1-20 周、周一至周日、1-12 节（可多选）的空教室，支持按楼号筛选、按楼分组；保留“官方页面”兜底入口
- 通知公告：原生读取教务“学生通知公告”，支持标题关键词检索、分页加载与正文阅读；图片 / 表格等完整排版可一键打开官方页
- 图书馆空位：免登录查询主校区 / 枫林 / 抚州各楼层座位余量，支持“现在 / 全天”与日期切换；预约、签到打开官方 H5（统一身份认证），App 不保存图书馆密码
- 校园卡：原生读取 ehall 一卡通的可用余额、本月消费/充值汇总与交易流水；支付宝随充随付的等额正负记录按充值/消费分开展示；首次在“官方页”登录一次
- 网上办事大厅：内置学校 ehall（PC 版，可切手机 UA），作为通知公告、等级考试等剩余官方服务的统一入口
- 电费：从今日校园“宿舍缴费”同步电表余额和最近 30 天日用量（按 7 天分块拉取，范围接口不可用时逐日兜底）；首页与电费页展示**昨日用电**（当天数据教务侧尚未结算）+ 7/30 天柱状图 + 历史记录
- U净洗衣：手机号 + 短信验证码登录（复现 H5 内置的 HMAC 验证码签名）；支持广兰 / 青岚 / 抚州校区切换，只读展示附近洗衣房、波轮/滚筒空闲数、预计等待时间与洗护价格，以及运行中 / 历史订单；顶部“最近使用”从历史订单反查常用楼栋（如 16栋-1），解决它不在附近 10 家列表里的问题；可一键跳转支付宝 U净 小程序扫码下单/支付（App 不创建订单），登录态保存在本机加密数据库
- 更多：账号状态、数据同步、成绩查询、生活服务分组、隐私说明、退出登录（清库 + 清 Cookie）
- 关于 冻小梨：检查更新（GitHub Releases API）、项目主页、蓝奏云 APK / ZIP 下载入口、版本信息
- 统一身份认证单点登录：一次登录拿到 `CASTGC`，课表、成绩、考试、空闲教室、通知公告、电费、校园卡、办事大厅共用；各系统会话过期时自动静默换票，不再逐模块重新登录
- 会话过期：根布局红色警告条，重新登录一次即可恢复全部统一认证系统
- 校园网检测：启动 / 回到前台时探测内网主机；不在校园网时顶部提示并可自动跳转零信任 VPN 登录页
- 校外 WebVPN：零信任认证后打开官方门户，进入“新教务管理系统”任一功能一次，App 会保存代理域会话；之后课表/成绩/考试/空闲教室会通过 `172-20-130-13.atrust.ecut.edu.cn` 代理同步，无需系统 VPN。门户右上角可切 PC / 手机 UA
- 登录：内嵌教务 SSO / 官方 CAS 页面，用户手动完成登录；点击“完成登录”后由 `CookieManager` 提取 Cookie，教务目标要求主域名存在 `JSESSIONID`，再写入 SQLCipher
- 教务同步：`JwAdapter` 调用正方 `xskbcx_cxXsgrkb.html`（课表）与 `cjcx_cxXsgrcj.html`（成绩），`JwSyncManager` 整体替换 Room 缓存
- 安全：数据库口令为随机 256 bit，由 Android Keystore 的 AES-GCM 密钥包裹；App 不读取、不保存账号密码
- 登录凭证写入 SQLCipher 后立即清空 WebView 的明文 Cookie，避免会话副本长期驻留
- 网络：OkHttp `CookieJar` 自动从加密库注入 / 回写 Cookie；统一认证客户端拦截 401 与回跳登录页，抛出 `SessionExpiredException`
- 图表：用 Compose Canvas 实现堆叠柱状图，未引入 Vico（避免图表库 API 与 Kotlin / Compose 版本耦合）

## 构建

要求：JDK 17、Android SDK 35。

本机工具链放在 `_toolchain/`（不随项目分发）：

```bash
JAVA_HOME="<你的 JDK 17 路径>" ./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

> Gradle 发行版默认指向腾讯镜像（见 `gradle/wrapper/gradle-wrapper.properties`），便于国内网络构建。

发布包默认未签名。需要签名时，在项目根目录创建 `keystore.properties`：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=******
keyAlias=campuspocket
keyPassword=******
```

然后 `./gradlew assembleRelease` 会输出已签名的 `app-release.apk`（0.4.1 约 21 MB，R8 + 资源压缩）。

> 正式签名使用项目外的专用 `release.jks`（`CampusPocket-keys/`）；`keystore.properties` 不入库，
> 请离线备份密钥与密码。换签名后旧调试包无法覆盖安装。

### 本地构建参数（不入库）

`local.properties` 里可配置第三方 H5 的签名 / 系统参数：

```properties
ujing.hmac.key=...
electricity.operator.pwd=...
```

未配置时，U净洗衣与宿舍电费会给出明确提示，其它功能不受影响。公开仓库不包含这些值。

## 目录

```
app/src/main/java/com/ecut/campuspocket/
├── AppContainer.kt          # 手工依赖容器（懒加载）
├── data/
│   ├── local/               # Room + SQLCipher、Keystore 口令管理
│   └── repository/          # 凭据 / 缓存仓库
├── network/                 # CookieJar、端点、会话异常、签名器
├── navigation/              # 路由常量
└── ui/                      # Compose 主题、组件、页面
```

## 安全边界

- 只读优先：不实现阳光跑自动打卡、不实现电费充值、不做支付。
- 登录页始终是学校官方页面；App 不注入 JS 读取表单。
- 退出登录会清空 `auth_credentials`、`cookies`、课表与电费缓存，并清空 WebView Cookie。
- 应用禁止备份（`allowBackup=false`），数据库与偏好不参与云备份 / 设备迁移。
- U净验证码的 HMAC key 与宿舍缴费的 `operatorPwd` 已改为构建期注入（`local.properties` → `BuildConfig`），公开仓库不包含明文；未配置时对应功能会明确提示。

## 配套抓包工具

`capture/` 是一个独立的“校园抓包助手”APK，用自带 WebView 打开 U净 / 慧生活798 / 闪动校园的 H5 版本，通过 JS 注入记录请求与响应，供离线补充接口参数。仅覆盖 H5，原生 App 的 HTTPS 抓包受 Android 证书信任与证书固定限制，无 root 无法可靠解密。详见 `capture/README.md`。

## 已知限制

- 教务系统（正方 `172.20.130.13`）已接入课表与成绩，需在校园网内使用。
- 电费已接入（今日校园 → 宿舍缴费背后的 xgwx H5，需登录一次）。
- U净洗衣已接入官方 H5 后端只读接口，并支持跳转支付宝小程序完成下单/支付；慧生活798 依赖支付宝小程序（设备二维码启动），App 已提供一键打开小程序入口；小程序流量走支付宝网关且证书固定，设备状态暂不可抓取；其原生 App 加固 + 证书固定，未接入。闪动已抓到接口但每请求带 `sign`，尚未接入。页面在无数据时会明确提示，不会展示伪造数据。
- 各业务系统（尤其闪动校园）使用独立授权，需要单独的登录链路。
- 零信任网页登录本身不建立系统 VPN 隧道；但门户的 WebVPN 可以把内网 IP 映射成 `https://172-20-130-13.atrust.ecut.edu.cn`。App 已支持保存该代理会话并原生同步教务，**校外首次**需在门户里进入“新教务管理系统”任一功能一次；之后可正常同步。代理会话过期后重进一次门户即可。
- aTrust 隧道使用深信服私有协议，App 不内置协议实现；提供官方客户端唤起（更多 → aTrust 隧道）与安装引导，用于需要完整内网（非 WebVPN 发布）的场景。
- 仅面向校园网；校外需先连接学校 VPN / aTrust 客户端（本 App 不处理）。
- 不提供电费充值、洗衣下单、阳光跑自动打卡等写入能力。

## 逆向记录

APK 静态分析结果与待补参数见 `docs/INTEGRATION.md`。
