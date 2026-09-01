# Add project specific ProGuard rules here.

# lz4-java loads the XXHash implementation through reflection at runtime.
-keep class net.jpountz.xxhash.** { *; }
