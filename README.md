
一个 LSPosed 模块：在 **ColorOS（国内版）** 上恢复两个 **OxygenOS（氧OS / 海外版）** 才有的特性。

---

## 功能

| 功能 | 效果 |
| --- | --- |
| **静音通知分区** | 普通通知在上，静音通知单独成组沉底，并带「静默」标题与一键清除 |
| **剪贴板弹窗** | 复制文字后左下角弹出剪贴板卡片，点击进入系统自带的剪贴板编辑页 |

（改完重启系统界面生效）。

---

## 前置条件

* 已 root 的设备（Magisk / KernelSU 等）
* 已安装 **LSPosed**（经典 Xposed API）或其它支持 **LibXposed API 102** 的框架
* 模块作用域勾选 **系统界面 / com.android.systemui**

## 兼容性

* 原理上适用于「ColorOS 16」的机型。
* 已适配 **LibXposed API 102**（经典 Xposed API 与 LibXposed 框架下都能正常加载）。



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

> 早期版本尝试过手动 `addView` 到堆叠视图，结果与框架重排互相打架（实测抛 383 次
> `child already has a parent` 且位置反复抖动）。**在渲染树层补 node** 才对，详见 git 历史。

### 三、剪贴板弹窗

`ClipboardListener#onPrimaryClipChanged()` 开头即判断是否为国内版本，国内直接 `return`。
弹窗、动作按钮、编辑页（`OplusEditTextActivity`）都随 ColorOS 的 SystemUI 一起发布，
所以放开那一行判断即可，无需自绘任何界面。这行判断同时也是 ColorOS「剪贴板超级防火墙」的拦截点，
放开后弹窗行为与 OxygenOS 一致（模块只改这一处分支，不读取、不记录、不上传剪贴板内容）。

---

## 许可

[GPL-3.0](LICENSE)（GNU General Public License v3.0）。

本项目不分发任何 OPPO / 一加（ColorOS / OxygenOS）的代码或资源，仅为运行时 Hook；
内置的 AndroidX / Material Components 为 Apache-2.0，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
