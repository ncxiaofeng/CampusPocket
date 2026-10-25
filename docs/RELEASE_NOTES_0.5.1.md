## 冻小梨 0.5.1 · 液态玻璃悬浮底栏

本次更新把底部导航重写为独立悬浮的液态玻璃组件。

### 底部导航
- 4 个 Tab：首页、课表、校园卡、更多；中间为绿色圆形加号 FAB。
- 底栏与页面内容完全分离：GlassBar 独立悬浮在底部，四周留白，带柔和彩色外部阴影。
- 底栏顶部中间开半圆缺口，FAB 悬浮在缺口上方，与缺口保持 8–10dp 间隙，不嵌入凹槽。
- 底栏使用低透明度玻璃渐变、原生 `Modifier.blur` 色斑、半透白描边、顶部镜面高光和青紫色散阴影，模拟液态玻璃；未引入第三方 UI 库。
- 底栏下方使用 `navigationBarsPadding` + 10dp 间距，适配 Android 手势条 / iOS 安全区。
- Tab 选中态使用绿色实心图标、绿色文字和淡绿色光晕；未选中为浅灰色。
- 点击加号继续打开现有快捷操作面板（数据同步 / 电费 / 校园卡 / 空闲教室 / 图书馆 / U净洗衣）。

### 组件位置
- `app/src/main/java/com/ecut/campuspocket/ui/components/LiquidGlassBottomBar.kt`
- 可直接复用到其它 Compose 页面：传入 `tabs`、`selectedIndex`、`onTabSelected`、`onCenterClick` 即可。

### 说明
- Compose 没有 Flutter 的 `BackdropFilter`，组件用原生 blur + 多层半透明渐变/高光实现等效的液态玻璃视觉；若后续需要真正采样背景内容做模糊，需要引入 Haze 或自行实现 PixelCopy/AGSL 方案。
