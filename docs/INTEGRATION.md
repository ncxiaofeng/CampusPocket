# 校园系统接入记录

## 教务系统（正方 jwglxt）

2026-10-22 在校内网用 Edge DevTools 实测确认。

- 主机：`http://172.20.130.13`（内网 IP，仅校园网可达）
- SSO 入口（ehall 应用链接）：
  `http://172.20.130.13/sso/jziotlogin?url=kbcx%2fxskbcx_cxXskbcxIndex.html%3fgnmkdm%3dN253508%26layout%3ddefault`
- 课表页：`GET /jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N253508&layout=default`
  - 从页面 `<select id="xnm">` / `<select id="xqm">` 的 selected option 读取当前学年 / 学期（2026-2027 第 1 学期为 `xnm=2026&xqm=3`）。
- 课表接口：
  `POST /jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508`
  body：`xnm=2026&xqm=3&kzlx=ck&xsdm=&kclbdm=&kclxdm=`
  响应 `kbList[]`，关键字段：`kcmc`（课程）、`xm`（教师）、`cdmc`（教室）、`xqj`（星期 1-7）、`jc`（节次如 1-2节）、`zcd`（周次如 4-9周 / 1-16周(单)）、`kch`。
- 成绩接口：
  `POST /jwglxt/cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305005`
  body：`xnm=&xqm=&sfzgcj=&cxbkjgcj=&kcbj=&pkey=&_search=false&nd=<毫秒>&queryModel.showCount=200&queryModel.currentPage=1&queryModel.sortName=+&queryModel.sortOrder=asc&time=0`
  - `xnm/xqm` 留空返回全部学期；`showCount=200` 一次取完（实测 53 条）。
  - 响应 `items[]`，关键字段：`kcmc`、`bfzcj`、`xf`、`jd`、`xnmmc`、`xqmmc`、`kcxzmc`、`khfsmc`、`row_id`。
- 考试接口（2026-10-24 真机验证，本学期 0 条）：
  `POST /jwglxt/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105`
  body：`xnm=2026&xqm=3&ksmcdmb_id=&kch=&kc=&ksrq=&kkbm_id=&_search=false&nd=<毫秒>&queryModel.showCount=200&queryModel.currentPage=1&queryModel.sortName=+&queryModel.sortOrder=asc&time=0`
  - 考试页的学年/学期下拉 id 是 `cx_xnm` / `cx_xqm`（课表页才是 `xnm` / `xqm`），已做兼容；读取失败时按当前日期回退。
  - 响应 `items[]`，字段：`xnmc`、`xqmmc`、`kcmc`、`kssj`（如 `2026-01-10(09:00-11:00)`）、`cdmc`、`cdxqmc`、`kch`、`ksmc`、`ksbz`、`khfs`、`jxbmc`、`kkxy`、`xqmc`、`ksfs`。
  - 该页面没有座位号 / 准考证号列（`zwh`/`zkzh` 不存在），App 仅在有值时显示。
  - 真机验证：教务官方页 2026-2027 第 1 学期显示“没有符合条件记录”，App 同步同样为 0，说明接口与参数正确，只是教务尚未发布考试。
- 空闲教室（2026-10-24 真机验证，原生）：
  - 页面：`GET /jwglxt/cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&layout=default`
    从 `#xqh_id` / `#lh` 解析校区与楼号，隐藏 input `#xnm` / `#xqm` 取当前学期。
  - 查询：`POST /jwglxt/cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155`
    body：`xqh_id=05&xnm=2026&xqm=3&cdlb_id=&cdejlb_id=&qszws=&jszws=&cdmc=&lh=&jyfs=0&cdjylx=&sfbhkc=&zcd=128&xqj=1&jcd=3&_search=false&nd=<毫秒>&queryModel.showCount=300&queryModel.currentPage=1&queryModel.sortName=cdbh+&queryModel.sortOrder=asc&time=0`
  - `zcd` 是周次位掩码（第 n 周 = `1 shl (n-1)`）；`jcd` 是节次位掩码（第 n 节同理）；`xqj` 为星期 1-7；`lh` 为空表示全部楼号。
  - 响应 `items[]`：`cdmc`、`cdbh`、`jxlmc`（楼）、`xqmc`、`cdlbmc`（类型）、`zws`（座位）、`kszws1`（考试座位）、`sfkjy`（可借用）、`cd_id`。
  - 校区切换：`GET /jwglxt/cdjy/cdjy_cxXqjc.html?xqh_id=04&xnm=2026&xqm=3&gnmkdm=N2155`
    返回 `{"lhList":[{"XQH_ID":"04","JXLDM":"5","JXLMC":"3号教学楼"}, ...]}`；
    该接口**不含“全部”选项**，客户端需补一个空 id 的“全部”。
  - 分页：响应 `totalResult` 是总条数；`showCount=300` 时需按 `currentPage` 翻页。
    实测广兰校区 570 间空闲，只取第一页会少 270 间。
  - 实测 2026-10-24 第 8 周周六第 1-2 节：枫林校区 113 间；广兰校区 570 间；按“图书馆（枫林）”筛选 25 间。
  - 官方页兜底：`/sso/jziotlogin?url=cdjy...` + WebView 注入 Cookie（`CampusWebPageScreen`）。
- 课表 UI 数据：
  - `kbList[].jc` 是精确节次（如 `1-2节`、`9-10节`），`xqmc` 是校区（如 `广兰校园`），`xm` 是教师；
    App 已存 `startPeriod` / `endPeriod` / `campus`，课表按“每节一行”绘制（WakeUp 风格）。
  - 2026 秋季学期第 1 周是 **2026-09-07（周一）**。已用手机里的 WakeUp 课程表核对：
    2026-10-24 是第 **7** 周，周次日期 10/19-10/25；教务页面同样显示第 7 周应上的课（如 `1-7周` 的现代海洋测绘）。
  - 上课时间采用南昌校区作息：1 节 08:00-08:45、2 节 08:55-09:40 … 10 节 19:55-20:40；
    抚州校区部分节次晚 10 分钟，当前未区分。
- Cookie：`172.20.130.13` 同时存在两个 `JSESSIONID`（path `/jwglxt` 与 `/sso`）。调用必须使用 `/jwglxt` 那个；CookieJar 已按路径过滤，同名取路径更长者。
- 实现：`data/remote/jw/JwAdapter.kt`、`data/repository/JwSyncManager.kt`；登录入口 `CampusEndpoints.JW_LOGIN`。
- 数据库升级教训：Room 版本变更若只靠 `fallbackToDestructiveMigration()`，升级安装会清空登录态。
  从 v2→v3 起改为显式 `Migration`（新增 `exams` 表），避免再次丢数据。

## 学生通知公告（正方移动端）

2026-10-24 通过零信任 WebVPN 门户进入“学生通知公告查询”并用 WebView CDP 实测。

- 页面：`GET /jwglxt/xtgl/xwglMobile_cxMoreXwList.html`（移动端公告列表，WebVPN 下同路径）
- 列表接口：`POST /jwglxt/xtgl/xwglMobile_cxMoreXwList.html`
  body（`application/x-www-form-urlencoded`）：
  `njdm=<起始偏移>&bjdm=<结束偏移>&doType=app&xwbt=<标题关键词>`
  - `njdm` / `bjdm` 实际是分页区间 `[start, end)`，不是年级/班级代码；官方页每页 4 条，App 用 10 条。
  - 响应是 **JSON 数组**，每项字段：`xwbh`（公告编号）、`xwbt`（标题）、`fbr`/`fbrxm`（发布人）、`fbsj`（发布日期）、`fbnr`（正文 HTML，列表接口直接返回）。
  - 正文里的图片是相对路径 `../ftp/...`，原生只渲染文字；完整排版走官方页。
- 详情接口（正文缺失时兜底）：
  `POST /jwglxt/xtgl/xwglMobile_cxXwDetail.html?doType=app&xwbh=<编号>`
  返回单个 JSON 对象，同样含 `xwbt` / `fbr` / `fbsj` / `fbnr`。
- 坑：页面 HTML 本身是 GBK，但这两个 AJAX 响应是 `application/json;charset=UTF-8`；OkHttp 直接按响应头解码即可。
- 实测 2026-10-24：列表 1 条《关于开展2026年秋季学期校园阳光健身跑的通知》，发布人李鹏，2026-10-21；正文 12957 字符。
- 实现：`JwAdapter.fetchNotices()`、`NoticesViewModel`、`NoticesScreen`；官方列表兜底用 `CampusWebPageScreen` + `JW_NOTICE_PATH`，详情页“官方页”直接打开 `xwglMobile_cxXwDetail.html?xwbh=...&hasHeader=`（`Routes.NOTICE_OFFICIAL_DETAIL`），保留图片 / 表格排版。

## H5 抓包（capture 模块）

无 root 的 Android 7+ 无法可靠解密原生 App 的 HTTPS（系统不信任用户 CA，且存在证书固定），因此另做了 `capture/` 独立 APK：

- 自带 WebView 打开 H5 版本，`WebViewCompat.addDocumentStartJavaScript` 注入抓包脚本。
- 记录 fetch / XHR / sendBeacon 的 URL、请求头、请求体、状态码、响应头、响应体；Cookie / Authorization / 密码 / 验证码 / token / 手机号写日志前脱敏。
- 导出 JSONL，通过系统分享发回电脑。
- 实测脚本在 Chromium 中可正确记录 fetch/XHR、脱敏嵌套 JSON 字符串（httpbin 验证）。

已确认的 H5 入口：

- U净 洗衣：`https://wx.zhinengxiyifang.cn/static/washer-h5/ujing/latest/index.html`
  - H5 的 `app.js` 调用 `https://phoenix.ujing.online:443/api/v1/`，与原生 App 同一后端。
- U净 饮水 / 淋浴：`https://static.ujing.com.cn/h5/water-h5/ujing/latest/index.html`、`.../shower-h5/...`
- 慧生活798：`https://cloud.hnkzy.com/h5/`
- 闪动 H5：`https://mh5.huachenjie.com/run-mobile-web`
  - H5 bundle 调用 `https://gateway.huachenjie.cn/web/api`（原生是 `api.huachenjie.com`）。

若 H5 提示“请在微信中打开”，需要微信内置浏览器，普通 WebView 无法完成该 OAuth 流程。

## 宿舍电费（今日校园 → 宿舍缴费 → 学生宿舍缴费）

2026-10-24 在真机上抓包并完成端到端验证。

- H5 主机：`https://xgwx.ecut.edu.cn`，入口：
  `GET /app/wechat/index.jsp?appId=wx3be3089d48d67c4e&operatorId=admin&operatorPwd=<固定系统参数>`
  （实现从 `local.properties` 的 `electricity.operator.pwd` 注入到 `BuildConfig`，公开仓库不含明文）
- 登录：CAS（authserver.ecut.edu.cn），与教务共用统一认证；登录后 xgwx 设置 `JSESSIONID`。
- 绑定宿舍 / 电表余额：
  `POST /app/app/api/user/searchBindHouseListForMoney`
  body：`userid=<学号>`
  返回：
  `data[].house_id / house_no / house_addr`，
  `data[].meter[].meter_id / meter_no / meter_balance / meter_balance_time`
  - `meter_balance` 为电表余额（元），实测 65.51。
- 按天用量：
  `POST /app/app/api/data/dosage`
  body：`houseId=<house_id>&meterId=<meter_id>&startTime=yyyy-M-d&endTime=yyyy-M-d`
  返回：
  `{"ret":"1","data":{"totle":5.68,"list":[{"meter_code":8846.42,"energy_used":5.68,"data_date":"20261018"}]}}`
  - `data.totle`（接口拼写）或 `list[].energy_used` 为当天用电量（度）；当天尚未出数时 `data` 为 null。
  - 30 天视图：客户端按 **7 天分块** 调用范围查询并合并 `data.list[]`；
    某一块返回空时对这一块逐日查询兜底。这样 7 天/30 天切换都有真实数据。
- 学号不需要用户输入：登录后的 `index.jsp` HTML 里包含 `userid=`，客户端正则解析。
- 实现：`data/remote/electricity/XgwxElectricityClient.kt`、`data/repository/XgwxElectricityRepository.kt`；
  登录入口 `CampusEndpoints.ELECTRICITY_LOGIN`（`systemId=ecut_electricity`）。
- 注意：登录页 HTML 本身包含 `authserver/login` 字样，不能据此判定会话失效；
  接口返回非 JSON 才判定过期。宿舍缴费过期时会 302 到 CAS，客户端允许跟随重定向，用本地 CASTGC 自动换票。

实测结果（2026-10-24）：

- 电表余额 ¥65.51；
- 最近 7 天：5.68 / 7.92 / 3.98 / 3.80 / 3.49 / 3.47 / 当天未出数；
- App 电费页与首页卡片均正确显示。

## 校园卡（ehall 一卡通）

2026-10-24 从 ehall 页面抓到并原生接入。

- 页面：`https://ehall.ecut.edu.cn/psfw/sys/psxykapp/*default/index.do#/xykxx`
  登录统一认证后，`CampusWebPageScreen(saveSessionHost="ehall.ecut.edu.cn")` 会保存 ehall 会话 Cookie。
- 卡信息 / 月账单：
  `POST /psfw/sys/psxykapp/modules/WisCardViewController/getWisCardInformation.do`
  body：`data={"XFNY":"2026-10"}`
  返回：`data.CSXX.CSDM`（如 `xkp`）、`CSXX.JKFS`（`WEBSERVICE`）、
  `MONTHBILL.XFJE/CZJE/XFZS/CZZS`（本月消费金额/充值金额/笔数）。
- 余额：
  `POST /psfw/sys/psxykapp/modules/CampusCardActionController/queryBaseInfo.do`
  body：`data={"CSDM":"xkp","JKFS":"WEBSERVICE"}`
  返回：`data.BALANCE`、`CARD_STATE`、`CARD_ACCNUM`。
- 流水：
  `POST /psfw/sys/psxykapp/modules/WisCardViewController/getWisCardBill.do`
  body：`data={"pagenum":1,"pagesize":30,"querySetting":"[]"}`
  返回：`data.BILL_ARRAY[]`（`XFFS` 1=充值/2=消费、`SJ`、`ZDMC`、`JE`、`MC`、`LXDM`），
  `data.COUNT.COUNT` 为总笔数。
  - 支付宝随充随付会产生等额的一正一负记录，App 按 `XFFS` 显示 +/−。
- 实现：`CampusCardClient`、`CampusCardViewModel`、`CampusCardScreen`；
  首次需在“校园卡 → 官方页”登录一次，之后原生余额/流水可读。

## 网上办事大厅（ehall）

- 入口：`https://ehall.ecut.edu.cn/new/index.html`（根路径 301 到 `/new/index.html`）。
- App 内以 **PC 版 UA** 打开（可切手机 UA），并注入 `authserver.ecut.edu.cn` 的 CASTGC 以尝试自动 SSO；
  校外访问前建议先在零信任门户认证。
- 用途：校园卡、通知公告、等级考试报名等尚未原生化服务的统一官方入口；
  App 不读取/提交表单，操作由用户在官方页面完成。

## 图书馆空间预约（libspace）

2026-10-24 从 H5 bundle 中提取并在真机验证。

- H5：`https://libspace.ecut.edu.cn/h5/index.html#/seat`（Vite SPA，前端路由 `#/seat`）。
- 空位接口（**免登录**）：
  `POST https://libspace.ecut.edu.cn/v4/space/pcTopFor`
  body：`{"day":"2026-10-24"}`，或带时段
  `{"day":"2026-10-24","start_time":"12:30","end_time":"22:00"}`
  返回 `data.date[]`（可选日期）与 `data.list[]`：
  - 校区：`id`、`name`（主校区 / 枫林校区 / 抚州校区）、`total_num`、`free_num`；
  - `children[]`：楼层/区域，同样带 `total_num`、`free_num`、`sort`。
- 实测 2026-10-24：主校区 1342 个座位，全天 985 空闲、当前时段 983 空闲；枫林、抚州可切换。
- 其他已确认接口：
  - `/v4/space/index`（筛选元数据：校区/楼层/座位类别/日期）
  - `/v4/Space/seat`（座位图）
  - `/v4/space/confirm`（预约提交）、`/v4/space/signin` 等（需要登录，**未接入**）
- App 实现：`data/remote/library/LibrarySeatClient.kt`、`ui/screens/LibrarySeatsScreen.kt`；
  只读查询，预约/签到/取消统一打开官方 H5，由用户在学校统一身份认证页面登录，App 不保存图书馆密码。
- 官方 H5 的登录是统一身份认证（学号 + 密码 + 验证码），登录态保存在 WebView localStorage；
  `CampusWebPageScreen` 对图书馆页面关闭了“教务登录已过期”提示，并使用移动端 UA。

## 零信任 WebVPN 代理（校外原生同步）

2026-10-24 打通。**不需要系统 VPN 隧道**，认证后通过门户的 WebVPN 代理读教务。

- 门户：`https://atrust1.ecut.edu.cn` → `/portal/#/app_center`。
- **WebView 白屏根因**：门户用 Tailwind 的 `h-screen`（`height:100vh`），而内嵌 WebView 里
  `window.innerHeight=600` 但 `100vh` 计算为 **0px**，整页高度塌成 0。
  在 `onPageFinished` 注入像素高度（`window.innerHeight`）覆盖
  `html/body/#app/.h-screen/.for-main-window/.main-page-wrapper` 后正常渲染
  （`PORTAL_HEIGHT_FIX_SCRIPT`，持续重试 12 次防路由重渲染）。
- 门户里的“新教务管理系统”把内网 IP 映射成域名：
  `https://172-20-130-13.atrust.ecut.edu.cn/jwglxt/...`，路径与内网完全一致。
- **代理会话**：打开门户 → 新教务管理系统 → 任一功能（如学生课表查询）后，
  代理域会设置 Cookie。`CampusWebPageScreen(saveProxySession=true)` 在
  `onPageFinished` 时用 `CookieManager.getCookie()` 读取，并以
  `systemId=ecut_jw_proxy` 写入 SQLCipher。
- **原生同步**：`JwAdapter` 的 baseUrl 改为 `suspend` provider：
  - 校内（OnCampus）→ `http://172.20.130.13`；
  - 有代理会话（且非 OnCampus）→ `https://172-20-130-13.atrust.ecut.edu.cn`；
  - 明确校外且无代理会话 → 抛 `ProxySessionRequiredException`，提示先打开门户。
  CookieJar 按代理域名注入刚保存的 Cookie，课表/成绩/考试/空闲教室全部可用。
- 实测（手机 5G、完全校外）：打开门户进入新教务一次后，App 内
  **课表同步 20 个课程块、成绩 53 门、空闲教室 114 间**，均走 WebVPN 代理。
- 门户右上角支持“PC / 手机”UA 切换（用户实测 PC UA + 网上办事大厅也可用）；
  另保留“浏览器”跳系统浏览器。
- 局限性：代理会话过期后需重新进一次门户；完整内网（非 WebVPN 发布的系统）仍需官方 aTrust 客户端。

## 校园网检测与零信任入口

- 检测方式：`CampusNetworkStatus` 用普通 OkHttp HEAD 探测内网地址
  `http://172.20.130.13/jwglxt/` 与 `http://172.20.134.7:8080/`。
  收到任意 HTTP 响应（包括 403）即判定在校园网；两次尝试间隔 700ms。
- 不读取 Wi-Fi SSID，因此不需要定位权限。
- 启动或回到前台时检测；首次判定为校外会自动跳到 `CampusEndpoints.ATRUST_LOGIN`
  （`authserver-ecut-edu-cn-s.atrust.ecut.edu.cn` 零信任 CAS 页），顶部同时保留警告条可手动重试。
- 注意：该网页登录只完成零信任认证，不会在系统层建立 VPN 隧道；课表/成绩接口仍要求校园网可达。

## 统一身份认证（CAS / aTrust）

### 一次登录，全站共用（2026-10-25 起）

学校所有需要登录的系统共用 `authserver.ecut.edu.cn` 的 **CASTGC**，
它们只是 CAS 的不同 service：

| 系统 | service 入口 |
| --- | --- |
| 教务（课表/成绩/考试/教室/公告） | `172.20.130.13/sso/jziotlogin?url=...` |
| 宿舍电费 | `xgwx.ecut.edu.cn/app/wechat/index.jsp?...` |
| 网上办事大厅 / 一卡通 | `ehall.ecut.edu.cn/psfw/sys/psxykapp/...` |

实现：

- 任意登录页（JW / 电费 / ehall / 零信任代理）出现 `CASTGC` 都会写入
  `auth_credentials` 的 `ecut_cas_master`。
- **CAS 主域必须整套保存**：`CASTGC` + `route` + `JSESSIONID`。CAS 是集群，
  `route` 是粘性会话路由；只存 `CASTGC` 时下一次换票可能落到没有该 TGT 的节点，
  表现为“刚登录就提示过期”。从零信任代理域镜像 `CASTGC` 时只取 ticket，
  并与主域已有 Cookie 合并，避免覆盖掉 `route`。
- `CasSsoManager.ensureServiceSession(serviceUrl)`：
  `GET /authserver/login?service=<encoded serviceUrl>` → CAS 302 回 service 并带 ticket
  → service 下发自己的 `JSESSIONID`，由 `CampusCookieJar` 自动落库。
  只有最终响应为 2xx 且已离开 CAS 域才算换票成功（正方 `jziotlogin` 校验 ticket 失败会返回 500）。
- 各原生客户端在“会话过期”时自动换票并重试一次：
  `JwAdapter`（仅校内直连）、`XgwxElectricityClient`、`CampusCardClient`。
- 校内登录（`Routes.LOGIN`）直接打开教务 SSO 页，由 WebView 执行完整重定向，
  一次同时拿到 `CASTGC` 和教务 `JSESSIONID` 并保存，不再依赖 OkHttp 静默换票。
- 校外登录走 CAS（落到 ehall）；教务同步必须在零信任门户打开一次“新教务管理系统”。
  只有代理域名 `172-20-130-13.atrust.ecut.edu.cn` 的会话才算 `ecut_jw_proxy`，
  门户主页 `atrust1.ecut.edu.cn` 的 Cookie 不算。
- 所有“去登录”统一进入 `Routes.LOGIN`，
  登录成功后落到 ehall 一卡通页并自动保存 CASTGC，不再按模块分别登录。
- 零信任代理域名下拿到的 `CASTGC` 会镜像到 CAS 主域，先登零信任也能解锁电费/校园卡。

入口（用户提供）：

```
https://authserver-ecut-edu-cn-s.atrust.ecut.edu.cn/authserver/login
  ?service=https%3A%2F%2Fatrust1.ecut.edu.cn%3A443%2Fpassport%2Fv1%2Fauth%2Fcas
  %3FsfDomain%3Dcas.ecut.edu.cn
```

- 实测 `https://authserver.ecut.edu.cn`（CAS 本体）与 aTrust 代理域名均可访问；`jw.ecut.edu.cn` 目前返回纪检网站点，不是教务系统。
- 学校网上办事大厅：`https://ehall.ecut.edu.cn`（统一身份认证后进入“教务管理系统”）。
- 登录在 WebView 内完成，App 只读取 `CookieManager` 中当前页面 / CAS 域名 / service 域名的 Cookie。
- 有效凭据判定：Cookie 名包含 `CASTGC`、`TGC`、`JSESSIONID` 或 `MOD_AUTH_CAS` 之一。
- 保存位置：SQLCipher 的 `auth_credentials`（整串）与 `cookies`（按域名拆分）。
- 后续请求由 `CampusCookieJar` 自动注入；`casHttpClient` 遇 401 或回跳登录页抛 `SessionExpiredException`。
- **零信任登录白屏修复（2026-10-24）**：CAS 回调成功后落到
  `atrust1.ecut.edu.cn/portal/shortcut.html`，该页是给 aTrust 客户端内置 WebView 用的 SPA，
  普通 WebView 会一直停在 `Loading...`/白屏。现在 `ATRUST_LOGIN.autoCompleteHosts` 包含
  `atrust1.ecut.edu.cn`，一旦落到该域就自动保存 Cookie 并返回；同时
  `preloadStoredCookies=true` 会先注入加密库里的 CASTGC，已有会话时可直接 SSO。
  WebView 还补了 `onReceivedError` / `onReceivedHttpError` / `onReceivedSslError` 的中文提示，不再纯白。

## 电费

- 目标：找到电控系统的免鉴权 / 轻鉴权 H5 查询接口（传入宿舍楼栋 + 房间号）。
- 当前：Room 表 `electricity_records` 与每小时 WorkManager 采样框架已就绪；具体 URL 与参数需在校内网抓包确认。
- 约束：只读，不实现充值。

## 闪动校园（阳光跑）

- APK 296 MB，主体为 Flutter（`libapp.so`）， Android 外壳内嵌大量 SDK。
- 静态提取到的业务域名与路径：
  - 生产 API：`https://api.huachenjie.com/`
  - 只读记录：`/run-front/indoor/sportsPage`、`/run-front/indoor/sportCount`
  - 其他：`/run-front/indoor/indoorRanking`、`/run-front/ai/pageRecordList`
- 请求使用 `Authorization` 头，登录态由 App 的手机号登录 / 校园绑定流程下发，CAS 无法替代。
- 策略：只读展示跑步记录，不实现任何自动打卡 / 上传；自动打卡交给用户自行选择的外部模块。
- 当前：接口路径已确认，但缺少可用的授权链路，页面保持占位。

## 慧生活798（2026-10-24 结论）

- 包内域名：`ilife798.com`、`aishuttler.com`（统计/SDK）、`api.shanghailingye.cn`；业务入口大量为 H5（`i.ilife798.com`）。
- APK 使用 InnoSecure 加固 + 证书固定；LSPatch 改包启动即自杀，原生接口无法直接抓。
- H5 是 uni-app：`pages/index/index` 只是使用说明；`pages/index/scan` 显示
  “支付宝扫码启动异常：支付宝常用小程序里删除‘慧生活798’后重新扫码使用”。
  说明业务完全依赖支付宝小程序（设备二维码启动），H5 不是独立 Web 应用。
- H5 内置 `ALIPAY_APPID=2019061165478963`（app-to-app auth）；用该 id 拉起支付宝只到首页，不是小程序。
- **真正的支付宝小程序**：appId `2019061465519660`，首页 `pages/index/index`。
  H5 的 `pages/app/index` 是下载页，`gotoAliPay()` 使用
  `alipayqr://platformapi/startapp?appId=2019061465519660&page=pages/index/index`。
- 设备二维码入口形如：
  `https://cloud.hnkzy.com/h5/?atype=1&id=<设备/商户id>#/pages/app/index`
  （`id` 可能是设备 / 商户标识；小程序未公开 query 协议，无法确认能否直接定位到该设备）。
- 签名规范（路线文档）：`X-Ashe = MD5( MD5(servername.methodname).lowercase() + timestamp + randomStr )`；`X-Nami` 未登录时为 UUID 截取 17 位。
- 已实现：`network/signing/AsheSigner.kt` 与该签名拦截器，待填入真实 `servername` / `methodname` 与设备接口路径。
- App 已提供“在支付宝打开慧生活798”入口（`alipays://platformapi/startapp?appId=2019061465519660&page=pages/index/index`）；
  设备状态仍不读取，避免伪造数据。
- 2026-10-24 真机验证：深链可打开“慧生活798”小程序首页（我的钱包 / 附近设备 / 扫一扫）。
  尝试 mitmproxy 抓小程序流量：只看到 Alipay 自己的 `mgw.alipay.com`、高德定位等域名，且全部
  `TLS-FAIL client rejected mitmproxy certificate`（支付宝网关证书固定），没有出现慧生活业务域名。
  “附近设备”依赖小程序定位，实测显示黔南民族师范学院（定位未更新到南昌），需扫机器二维码定位到本校。
- 把二维码里的 `id` / `atype` 作为 `query=id%3D...%26atype%3D1` 传给小程序首页，页面没有变化，
  说明首页不消费这两个参数；具体设备页路径未知。
- **结论**：小程序入口可用，但设备状态接口经支付宝网关且证书固定，无法抓取；原生 App 加固 + 证书固定。
  第一版只做官方小程序入口，不伪造设备数据。

## U净洗衣（2026-10-24 抓包完成）

目标：只读查询附近洗衣房、洗衣机空闲数量与洗护价格；不做下单 / 支付。

### 登录（手机号 + 短信验证码）

- 验证码签名：`nonce` = 32 位十六进制 UUID，`timestamp` = Unix 秒，
  `signature` = Base64(HMAC-SHA256(plaintext = nonce + timestamp, key))；
  key 为 H5 内置常量（已脱敏；完整值仅本地保留）。
- 发送验证码：
  `GET https://phoenix.ujing.online:443/api/v1/wechat/captcha/create?mobile=<手机号>&type=1&nonce=<nonce>&timestamp=<ts>&signature=<urlencoded>`
  返回 `{"code":0,"message":"success"}`。
- 登录：
  `POST https://phoenix.ujing.online:443/api/v1/login`
  body `{"mobile":"...","captcha":"..."}`
  返回 `data.token`（JWT，实测有效期约 93 天）、`data.id`、`data.lbUserId`、`data.mobile`、
  `data.college`、`data.serviceSubjectId`、`data.category.washer`、`data.userId`。
- 请求头：`Authorization: Bearer <token>`、`x-app-code: BO`、`x-app-version: 1.1.4`、`Accept: application/json`。
- 注意：H5 默认的 `/wechat/third-party/thirdLoginV2` 需要第三方临时 token；直连
  `/api/v1/login` 只需手机号 + 验证码，已实测可用。

### 附近洗衣房

`GET /api/v1/stores/near?lat=<纬度>&lont=<经度>&skip=0&limit=10`（参数名是 `lont`，不是 `lng`）

返回 `data.storeList[]`：

- `id`、`name`（如 `11-3`）、`coordinates:[lng,lat]`、`distance`（km）、`deviceNum`
- `serviceSubject.id / name / address`
- `storeInfo[]`：`access`（该类设备数量）、`category`（1=洗衣，2=烘干）、`name`
- `reservationEnabled`

### 单店设备数量 / 空闲数

`GET /api/v1/wechat/washer/store/info?storeId=<id>`

返回 `data.deviceTags[]`：

- `deviceTypeId`（1=波轮机，2=滚筒机）、`deviceTypeName`、`totalNum`、`idleNum`
- 另有 `skuConfig`、`isWashTemperatureEnable`、`isOpenDoubleExposure`

### 洗护程序与价格

`GET /api/v1/wechat/washer/devices/program/info?storeId=<id>&deviceTypeId=<1|2>`

返回 `data.deviceWashModel[]`：

- `workModelId`、`workModelName`（强力洗 / 普通洗 / 单脱水）
- `basePrice`（分）、`time`（分钟）、`tags`、`description`
- `additionParams`（水温选项）、`additionDevices`（洗衣液 / 除菌液加购）
- 未选具体机器时 `deviceId` / `deviceNo` 为空

### 等待时间

`GET /api/v1/wechat/washer/store/waitTime?storeId=<id>&deviceTypeId=<1|2>` → `data.waitTime`（分钟）。

### 订单（只读）

- `GET /api/v1/orders/running` → `data[]`（运行中订单）
- `GET /api/v1/orders/history?skip=0&limit=10` → `data[]`：`orderId`、`deviceTypeId`、
  `deviceTypeName`、`deviceNo`、`storeName`、`createAt`、`status`（`"50"`=已完成）、
  `statusRemark`、`payFlag`、`remainTime`
- `GET /api/v1/orders/{orderId}/detail`

### 最近使用（解决常用楼栋不在附近列表）

`stores/near` 每个坐标只返回最近的 10 家；用户常去的 16栋-1 可能不在里面。用订单历史补：

- `GET /orders/history?skip=0&limit=10` → `orderId`
- `GET /orders/{orderId}/detail` → `storeId` / `storeName`
- 再用 `storeId` 调 `storeInfo` / `program/info` / `waitTime`

实测 16栋-1 的 `storeId` 为 `64253422e3d2ab27db63d950`，serviceSubject 是广兰校区。
App 顶部“最近使用”就是从这里来的，不依赖定位。

### 校区坐标

- 广兰校区：`28.718795, 115.825926`（默认；`stores/near` 返回 1栋 / 2栋 等）
- 青岚校区：`28.7202, 115.8203`（11栋 / 16栋）
- 抚州校区：`27.94705, 116.41202`（兜底）

`stores/near` 的 `serviceSubject.name` 会显示“广兰校区 / 青岚校区”，App 顶部可切换。

### 支付宝小程序

- 小程序 appId：`2021002174638299`
- 首页：`pages/home/home`
- 订单页：`pages/order-list/order-list?orderType=2`
- 深链：`alipays://platformapi/startapp?appId=2021002174638299&page=pages/home/home`
- 官方下单链路是“小程序里扫机器二维码 → 选方案 → 支付”，App 只负责跳转，不创建订单。
  小程序未公开 query 协议，无法可靠预填 storeId / deviceId / workModelId。

### 备注

- 所有响应为 UTF-8 JSON。
- `devices/near` 是饮水 / 淋浴的充电设备接口，洗衣机不走它；洗衣机按 `storeId` 查。
- H5 入口：`https://wx.zhinengxiyifang.cn/static/washer-h5/ujing/latest/index.html#/login`。
- APK 加固（`libzeus` / `libentryexpro`）会检测重打包并自杀，LSPatch 不可用；H5 / 直连是可行路线。

## 完美校园

- APK 经过加固，dex 可读字符串很少，无法直接提取接口。
- 参考开源项目 `ReaJason/17wanxiaoCheckin` 的协议思路；注意固定 `device_id` 会导致官方 App 被踢下线，第一版不接入登录，仅在需要时以 WebView 兜底。

## 今日校园

- 聚合类超级 App，接口多且强绑定学校租户；第一版不接入原生接口。
- 如需使用，按 WebView 兜底处理并仅保存 Cookie。

## 衣可林洗衣

- 无公开逆向资料，依托微信公众号；按 WebView 兜底，只拉取只读订单列表。

## 待办

- [x] 校内网抓包确认教务系统域名与课表 / 成绩接口（正方 jwglxt，已实现）
- [x] 电费查询接口与宿舍绑定参数（今日校园 → xgwx H5，已实现）
- [x] 考试安排真机验证（2026-2027-1 教务确实无记录）
- [x] 空闲教室原生查询（cdjy 页面参数已抓取并验证）
- [x] 学生通知公告列表 / 正文（正方移动端接口已抓取并验证）
- [ ] 慧生活798 的 servername / methodname 与设备列表
- [x] U净登录 + 门店 / 空闲数 / 价格 / 订单只读接口（2026-10-24 真机验证）
- [ ] 闪动校园只读记录接口
