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

import java.util.Set;

/** Compile-time stub of the Xposed API. */
public final class XposedBridge {
    public static void log(String text) { }

    public static void log(Throwable t) { }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(
            Class<?> hookClass, String methodName, XC_MethodHook callback) {
        return null;
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(
            Class<?> hookClass, XC_MethodHook callback) {
        return null;
    }

    public static XC_MethodHook.Unhook hookMethod(java.lang.reflect.Member hookMethod, XC_MethodHook callback) {
        return null;
    }
}
