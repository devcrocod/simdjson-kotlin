#include <jni.h>
#include "simdjson_c.h"

#include <cstring>
#include <string>
#include <vector>

// ---------------------------------------------------------------------------
// UTF-8 → jstring helper (standard UTF-8 → UTF-16 via NewString)
// ---------------------------------------------------------------------------

static jstring utf8_to_jstring(JNIEnv* env, const char* str, size_t len) {
    if (!str || len == 0) {
        return env->NewString(nullptr, 0);
    }

    // len is a safe upper bound for UTF-16 code units:
    // 1-byte UTF-8 → 1 jchar, 2-byte → 1, 3-byte → 1, 4-byte → 2 (always ≤ input bytes)
    std::vector<jchar> buf(len);
    const auto* src = reinterpret_cast<const uint8_t*>(str);
    const auto* end = src + len;
    size_t pos = 0;

    while (src < end) {
        uint32_t cp;
        if (*src < 0x80) {
            cp = *src++;
        } else if ((*src & 0xE0) == 0xC0) {
            cp = (*src++ & 0x1F) << 6;
            cp |= (*src++ & 0x3F);
        } else if ((*src & 0xF0) == 0xE0) {
            cp = (*src++ & 0x0F) << 12;
            cp |= (*src++ & 0x3F) << 6;
            cp |= (*src++ & 0x3F);
        } else {
            cp = (*src++ & 0x07) << 18;
            cp |= (*src++ & 0x3F) << 12;
            cp |= (*src++ & 0x3F) << 6;
            cp |= (*src++ & 0x3F);
        }

        if (cp >= 0x10000) {
            cp -= 0x10000;
            buf[pos++] = static_cast<jchar>(0xD800 | (cp >> 10));
            buf[pos++] = static_cast<jchar>(0xDC00 | (cp & 0x3FF));
        } else {
            buf[pos++] = static_cast<jchar>(cp);
        }
    }

    return env->NewString(buf.data(), static_cast<jsize>(pos));
}

static jstring utf8_to_jstring(JNIEnv* env, const char* str) {
    if (!str) return nullptr;
    return utf8_to_jstring(env, str, strlen(str));
}

// ---------------------------------------------------------------------------
// JNI iterator wrapper structs
// ---------------------------------------------------------------------------

struct jni_object_iterator {
    simdjson_object_iterator_t iter;
    bool has_current;
    const char* current_key;
    size_t current_key_len;
    simdjson_value_t current_value;
};

struct jni_array_iterator {
    simdjson_array_iterator_t iter;
    bool has_current;
    simdjson_value_t current_value;
};

// ---------------------------------------------------------------------------
// Exception helpers
// ---------------------------------------------------------------------------

static void throw_simdjson_exception(JNIEnv* env, simdjson_error_code code) {
    const char* msg = simdjson_error_message(code);
    if (!msg) msg = "Unknown simdjson error";

    const char* class_name;
    switch (code) {
        case SIMDJSON_UTF8_ERROR:
            class_name = "io/github/devcrocod/simdjson/JsonEncodingException";
            break;
        case SIMDJSON_INCORRECT_TYPE:
            class_name = "io/github/devcrocod/simdjson/JsonParsingException";
            break;
        case SIMDJSON_OUT_OF_ORDER_ITERATION:
        case SIMDJSON_SCALAR_DOCUMENT_AS_VALUE:
            class_name = "io/github/devcrocod/simdjson/JsonIterationException";
            break;
        default:
            class_name = "io/github/devcrocod/simdjson/JsonParsingException";
            break;
    }

    jclass cls = env->FindClass(class_name);
    if (cls) {
        env->ThrowNew(cls, msg);
    }
}

#define CHECK_ERROR(env, code)        \
    do {                              \
        if ((code) != SIMDJSON_OK) {  \
            throw_simdjson_exception((env), (code)); \
            return 0;                 \
        }                            \
    } while (0)

#define CHECK_ERROR_VOID(env, code)   \
    do {                              \
        if ((code) != SIMDJSON_OK) {  \
            throw_simdjson_exception((env), (code)); \
            return;                   \
        }                            \
    } while (0)

// ---------------------------------------------------------------------------
// Parser lifecycle
// ---------------------------------------------------------------------------

static jlong jni_parser_create(JNIEnv* env, jclass, jlong capacity, jlong maxDepth) {
    simdjson_parser_t parser = simdjson_parser_create(
        static_cast<size_t>(capacity),
        static_cast<size_t>(maxDepth));
    if (!parser) {
        jclass cls = env->FindClass("io/github/devcrocod/simdjson/JsonParsingException");
        if (cls) env->ThrowNew(cls, "Failed to allocate native parser");
        return 0;
    }
    return reinterpret_cast<jlong>(parser);
}

static void jni_parser_destroy(JNIEnv*, jclass, jlong handle) {
    if (handle != 0) {
        simdjson_parser_destroy(reinterpret_cast<simdjson_parser_t>(handle));
    }
}

// ---------------------------------------------------------------------------
// Parsing
// ---------------------------------------------------------------------------

static jlong jni_iterate(JNIEnv* env, jclass, jlong parser_handle, jbyteArray data, jint length) {
    auto parser = reinterpret_cast<simdjson_parser_t>(parser_handle);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) return 0;

    simdjson_document_t doc = nullptr;
    size_t error_offset = SIZE_MAX;
    simdjson_error_code err = simdjson_iterate(
        parser,
        reinterpret_cast<const uint8_t*>(bytes),
        static_cast<size_t>(length),
        &doc,
        &error_offset
    );

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    if (err != SIMDJSON_OK) {
        const char* msg = simdjson_error_message(err);
        if (!msg) msg = "Unknown simdjson error";

        const char* class_name;
        switch (err) {
            case SIMDJSON_UTF8_ERROR:
                class_name = "io/github/devcrocod/simdjson/JsonEncodingException";
                break;
            case SIMDJSON_OUT_OF_ORDER_ITERATION:
            case SIMDJSON_SCALAR_DOCUMENT_AS_VALUE:
                class_name = "io/github/devcrocod/simdjson/JsonIterationException";
                break;
            default:
                class_name = "io/github/devcrocod/simdjson/JsonParsingException";
                break;
        }

        jclass cls = env->FindClass(class_name);
        if (cls) {
            if (err != SIMDJSON_UTF8_ERROR
                && err != SIMDJSON_OUT_OF_ORDER_ITERATION
                && err != SIMDJSON_SCALAR_DOCUMENT_AS_VALUE
                && error_offset != SIZE_MAX) {
                jmethodID ctor = env->GetMethodID(cls, "<init>",
                    "(Ljava/lang/String;JLjava/lang/Throwable;)V");
                if (ctor) {
                    jstring jmsg = utf8_to_jstring(env, msg);
                    jthrowable ex = static_cast<jthrowable>(
                        env->NewObject(cls, ctor, jmsg, static_cast<jlong>(error_offset), nullptr));
                    if (ex) env->Throw(ex);
                } else {
                    env->ThrowNew(cls, msg);
                }
            } else {
                env->ThrowNew(cls, msg);
            }
        }
        return 0;
    }
    return reinterpret_cast<jlong>(doc);
}

static jlong jni_dom_parse(JNIEnv* env, jclass, jlong parser_handle, jbyteArray data, jint length) {
    auto parser = reinterpret_cast<simdjson_parser_t>(parser_handle);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) return 0;

    simdjson_document_t doc = nullptr;
    size_t error_offset = SIZE_MAX;
    simdjson_error_code err = simdjson_dom_parse(
        parser,
        reinterpret_cast<const uint8_t*>(bytes),
        static_cast<size_t>(length),
        &doc,
        &error_offset
    );

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    if (err != SIMDJSON_OK) {
        const char* msg = simdjson_error_message(err);
        if (!msg) msg = "Unknown simdjson error";

        const char* class_name;
        switch (err) {
            case SIMDJSON_UTF8_ERROR:
                class_name = "io/github/devcrocod/simdjson/JsonEncodingException";
                break;
            case SIMDJSON_OUT_OF_ORDER_ITERATION:
            case SIMDJSON_SCALAR_DOCUMENT_AS_VALUE:
                class_name = "io/github/devcrocod/simdjson/JsonIterationException";
                break;
            default:
                class_name = "io/github/devcrocod/simdjson/JsonParsingException";
                break;
        }

        jclass cls = env->FindClass(class_name);
        if (cls) {
            if (err != SIMDJSON_UTF8_ERROR
                && err != SIMDJSON_OUT_OF_ORDER_ITERATION
                && err != SIMDJSON_SCALAR_DOCUMENT_AS_VALUE
                && error_offset != SIZE_MAX) {
                jmethodID ctor = env->GetMethodID(cls, "<init>",
                    "(Ljava/lang/String;JLjava/lang/Throwable;)V");
                if (ctor) {
                    jstring jmsg = utf8_to_jstring(env, msg);
                    jthrowable ex = static_cast<jthrowable>(
                        env->NewObject(cls, ctor, jmsg, static_cast<jlong>(error_offset), nullptr));
                    if (ex) env->Throw(ex);
                } else {
                    env->ThrowNew(cls, msg);
                }
            } else {
                env->ThrowNew(cls, msg);
            }
        }
        return 0;
    }
    return reinterpret_cast<jlong>(doc);
}

// ---------------------------------------------------------------------------
// Document
// ---------------------------------------------------------------------------

static void jni_document_destroy(JNIEnv*, jclass, jlong handle) {
    if (handle != 0) {
        simdjson_document_destroy(reinterpret_cast<simdjson_document_t>(handle));
    }
}

static jlong jni_doc_get_object(JNIEnv* env, jclass, jlong handle) {
    simdjson_object_t obj = nullptr;
    CHECK_ERROR(env, simdjson_doc_get_object(reinterpret_cast<simdjson_document_t>(handle), &obj));
    return reinterpret_cast<jlong>(obj);
}

static jlong jni_doc_get_array(JNIEnv* env, jclass, jlong handle) {
    simdjson_array_t arr = nullptr;
    CHECK_ERROR(env, simdjson_doc_get_array(reinterpret_cast<simdjson_document_t>(handle), &arr));
    return reinterpret_cast<jlong>(arr);
}

static jstring jni_doc_get_string(JNIEnv* env, jclass, jlong handle) {
    const char* str = nullptr;
    size_t len = 0;
    simdjson_error_code err = simdjson_doc_get_string(
        reinterpret_cast<simdjson_document_t>(handle), &str, &len);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return nullptr;
    }
    return utf8_to_jstring(env, str, len);
}

static jlong jni_doc_get_int64(JNIEnv* env, jclass, jlong handle) {
    int64_t out = 0;
    CHECK_ERROR(env, simdjson_doc_get_int64(reinterpret_cast<simdjson_document_t>(handle), &out));
    return static_cast<jlong>(out);
}

static jlong jni_doc_get_uint64(JNIEnv* env, jclass, jlong handle) {
    uint64_t out = 0;
    CHECK_ERROR(env, simdjson_doc_get_uint64(reinterpret_cast<simdjson_document_t>(handle), &out));
    return static_cast<jlong>(out);
}

static jdouble jni_doc_get_double(JNIEnv* env, jclass, jlong handle) {
    double out = 0.0;
    simdjson_error_code err = simdjson_doc_get_double(
        reinterpret_cast<simdjson_document_t>(handle), &out);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return 0.0;
    }
    return static_cast<jdouble>(out);
}

static jboolean jni_doc_get_bool(JNIEnv* env, jclass, jlong handle) {
    bool out = false;
    simdjson_error_code err = simdjson_doc_get_bool(
        reinterpret_cast<simdjson_document_t>(handle), &out);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return JNI_FALSE;
    }
    return out ? JNI_TRUE : JNI_FALSE;
}

static jboolean jni_doc_is_null(JNIEnv* env, jclass, jlong handle) {
    bool out = false;
    simdjson_error_code err = simdjson_doc_is_null(
        reinterpret_cast<simdjson_document_t>(handle), &out);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return JNI_FALSE;
    }
    return out ? JNI_TRUE : JNI_FALSE;
}

static jint jni_doc_get_type(JNIEnv* env, jclass, jlong handle) {
    simdjson_value_type type = SIMDJSON_TYPE_NULL;
    simdjson_error_code err = simdjson_doc_get_type(
        reinterpret_cast<simdjson_document_t>(handle), &type);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return 0;
    }
    return static_cast<jint>(type);
}

// ---------------------------------------------------------------------------
// Object
// ---------------------------------------------------------------------------

static jlong jni_object_find_field(JNIEnv* env, jclass, jlong handle, jstring key) {
    auto obj = reinterpret_cast<simdjson_object_t>(handle);
    const char* key_chars = env->GetStringUTFChars(key, nullptr);
    if (!key_chars) return 0;
    size_t key_len = static_cast<size_t>(env->GetStringUTFLength(key));

    simdjson_value_t val = nullptr;
    simdjson_error_code err = simdjson_object_find_field(obj, key_chars, key_len, &val);

    env->ReleaseStringUTFChars(key, key_chars);
    CHECK_ERROR(env, err);
    return reinterpret_cast<jlong>(val);
}

static jlong jni_object_find_field_unordered(JNIEnv* env, jclass, jlong handle, jstring key) {
    auto obj = reinterpret_cast<simdjson_object_t>(handle);
    const char* key_chars = env->GetStringUTFChars(key, nullptr);
    if (!key_chars) return 0;
    size_t key_len = static_cast<size_t>(env->GetStringUTFLength(key));

    simdjson_value_t val = nullptr;
    simdjson_error_code err = simdjson_object_find_field_unordered(obj, key_chars, key_len, &val);

    env->ReleaseStringUTFChars(key, key_chars);
    CHECK_ERROR(env, err);
    return reinterpret_cast<jlong>(val);
}

static jlong jni_object_iterator_create(JNIEnv* env, jclass, jlong handle) {
    auto obj = reinterpret_cast<simdjson_object_t>(handle);
    simdjson_object_iterator_t raw_iter = nullptr;
    simdjson_error_code err = simdjson_object_iterator_create(obj, &raw_iter);
    CHECK_ERROR(env, err);

    auto* wrapper = new jni_object_iterator();
    wrapper->iter = raw_iter;
    wrapper->has_current = false;
    wrapper->current_key = nullptr;
    wrapper->current_key_len = 0;
    wrapper->current_value = nullptr;
    return reinterpret_cast<jlong>(wrapper);
}

static jboolean jni_object_iterator_advance(JNIEnv* env, jclass, jlong handle) {
    auto* wrapper = reinterpret_cast<jni_object_iterator*>(handle);
    bool has_next = false;
    const char* key = nullptr;
    size_t key_len = 0;
    simdjson_value_t val = nullptr;

    simdjson_error_code err = simdjson_object_iterator_next(
        wrapper->iter, &has_next, &key, &key_len, &val);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return JNI_FALSE;
    }

    wrapper->has_current = has_next;
    wrapper->current_key = key;
    wrapper->current_key_len = key_len;
    wrapper->current_value = val;
    return has_next ? JNI_TRUE : JNI_FALSE;
}

static jstring jni_object_iterator_key(JNIEnv* env, jclass, jlong handle) {
    auto* wrapper = reinterpret_cast<jni_object_iterator*>(handle);
    if (!wrapper->has_current || !wrapper->current_key) return nullptr;
    return utf8_to_jstring(env, wrapper->current_key, wrapper->current_key_len);
}

static jlong jni_object_iterator_value(JNIEnv*, jclass, jlong handle) {
    auto* wrapper = reinterpret_cast<jni_object_iterator*>(handle);
    if (!wrapper->has_current) return 0;
    return reinterpret_cast<jlong>(wrapper->current_value);
}

static void jni_object_iterator_destroy(JNIEnv*, jclass, jlong handle) {
    if (handle != 0) {
        auto* wrapper = reinterpret_cast<jni_object_iterator*>(handle);
        if (wrapper->iter) {
            simdjson_object_iterator_destroy(wrapper->iter);
        }
        delete wrapper;
    }
}

static void jni_object_destroy(JNIEnv*, jclass, jlong handle) {
    if (handle != 0) {
        simdjson_object_destroy(reinterpret_cast<simdjson_object_t>(handle));
    }
}

// ---------------------------------------------------------------------------
// Array
// ---------------------------------------------------------------------------

static jlong jni_array_iterator_create(JNIEnv* env, jclass, jlong handle) {
    auto arr = reinterpret_cast<simdjson_array_t>(handle);
    simdjson_array_iterator_t raw_iter = nullptr;
    simdjson_error_code err = simdjson_array_iterator_create(arr, &raw_iter);
    CHECK_ERROR(env, err);

    auto* wrapper = new jni_array_iterator();
    wrapper->iter = raw_iter;
    wrapper->has_current = false;
    wrapper->current_value = nullptr;
    return reinterpret_cast<jlong>(wrapper);
}

static jboolean jni_array_iterator_advance(JNIEnv* env, jclass, jlong handle) {
    auto* wrapper = reinterpret_cast<jni_array_iterator*>(handle);
    bool has_next = false;
    simdjson_value_t val = nullptr;

    simdjson_error_code err = simdjson_array_iterator_next(wrapper->iter, &has_next, &val);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return JNI_FALSE;
    }

    wrapper->has_current = has_next;
    wrapper->current_value = val;
    return has_next ? JNI_TRUE : JNI_FALSE;
}

static jlong jni_array_iterator_value(JNIEnv*, jclass, jlong handle) {
    auto* wrapper = reinterpret_cast<jni_array_iterator*>(handle);
    if (!wrapper->has_current) return 0;
    return reinterpret_cast<jlong>(wrapper->current_value);
}

static void jni_array_iterator_destroy(JNIEnv*, jclass, jlong handle) {
    if (handle != 0) {
        auto* wrapper = reinterpret_cast<jni_array_iterator*>(handle);
        if (wrapper->iter) {
            simdjson_array_iterator_destroy(wrapper->iter);
        }
        delete wrapper;
    }
}

static void jni_array_destroy(JNIEnv*, jclass, jlong handle) {
    if (handle != 0) {
        simdjson_array_destroy(reinterpret_cast<simdjson_array_t>(handle));
    }
}

// ---------------------------------------------------------------------------
// Value
// ---------------------------------------------------------------------------

static jlong jni_value_get_object(JNIEnv* env, jclass, jlong handle) {
    simdjson_object_t obj = nullptr;
    CHECK_ERROR(env, simdjson_value_get_object(reinterpret_cast<simdjson_value_t>(handle), &obj));
    return reinterpret_cast<jlong>(obj);
}

static jlong jni_value_get_array(JNIEnv* env, jclass, jlong handle) {
    simdjson_array_t arr = nullptr;
    CHECK_ERROR(env, simdjson_value_get_array(reinterpret_cast<simdjson_value_t>(handle), &arr));
    return reinterpret_cast<jlong>(arr);
}

static jstring jni_value_get_string(JNIEnv* env, jclass, jlong handle) {
    const char* str = nullptr;
    size_t len = 0;
    simdjson_error_code err = simdjson_value_get_string(
        reinterpret_cast<simdjson_value_t>(handle), &str, &len);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return nullptr;
    }
    return utf8_to_jstring(env, str, len);
}

static jlong jni_value_get_int64(JNIEnv* env, jclass, jlong handle) {
    int64_t out = 0;
    CHECK_ERROR(env, simdjson_value_get_int64(reinterpret_cast<simdjson_value_t>(handle), &out));
    return static_cast<jlong>(out);
}

static jlong jni_value_get_uint64(JNIEnv* env, jclass, jlong handle) {
    uint64_t out = 0;
    CHECK_ERROR(env, simdjson_value_get_uint64(reinterpret_cast<simdjson_value_t>(handle), &out));
    return static_cast<jlong>(out);
}

static jdouble jni_value_get_double(JNIEnv* env, jclass, jlong handle) {
    double out = 0.0;
    simdjson_error_code err = simdjson_value_get_double(
        reinterpret_cast<simdjson_value_t>(handle), &out);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return 0.0;
    }
    return static_cast<jdouble>(out);
}

static jboolean jni_value_get_bool(JNIEnv* env, jclass, jlong handle) {
    bool out = false;
    simdjson_error_code err = simdjson_value_get_bool(
        reinterpret_cast<simdjson_value_t>(handle), &out);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return JNI_FALSE;
    }
    return out ? JNI_TRUE : JNI_FALSE;
}

static jboolean jni_value_is_null(JNIEnv* env, jclass, jlong handle) {
    bool out = false;
    simdjson_error_code err = simdjson_value_is_null(
        reinterpret_cast<simdjson_value_t>(handle), &out);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return JNI_FALSE;
    }
    return out ? JNI_TRUE : JNI_FALSE;
}

static jint jni_value_get_type(JNIEnv* env, jclass, jlong handle) {
    simdjson_value_type type = SIMDJSON_TYPE_NULL;
    simdjson_error_code err = simdjson_value_get_type(
        reinterpret_cast<simdjson_value_t>(handle), &type);
    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return 0;
    }
    return static_cast<jint>(type);
}

static void jni_value_destroy(JNIEnv*, jclass, jlong handle) {
    if (handle != 0) {
        simdjson_value_destroy(reinterpret_cast<simdjson_value_t>(handle));
    }
}

// ---------------------------------------------------------------------------
// Error utilities
// ---------------------------------------------------------------------------

static jstring jni_error_message(JNIEnv* env, jclass, jint code) {
    const char* msg = simdjson_error_message(static_cast<simdjson_error_code>(code));
    return msg ? utf8_to_jstring(env, msg) : nullptr;
}

// ---------------------------------------------------------------------------
// Stage 1: structural indexing
// ---------------------------------------------------------------------------

static jintArray jni_get_structural_indices(JNIEnv* env, jclass, jlong parserHandle, jbyteArray data, jint length) {
    simdjson_parser_t parser = reinterpret_cast<simdjson_parser_t>(parserHandle);

    jbyte* buf = env->GetByteArrayElements(data, nullptr);
    if (!buf) return nullptr;

    const uint32_t* indices = nullptr;
    size_t count = 0;
    simdjson_error_code err = simdjson_stage1(
        parser,
        reinterpret_cast<const uint8_t*>(buf), static_cast<size_t>(length),
        &indices, &count);

    env->ReleaseByteArrayElements(data, buf, JNI_ABORT);

    if (err != SIMDJSON_OK) {
        throw_simdjson_exception(env, err);
        return nullptr;
    }

    jintArray result = env->NewIntArray(static_cast<jsize>(count));
    if (!result) return nullptr;

    // uint32_t and jint are both 32-bit — safe to copy directly
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(count),
                           reinterpret_cast<const jint*>(indices));
    return result;
}

// ---------------------------------------------------------------------------
// JNI_OnLoad — register all native methods
// ---------------------------------------------------------------------------

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass cls = env->FindClass("io/github/devcrocod/simdjson/SimdjsonJni");
    if (cls == nullptr) return JNI_ERR;

    static const JNINativeMethod methods[] = {
        // Parser lifecycle
        {const_cast<char*>("nativeParserCreate"),              const_cast<char*>("(JJ)J"),                 reinterpret_cast<void*>(jni_parser_create)},
        {const_cast<char*>("nativeParserDestroy"),             const_cast<char*>("(J)V"),                  reinterpret_cast<void*>(jni_parser_destroy)},

        // Parsing
        {const_cast<char*>("nativeIterate"),                   const_cast<char*>("(J[BI)J"),               reinterpret_cast<void*>(jni_iterate)},
        {const_cast<char*>("nativeDomParse"),                  const_cast<char*>("(J[BI)J"),               reinterpret_cast<void*>(jni_dom_parse)},

        // Stage 1
        {const_cast<char*>("nativeGetStructuralIndices"), const_cast<char*>("(J[BI)[I"), reinterpret_cast<void*>(jni_get_structural_indices)},

        // Document
        {const_cast<char*>("nativeDocumentDestroy"),           const_cast<char*>("(J)V"),                  reinterpret_cast<void*>(jni_document_destroy)},
        {const_cast<char*>("nativeDocGetObject"),              const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_doc_get_object)},
        {const_cast<char*>("nativeDocGetArray"),               const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_doc_get_array)},
        {const_cast<char*>("nativeDocGetString"),              const_cast<char*>("(J)Ljava/lang/String;"), reinterpret_cast<void*>(jni_doc_get_string)},
        {const_cast<char*>("nativeDocGetInt64"),               const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_doc_get_int64)},
        {const_cast<char*>("nativeDocGetUint64"),              const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_doc_get_uint64)},
        {const_cast<char*>("nativeDocGetDouble"),              const_cast<char*>("(J)D"),                  reinterpret_cast<void*>(jni_doc_get_double)},
        {const_cast<char*>("nativeDocGetBool"),                const_cast<char*>("(J)Z"),                  reinterpret_cast<void*>(jni_doc_get_bool)},
        {const_cast<char*>("nativeDocIsNull"),                 const_cast<char*>("(J)Z"),                  reinterpret_cast<void*>(jni_doc_is_null)},
        {const_cast<char*>("nativeDocGetType"),                const_cast<char*>("(J)I"),                  reinterpret_cast<void*>(jni_doc_get_type)},

        // Object
        {const_cast<char*>("nativeObjectFindField"),           const_cast<char*>("(JLjava/lang/String;)J"), reinterpret_cast<void*>(jni_object_find_field)},
        {const_cast<char*>("nativeObjectFindFieldUnordered"),  const_cast<char*>("(JLjava/lang/String;)J"), reinterpret_cast<void*>(jni_object_find_field_unordered)},
        {const_cast<char*>("nativeObjectIteratorCreate"),      const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_object_iterator_create)},
        {const_cast<char*>("nativeObjectIteratorAdvance"),     const_cast<char*>("(J)Z"),                  reinterpret_cast<void*>(jni_object_iterator_advance)},
        {const_cast<char*>("nativeObjectIteratorKey"),         const_cast<char*>("(J)Ljava/lang/String;"), reinterpret_cast<void*>(jni_object_iterator_key)},
        {const_cast<char*>("nativeObjectIteratorValue"),       const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_object_iterator_value)},
        {const_cast<char*>("nativeObjectIteratorDestroy"),     const_cast<char*>("(J)V"),                  reinterpret_cast<void*>(jni_object_iterator_destroy)},
        {const_cast<char*>("nativeObjectDestroy"),             const_cast<char*>("(J)V"),                  reinterpret_cast<void*>(jni_object_destroy)},

        // Array
        {const_cast<char*>("nativeArrayIteratorCreate"),       const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_array_iterator_create)},
        {const_cast<char*>("nativeArrayIteratorAdvance"),      const_cast<char*>("(J)Z"),                  reinterpret_cast<void*>(jni_array_iterator_advance)},
        {const_cast<char*>("nativeArrayIteratorValue"),        const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_array_iterator_value)},
        {const_cast<char*>("nativeArrayIteratorDestroy"),      const_cast<char*>("(J)V"),                  reinterpret_cast<void*>(jni_array_iterator_destroy)},
        {const_cast<char*>("nativeArrayDestroy"),              const_cast<char*>("(J)V"),                  reinterpret_cast<void*>(jni_array_destroy)},

        // Value
        {const_cast<char*>("nativeValueGetObject"),            const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_value_get_object)},
        {const_cast<char*>("nativeValueGetArray"),             const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_value_get_array)},
        {const_cast<char*>("nativeValueGetString"),            const_cast<char*>("(J)Ljava/lang/String;"), reinterpret_cast<void*>(jni_value_get_string)},
        {const_cast<char*>("nativeValueGetInt64"),             const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_value_get_int64)},
        {const_cast<char*>("nativeValueGetUint64"),            const_cast<char*>("(J)J"),                  reinterpret_cast<void*>(jni_value_get_uint64)},
        {const_cast<char*>("nativeValueGetDouble"),            const_cast<char*>("(J)D"),                  reinterpret_cast<void*>(jni_value_get_double)},
        {const_cast<char*>("nativeValueGetBool"),              const_cast<char*>("(J)Z"),                  reinterpret_cast<void*>(jni_value_get_bool)},
        {const_cast<char*>("nativeValueIsNull"),               const_cast<char*>("(J)Z"),                  reinterpret_cast<void*>(jni_value_is_null)},
        {const_cast<char*>("nativeValueGetType"),              const_cast<char*>("(J)I"),                  reinterpret_cast<void*>(jni_value_get_type)},
        {const_cast<char*>("nativeValueDestroy"),              const_cast<char*>("(J)V"),                  reinterpret_cast<void*>(jni_value_destroy)},

        // Error
        {const_cast<char*>("nativeErrorMessage"),              const_cast<char*>("(I)Ljava/lang/String;"), reinterpret_cast<void*>(jni_error_message)},
    };

    if (env->RegisterNatives(cls, methods, sizeof(methods) / sizeof(methods[0])) < 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
