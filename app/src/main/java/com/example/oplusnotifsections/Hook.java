/*
 * ColorOS Enhance — LSPosed module
 * Copyright (C) 2026  HeckyKai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.example.oplusnotifsections;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * ColorOS 增强（LSPosed 模块，作用域：com.android.systemui）
 *
 * 功能一：静音通知分区（氧OS 行为）
 *   1) NotificationSortExImpl#modifyOrderedSection 内让 isExpRegion() 临时返回 true，
 *      保住 AOSP 的 Alerting / Silent / Minimized 分区；
 *   2) NodeSpecBuilder#buildNodeSpec 产出渲染树后，把静音表头 node 补到第一条
 *      *可见*静音通知之前（跳过 keepGone 的隐藏节点）。
 *
 * 功能二：剪贴板弹窗（氧OS 行为）
 *   ClipboardListener#onPrimaryClipChanged 开头有国内判断：
 *     if (!isExpRegion() && !OpUtils.sIsClosedSuperFirewall) return;   // 国内直接不弹
 *   在这里同样让 isExpRegion() 临时返回 true，即可放开整条原生链路
 *   （弹窗、编辑页 OplusEditTextActivity 都随 ColorOS 的 SystemUI 一起发布了）。
 *
 * 两个功能的开关由模块 App（MainActivity）通过广播同步进来，并缓存在 SystemUI 自己的
 * SharedPreferences 里，所以重启手机后不需要模块 App 先运行。
 */
public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "OplusEnhance";
    private static final String TARGET_PKG = "com.android.systemui";
    private static final String CACHE_PREFS = "oplus_enhance_cache";

    private static final String CLS_FEATURE_OPTION = "com.oplusos.systemui.common.feature.FeatureOption";
    private static final String CLS_SORT_EX_IMPL = "com.oplus.systemui.notification.sort.NotificationSortExImpl";
    private static final String CLS_CONVERSATION =
            "com.android.systemui.statusbar.notification.collection.coordinator.ConversationCoordinator";
    private static final String CLS_HEADSUP =
            "com.android.systemui.statusbar.notification.collection.coordinator.HeadsUpCoordinator";
    private static final String CLS_CLIPBOARD_LISTENER = "com.android.systemui.clipboardoverlay.ClipboardListener";
    private static final String CLS_NODE_SPEC_BUILDER =
            "com.android.systemui.statusbar.notification.collection.render.NodeSpecBuilder";
    private static final String CLS_NODE_SPEC_IMPL =
            "com.android.systemui.statusbar.notification.collection.render.NodeSpecImpl";
    private static final String CLS_NODE_CONTROLLER =
            "com.android.systemui.statusbar.notification.collection.render.NodeController";

    private static final ThreadLocal<Boolean> IN_MODIFY_ORDERED_SECTION = flag();
    private static final ThreadLocal<Boolean> IN_CLIPBOARD_LISTENER = flag();

    private static volatile boolean notifEnabled = true;
    private static volatile boolean clipboardEnabled = true;
    private static volatile Object silentHeaderController = null;
    private static volatile Object nodeSpecImplClass = null;
    private static final AtomicBoolean SETTINGS_READY = new AtomicBoolean(false);
    private static int failureLogCount = 0;
    private static int hiddenSkipLogCount = 0;

    private static ThreadLocal<Boolean> flag() {
        return new ThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return Boolean.FALSE;
            }
        };
    }

    private static void logFailure(String what, Throwable t) {
        if (failureLogCount < 5) {
            failureLogCount++;
            XposedBridge.log(TAG + ": " + what + " failed: " + t);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }
        try {
            nodeSpecImplClass = XposedHelpers.findClass(CLS_NODE_SPEC_IMPL, lpparam.classLoader);
            hookIsExpRegion(lpparam.classLoader);
            hookModifyOrderedSection(lpparam.classLoader);
            hookClipboardListener(lpparam.classLoader);
            hookNodeSpecBuilder(lpparam.classLoader);
            hookApplicationCreate();
            XposedBridge.log(TAG + ": ready (notif=" + notifEnabled + ", clipboard=" + clipboardEnabled + ")");
        } catch (Throwable t) {
            logFailure("hook install", t);
        }
    }

    // ------------------------------------------------------------------ 设置同步

    /** SystemUI 的 Application 一创建就拿到 Context，用于读缓存 + 注册设置广播。 */
    private void hookApplicationCreate() {
        XposedBridge.hookAllMethods(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    setupSettings((Context) param.thisObject);
                } catch (Throwable t) {
                    logFailure("settings setup", t);
                }
            }
        });
    }

    private void setupSettings(Context context) {
        if (context == null || !SETTINGS_READY.compareAndSet(false, true)) {
            return;
        }
        SharedPreferences sp = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
        notifEnabled = sp.getBoolean(MainActivity.KEY_NOTIF, true);
        clipboardEnabled = sp.getBoolean(MainActivity.KEY_CLIP, true);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null) {
                    return;
                }
                notifEnabled = intent.getBooleanExtra(MainActivity.KEY_NOTIF, notifEnabled);
                clipboardEnabled = intent.getBooleanExtra(MainActivity.KEY_CLIP, clipboardEnabled);
                ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean(MainActivity.KEY_NOTIF, notifEnabled)
                        .putBoolean(MainActivity.KEY_CLIP, clipboardEnabled)
                        .apply();
                XposedBridge.log(TAG + ": settings updated (notif=" + notifEnabled
                        + ", clipboard=" + clipboardEnabled + ")");
            }
        };
        IntentFilter filter = new IntentFilter(MainActivity.ACTION_SETTINGS);
        try {
            // Android 13+ 要求显式声明导出标志（发送方是模块 App，属于跨应用广播）
            Method register = Context.class.getMethod(
                    "registerReceiver", BroadcastReceiver.class, IntentFilter.class, int.class);
            register.invoke(context, receiver, filter, 0x2 /* RECEIVER_EXPORTED */);
        } catch (Throwable t) {
            context.registerReceiver(receiver, filter);
        }
        XposedBridge.log(TAG + ": settings receiver registered");
    }

    // ------------------------------------------------------- 功能一：静音通知分区

    private void hookModifyOrderedSection(ClassLoader cl) throws Throwable {
        XposedHelpers.findAndHookMethod(
                CLS_SORT_EX_IMPL, cl, "modifyOrderedSection",
                XposedHelpers.findClass(CLS_CONVERSATION, cl),
                XposedHelpers.findClass(CLS_HEADSUP, cl),
                List.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (notifEnabled) {
                            IN_MODIFY_ORDERED_SECTION.set(Boolean.TRUE);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        IN_MODIFY_ORDERED_SECTION.set(Boolean.FALSE);
                        captureSilentHeaderController(param.args.length > 2 ? param.args[2] : null);
                    }
                });
    }

    private void hookNodeSpecBuilder(ClassLoader cl) throws Throwable {
        Class<?> builder = XposedHelpers.findClass(CLS_NODE_SPEC_BUILDER, cl);
        XposedHelpers.findAndHookMethod(
                builder, "buildNodeSpec",
                XposedHelpers.findClass(CLS_NODE_CONTROLLER, cl), List.class, List.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!notifEnabled) {
                            return;
                        }
                        try {
                            injectSilentHeaderNode(param.getResult());
                        } catch (Throwable t) {
                            logFailure("inject", t);
                        }
                    }
                });
    }

    /** 记下静音分区的表头 node controller（RankingCoordinator$2/$3 是同一个实例）。 */
    private void captureSilentHeaderController(Object sectioners) {
        if (!(sectioners instanceof List)) {
            return;
        }
        for (Object sectioner : (List<?>) sectioners) {
            if (sectioner == null) {
                continue;
            }
            Object header = null;
            try {
                header = XposedHelpers.callMethod(sectioner, "getHeaderNodeController");
            } catch (Throwable ignored) {
                // 不是每个 sectioner 都有表头
            }
            // 每次都以最新的实例为准（SystemUI 组件重建后会换新的 controller）
            if (header != null
                    && sectioner.getClass().getSimpleName().startsWith("RankingCoordinator")) {
                silentHeaderController = header;
            }
        }
    }

    /** 把静音表头 node 插到渲染树里第一条"可见"静音通知之前。 */
    private void injectSilentHeaderNode(Object rootSpec) {
        Object headerController = silentHeaderController;
        Object specClass = nodeSpecImplClass;
        if (rootSpec == null || headerController == null || specClass == null) {
            return;
        }
        List<?> children = (List<?>) XposedHelpers.callMethod(rootSpec, "getChildren");
        int insertAt = -1;
        int silentCount = 0;
        for (int i = 0; i < children.size(); i++) {
            Object spec = children.get(i);
            Object controller = XposedHelpers.callMethod(spec, "getController");
            if (controller == headerController) {
                return;   // 框架已插入，避免重复
            }
            if (bucketOfControllerView(controller) == 6) {
                silentCount++;
                // keepGone = 这一帧里被隐藏（锁屏隐藏、"已读不再显示"等）。
                // 只信渲染树里的这个标志：视图的 getVisibility() 在这次渲染中还是上一次的状态，
                // 用它判断会把"刚刚从隐藏变回可见"的通知误判成隐藏，导致表头被跳过。
                if (insertAt < 0 && !isSpecHidden(spec)) {
                    insertAt = i;
                }
            }
        }
        if (insertAt < 0) {
            if (silentCount > 0 && hiddenSkipLogCount < 5) {
                hiddenSkipLogCount++;
                XposedBridge.log(TAG + ": " + silentCount
                        + " silent notification(s) hidden this frame, header skipped");
            }
            return;   // 没有静音通知，或这一帧它们全被隐藏
        }
        Object node = XposedHelpers.newInstance((Class<?>) specClass, rootSpec, headerController);
        @SuppressWarnings("unchecked")
        List<Object> mutable = (List<Object>) children;
        mutable.add(insertAt, node);
    }

    private boolean isSpecHidden(Object spec) {
        try {
            Object keepGone = XposedHelpers.callMethod(spec, "getKeepGone");
            return keepGone instanceof Boolean && (Boolean) keepGone;
        } catch (Throwable t) {
            return false;
        }
    }

    private int bucketOfControllerView(Object controller) {
        try {
            Object view = XposedHelpers.callMethod(controller, "getView");
            if (view == null
                    || !"ExpandableNotificationRow".equals(view.getClass().getSimpleName())) {
                return -1;
            }
            Object entry = XposedHelpers.callMethod(view, "getEntryLegacy");
            if (entry == null) {
                return -1;
            }
            return (Integer) XposedHelpers.callMethod(entry, "getBucket");
        } catch (Throwable t) {
            return -1;
        }
    }

    // --------------------------------------------------------- 功能二：剪贴板弹窗

    private void hookClipboardListener(ClassLoader cl) throws Throwable {
        Class<?> listener = XposedHelpers.findClass(CLS_CLIPBOARD_LISTENER, cl);
        XposedHelpers.findAndHookMethod(listener, "onPrimaryClipChanged", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                // 国内分支开头会因为 isExpRegion()==false 直接 return，这里放开它
                if (clipboardEnabled) {
                    IN_CLIPBOARD_LISTENER.set(Boolean.TRUE);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                IN_CLIPBOARD_LISTENER.set(Boolean.FALSE);
            }
        });
    }

    /** 两个功能各自在自己那段代码里让 isExpRegion() 返回 true，其余调用点不受影响。 */
    private void hookIsExpRegion(ClassLoader cl) throws Throwable {
        Class<?> featureOption = XposedHelpers.findClass(CLS_FEATURE_OPTION, cl);
        XposedBridge.hookAllMethods(featureOption, "isExpRegion", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(IN_MODIFY_ORDERED_SECTION.get())
                        || Boolean.TRUE.equals(IN_CLIPBOARD_LISTENER.get())) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });
    }
}
