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

import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * LibXposed API（100+，含 102）入口。
 *
 * <p>入口类在打包时通过 {@code META-INF/xposed/java_init.list} 声明，
 * 模块信息与作用域见同目录下的 {@code module.prop} / {@code scope.list}。</p>
 *
 * <p>逻辑复用 {@link HookCore}；与经典入口同时存在时，由 HookCore 内部保证只安装一次。</p>
 */
public class LibXposedEntry extends XposedModule {

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, HookCore.TAG, "module loaded, process=" + param.getProcessName()
                + ", systemServer=" + param.isSystemServer()
                + ", framework=" + getFrameworkName() + " " + getFrameworkVersion());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!HookCore.TARGET_PKG.equals(param.getPackageName())) {
            return;   // 作用域外（框架可能因同进程其它包回调进来）
        }
        HookCore.install(new LibXposedApi(this), param.getDefaultClassLoader());
    }

    /** 把 {@link HookApi} 的调用翻译成 LibXposed 的 interceptor-chain 模型。 */
    private static final class LibXposedApi implements HookApi {

        private final XposedModule module;

        LibXposedApi(XposedModule module) {
            this.module = module;
        }

        @Override
        public String name() {
            return "libxposed-api" + module.getApiVersion();
        }

        @Override
        public void hookAllMethods(Class<?> clazz, String methodName, Around around) {
            for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
                for (Method method : c.getDeclaredMethods()) {
                    if (!method.getName().equals(methodName)) {
                        continue;
                    }
                    method.setAccessible(true);
                    module.hook(method).intercept(wrap(around));
                }
            }
        }

        @Override
        public void hookMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes,
                               Around around) {
            try {
                Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                module.hook(method).intercept(wrap(around));
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("method not found: "
                        + clazz.getName() + "#" + methodName, e);
            }
        }

        @Override
        public void log(String message) {
            module.log(Log.INFO, HookCore.TAG, message);
        }

        @Override
        public void log(String message, Throwable throwable) {
            module.log(Log.ERROR, HookCore.TAG, message, throwable);
        }

        private static XposedInterface.Hooker wrap(final Around around) {
            return new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object thisObject = chain.getThisObject();
                    List<Object> argList = chain.getArgs();
                    Object[] args = argList == null ? new Object[0] : argList.toArray();
                    around.before(thisObject, args);
                    Object result = chain.proceed();
                    return around.after(thisObject, args, result);
                }
            };
        }
    }
}
