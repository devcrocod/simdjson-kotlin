data class JniTarget(
    val os: String,
    val arch: String,
    val artifactOs: String,
    val artifactArch: String,
    val libFileName: String,
) {
    val artifactSuffix: String get() = "$artifactOs-$artifactArch"
    val resourceDir: String get() = "natives/$os-$arch"
}

val JNI_TARGETS = listOf(
    JniTarget(os = "macos", arch = "aarch64", artifactOs = "macos", artifactArch = "arm64", libFileName = "libsimdjson_jni.dylib"),
    JniTarget(os = "macos", arch = "x86_64", artifactOs = "macos", artifactArch = "x64", libFileName = "libsimdjson_jni.dylib"),
    JniTarget(os = "linux", arch = "x86_64", artifactOs = "linux", artifactArch = "x64", libFileName = "libsimdjson_jni.so"),
    JniTarget(os = "linux", arch = "aarch64", artifactOs = "linux", artifactArch = "arm64", libFileName = "libsimdjson_jni.so"),
    JniTarget(os = "windows", arch = "x86_64", artifactOs = "windows", artifactArch = "x64", libFileName = "simdjson_jni.dll"),
)
