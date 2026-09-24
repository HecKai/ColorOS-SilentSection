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

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Compile-time stub of the Xposed API (the real class is provided by LSPosed at runtime). */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
