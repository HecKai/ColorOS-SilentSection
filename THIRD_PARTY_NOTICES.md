# 第三方组件声明

本项目的 APK 在构建时静态链接了下列第三方库（均为 Apache License 2.0），
完整许可文本见 [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt)。

| 组件 | 用途 | 许可 |
| --- | --- | --- |
| AndroidX（core / appcompat / activity / fragment / lifecycle / recyclerview 等） | 应用框架、Compat API、Insets 处理 | Apache-2.0 |
| Material Components for Android (`com.google.android.material:material:1.12.0`) | Material 3 组件（MaterialToolbar / MaterialCardView / MaterialSwitch）与主题 | Apache-2.0 |
| Kotlin stdlib（AndroidX 的传递依赖） | 运行期依赖 | Apache-2.0 |
| LibXposed API (`io.github.libxposed:api:102.0.0`，仓库内以 `app/libs/libxposed-api-102.0.0.jar` 提供) | LibXposed API 100+ 模块接口（编译期，运行期由框架提供） | Apache-2.0 |

## 关于 Xposed API

`xposedstub/` 与 `xposed-api-stub/` 中的类是为编译期提供的最小**接口桩**（仅方法签名，随 GPL-3.0 发布）。
真正的 Xposed API 实现在运行期由 **LSPosed** 提供，本项目不再分发其实现代码。

`app/libs/libxposed-api-102.0.0.jar` 是 LibXposed API 102 的 `classes.jar`（Apache-2.0，取自官方构件
`io.github.libxposed:api:102.0.0`）。内置而非直接引用 Maven 依赖的原因：官方 AAR 的元数据要求
`compileSdk 37`，而本项目使用 `compileSdk 34` + AGP 8.5。该 jar 只参与编译，
运行期同样由支持 LibXposed 的框架提供 API 实现。

## 未被本项目分发的内容

本项目**不包含**任何 OPPO / 一加（ColorOS / OxygenOS）的代码或资源。
模块通过运行时 Hook 调用设备自带 SystemUI 中的公开/内部方法，这些代码始终留在用户设备的系统分区内。
