# ColorOS Enhance

一个 LSPosed 模块：在 **ColorOS（国内版）** 上恢复两个 **OxygenOS（氧OS / 海外版）** 才有的特性，
并提供一个原生 Material 3 的开关界面。

> ⚠️ **非官方项目**。与 OPPO / 一加（ColorOS / OxygenOS）无关，仅供在**你自己的设备**上做界面定制。
> 需要 root + LSPosed，使用风险自负。

---

## 功能

| 功能 | 效果 |
| --- | --- |
| **静音通知分区** | 普通通知在上，静音通知单独成组沉底，并带「静默」标题与一键清除 |
| **剪贴板弹窗** | 复制文字后左下角弹出剪贴板卡片，点击进入系统自带的剪贴板编辑页 |

两个功能都能在 App 里单独开关（改完立即生效；通知分区涉及 SystemUI 启动时构建的列表，切换后建议重启 SystemUI）。

### ⚠️ 关于剪贴板功能的隐私提示

ColorOS 国内版里，剪贴板弹窗被一处地区/安全判断整体屏蔽：

```java
if (!FeatureOption.isExpRegion() && !OpUtils.sIsClosedSuperFirewall) return;
```

本模块会让 `isExpRegion()` 在这一段判断中临时返回 `true`，即**同时绕过 ColorOS 的"超级防火墙"剪贴板拦截**。
后果是：复制验证码、密码、卡号等敏感内容时也会弹出预览卡片（氧OS 本来就是这个行为，ColorOS 是有意屏蔽的）。
模块**不会**修改剪贴板数据、不会上传任何内容，也不改变其它 App 读取剪贴板的权限（那是 NotificationManager 的策略，与本模块无关）；
源应用主动标记 `SUPPRESS_CLIPBOARD_OVERLAY` 或内容被系统判定为敏感分类时，依旧不会弹窗。

如果你不希望绕过这层拦截，可以只开"静音通知分区"，或在代码里去掉剪贴板那一个 Hook。

---

## 前置条件

* 已 root 的设备（Magisk / KernelSU 等）+ **Zygisk**
* 已安装 **LSPosed**（本项目用 LSPosed 的经典 Xposed API）
* 模块作用域勾选 **系统界面 / com.android.systemui**

## 兼容性

* 实测：OnePlus PJZ110，ColorOS 16.0.10.501（Android 16），SystemUI **16.99.12**
* 原理上适用于「国内版 ColorOS + AOSP 通知分区代码」的机型；其它版本若不生效，见下方 FAQ

---

## 安装

1. 从 Releases 下载 APK（或用下面的方法自行构建）
2. 安装后在 LSPosed 中启用本模块，作用域勾选「系统界面」
3. 重启 SystemUI 或重启手机

## 构建

用 Android Studio（Koala 2024.1+ / JDK 17）直接 **File → Open** 本工程即可，首次同步会拉取
Material 1.12 等依赖。命令行：

```bash
./gradlew :app:assembleRelease      # Windows: gradlew.bat :app:assembleRelease
```

**签名**：仓库中不含私钥。把你的 keystore 放在项目根目录，并创建 `keystore.properties`（已被 `.gitignore` 忽略）：

```properties
storeFile=my-release.jks
storePassword=******
keyAlias=******
keyPassword=******
```

没有这个文件时会自动退回 debug 签名（能构建，只是换签名后安装需要先卸载旧版本）。
详见 [`keystore.properties.example`](keystore.properties.example)。

Release 构建默认开启 R8 + 资源压缩 + 仅保留中英文资源，APK 约 1.9 MB（不压缩约 9.4 MB）。

---

## 实现原理

### 一、静音通知分区

ColorOS 国内分支会在 `NotificationSortExImpl#modifyOrderedSection()` 里
（判断条件同样是 `FeatureOption.isExpRegion()`）把 AOSP 的分区列表**整段丢弃**，
只保留 Oplus 自有分区，于是 Alerting / Silent / Minimized 都没了，普通与静音通知一起落进默认分区。

模块用 `ThreadLocal` 把 `isExpRegion()` 的覆盖**限定在这一个方法调用内**（该方法在全局有 100+ 处调用，
AOD、导航栏、锁屏、钱包都会用到，不能全局改），从而恢复 AOSP 分区列表。

### 二、把静音表头补进渲染树

分区恢复后，`Silent` 表头视图仍不会显示：视图虽然被 inflate，但从未挂进视图树
（`SectionHeaderNodeControllerImpl.reinflateView()` 首次 inflate 不会 `addView`，后续依赖渲染树插入，
而国内路径没把它放进 `NodeSpec`）。模块挂钩 `NodeSpecBuilder#buildNodeSpec`，
用 `RankingCoordinator$2/#3.getHeaderNodeController()` 拿到表头 controller，
把它插到**第一条可见静音通知之前**；增删/排序/动画仍由框架的 differ 负责。

注意 ColorOS 会把"隐藏但保留"的通知用 `NodeSpec#getKeepGone()` 留在树里
（锁屏隐藏静音通知、"已读通知锁屏不再显示"都走这条路），插入时必须跳过，否则会出现"只有表头没有通知"。

> 开发笔记：早期版本尝试过手动 `addView` 到堆叠视图，结果与框架重排互相打架（实测抛 383 次
> `child already has a parent` 且位置反复抖动）。**在渲染树层补 node** 才是正解，详见 git 历史。

### 三、剪贴板弹窗

`ClipboardListener#onPrimaryClipChanged()` 开头即国内判断（见上文隐私提示）。
弹窗、动作按钮、编辑页（`OplusEditTextActivity`）都随 ColorOS 的 SystemUI 一起发布，
所以放开那一行判断即可，无需自绘任何界面。

### 四、开关同步

App 侧改动写入自己的 SharedPreferences，并广播 `com.example.oplusnotifsections.SETTINGS`
（`setPackage("com.android.systemui")`）；SystemUI 侧在 `Application#onCreate` 拿到 Context 后注册动态接收器，
把结果缓存到 SystemUI 自己的 SharedPreferences —— 因此**重启手机后无需先打开 App**。

---

## 常见问题

**Q：锁屏上看不到「静默」标题？**
A：这是设计如此。ColorOS 在锁屏隐藏静音通知（或"已读通知不再显示"）时，
`NodeSpec` 里这些行是 `keepGone` 状态，模块会**跟着一起隐藏表头**，避免出现"只有标题没有通知"。解锁后即恢复。

**Q：某些 App 复制后不弹剪贴板卡片？**
A：系统或应用侧主动抑制：应用给剪切内容打了 `SUPPRESS_CLIPBOARD_OVERLAY` 标记、
内容被判定为敏感分类（只显示"已复制"提示）、来源是 `com.android.shell`（adb）、
或设备处于锁屏/未完成初始设置状态。氧OS 行为相同。

**Q：ColorOS 更新后功能失效了？**
A：模块依赖 SystemUI 16.99.12 的类名/方法名。升级后若改名，钩子会失败但**不会影响系统运行**
（失败信息会写入 LSPosed 日志，Tag：`OplusEnhance`）。提 Issue 时请附 LSPosed 导出的日志。

**Q：会不会增加耗电？**
A：不会。模块只有三个方法钩子，没有 Service / Receiver（除设置广播）/ 定时器 / 网络，
息屏状态下不执行任何代码；只在 SystemUI 自己干活时被顺带调用。

---

## 许可

[GPL-3.0](LICENSE)（GNU General Public License v3.0）。

本项目不分发任何 OPPO / 一加（ColorOS / OxygenOS）的代码或资源，仅为运行时 Hook；
内置的 AndroidX / Material Components 为 Apache-2.0，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
