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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 与 Xposed API 无关的核心逻辑，由 {@link Hook}（经典 API）或 {@link LibXposedEntry}（LibXposed API）驱动。
 *
 * <p>功能一：静音通知分区 —— 在 {@code NotificationSortExImpl#modifyOrderedSection} 内让
 * {@code FeatureOption.isExpRegion()} 临时返回 true，保住 AOSP 的 Alerting/Silent/Minimized 分区；
 * 再往渲染树里补回国内分支漏掉的静音表头 node。</p>
 *
 * <p>功能二：剪贴板弹窗 —— {@code ClipboardListener#onPrimaryClipChanged} 开头的国内判断放行。</p>
 */
public final class HookCore {

    static final String TAG = "OplusEnhance";
    static final String TARGET_PKG = "com.android.systemui";
    private static final String CACHE_PREFS = "oplus_enhance_cache";

    private static final String CLS_FEATURE_OPTION = "com.oplusos.systemui.common.feature.FeatureOption";
    private static final String CLS_SORT_EX_IMPL =
            "com.oplus.systemui.notification.sort.NotificationSortExImpl";
    private static final String CLS_CONVERSATION =
            "com.android.systemui.statusbar.notification.collection.coordinator.ConversationCoordinator";
    private static final String CLS_HEADSUP =
            "com.android.systemui.statusbar.notification.collection.coordinator.HeadsUpCoordinator";
    private static final String CLS_CLIPBOARD_LISTENER =
            "com.android.systemui.clipboardoverlay.ClipboardListener";
    private static final String CLS_NODE_SPEC_BUILDER =
            "com.android.systemui.statusbar.notification.collection.render.NodeSpecBuilder";
    private static final String CLS_NODE_SPEC_IMPL =
            "com.android.systemui.statusbar.notification.collection.render.NodeSpecImpl";
    private static final String CLS_NODE_CONTROLLER =
            "com.android.systemui.statusbar.notification.collection.render.NodeController";

    /** 只在指定方法执行期间覆盖 isExpRegion()，其余 100+ 处调用点不受影响 */
    private static final ThreadLocal<Boolean> IN_MODIFY_ORDERED_SECTION = flag();
    private static final ThreadLocal<Boolean> IN_CLIPBOARD_LISTENER = flag();

    private static volatile boolean notifEnabled = true;
    private static volatile boolean clipboardEnabled = true;
    private static volatile Object silentHeaderController = null;
    private static volatile Class<?> nodeSpecImplClass = null;

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicBoolean SETTINGS_READY = new AtomicBoolean(false);
    private static volatile HookApi api;
    private static int failureLogCount = 0;
    private static int hiddenSkipLogCount = 0;

    private HookCore() {
    }

    private static ThreadLocal<Boolean> flag() {
        return new ThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return Boolean.FALSE;
            }
        };
    }

    /** 两套 API 可能同时加载（模块同时声明了经典入口与 LibXposed 入口），这里保证只装一次。 */
    static void install(HookApi hookApi, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) {
            hookApi.log(TAG + ": already installed, skip (" + hookApi.name() + ")");
            return;
        }
        api = hookApi;
        try {
            nodeSpecImplClass = classLoader.loadClass(CLS_NODE_SPEC_IMPL);
            hookIsExpRegion(classLoader);
            hookModifyOrderedSection(classLoader);
            hookClipboardListener(classLoader);
            hookNodeSpecBuilder(classLoader);
            hookApplicationCreate();
            api.log(TAG + ": ready via " + hookApi.name()
                    + " (notif=" + notifEnabled + ", clipboard=" + clipboardEnabled + ")");
        } catch (Throwable t) {
            logFailure("hook install", t);
        }
    }

    // ------------------------------------------------------------------ 设置

    private static void hookApplicationCreate() {
        api.hookAllMethods(Application.class, "onCreate", new HookApi.Around() {
            @Override
            public void before(Object thisObject, Object[] args) {
                // 只关心 Context
            }

            @Override
            public Object after(Object thisObject, Object[] args, Object result) {
                try {
                    if (thisObject instanceof Context) {
                        setupSettings((Context) thisObject);
                    }
                } catch (Throwable t) {
                    logFailure("settings setup", t);
                }
                return result;
            }
        });
    }

    private static void setupSettings(Context context) {
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
                api.log(TAG + ": settings updated (notif=" + notifEnabled
                        + ", clipboard=" + clipboardEnabled + ")");
            }
        };
        IntentFilter filter = new IntentFilter(MainActivity.ACTION_SETTINGS);
        try {
            // Android 13+ 需要显式声明导出标志（发送方是模块 App，属于跨应用广播）
            Method register = Context.class.getMethod(
                    "registerReceiver", BroadcastReceiver.class, IntentFilter.class, int.class);
            register.invoke(context, receiver, filter, 0x2 /* RECEIVER_EXPORTED */);
        } catch (Throwable t) {
            context.registerReceiver(receiver, filter);
        }
        api.log(TAG + ": settings receiver registered");
    }

    // ------------------------------------------------------- 功能一：静音通知分区

    private static void hookModifyOrderedSection(ClassLoader cl) throws Throwable {
        Class<?> impl = cl.loadClass(CLS_SORT_EX_IMPL);
        api.hookMethod(impl, "modifyOrderedSection", new Class<?>[]{
                        cl.loadClass(CLS_CONVERSATION), cl.loadClass(CLS_HEADSUP), List.class},
                new HookApi.Around() {
                    @Override
                    public void before(Object thisObject, Object[] args) {
                        if (notifEnabled) {
                            IN_MODIFY_ORDERED_SECTION.set(Boolean.TRUE);
                        }
                    }

                    @Override
                    public Object after(Object thisObject, Object[] args, Object result) {
                        IN_MODIFY_ORDERED_SECTION.set(Boolean.FALSE);
                        captureSilentHeaderController(args != null && args.length > 2 ? args[2] : null);
                        return result;
                    }
                });
    }

    private static void hookNodeSpecBuilder(ClassLoader cl) throws Throwable {
        Class<?> builder = cl.loadClass(CLS_NODE_SPEC_BUILDER);
        api.hookMethod(builder, "buildNodeSpec", new Class<?>[]{
                        cl.loadClass(CLS_NODE_CONTROLLER), List.class, List.class},
                new HookApi.Around() {
                    @Override
                    public void before(Object thisObject, Object[] args) {
                        // no-op
                    }

                    @Override
                    public Object after(Object thisObject, Object[] args, Object result) {
                        if (!notifEnabled) {
                            return result;
                        }
                        try {
                            injectSilentHeaderNode(result);
                        } catch (Throwable t) {
                            logFailure("inject", t);
                        }
                        return result;
                    }
                });
    }

    /** 记下静音分区的表头 node controller（RankingCoordinator$2/$3 是同一个实例）。 */
    private static void captureSilentHeaderController(Object sectioners) {
        if (!(sectioners instanceof List)) {
            return;
        }
        for (Object sectioner : (List<?>) sectioners) {
            if (sectioner == null) {
                continue;
            }
            Object header = null;
            try {
                header = call(sectioner, "getHeaderNodeController");
            } catch (Throwable ignored) {
                // 不是每个 sectioner 都有表头
            }
            if (header != null && sectioner.getClass().getSimpleName().startsWith("RankingCoordinator")) {
                silentHeaderController = header;   // 每次都刷新，防组件重建后的旧实例
            }
        }
    }

    /** 把静音表头 node 插到渲染树里第一条"可见"静音通知之前。 */
    private static void injectSilentHeaderNode(Object rootSpec) throws Exception {
        Object headerController = silentHeaderController;
        Class<?> specClass = nodeSpecImplClass;
        if (rootSpec == null || headerController == null || specClass == null) {
            return;
        }
        List<?> children = (List<?>) call(rootSpec, "getChildren");
        int insertAt = -1;
        int silentCount = 0;
        for (int i = 0; i < children.size(); i++) {
            Object spec = children.get(i);
            Object controller = call(spec, "getController");
            if (controller == headerController) {
                return;   // 框架已插入，避免重复
            }
            if (bucketOfControllerView(controller) == 6) {
                silentCount++;
                // keepGone = 这一帧被隐藏（锁屏隐藏、"已读不再显示"等）。
                // 只信渲染树里的这个标志：视图的可见性在本次渲染时还是上一帧的状态，
                // 用它判断会把"刚刚从隐藏变回可见"的通知误判成隐藏，导致表头被跳过。
                if (insertAt < 0 && !isSpecHidden(spec)) {
                    insertAt = i;
                }
            }
        }
        if (insertAt < 0) {
            if (silentCount > 0 && hiddenSkipLogCount < 5) {
                hiddenSkipLogCount++;
                api.log(TAG + ": " + silentCount
                        + " silent notification(s) hidden this frame, header skipped");
            }
            return;   // 没有静音通知，或这一帧它们全被隐藏
        }
        Constructor<?> ctor = specClass.getDeclaredConstructor(
                Class.forName("com.android.systemui.statusbar.notification.collection.render.NodeSpec"),
                Class.forName(CLS_NODE_CONTROLLER));
        ctor.setAccessible(true);
        Object node = ctor.newInstance(rootSpec, headerController);
        @SuppressWarnings("unchecked")
        List<Object> mutable = (List<Object>) children;
        mutable.add(insertAt, node);
    }

    private static boolean isSpecHidden(Object spec) {
        try {
            Object keepGone = call(spec, "getKeepGone");
            return keepGone instanceof Boolean && (Boolean) keepGone;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 该 controller 对应视图所属通知的 bucket（静音类都是 6）。 */
    private static int bucketOfControllerView(Object controller) {
        try {
            Object view = call(controller, "getView");
            if (view == null
                    || !"ExpandableNotificationRow".equals(view.getClass().getSimpleName())) {
                return -1;
            }
            Object entry = call(view, "getEntryLegacy");
            if (entry == null) {
                return -1;
            }
            return (Integer) call(entry, "getBucket");
        } catch (Throwable t) {
            return -1;
        }
    }

    // --------------------------------------------------------- 功能二：剪贴板弹窗

    private static void hookClipboardListener(ClassLoader cl) throws Throwable {
        Class<?> listener = cl.loadClass(CLS_CLIPBOARD_LISTENER);
        api.hookMethod(listener, "onPrimaryClipChanged", new Class<?>[0], new HookApi.Around() {
            @Override
            public void before(Object thisObject, Object[] args) {
                // 国内分支开头会因为 isExpRegion()==false 直接 return，这里放开它
                if (clipboardEnabled) {
                    IN_CLIPBOARD_LISTENER.set(Boolean.TRUE);
                }
            }

            @Override
            public Object after(Object thisObject, Object[] args, Object result) {
                IN_CLIPBOARD_LISTENER.set(Boolean.FALSE);
                return result;
            }
        });
    }

    /** 两个功能各自在自己那段代码里让 isExpRegion() 返回 true，其余调用点不受影响。 */
    private static void hookIsExpRegion(ClassLoader cl) throws Throwable {
        Class<?> featureOption = cl.loadClass(CLS_FEATURE_OPTION);
        api.hookAllMethods(featureOption, "isExpRegion", new HookApi.Around() {
            @Override
            public void before(Object thisObject, Object[] args) {
                // no-op
            }

            @Override
            public Object after(Object thisObject, Object[] args, Object result) {
                if (Boolean.TRUE.equals(IN_MODIFY_ORDERED_SECTION.get())
                        || Boolean.TRUE.equals(IN_CLIPBOARD_LISTENER.get())) {
                    return Boolean.TRUE;
                }
                return result;
            }
        });
    }

    // ------------------------------------------------------------------ 反射小工具

    private static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, parameterTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // 继续往上找
            }
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                Method m = iface.getMethod(name, parameterTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // 继续找下一个接口
            }
        }
        return null;
    }

    private static Object call(Object target, String name) throws Exception {
        Method m = findMethod(target.getClass(), name);
        if (m == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
        }
        return m.invoke(target);
    }

    private static Object getField(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                // 继续往上找
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "#" + name);
    }

    private static void logFailure(String what, Throwable t) {
        if (failureLogCount < 5) {
            failureLogCount++;
            api.log(TAG + ": " + what + " failed", t);
        }
    }
}
