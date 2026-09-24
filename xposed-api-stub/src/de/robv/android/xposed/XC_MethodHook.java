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

import java.lang.reflect.Member;

/** Compile-time stub of the Xposed API. */
public abstract class XC_MethodHook {

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        public Object getResult() { return null; }
        public void setResult(Object result) { }
        public Throwable getThrowable() { return null; }
        public boolean hasThrowable() { return false; }
        public void setThrowable(Throwable throwable) { }
        public Object getResultOrThrowable() throws Throwable { return null; }
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable { }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }

    /** Stub for the unhook handle returned by the helpers. */
    public static class Unhook {
        public void unhook() { }
    }
}
