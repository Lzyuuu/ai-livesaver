#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "LlamaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct LlamaSession {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    ~LlamaSession() {
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
    }
};

// Gemma it 对话模板（V1 手工拼接；SE-03 接入后由模型元数据驱动）。
std::string wrapGemmaPrompt(const std::string &userPrompt) {
    return "<start_of_turn>user\n" + userPrompt + "<end_of_turn>\n<start_of_turn>model\n";
}

jstring toJString(JNIEnv *env, const std::string &s) {
    return env->NewStringUTF(s.c_str());
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_lzyuuu_ailivesaver_LlamaNative_nativeLoadModel(
        JNIEnv *env, jobject, jstring jPath, jint nCtx, jint nThreads) {
    const char *path = env->GetStringUTFChars(jPath, nullptr);
    llama_backend_init();
    auto mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // V1 纯 CPU；GPU 后端（SE-03 切换）后续接。
    llama_model *model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jPath, path);
    if (!model) {
        LOGE("model load failed: %s", path);
        return 0;
    }
    auto cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(nCtx);
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;
    llama_context *ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("context init failed");
        llama_model_free(model);
        return 0;
    }
    auto *session = new LlamaSession{model, ctx};
    LOGI("model loaded");
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_lzyuuu_ailivesaver_LlamaNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<LlamaSession *>(handle);
}

/**
 * 最小流式补全：贪心采样（V1）；逐 token 回调 Java 侧 onToken(jstring)。
 * 返回完整文本长度（字符），失败返回 -1。
 */
extern "C" JNIEXPORT jint JNICALL
Java_io_github_lzyuuu_ailivesaver_LlamaNative_nativeStreamCompletion(
        JNIEnv *env, jobject, jlong handle, jstring jPrompt,
        jint maxTokens, jobject callback) {
    auto *session = reinterpret_cast<LlamaSession *>(handle);
    if (!session || !session->model || !session->ctx) return -1;
    const char *raw = env->GetStringUTFChars(jPrompt, nullptr);
    const std::string prompt = wrapGemmaPrompt(raw);
    env->ReleaseStringUTFChars(jPrompt, raw);

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    // 重置上下文（V1 单轮；多轮对话缓存管理后续接）。
    llama_memory_t mem = llama_get_memory(session->ctx);
    if (mem) llama_memory_clear(mem, true);

    std::vector<llama_token> tokens(prompt.size() + 2);
    const int nPrompt = llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (nPrompt <= 0) {
        LOGE("tokenize failed");
        return -1;
    }
    tokens.resize(nPrompt);

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    std::string full;
    for (int i = 0; i < maxTokens; ++i) {
        llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
        if (llama_decode(session->ctx, batch) != 0) {
            LOGE("decode failed at %d", i);
            llama_sampler_free(smpl);
            return -1;
        }
        const llama_token next = llama_sampler_sample(smpl, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, next)) break;
        char piece[64];
        const int n = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, true);
        if (n <= 0) continue;
        std::string tokenText(piece, static_cast<size_t>(n));
        full += tokenText;
        env->CallVoidMethod(callback, onToken, toJString(env, tokenText));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            break;
        }
        tokens.clear();
        tokens.push_back(next);
    }
    llama_sampler_free(smpl);
    return static_cast<jint>(full.size());
}
