# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.server.** { *; }
-keepclassmembers class * {
    public <init>(android.content.Context);
}

# UserService (ComponentName で固定参照)
-keep class net.ogatomo.developerOptions.shizuku.ShellUserService { *; }
-keep class net.ogatomo.developerOptions.IShellCommandService { *; }
-keep class net.ogatomo.developerOptions.IShellCommandService$Stub { *; }
-keep class net.ogatomo.developerOptions.IShellCommandService$Stub$Proxy { *; }

# Reflection targets (WRITE_SECURE_SETTINGS grant)
-keep class android.content.pm.IPackageManager { *; }
-keep class android.content.pm.IPackageManager$Stub { *; }

# Hidden API bypass
-keep class org.lsposed.hiddenapibypass.** { *; }