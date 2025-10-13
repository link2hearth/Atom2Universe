# Add project specific ProGuard rules here.
# By default, the flags in this file are applied to duplicates.
# You can tell the Android Gradle plugin to apply these rules to
# your test APKs by adding the following in your build.gradle file:
#
# android {
#     buildTypes {
#         debug {
#             proguardFiles getDefaultProguardFile('proguard-android.txt'),
#                     'proguard-rules.pro'
#         }
#     }
# }
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If you use reflection, typically to load classes by name,
# you need to keep those classes.
#-keep public class com.google.vending.licensing.ILicensingService
#-keep public class com.android.vending.licensing.ILicensingService

# We suggest to keep all public classes whose names end with "Service".
# e.g. com.example.MyService
#-keep public class * extends android.app.Service
