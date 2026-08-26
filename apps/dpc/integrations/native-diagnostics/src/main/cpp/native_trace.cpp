#include <jni.h>
#include <android/log.h>
#include <time.h>
#include <unistd.h>

static jlong monotonic_now() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<jlong>(ts.tv_sec) * 1000000000LL + static_cast<jlong>(ts.tv_nsec);
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_dpcaio_nativebridge_NativeTraceBridge_monotonicNanos(JNIEnv*, jobject) {
    return monotonic_now();
}

extern "C" JNIEXPORT jint JNICALL
Java_io_dpcaio_nativebridge_NativeTraceBridge_pageSize(JNIEnv*, jobject) {
    return static_cast<jint>(sysconf(_SC_PAGESIZE));
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_dpcaio_nativebridge_NativeTraceBridge_traceMarker(JNIEnv* env, jobject, jstring label) {
    const char* text = label ? env->GetStringUTFChars(label, nullptr) : nullptr;
    if (text) {
        __android_log_print(ANDROID_LOG_INFO, "DPC-AIO-Native", "traceMarker: %s", text);
        env->ReleaseStringUTFChars(label, text);
    }
    return monotonic_now();
}
