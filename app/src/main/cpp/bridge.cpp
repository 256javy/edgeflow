#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include <algorithm>
#include <thread>

static std::mutex engine_mutex;
static std::atomic<bool> cancelled{false};
static whisper_context *ctx = nullptr;
static std::string loaded_path;
static void quiet(enum ggml_log_level, const char *, void *) {}
static bool abort_decode(void *) { return cancelled.load(); }

extern "C" JNIEXPORT void JNICALL
Java_dev_edgeflow_NativeWhisper_cancel(JNIEnv *, jobject) { cancelled.store(true); }

extern "C" JNIEXPORT void JNICALL
Java_dev_edgeflow_NativeWhisper_prepare(JNIEnv *, jobject) { cancelled.store(false); }

extern "C" JNIEXPORT jstring JNICALL
Java_dev_edgeflow_NativeWhisper_transcribe(JNIEnv *env, jobject, jstring path, jfloatArray input, jstring lang, jstring hint) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    const char *p = env->GetStringUTFChars(path, nullptr);
    std::string model(p);
    env->ReleaseStringUTFChars(path, p);
    const char *l = env->GetStringUTFChars(lang, nullptr);
    std::string language(l);
    env->ReleaseStringUTFChars(lang, l);
    const char *h = env->GetStringUTFChars(hint, nullptr);
    std::string vocabulary(h);
    env->ReleaseStringUTFChars(hint, h);
    whisper_log_set(quiet, nullptr);
    auto fail = [&](const char *message) -> jstring {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
        return nullptr;
    };
    if (cancelled.load()) return fail("Dictado cancelado");
    if (loaded_path != model || !ctx) {
        if (ctx) whisper_free(ctx);
        ctx = nullptr;
        loaded_path.clear();
        auto options = whisper_context_default_params();
        options.use_gpu = false;
        ctx = whisper_init_from_file_with_params(model.c_str(), options);
        if (!ctx) return fail("No se pudo cargar el modelo local");
        loaded_path = model;
    }
    std::vector<float> samples(env->GetArrayLength(input));
    env->GetFloatArrayRegion(input, 0, samples.size(), samples.data());
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = std::min(4u, std::max(1u, std::thread::hardware_concurrency()));
    params.translate = false;
    params.language = language == "auto" ? "auto" : language.c_str();
    params.no_context = true;
    params.initial_prompt = vocabulary.empty() ? nullptr : vocabulary.c_str();
    params.no_timestamps = true;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_blank = true;
    params.abort_callback = abort_decode;
    params.abort_callback_user_data = nullptr;
    if (whisper_full(ctx, params, samples.data(), samples.size()) != 0 || cancelled.load()) {
        std::fill(samples.begin(), samples.end(), 0);
        return fail("Inferencia interrumpida o fallida");
    }
    std::string result;
    for (int i = 0; i < whisper_full_n_segments(ctx); ++i) result += whisper_full_get_segment_text(ctx, i);
    std::fill(samples.begin(), samples.end(), 0);
    // JNI NewStringUTF expects modified UTF-8; decode standard UTF-8 through Java instead.
    jbyteArray bytes = env->NewByteArray(result.size());
    env->SetByteArrayRegion(bytes, 0, result.size(), reinterpret_cast<const jbyte *>(result.data()));
    auto cls = env->FindClass("java/lang/String");
    auto ctor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
    auto utf8 = env->NewStringUTF("UTF-8");
    return static_cast<jstring>(env->NewObject(cls, ctor, bytes, utf8));
}
extern "C" JNIEXPORT void JNICALL
Java_dev_edgeflow_NativeWhisper_unload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    if (ctx) whisper_free(ctx);
    ctx = nullptr;
    loaded_path.clear();
}
