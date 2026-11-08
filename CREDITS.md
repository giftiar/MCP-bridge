# 致谢与第三方声明

## 作者

产品设计、技术方案与测试：giftiar

---

## 参考实现

以下项目仅作为技术资料参考。本项目为 Java 独立实现，未复制其源代码；
类结构、变量命名与方法划分为自写。

### scrcpy

- 地址：https://github.com/Genymobile/scrcpy
- 作者：Romain Vimont（rom1v）与贡献者
- 许可：Apache License 2.0
- 参考内容：
    - `InputManager.injectInputEvent` 的参数语义，以及代理模式下会阻塞输入分发链路
    - `InputEvent.setDisplayId` 需在注入前调用
    - 触摸事件需以 `PointerProperties` 与 `PointerCoords` 构造，并显式声明
      `TOOL_TYPE_FINGER` 与 `SOURCE_TOUCHSCREEN`
    - `Device.injectEvent` 中先设置显示 ID 再注入的调用顺序
    - 虚拟屏需声明触摸支持才能接收输入
    - 通过反射读取 `DisplayManagerGlobal.getDisplayInfo` 取得真实尺寸与旋转
    - 建屏前将主屏尺寸归正到自然方向
    - `ROTATES_WITH_CONTENT` 的适用场景（本项目评估后未采用）
- 说明：以上均为对平台行为与 API 用法的参考。相关实现见 `VdSvc.java` 与 `Disp.java`

### Ruto-GLM

- 地址：https://github.com/iamr0s/Ruto-GLM
- 作者：iamr0s
- 许可：Apache License 2.0
- 参考内容：虚拟屏创建、输入注入与输入法接入的总体思路

---

## 依赖库

| 项目 | 作者 | 许可 | 用途 |
|---|---|---|---|
| [Shizuku API](https://github.com/RikkaApps/Shizuku-API) | RikkaApps | Apache 2.0 | 以 shell 身份运行特权服务 |
| [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) | LSPosed | Apache 2.0 | 访问隐藏 API |
| [Material Components](https://github.com/material-components/material-components-android) | Google | Apache 2.0 | Material 3 组件 |
| [AndroidX AppCompat](https://developer.android.com/jetpack/androidx) | Google | Apache 2.0 | Activity 兼容 |
| [ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2) | Google | Google ML Kit 服务条款 | 离线中文文字识别 |

---

## 关于 ML Kit 与 GPLv3

本项目源代码以 GPLv3 授权。文字识别依赖 Google ML Kit
（`com.google.mlkit:text-recognition-chinese`），该库依 Google ML Kit
服务条款分发，不是自由软件协议。它通过 Maven 依赖引入，本仓库不包含其源代码。

GPLv3 与 ML Kit 条款的组合存在法律争议。本项目采用 FSF 在 GPLv3 FAQ 中
给出的官方机制处理：

> **GNU GPL version 3 section 7 additional permission**
>
> If you modify this Program, or any covered work, by linking or combining it
> with Google ML Kit (or a modified version of that library), containing parts
> covered by the Google ML Kit Terms of Service, the licensors of this Program
> grant you additional permission to convey the resulting work. However, you
> must still comply with the Google ML Kit Terms of Service with respect to the
> ML Kit components themselves.

该额外许可的原文同时列于 `LICENSE` 文件末尾。

**该例外的作用范围**

1. 分发包含本程序与 ML Kit 的组合产物时，GPL 一侧的 copyleft 要求已被豁免。
2. 不改变 Google 的条款。额外许可是本项目版权人的单方放弃，对 Google 无约束力。
3. 不强制后续修改者保留。他人修改后可选择移除该例外，按纯 GPLv3 分发。
4. 不改变自由软件的认定。包含 ML Kit 的 APK 仍不属于完全自由的构建。

如需完全自由的构建，可移除 `Ocr.java` 与 `Ui.ocrPng()` 中的 ML Kit 调用。

ML Kit 服务条款：https://developers.google.com/ml-kit/terms

---

## 关于 `SelectToSpeakService` 的包名

`com.google.android.accessibility.selecttospeak.SelectToSpeakService`
为本项目自行编写的无障碍服务空实现，不是 Google 的 SelectToSpeak。

部分应用检测到第三方自动化无障碍服务后拒绝返回节点树。
将其注册为该名称可使这类应用恢复正常行为。
