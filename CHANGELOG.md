# 变更记录

本项目在 2026-10-16 至 10-25 这九天内开发，全程未使用版本控制。
因此下文将功能演进与开发历程分开记录，后者按时间顺序排列。

---

## 0.1.0 — 2026-10-25

首个公开版本。

### 虚拟屏模式

- 通过 Shizuku 用户服务创建独立虚拟屏，用户自己的屏幕完全不受影响
- `ImageReader` 持有 Surface 全分辨率截图，不需要 fork 截图进程
- `InputEvent.setDisplayId` + `injectInputEvent` 直注输入，路由到目标屏
- 点击、长按、滑动、长按拖拽四种手势，事件构造与 `input` 命令保持一致
- 离线 OCR 读取界面，返回文本与精确中心坐标
- 悬浮预览窗：可拖动、双指缩放、折叠成圆球，折叠后与急停球同尺寸
- 竖屏启动，跟随前台应用自动旋转，切换时重建 Surface
- 三层清理机制：启动扫描、Binder 死亡通知、周期性 `isBinderAlive` 轮询
- 自带输入法，通过 `InputConnection.commitText` 写入，不干扰用户的输入法
- `overlay_display_devices` 旧方案完全移除，不再写入任何系统设置

### 普通模式

- 基于无障碍节点树读取界面，坐标精确无需 OCR
- 支持文字输入与按键事件
- 状态卡片显示当前进度

### 模式隔离

两种模式使用完全不同的工具集与提示词，模型不需要知道存在另一种模式。
工作模式在首次启动时选择，写入标记文件，之后可直接进入主界面。

### 界面

- Material 3 重构，六套淡色主题，Android 12 以上支持莫奈取色
- 引导页按模式隐藏无关权限项
- 虚拟屏管理面板：列出本应用创建的虚拟屏与孤儿进程，支持一键清理
- 清理范围严格限定为 `com.mcpmate.bridge:vd`，不触碰第三方虚拟屏

### 已知限制

- 虚拟屏没有窗口焦点，系统输入法与按键事件不可用
- 自带输入法依赖目标应用是否接受外部输入连接，部分应用会拒绝
- OCR 依赖 Google ML Kit，其许可为非自由协议

---

## 开发历程

以下按实际顺序记录，包含失败与回退。

### 第一阶段：最小可用

最初的版本只能完成最基本的事情：一个 HTTP 服务器加一个无障碍服务，
能读取节点树、点击坐标、按键。没有引导，没有状态显示，没有急停手段。

当时的配置项直接写在代码里，端口固定，没有任何安全限制。

### 第二阶段：引导页与系统崩溃

加入引导页后发现了第一个严重问题：**滑动和拖拽状态栏会让 SystemUI 崩溃**，
表现为黑屏或锁屏，必须长按电源键恢复。

原因是往状态栏区域注入滑动手势，系统无法承受。修改方式是加入
`systemBarAt(x, y, scr, allowClick)` 护栏：点击放行，滑动与拖拽拦截。

同一时期还发现 400ms 的滑动会被 MIUI 判定为 fling 惯性甩动，
实际不生效。把默认滑动时长改成 800ms 后才稳定。

### 第三阶段：微信卡死与兼容模式

接入真实使用场景后发现：**微信等应用检测到无障碍服务后不返回任何界面内容**。
节点树是空的，所有读取都失败。

这不是权限问题，是应用主动防御。解决办法是增加第二个无障碍服务
（`SelectToSpeakService`，命名为「兼容模式」），它自身不读取任何内容，
只用来让目标应用认为无障碍已开启，从而恢复节点返回。

这个发现后来成了引导页的必备项。

### 第四阶段：构建环境迁移

最初使用 AIDE 开发。在引入 AndroidX 的 AAR 依赖时，AIDE 自带的 aapt2
无法处理现代 AAR，构建直接崩溃，产物是同名但损坏的 ZIP。

换成 Android Code Studio 后问题消失。此后构建基线固定为
Gradle 8.7 + AGP 8.6.1 + Java 17。

### 第五阶段：纯 OCR

节点树无法覆盖的场景越来越多：游戏、画布、视频画面、以及各类主动防御的应用。
于是接入 Google ML Kit 的中文离线识别，运行时不需要网络。

OCR 返回每行文本的边界框，坐标来自真实像素，误差在 ±5px。
为了配合识别，加入 1.6 倍放大预处理和短文本过滤。

同期还试验过基于投影的文字块检测（locate），想用几何切分替代 OCR。
在密集列表上切分结果严重粘连，多行文字被并成一块，最终放弃该功能。

### 第六阶段：主题与界面重构

界面从原始控件重构为 Material 3 风格，配六套淡色主题。
Android 12 及以上支持从壁纸取色。

重构过程中遇到两个问题：

- `Theme.Material3.*` 配 `AppCompatActivity` 会崩溃，改为继承 `android.app.Activity`
- 卡片圆角用 `setCornerRadii` 单独设置，否则会出现「上面变尖」的渲染异常

### 第七阶段：虚拟屏，三次尝试

目标很明确：让 AI 在后台操作，用户同时可以正常使用手机。

**第一次：`app_process` 直接调用 `createVirtualDisplay`**

失败。uid 0 报 `packageName must match the calling uid`；用 `su 1000` 能改 uid
但 SELinux 域仍是 `magisk`；MIUI 的 `ThemeCompatibility` 读取
`/data/system/theme_config/theme_compatibility.xml` 报 ENOENT；
最终 `app_process` 直接 SIGABRT。

**第二次：`settings put global overlay_display_devices`**

能够建屏、启动应用、截图、点击。但存在三个无法绕过的缺陷：

- 显示 ID 每次重建都变化（观察到 2、12、17、18、19、22），无法硬编码
- 截图必须 fork `screencap` 进程，ID 传错就得到空文件
- 系统会把虚拟屏缩放后叠加显示在物理屏上，用户始终能看到一个缩略窗

`uiautomator dump --display` 在此机型上不解析该参数，节点树方案彻底排除。

**第三次：Shizuku 用户服务**

成功。通过 Shizuku 在 shell 身份下创建虚拟屏，自己持有 `ImageReader` 的 Surface。

关键障碍与解法：

- 进程以 root 启动时 `getPackagesForUid(0)` 为空，建屏被拒。
  解法是先 `setuid(2000)` 降权到 shell，再用 `createPackageContext("com.android.shell")`
  取得包名与 uid 匹配的 Context
- 官方 Shizuku 的进程域是 `u:r:shell:s0`，`injectInputEvent` 可用
- SUI 的进程域是 `u:r:magisk:s0`，注入被拒且 `setcon` 无法解除，
  自动降级到 `input` 命令（约 71ms 一次）

### 第八阶段：清理机制

测试中发现虚拟屏和用户服务进程会残留。经排查，泄漏全部来自早期 demo 项目，
主工程的命名前缀下零残留验证通过。

但仍然加入三层保护：

- 服务绑定成功后先清理再建屏
- 客户端 Binder 注册死亡通知
- 每三秒轮询 `isBinderAlive` 兜底

三次迭代的原因：

1. `daemon(false)` 实测不能保证跟随客户端退出
2. `linkToDeath` 在持有临时 `Binder` 对象时失效，改为静态强引用
3. 超时自杀方案会误杀「等待应用冷启动」和「用户主动挂屏」两种正常场景，弃用

### 第九阶段：输入法

虚拟屏无窗口焦点，系统输入法不会绑定，`InputConnection` 为空。
自带一个 `InputMethodService`，通过 `ime enable` / `ime set` 切换，
`commitText` 写入后自动还原用户原来的输入法。

受限于焦点问题，该能力在部分应用上不可用。

