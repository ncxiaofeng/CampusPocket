# 发布说明

## 网络环境

冻小梨（CampusPocket）面向**东华理工大学校园网**。校外使用时需先连接学校 VPN / aTrust 客户端；
App 本身不建立 VPN，也不绕过学校的访问控制。

零信任网页登录（`https://atrust1.ecut.edu.cn`）认证后，App 顶部显示“零信任已认证”并提供官方门户入口，
不会再提示“未连接校园网”或反复弹登录页。门户的 WebVPN 会把内网 IP 映射成
`https://172-20-130-13.atrust.ecut.edu.cn`；在门户里进入“新教务管理系统”任一功能一次后，
App 会保存代理会话，并以代理域名原生同步课表 / 成绩 / 考试 / 空闲教室，**校外 5G 实测可用**。
代理会话过期后重进一次门户即可。

aTrust 使用深信服私有隧道协议，App 不内置协议实现，也不绕过学校访问控制；
“更多 → aTrust 隧道”用于唤起已安装的官方客户端，未安装时引导到应用商店 / 官方使用指南，
用于需要完整内网（非 WebVPN 发布）的场景。

## 隐私与权限

| 权限 | 用途 |
| --- | --- |
| `INTERNET` | 访问学校官方页面与校园服务接口 |
| `ACCESS_NETWORK_STATE` | WorkManager 联网约束 / 网络状态辅助 |
| `WAKE_LOCK` / `RECEIVE_BOOT_COMPLETED` | WorkManager 每小时采样电费（接口启用后） |

- 不申请存储、定位、相机、通讯录、电话等权限。
- 不允许系统备份：`allowBackup=false`，数据库与 SharedPreferences 不进入云备份 / 设备迁移。
- 账号密码只在官方登录页输入；App 不读取 DOM、不记录键盘输入。
- 会话 Cookie 经 Android Keystore 保护的密钥 + SQLCipher 加密落库；落库后清除 WebView 明文 Cookie。

## 构建与签名

```bash
./gradlew assembleDebug     # 可直接安装的调试包
./gradlew assembleRelease   # R8 + 资源压缩，默认未签名
```

发布签名：在项目根目录提供 `keystore.properties`（见 README），再次执行 `assembleRelease`。

> 正式签名使用项目外的专用 `release.jks`（本机位于 `CampusPocket-keys/`）；
> `keystore.properties` 已被 gitignore，不入库。请把 `.jks` 与密码离线备份；
> 换签名后旧调试包无法覆盖安装，需要让用户卸载一次。

产物：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`（配置签名后；0.4.1 实测约 21 MB）

## 分发建议（2026-10-25）

- **首选：GitHub / Gitee Releases + 蓝奏云镜像**。Release 页放 APK + 更新说明，版本号清晰；
  蓝奏云给国内同学一个直连下载。两者互补：Release 负责版本历史 / 自动更新，蓝奏云负责“发个链接就能下”。
- GitHub 国内访问不稳定：可以用 `ghproxy.net` / `gh-proxy.com` 等镜像下载 Release 附件，
  或把仓库镜像到 **Gitee（码云）**。Gitee 国内直连、有 Releases，适合作为主仓库；
  公开仓库可能需要实名认证。
- 蓝奏云适合非技术用户，但没有版本历史 / API / 自动更新，链接也可能失效，不适合作为唯一来源。
- App 内「关于 冻小梨」已内置检查更新：读取 GitHub Releases 最新版并与 `versionName` 比较；
  GitHub 不可达时引导用户走蓝奏云。每次发版记得同步更新 `data/AppLinks.kt` 里的蓝奏云地址
  （蓝奏云直链随文件变化；如需稳定入口，可改成固定的分享页 / 文件夹链接）。
- 发布纪律：`versionCode` 递增、`versionName` 更新、始终用同一个 release keystore 签名；
  不要把 `.jks` / `keystore.properties` / `local.properties` 提交到公开仓库。

## GitHub Release 自动发布

`.github/workflows/release.yml` 在推送 `v*` tag 时自动创建 GitHub Release：

1. 把 `CampusPocket-<version>.apk` 推到临时分支 `release-assets`；
2. 在 `main` 上打并推送 `v<version>` tag；
3. 工作流从 `release-assets` 读取 APK，用 `docs/RELEASE_NOTES_<version>.md` 作为说明，
   通过内置 `GITHUB_TOKEN` 创建 Release 并上传 APK；
4. Release 资产已独立保存，之后可以删除 `release-assets` 分支，避免 20MB 二进制长期留在仓库里。

> 工作流需要仓库 Actions 的 `GITHUB_TOKEN` 具有 `contents: write` 权限（`permissions` 已声明）。
> 如果仓库设置把默认权限改成只读，需要改成 Read and write 或使用 PAT。

### 0.4.1 蓝奏云镜像（2026-10-25）

- 固定文件夹（提取码 `c3bc`）：https://ncxiaofeng.lanzoub.com/b0j1u822d
  - 内含 `CampusPocket-0.4.1.zip`（APK + SHA256 + 安装说明），以后发版只需上传新 ZIP 并移入该文件夹。
- 免费账号已不支持直接分享 APK（打开会提示“非会员不支持分享 apk 文件”），因此国内镜像统一提供 ZIP；
  原始 APK 放在 GitHub Release。
- 上传脚本只保存在本地 `_analysis/lanzou_upload.py`，Cookie 不入库；Cookie 过期后重新导出。
  文件夹 ID `14140863`，可用 `task=20&folder_id=14140863&file_id=<新文件ID>` 把新 ZIP 移入。

### 0.4.0 蓝奏云镜像（2026-10-24）

- APK：https://ncxiaofeng.lanzoub.com/i38c64d7sg6f （20.4 MB；免费账号在手机端分享 APK 可能受限）
- ZIP（含 APK + SHA256 + 安装说明）：https://ncxiaofeng.lanzoub.com/ic6YC4d7si7i （10.2 MB，手机友好）

## 功能边界

- 校园网检测仅探测学校内网主机，不读取 SSID、不申请定位权限。
- 零信任网页登录是官方页面，不建立 VPN 隧道；校外访问内网服务仍需 aTrust 客户端。
- 只读：不实现电费充值、洗衣下单、阳光跑自动打卡或上传。
- 第一版不接入高风控系统的写入链路；相关服务仅做展示或 WebView 兜底。
- 接口未接入时，界面显示明确的空状态，不展示伪造数据。

## 版本

- 0.4.1（2026-10-25）：
  - 统一身份认证单点登录：一次登录后，课表、成绩、考试、空闲教室、通知公告、电费、校园卡、
    办事大厅共用同一个 CAS 会话；各系统会话过期时自动静默换票，不再逐模块重新登录。
  - 修复进入电费页仍提示“登录已过期”、重新登录又回到统一认证页的问题。
  - 新增“关于 冻小梨”：检查更新、GitHub 项目主页 / Releases、蓝奏云安装包下载、版本信息。
  - 构建参数脱敏：U净 HMAC key 与宿舍缴费 operatorPwd 改为 `local.properties` → `BuildConfig`
    注入，公开仓库不含明文。
- 0.4.0（2026-10-24）：
  - 新增 U净洗衣：手机号 + 短信验证码登录（复现 H5 内置 HMAC 签名）；广兰 / 青岚 / 抚州校区切换；
    从历史订单反查“最近使用”（16栋-1）；波轮 / 滚筒空闲数、等待时间、洗护价格、运行中 / 历史订单；
    一键跳转支付宝 U净 小程序扫码下单 / 支付（App 不创建订单）。
  - 新增慧生活798 页面：官方支付宝小程序入口（`appId=2019061465519660`），并说明官方每年会临时关闭
    小程序设备页、引导下载 App。
  - 服务图标从单个汉字改为 Material 图标（洗衣机、水滴、闪电、校园卡、图书馆、广播等）。
  - 发布包开启 R8 + 资源压缩，修复 debug 包滑动 / 点击掉帧；`gfxinfo` 实测 99th percentile 约 16ms。
- 0.3.2：应用更名为“冻小梨”，启用卡通冻梨自适应图标（含单色主题图标）；包名与数据保持不变。
- 0.3.1：新增“通知公告”原生列表 / 正文 / 标题检索（教务移动端 `xwglMobile_cxMoreXwList`，
  列表接口直接返回正文；图片与表格可打开官方页）；修复 30 天用电图表横轴日期被裁成
  “9/”“10” 的问题（按实际标签数量均分宽度）。真机复核：校园卡余额 / 本月汇总 / 188 笔流水、
  30 天用电（近 30 天 62.2 度）均正常。
- 0.3.0：新增考试安排（考场 / 时间，已真机验证：本学期教务尚未发布）；
  空闲教室原生查询（校区 / 周次 / 星期 / 节次 / 楼号，分页取全，官方页兜底）；
  课表改为 WakeUp 风格逐节课表（精确节次、校区/教室/教师、点击看详情、当前时间红线），
  学期起点按 2026-09-07 修正；电费主指标改为昨日用电；
  新增图书馆空位查询（免登录，校区/楼层/现在/全天）与官方预约入口；
  修复零信任 VPN 登录后门户白屏（到 atrust1 自动保存并返回，同时注入已有会话）；
  认证后顶部改为“零信任已认证”，不再重复提示未连接校园网或自动弹登录页，并提供零信任门户入口；
  修复 aTrust 门户在 WebView 中 `100vh=0` 导致的整页白屏，门户内“新教务管理系统”可正常打开；
  门户进入新教务一次后保存 WebVPN 代理会话，校外原生同步课表/成绩/考试/空闲教室；
  门户支持 PC / 手机 UA 切换；
  电费 30 天视图改为真实拉取（7 天分块 + 逐日兜底），不再是假按钮；
  新增校园卡原生余额/本月汇总/交易流水（ehall 一卡通，支付宝随充随付等额正负记录分开展示）；
  新增网上办事大厅入口（PC 版，可切手机 UA），承接通知/等级考试等剩余官方服务；
  Room 升级为显式迁移（v3→v4 增加节次/校区字段），避免升级安装清空登录态。
- 0.2.0：接入正方教务课表/成绩、今日校园宿舍电费（余额 + 7 天用量）、校园网检测与零信任登录跳转。
- 0.1.0：Phase 1 / 2 / 4 基础能力。
