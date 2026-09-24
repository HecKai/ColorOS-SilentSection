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

/**
 * 两套 Xposed API 的最小公共抽象：
 * <ul>
 *     <li>{@link Hook} —— 经典 XposedBridge API（de.robv.android.xposed）</li>
 *     <li>{@link LibXposedEntry} —— LibXposed API 100+（io.github.libxposed.api）</li>
 * </ul>
 * 核心逻辑（{@link HookCore}）只依赖这个接口，因此两套框架可以共用同一份实现。
 */
public interface HookApi {

    /** 用于日志前缀，例如 "legacy" / "libxposed" */
    String name();

    /** hook 一个类里所有同名方法（重载、静态/实例都会命中） */
    void hookAllMethods(Class<?> clazz, String methodName, Around around);

    /** hook 精确签名的方法 */
    void hookMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Around around);

    void log(String message);

    void log(String message, Throwable throwable);

    /** 方法拦截回调：before 先执行，after 拿到（并可替换）返回值 */
    interface Around {

        void before(Object thisObject, Object[] args);

        /**
         * @param result 原始返回值；返回什么，调用方就拿到什么（可替换）
         */
        Object after(Object thisObject, Object[] args, Object result);
    }
}
