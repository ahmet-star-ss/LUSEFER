// LUSIFER - llama.cpp JNI köprüsü (Qwen2.5 GGUF)
#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <android/log.h>
#ifdef HAVE_LLAMA
#include "llama.h"
#endif

#define TAG "LusiferLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#ifdef HAVE_LLAMA
struct Handle { llama_model* model; int nctx; int threads; };
#endif

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_sadrazam_lusifer_core_LlmNative_load(JNIEnv* env, jobject, jstring path, jint nctx, jint threads) {
#ifdef HAVE_LLAMA
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.use_mmap = true;
    const char* p = env->GetStringUTFChars(path, nullptr);
    llama_model* m = llama_model_load_from_file(p, mp);
    env->ReleaseStringUTFChars(path, p);
    if (!m) { LOGI("model yuklenemedi"); return 0; }
    return (jlong) new Handle{m, (int) nctx, (int) threads};
#else
    (void) env; (void) path; (void) nctx; (void) threads;
    return 0;
#endif
}

JNIEXPORT void JNICALL
Java_com_sadrazam_lusifer_core_LlmNative_free(JNIEnv*, jobject, jlong h) {
#ifdef HAVE_LLAMA
    Handle* H = (Handle*) h;
    if (H) { if (H->model) llama_model_free(H->model); delete H; }
#else
    (void) h;
#endif
}

JNIEXPORT jbyteArray JNICALL
Java_com_sadrazam_lusifer_core_LlmNative_generate(JNIEnv* env, jobject, jlong h, jstring prompt, jint maxTokens, jfloat temp) {
    std::string out;
#ifdef HAVE_LLAMA
    Handle* H = (Handle*) h;
    if (H && H->model) {
        const char* pc = env->GetStringUTFChars(prompt, nullptr);
        std::string pr(pc);
        env->ReleaseStringUTFChars(prompt, pc);

        const llama_vocab* vocab = llama_model_get_vocab(H->model);
        int n = -llama_tokenize(vocab, pr.c_str(), (int32_t) pr.size(), nullptr, 0, true, true);
        if (n > 0 && n < H->nctx - 24) {
            std::vector<llama_token> toks(n);
            llama_tokenize(vocab, pr.c_str(), (int32_t) pr.size(), toks.data(), (int32_t) toks.size(), true, true);

            llama_context_params cp = llama_context_default_params();
            cp.n_ctx = H->nctx;
            cp.n_batch = 512;
            cp.n_ubatch = 512;
            cp.n_threads = H->threads;
            cp.n_threads_batch = H->threads;
            llama_context* ctx = llama_init_from_model(H->model, cp);
            if (ctx) {
                llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
                if (temp <= 0.01f) {
                    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
                } else {
                    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
                    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
                    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
                    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
                }
                int maxNew = std::min((int) maxTokens, H->nctx - n - 8);
                bool fail = false;
                for (int i = 0; i < n; i += 512) {
                    int cnt = std::min(512, n - i);
                    llama_batch b = llama_batch_get_one(toks.data() + i, cnt);
                    if (llama_decode(ctx, b)) { fail = true; break; }
                }
                if (!fail) {
                    for (int i = 0; i < maxNew; i++) {
                        llama_token tok = llama_sampler_sample(smpl, ctx, -1);
                        if (llama_vocab_is_eog(vocab, tok)) break;
                        char buf[256];
                        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
                        if (len > 0) out.append(buf, len);
                        llama_batch b = llama_batch_get_one(&tok, 1);
                        if (llama_decode(ctx, b)) break;
                    }
                }
                llama_sampler_free(smpl);
                llama_free(ctx);
            }
        }
    }
#else
    (void) h; (void) prompt; (void) maxTokens; (void) temp;
#endif
    jbyteArray arr = env->NewByteArray((jsize) out.size());
    if (!out.empty()) env->SetByteArrayRegion(arr, 0, (jsize) out.size(), (const jbyte*) out.data());
    return arr;
}

} // extern "C"
