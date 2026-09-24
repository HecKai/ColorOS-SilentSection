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

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 经典 Xposed API 入口（de.robv.android.xposed，LSPosed 1.x 等框架）。
 *
 * <p>真正逻辑在 {@link HookCore}；本类只做 API 适配，因此同一份实现也能被
 * {@link LibXposedEntry}（LibXposed API 100+）驱动。</p>
 */
public class Hook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!HookCore.TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }
        HookCore.install(new ClassicApi(), lpparam.classLoader);
    }

    /** 把 {@link HookApi} 的调用翻译成经典 XposedBridge API。 */
    private static final class ClassicApi implements HookApi {

        @Override
        public String name() {
            return "classic-xposed";
        }

        @Override
        public void hookAllMethods(Class<?> clazz, String methodName, Around around) {
            XposedBridge.hookAllMethods(clazz, methodName, wrap(around));
        }

        @Override
        public void hookMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes,
                               Around around) {
            try {
                Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                XposedBridge.hookMethod(method, wrap(around));
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("method not found: "
                        + clazz.getName() + "#" + methodName, e);
            }
        }

        @Override
        public void log(String message) {
            XposedBridge.log(message);
        }

        @Override
        public void log(String message, Throwable throwable) {
            XposedBridge.log(message + ": " + throwable);
        }

        private static XC_MethodHook wrap(final Around around) {
            return new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    around.before(param.thisObject, param.args);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) {
                        around.after(param.thisObject, param.args, null);
                        return;
                    }
                    param.setResult(around.after(param.thisObject, param.args, param.getResult()));
                }
            };
        }
    }
}
