# 第三方组件声明

本项目的 APK 在构建时静态链接了下列第三方库（均为 Apache License 2.0），
完整许可文本见 [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt)。

| 组件 | 用途 | 许可 |
| --- | --- | --- |
| AndroidX（core / appcompat / activity / fragment / lifecycle / recyclerview 等） | 应用框架、Compat API、Insets 处理 | Apache-2.0 |
| Material Components for Android (`com.google.android.material:material:1.12.0`) | Material 3 组件（MaterialToolbar / MaterialCardView / MaterialSwitch）与主题 | Apache-2.0 |
| Kotlin stdlib（AndroidX 的传递依赖） | 运行期依赖 | Apache-2.0 |

## 关于 Xposed API

`xposedstub/` 与 `xposed-api-stub/` 中的类是为编译期提供的最小**接口桩**（仅方法签名，随 GPL-3.0 发布）。
真正的 Xposed API 实现在运行期由 **LSPosed** 提供，本项目不再分发其实现代码。

## 未被本项目分发的内容

本项目**不包含**任何 OPPO / 一加（ColorOS / OxygenOS）的代码或资源。
模块通过运行时 Hook 调用设备自带 SystemUI 中的公开/内部方法，这些代码始终留在用户设备的系统分区内。
