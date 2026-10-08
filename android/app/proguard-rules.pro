# Native Go bridge uses class and method names through JNI; shrinking those breaks startup.
-keep class go.** { *; }
-keep class com.hiddify.core.** { *; }
-keep interface com.hiddify.core.** { *; }
-keep class com.hiddify.hiddify.bg.** { *; }
# Wire RPC interfaces/adapters and Gson generic type information are reflection inputs.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
# gomobile's ABI is not described by Java bytecode alone.
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
