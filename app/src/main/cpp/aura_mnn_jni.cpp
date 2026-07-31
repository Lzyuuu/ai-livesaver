#include <jni.h>
#include <MNN/Interpreter.hpp>

#include <mutex>
#include <string>
#include <vector>

namespace {
struct Graph {
    MNN::Interpreter* interpreter = nullptr;
    MNN::Session* session = nullptr;
};

std::mutex mutex;
Graph detector, embedding, swapper, restore;

void release(Graph& graph) {
    if (graph.interpreter == nullptr) return;
    if (graph.session != nullptr) graph.interpreter->releaseSession(graph.session);
    graph.interpreter->releaseModel();
    delete graph.interpreter;
    graph = {};
}

std::string path(JNIEnv* env, jstring value, const char* role) {
    if (value == nullptr) return std::string(role) + ": path is null";
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return std::string(role) + ": unable to read path";
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string loadGraph(Graph& graph, const std::string& file, const char* role) {
    graph.interpreter = MNN::Interpreter::createFromFile(file.c_str());
    if (graph.interpreter == nullptr) return std::string(role) + ": createFromFile failed";
    MNN::ScheduleConfig config;
    config.type = MNN_FORWARD_CPU;
    config.numThread = 2;
    graph.session = graph.interpreter->createSession(config);
    if (graph.session == nullptr) {
        release(graph);
        return std::string(role) + ": createSession failed";
    }
    return {};
}

jfloatArray run(JNIEnv* env, Graph& graph, jfloatArray input, const std::vector<int>& shape, int expected, const char* role, jfloatArray second = nullptr) {
    if (graph.interpreter == nullptr || graph.session == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "MNN models are not loaded");
        return nullptr;
    }
    const jsize inputLength = env->GetArrayLength(input);
    if (inputLength != shape[0] * shape[1] * shape[2] * shape[3]) {
        std::string message = std::string(role) + ": input length mismatch";
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), message.c_str());
        return nullptr;
    }
    MNN::Tensor* deviceInput = graph.interpreter->getSessionInput(graph.session, nullptr);
    if (deviceInput == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "MNN input tensor is missing");
        return nullptr;
    }
    graph.interpreter->resizeTensor(deviceInput, shape);
    graph.interpreter->resizeSession(graph.session);
    std::vector<float> values(static_cast<size_t>(inputLength));
    env->GetFloatArrayRegion(input, 0, inputLength, values.data());
    std::unique_ptr<MNN::Tensor> hostInput(MNN::Tensor::create<float>(shape, values.data(), MNN::Tensor::CAFFE));
    if (!deviceInput->copyFromHostTensor(hostInput.get())) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "MNN input copy failed");
        return nullptr;
    }
    if (second != nullptr) {
        const auto& inputs = graph.interpreter->getSessionInputAll(graph.session);
        MNN::Tensor* latent = nullptr;
        for (const auto& item : inputs) if (item.second != deviceInput) { latent = item.second; break; }
        if (latent == nullptr || env->GetArrayLength(second) != 512) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "inswapper embedding input is missing");
            return nullptr;
        }
        float embedding[512];
        env->GetFloatArrayRegion(second, 0, 512, embedding);
        std::unique_ptr<MNN::Tensor> hostLatent(MNN::Tensor::create<float>({1, 512}, embedding, MNN::Tensor::CAFFE));
        if (!latent->copyFromHostTensor(hostLatent.get())) {
            env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "MNN embedding copy failed");
            return nullptr;
        }
    }
    if (graph.interpreter->runSession(graph.session) != MNN::NO_ERROR) {
        std::string message = std::string(role) + ": inference failed";
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
        return nullptr;
    }
    MNN::Tensor* deviceOutput = graph.interpreter->getSessionOutput(graph.session, nullptr);
    if (deviceOutput == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "MNN output tensor is missing");
        return nullptr;
    }
    std::unique_ptr<MNN::Tensor> hostOutput(new MNN::Tensor(deviceOutput, MNN::Tensor::CAFFE, true));
    if (!deviceOutput->copyToHostTensor(hostOutput.get()) || (expected > 0 && hostOutput->elementSize() != expected)) {
        std::string message = std::string(role) + ": unexpected output size";
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
        return nullptr;
    }
    const int outputSize = static_cast<int>(hostOutput->elementSize());
    auto* output = env->NewFloatArray(outputSize);
    env->SetFloatArrayRegion(output, 0, outputSize, hostOutput->host<float>());
    return output;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeLoad(
        JNIEnv* env, jclass, jstring detectPath, jstring embedPath, jstring swapPath,
        jstring restorePath, jboolean useGpu) {
    std::lock_guard<std::mutex> lock(mutex);
    release(detector); release(embedding); release(swapper); release(restore);
    const std::string paths[] = {path(env, detectPath, "SCRFD"), path(env, embedPath, "ArcFace"),
                                 path(env, swapPath, "inswapper"), path(env, restorePath, "CodeFormer")};
    if (useGpu) return env->NewStringUTF("GPU backend is not enabled in this build");
    Graph* graphs[] = {&detector, &embedding, &swapper, &restore};
    const char* roles[] = {"SCRFD", "ArcFace", "inswapper", "CodeFormer"};
    for (int i = 0; i < 4; ++i) {
        const std::string error = loadGraph(*graphs[i], paths[i], roles[i]);
        if (!error.empty()) {
            release(detector); release(embedding); release(swapper); release(restore);
            return env->NewStringUTF(error.c_str());
        }
    }
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeDetect(JNIEnv* env, jclass, jfloatArray input) {
    std::lock_guard<std::mutex> lock(mutex);
    return run(env, detector, input, {1, 3, 640, 640}, -1, "SCRFD");
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeEmbed(JNIEnv* env, jclass, jfloatArray input) {
    std::lock_guard<std::mutex> lock(mutex);
    return run(env, embedding, input, {1, 3, 112, 112}, 512, "ArcFace");
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeSwap(JNIEnv* env, jclass, jfloatArray input, jfloatArray embeddingInput) {
    (void) embeddingInput;
    std::lock_guard<std::mutex> lock(mutex);
    return run(env, swapper, input, {1, 3, 128, 128}, 49152, "inswapper", embeddingInput);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeRestore(JNIEnv* env, jclass, jfloatArray input, jfloat fidelity) {
    (void) fidelity;
    std::lock_guard<std::mutex> lock(mutex);
    return run(env, restore, input, {1, 3, 512, 512}, 786432, "CodeFormer");
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeLoadModels(
        JNIEnv* env, jclass clazz, jstring detectorPath, jstring embeddingPath, jstring swapperPath) {
    return Java_io_github_lzyuuu_ailivesaver_MnnNative_nativeLoad(env, clazz, detectorPath, embeddingPath, swapperPath, nullptr, JNI_FALSE);
}
