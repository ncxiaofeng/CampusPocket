## 冻小梨 0.5.2 · 真实背景采样底栏 + 设备定位天气

### 底部导航
- 绿色加号改回参考图中的位置：**位于底栏内部居中**，不再与底栏完全分离。
- 底栏改为完整圆角悬浮玻璃片，去掉此前较高的独立 FAB 和缺口，视觉上更像一块浮在界面上的液态玻璃。
- **真实背景采样**：页面内容通过 `GraphicsLayer.record` 记录，底栏用 `drawLayer` 按坐标绘制同一帧内容，再用 `RenderEffect` 高斯模糊；滚动时底栏里的模糊内容会同步变化。
- 玻璃上层保留低透明度白/浅蓝/浅紫着色、顶部高光、白/青/紫描边和柔和阴影；下半部逐渐加重白色，保证 Tab 文字压在真实内容上仍可读。
- 底栏仍在 `Scaffold.bottomBar` 固定，内容可以滑到玻璃后面；页面列表额外预留底部空间，滚到底时最后一项不会被永久遮住。
- 4 个 Tab：首页、课表、校园卡、更多；选中绿色，未选中浅灰；中间加号打开快捷服务面板。
- 安全区：`navigationBarsPadding + 10dp`，不贴屏幕底边。

### 天气定位
- 新增 `DeviceLocationProvider`，使用 Android 原生 `LocationManager`（无 Google Play Services / 高德 SDK）请求一次设备位置。
- 首次进入首页会请求 `ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION`；授权后天气改用设备实际坐标，并显示「当前位置」。
- 拒绝授权或定位失败时自动回退到广兰校区坐标，不影响天气功能。
- 天气卡片和隐私说明已注明：只发送设备定位或校区坐标，不发送学号等个人信息。

### 说明
- Compose 真实背景采样依赖 `GraphicsLayer + RenderEffect`，Android 12（API 31）以下不支持 RenderEffect，底栏会退化为半透明玻璃（不崩溃）。
- 真实采样会在每帧记录页面内容，低端设备上可能增加一点 GPU 开销；当前 Redmi K40 真机滚动正常。
