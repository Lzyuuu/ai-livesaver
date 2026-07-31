#include <jni.h>
#include <MNN/Interpreter.hpp>

#include <string>

namespace {
std::string load(const char* role, jstring path, JNIEnv* env) {
    if (path == nullptr) return std::string(role) + ": path is null";
    const char* value = env->GetStringUTFChars(path, nullptr);
    if (value == nullptr) return std::string(role) + ": unable to read path";
    MNN::Interpreter* interpreter = MNN::Interpreter::createFromFile(value);
    env->ReleaseStringUTFChars(path, value);
    if (interpreter == nullptr) {
        return std::string(role) + ": MNN::Interpreter::createFromFile failed";
    }
    MNN::ScheduleConfig config;
    MNN::Session* session = interpreter->createSession(config);
    if (session == nullptr) {
        interpreter->releaseModel();
        delete interpreter;
        return std::string(role) + ": MNN::Interpreter::createSession failed";
    }
    interpreter->releaseSession(session);
    interpreter->releaseModel();
    delete interpreter;
    return {};
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeLoadModels(
        JNIEnv* env, jclass, jstring detector, jstring embedding, jstring swapper) {
    for (const auto& model : {
            std::pair<const char*, jstring>{"SCRFD detector", detector},
            std::pair<const char*, jstring>{"ArcFace embedding", embedding},
            std::pair<const char*, jstring>{"inswapper", swapper}}) {
        const std::string error = load(model.first, model.second, env);
        if (!error.empty()) return env->NewStringUTF(error.c_str());
    }
    return env->NewStringUTF("");
}
