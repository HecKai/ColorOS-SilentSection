/*
 * ColorOS Enhance — LSPosed module (Xposed API stub)
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
 */
package de.robv.android.xposed;

/** Compile-time stub of the Xposed API. */
public final class XposedHelpers {
    public static Class<?> findClass(String className, ClassLoader classLoader) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            String className, ClassLoader classLoader, String methodName,
            Object... parameterTypesAndCallback) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }

    public static Object getObjectField(Object obj, String fieldName) { return null; }

    public static void setObjectField(Object obj, String fieldName, Object value) { }

    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) { return null; }

    public static int getIntField(Object obj, String fieldName) { return 0; }

    public static boolean getBooleanField(Object obj, String fieldName) { return false; }

    public static Object newInstance(Class<?> clazz, Object... args) { return null; }
}
