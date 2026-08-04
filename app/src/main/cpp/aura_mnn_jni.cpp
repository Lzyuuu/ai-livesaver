#include <dlfcn.h>
#include <jni.h>

#include <string>

namespace {
using Load = jboolean (*)(JNIEnv*, jclass, jstring, jstring, jstring, jstring, jboolean);
using Detect = jfloatArray (*)(JNIEnv*, jclass, jfloatArray);
using Embed = jfloatArray (*)(JNIEnv*, jclass, jfloatArray);
using Swap = jfloatArray (*)(JNIEnv*, jclass, jfloatArray, jfloatArray);
using Restore = jfloatArray (*)(JNIEnv*, jclass, jfloatArray, jfloat);
using Unload = void (*)(JNIEnv*, jclass);

template <typename T>
T symbol(const char* name) {
    static void* handle = dlopen("libfancysd.so", RTLD_NOW | RTLD_GLOBAL);
    return handle == nullptr ? nullptr : reinterpret_cast<T>(dlsym(handle, name));
}

bool ready(JNIEnv* env) {
    static const char* required[] = {
        "Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeLoad",
        "Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeDetect",
        "Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeEmbed",
        "Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeSwap",
        "Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeRestore",
        "Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeUnload",
    };
    for (const char* name : required) {
        if (symbol<void*>(name) == nullptr) {
            env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),
                          (std::string("fancysd symbol missing: ") + name).c_str());
            return false;
        }
    }
    return true;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeLoad(
        JNIEnv* env, jclass clazz, jstring detect, jstring embed, jstring swap,
        jstring restore, jboolean useGpu) {
    if (!ready(env)) return nullptr;
    if (useGpu) return env->NewStringUTF("GPU backend is not enabled in this build");
    const auto load = symbol<Load>("Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeLoad");
    if (!load(env, clazz, detect, embed, swap, restore, JNI_FALSE)) {
        return env->NewStringUTF("fancysd nativeLoad failed");
    }
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeDetect(JNIEnv* env, jclass clazz, jfloatArray input) {
    if (!ready(env)) return nullptr;
    return symbol<Detect>("Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeDetect")(env, clazz, input);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeEmbed(JNIEnv* env, jclass clazz, jfloatArray input) {
    if (!ready(env)) return nullptr;
    return symbol<Embed>("Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeEmbed")(env, clazz, input);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeSwap(JNIEnv* env, jclass clazz, jfloatArray input, jfloatArray embedding) {
    if (!ready(env)) return nullptr;
    return symbol<Swap>("Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeSwap")(env, clazz, input, embedding);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeRestore(JNIEnv* env, jclass clazz, jfloatArray input, jfloat fidelity) {
    if (!ready(env)) return nullptr;
    return symbol<Restore>("Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeRestore")(env, clazz, input, fidelity);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeUnload(JNIEnv* env, jclass clazz) {
    if (!ready(env)) return;
    symbol<Unload>("Java_com_mrj_fancyai_sd_face_swap_MnnFaceSwap_nativeUnload")(env, clazz);
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeLoadModels(
        JNIEnv* env, jclass clazz, jstring detector, jstring embedding, jstring swapper) {
    (void) clazz; (void) detector; (void) embedding; (void) swapper;
    return env->NewStringUTF("SCRFD: four model paths are required");
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeLoadWithoutRestore(
        JNIEnv* env, jclass clazz, jstring detector, jstring embedding, jstring swapper) {
    return Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeLoad(
            env, clazz, detector, embedding, swapper, nullptr, JNI_FALSE);
}
