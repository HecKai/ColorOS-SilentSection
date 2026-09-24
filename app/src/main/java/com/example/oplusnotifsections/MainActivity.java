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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * 开关界面：完全使用原生 Material 3 组件
 * （MaterialToolbar / MaterialCardView / MaterialSwitch，主题 Theme.Material3.DayNight.NoActionBar，
 *  并由 App 里的 DynamicColors 跟随系统动态取色）。
 */
public class MainActivity extends AppCompatActivity {

    public static final String PREFS = "settings";
    public static final String KEY_NOTIF = "notif_enabled";
    public static final String KEY_CLIP = "clip_enabled";

    /** 与 Hook 中约定的广播 action / 目标包。 */
    public static final String ACTION_SETTINGS = "com.example.oplusnotifsections.SETTINGS";
    public static final String TARGET_PKG = "com.android.systemui";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Test");
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, dp(8), pad, dp(28));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(settingCard("静音通知分区",
                "普通通知在上、静音通知单独成组。", KEY_NOTIF, true));
        content.addView(spacer(dp(12)));
        content.addView(settingCard("剪贴板弹窗",
                "复制文字后左下角弹出剪贴板卡片，点击卡片可进入剪贴板编辑页。", KEY_CLIP, true));

        setContentView(root);
        applyEdgeToEdge(root, toolbar);
        syncToSystemUi();   // 打开界面就同步一次，避免 SystemUI 缓存落后
    }

    /**
     * 状态栏沉浸（edge-to-edge）：内容绘制到状态栏 / 导航栏下方，两条系统栏都设为透明，
     * 用 WindowInsets 给根布局留出安全间距，并让状态栏图标颜色跟随浅色 / 深色主题。
     */
    private void applyEdgeToEdge(final View root, MaterialToolbar toolbar) {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);       // 别让系统给状态栏加半透明底
            window.setNavigationBarContrastEnforced(false);
        }

        boolean lightIcons = !isNightMode();                  // 浅色背景 → 深色图标
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, root);
        controller.setAppearanceLightStatusBars(lightIcons);
        controller.setAppearanceLightNavigationBars(lightIcons);

        // 状态栏区域露出的就是根布局底色，让工具栏同色，视觉上是一整块
        int surface = color(com.google.android.material.R.attr.colorSurface);
        root.setBackgroundColor(surface);
        toolbar.setBackgroundColor(surface);

        ViewCompat.setOnApplyWindowInsetsListener(root, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsetsCompat.CONSUMED;
            }
        });
        ViewCompat.requestApplyInsets(root);
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 一张 MaterialCardView：MaterialSwitch + 说明文字（均使用 MD3 默认样式）。 */
    private View settingCard(String title, String desc, final String key, boolean defValue) {
        MaterialCardView card = new MaterialCardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setCardBackgroundColor(
                color(com.google.android.material.R.attr.colorSurfaceContainerLow));
        card.setRadius(dp(16));
        card.setCardElevation(0f);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(14), pad, dp(18));
        card.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setText(title);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sw.setChecked(prefs.getBoolean(key, defValue));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(key, isChecked).apply();
                syncToSystemUi();
            }
        });
        box.addView(sw);

        TextView tv = new TextView(this);
        tv.setText(desc);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        tv.setPadding(dp(2), dp(8), 0, 0);
        box.addView(tv);

        return card;
    }

    private int color(int attrRes) {
        return MaterialColors.getColor(getWindow().getDecorView(), attrRes);
    }

    private View spacer(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));
        return v;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 把当前开关状态广播给 SystemUI（只有 SystemUI 里注册的动态接收器会收到）。 */
    private void syncToSystemUi() {
        Intent intent = new Intent(ACTION_SETTINGS);
        intent.setPackage(TARGET_PKG);
        intent.putExtra(KEY_NOTIF, prefs.getBoolean(KEY_NOTIF, true));
        intent.putExtra(KEY_CLIP, prefs.getBoolean(KEY_CLIP, true));
        sendBroadcast(intent);
    }
}
