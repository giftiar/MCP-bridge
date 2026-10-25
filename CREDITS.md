# 致谢与第三方声明

## 开发说明

本项目的全部源代码由 **DeepSeek** 编写。
产品设计、技术方案选择、调试与测试验证均由 giftiar 完成。

---

## 参考实现

### Ruto-GLM

- 地址：https://github.com/iamr0s/Ruto-GLM
- 作者：iamr0s
- 许可：Apache License 2.0
- 参考内容：虚拟屏创建、输入注入、输入法接入的技术思路
- 说明：本项目为 Java 独立实现，未复制其源代码

---

## 依赖库

### Shizuku API

- 地址：https://github.com/RikkaApps/Shizuku-API
- 作者：RikkaApps
- 许可：Apache License 2.0
- 用途：以 shell 身份运行特权服务，用于创建虚拟屏、注入输入事件

### AndroidHiddenApiBypass

- 地址：https://github.com/LSPosed/AndroidHiddenApiBypass
- 作者：LSPosed
- 许可：Apache License 2.0
- 用途：访问 Android 隐藏 API

### Material Components for Android

- 地址：https://github.com/material-components/material-components-android
- 作者：Google
- 许可：Apache License 2.0
- 用途：Material 3 界面组件

### AndroidX AppCompat

- 地址：https://developer.android.com/jetpack/androidx
- 作者：Google
- 许可：Apache License 2.0

### Google ML Kit Text Recognition

- 地址：https://developers.google.com/ml-kit/vision/text-recognition/v2
- 作者：Google
- 许可：Google ML Kit Terms of Service
- 用途：离线中文文字识别

## 关于 ML Kit 与 GPLv3

本项目的源代码以 GPLv3 授权，完整源码见仓库。

OCR 功能依赖 Google ML Kit（`com.google.mlkit:text-recognition-chinese`），
该库依据 **Google ML Kit Terms of Service** 分发，不是自由软件协议。
它通过标准 Maven 依赖引入，本仓库不包含其源代码，也未对其做任何修改。

GPLv3 与 ML Kit 条款的组合在法律界存在争议。本项目采用 FSF 在 GPLv3 FAQ
中给出的官方机制处理这一情况：

> **GNU GPL version 3 section 7 additional permission**
>
> If you modify this Program, or any covered work, by linking or combining it
> with Google ML Kit (or a modified version of that library), containing parts
> covered by the Google ML Kit Terms of Service, the licensors of this Program
> grant you additional permission to convey the resulting work. However, you
> must still comply with the Google ML Kit Terms of Service with respect to the
> ML Kit components themselves.

该额外许可的原文同时列于 `LICENSE` 文件末尾。

### 这个例外能做什么

GPLv3 本身不再构成分发「本项目代码 + ML Kit」组合产物的障碍。
分发包含两者的 APK 时，GPL 这一侧对组合作品的 copyleft 要求已被豁免。

### 这个例外不能做什么

1. **不改变 Google 的条款。** 额外许可是本项目版权人放弃 GPL 赋予自己的那部分
   限制，对 Google 没有任何约束力。ML Kit 仍受其服务条款约束，包括禁止逆向工程等。
2. **不强制后续修改者保留它。** 其他人修改本项目后，可以选择移除这一例外，
   按纯 GPLv3 分发。
3. **不解决「完全自由软件」的认定问题。** F-Droid 等渠道仍会将包含 ML Kit 的
   APK 判定为非自由构建，因为 ML Kit 本身不是自由组件。

### 对使用者的意义

- 本项目的**源代码**以 GPLv3 授权，任何人可以获取、修改、再分发
- 本项目的**APK** 包含受 Google 条款约束的非自由组件，因此不能被认定为
  完全自由的软件
- 如需完全自由的构建，可移除 `Ocr.java` 与 `Ui.ocrPng()` 中的 ML Kit 调用

ML Kit 服务条款：https://developers.google.com/ml-kit/terms

---

## 关于 `SelectToSpeakService` 的包名

`com.google.android.accessibility.selecttospeak.SelectToSpeakService`
是本项目自行编写的空实现无障碍服务，**不是 Google 的 SelectToSpeak**。

包名有意与系统组件同名：微信等应用检测到第三方自动化无障碍服务后
会拒绝返回节点树，注册为该名称可使其恢复正常行为。

该服务不读取任何内容、不发送任何数据、不执行任何操作。

---

## 字体与图标

本项目未引入第三方字体或图标资源，界面图标使用 Material Components 内置图标。
