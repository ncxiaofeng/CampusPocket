# ADB 免 root 抓包记录（2026-10-24）

设备：一台 Android 测试机（具体型号、系统版本与 root 状态已脱敏）。

## 方式

- `adb reverse tcp:8899 tcp:8899`
- `adb shell settings put global http_proxy 127.0.0.1:8899`
- PC 上 mitmproxy 12.3 监听 127.0.0.1:8899
- 手机安装 mitmproxy CA 为用户证书（已装）
- 日志：`_mitm/capture_sanitized.jsonl`（token / cookie 已脱敏）

代理已经在抓完后关闭（`http_proxy=:0`），手机网络恢复正常。

## 结果

### 闪动校园（com.huachenjie.shandong_school）

**接受用户 CA，抓包成功。**

登录：
- `POST /run-front/captcha/get`
- `POST /run-front/captcha/check`
- `POST /run-front/auth/loginPassword`

跑步数据：
- `POST /run-front/attend/semesterSelector`（学期列表，当前 `semesterCode=13`）
- `POST /run-front/run/pageSunRunRecord`
  body：`runPlanCode`、`semesterCode`、`pageSize`、`pageNum`、`deviceId`、`timestamp` 等
  response：`data.list[]`，字段：
  `runRecordCode, sportType, totalStep, startTime, endTime, pace, calorie, frequency, validDistance, duration, distance, recordImgUrl, sunRunRecordStatus, status, runActivityFlag, reward, morningFlag, archiveState, warn, runPlanName, semesterCode, recheckStatus, runScene`
- `POST /run-front/run/querySunRunAbstractInfoV2`（目标/已完成里程/次数、学校规则）
- 其他：`/run-front/api/tasks/runTask`、`/run-front/activity/getCurrent`、`/run-front/run/queryUnFinishRun`

请求头（关键）：
```
app: run-front
v: pageSunRunRecord
pv: 2
api: run
e: 0
Authorization: <token>
satoken: <token>
sign: <每请求签名>
```
`sign` 由 App 的安全库生成，单独重放需要逆向签名算法；因此只读 Adapter 还不能直接
用这几个接口，除非补上签名实现，或改为抓取用户手动登录后的 token。

### U净（com.midea.vm.washer）

- `https://phoenix.ujing.online:443` → **TLS 失败：App 拒绝用户 CA**
- `https://d.ujing.online:3443`、`https://check.ujing.online` 同样失败
- 结论：证书固定 / 不信任用户证书，免 root 代理抓不到 API。

### 慧生活798（com.cloudora.android）

- `https://i.ilife798.com:443` → **TLS 失败：App 拒绝用户 CA**
- 免 root 代理抓不到 API。

### 完美校园（com.newcapec.mobile.ncp）

- `https://app.17wanxiao.com` → TLS 失败（主 API）
- 明文 HTTP 接口可抓：
  - `POST http://vmcard.17wanxiao.com/tsm_iface/app_service.action`
    - `command=TSM01010`：读取虚拟校园卡成员信息（姓名、卡号等）
    - `command=getNewCapecKey`：取卡密钥
  - `POST http://120.26.154.238/campus/cam_iface46/gainDomain.action` → 504
- 未发现电费接口；需要用户确认电费入口是哪个 App / H5。

### 今日校园（com.wisedu.cpdaily）→ 宿舍缴费（电费）

**核心 API 拒绝用户证书，但“宿舍缴费”是 ehall / xgwx 的 H5，接受用户证书，抓包成功。**

路径：
1. 今日校园首页 → 宿舍缴费；
2. 学生宿舍缴费 → CAS SSO → `https://xgwx.ecut.edu.cn/app/wechat/index.jsp?...`；
3. 余额查询 / 用量查询 的接口：

- `POST https://xgwx.ecut.edu.cn/app/app/api/user/searchBindHouseListForMoney`
  body `userid=<学号>`；返回宿舍、电表、`meter_balance`。
- `POST https://xgwx.ecut.edu.cn/app/app/api/data/dosage`
  body `houseId&meterId&startTime&endTime`；返回 `data.totle` 和 `list[].energy_used`。

详见 `docs/INTEGRATION.md`；App 已接入并验证（余额 65.51 元、7 天用量曲线）。

### 教务空闲教室：App 内 WebView + CDP（2026-10-24，已成功）

原生 App 的接口抓不到时，教务这类**网页系统**可以直接用 App 自己的 WebView：

1. 调试包在 `CampusWebPageScreen` 里调用 `WebView.setWebContentsDebuggingEnabled(true)`；
2. `adb shell cat /proc/net/unix | grep webview_devtools_remote` 拿 socket 名；
3. `adb forward tcp:9223 localabstract:webview_devtools_remote_<pid>`；
4. `curl http://127.0.0.1:9223/json` 拿 `webSocketDebuggerUrl`；
5. Python websocket 连接时 **`suppress_origin=True`**（否则 403），用 `Runtime.evaluate`：
   - `jQuery('#tabGrid').jqGrid('getGridParam','url'/'postData')`
   - 挂 `XMLHttpRequest.prototype.open/send` 钩子后触发页面查询，读回真实 body 与响应。

脚本：`_analysis/wv_cdp.py`。注意：正方页面会覆盖 `Array.prototype.filter` 等，注入的 JS 尽量用 `for` 循环。

这样拿到了空闲教室的真实请求：
`POST /jwglxt/cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155`
（`xqh_id/xnm/xqm/lh/zcd/xqj/jcd/queryModel.*`，详见 `docs/INTEGRATION.md`），已改为原生查询。

## 下一步选项

1. **App Cloner / 重打包**：测试过“信任所有证书”的克隆与 LSPatch，均被目标 App 的
   加固 / 签名校验拦截（改包后启动即退出）。
2. **Root**：测试机未 root；如果以后 root，可把 mitmproxy CA 放进系统证书，
   并用 LSPosed / Frida 绕过证书固定（有变砖 / integrity 风险）。
3. **LSPatch / 重打包**：免 root 修改目标 APK 信任用户证书并尝试去 pinning；
   签名改变，部分 App 会拒绝。
4. **只实现已抓到的**：闪动接口需要 sign，暂不能直接接；完美校园只有校园卡 HTTP 接口。
   电费仍需确认入口。

## 清理

- 移除手机用户 CA：设置 → 安全 → 加密与凭据 → 用户凭据 → 删除 mitmproxy。
- 清除代理（已做）：`adb shell settings put global http_proxy :0`
- 原始未脱敏日志已删除；保留 `capture_sanitized.jsonl`。
