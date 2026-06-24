data class JniTarget(
    val os: String,
    val arch: String,
    val libFileName: String,
) {
    val artifactSuffix: String get() = "$os-$arch"
    val resourceDir: String get() = "natives/$os-$arch"
}

val JNI_TARGETS = listOf(
    JniTarget(os = "macos", arch = "arm64", libFileName = "libsimdjson_jni.dylib"),
    JniTarget(os = "linux", arch = "x64", libFileName = "libsimdjson_jni.so"),
    JniTarget(os = "linux", arch = "arm64", libFileName = "libsimdjson_jni.so"),
    JniTarget(os = "windows", arch = "x64", libFileName = "simdjson_jni.dll"),
)
