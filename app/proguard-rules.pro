# LSPosed 通过 assets/xposed_init 里的类名加载入口，R8 不能改名/删掉它
-keep class com.example.oplusnotifsections.Hook { *; }

# LibXposed API 通过 META-INF/xposed/java_init.list 里的类名加载入口，同样必须保留
-keep class com.example.oplusnotifsections.LibXposedEntry { *; }

# 清单里引用的组件（R8 一般会自动保留，这里显式写上更稳）
-keep class com.example.oplusnotifsections.MainActivity { *; }
-keep class com.example.oplusnotifsections.App { *; }

# Xposed API 是 compileOnly（运行期由 LSPosed 提供），R8 解析不到这些类，别报错
-dontwarn de.robv.android.xposed.**

# LibXposed API 同样是 compileOnly
-dontwarn io.github.libxposed.**
