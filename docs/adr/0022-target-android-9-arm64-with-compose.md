# 面向 Android 9 以上的 ARM64 设备

主应用使用 Kotlin、Jetpack Compose 和 Material Design 3，最低支持 Android 9（API 28），只发布 `arm64-v8a` APK，并优先设计手机竖屏体验。该基线与 Local Dream 当前的最低 SDK 和 ABI 对齐，避免为无法运行伴生生图引擎的旧设备与 32 位架构增加兼容负担。
