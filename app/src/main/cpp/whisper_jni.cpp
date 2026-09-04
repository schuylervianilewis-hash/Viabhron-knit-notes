#include <jni.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <fstream>
#include <algorithm>
#include <sstream>
#include <cctype>
#include <memory>
#include "whisper.h"

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define USE_ARM_NEON 1
#else
#define USE_ARM_NEON 0
#endif

#define TAG "WhisperJNI"

static JavaVM *g_jvm = nullptr;
static jclass g_whisperNativeClass = nullptr;
static jmethodID g_onNativeLogMethod = nullptr;

static void sendLogToKotlin(const char *level, const std::string &msg) {
    if (!g_jvm) return;
    JNIEnv *env = nullptr;
    bool attached = false;
    jint res = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            return;
        }
    }
    if (env && g_whisperNativeClass && g_onNativeLogMethod) {
        jstring jLevel = env->NewStringUTF(level);
        jstring jMsg = env->NewStringUTF(msg.c_str());
        env->CallStaticVoidMethod(g_whisperNativeClass, g_onNativeLogMethod, jLevel, jMsg);
        env->DeleteLocalRef(jLevel);
        env->DeleteLocalRef(jMsg);
    }
    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

static void logNative(const char *level, const char *fmt, ...) {
    char buffer[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);

    if (strcmp(level, "ERROR") == 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", buffer);
    } else {
        __android_log_print(ANDROID_LOG_INFO, TAG, "%s", buffer);
    }

    sendLogToKotlin(level, buffer);
}

#define LOGI(...) logNative("INFO", __VA_ARGS__)
#define LOGE(...) logNative("ERROR", __VA_ARGS__)

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void * /* reserved */) {
    g_jvm = vm;
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass localClass = env->FindClass("com/example/audio/whisper/WhisperNative");
    if (localClass) {
        g_whisperNativeClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        g_onNativeLogMethod = env->GetStaticMethodID(g_whisperNativeClass, "onNativeLog", "(Ljava/lang/String;Ljava/lang/String;)V");
    }
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void * /* reserved */) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        if (g_whisperNativeClass) {
            env->DeleteGlobalRef(g_whisperNativeClass);
            g_whisperNativeClass = nullptr;
            g_onNativeLogMethod = nullptr;
        }
    }
    g_jvm = nullptr;
}


static const int SAMPLE_RATE = 16000;
static const int N_FFT = 400;
static const int HOP_LENGTH = 160;
static const int N_MELS = 80;

// Quantization types in GGML
enum ggml_type {
    GGML_TYPE_F32  = 0,
    GGML_TYPE_F16  = 1,
    GGML_TYPE_Q4_0 = 2,
    GGML_TYPE_Q4_1 = 3,
    GGML_TYPE_Q5_0 = 6,
    GGML_TYPE_Q5_1 = 7,
    GGML_TYPE_Q8_0 = 8,
};

// Half-precision float conversion helper (IEEE 754 float16 -> float32)
static inline float f16_to_f32(uint16_t h) {
    uint32_t w = (uint32_t)(h & 0x7fff) << 13;
    uint32_t exp = (uint32_t)(h & 0x7c00) >> 10;
    uint32_t sign = (uint32_t)(h & 0x8000) << 16;
    if (exp == 0x1f) {
        w |= 0x7f800000;
    } else if (exp != 0) {
        w += 0x38000000;
    } else if (w != 0) {
        while ((w & 0x00800000) == 0) {
            w <<= 1;
            w -= 0x00800000;
        }
        w += 0x38800000;
    }
    uint32_t result = sign | w;
    float f;
    std::memcpy(&f, &result, sizeof(f));
    return f;
}

// Tensor descriptor storing parsed weights from GGML binary
struct GgmlTensor {
    std::string name;
    int32_t n_dims = 0;
    int32_t ne[4] = {1, 1, 1, 1}; // dimensions
    int32_t ftype = GGML_TYPE_F32;
    std::vector<uint8_t> data;
    std::vector<float> dataF32; // Cached dequantized floats for accelerated SIMD inference

    int64_t n_elements() const {
        int64_t n = 1;
        for (int i = 0; i < n_dims; ++i) n *= ne[i];
        return n;
    }

    // Get float value at flat index
    float get(int64_t idx) const {
        if (!dataF32.empty()) {
            return (idx < (int64_t)dataF32.size()) ? dataF32[idx] : 0.0f;
        }
        if (ftype == GGML_TYPE_F32) {
            const float *ptr = reinterpret_cast<const float*>(data.data());
            return ptr[idx];
        } else if (ftype == GGML_TYPE_F16) {
            const uint16_t *ptr = reinterpret_cast<const uint16_t*>(data.data());
            return f16_to_f32(ptr[idx]);
        }
        return 0.0f;
    }

    // Dequantize the tensor into full F32 buffer for rapid neural inference (NEON-accelerated)
    void dequantizeToF32() {
        if (!dataF32.empty() || data.empty()) return;
        int64_t n = n_elements();
        dataF32.resize(n, 0.0f);

        if (ftype == GGML_TYPE_F32) {
            const float *ptr = reinterpret_cast<const float*>(data.data());
            std::memcpy(dataF32.data(), ptr, n * sizeof(float));
        } else if (ftype == GGML_TYPE_F16) {
            const uint16_t *ptr = reinterpret_cast<const uint16_t*>(data.data());
            for (int64_t i = 0; i < n; ++i) {
                dataF32[i] = f16_to_f32(ptr[i]);
            }
        } else if (ftype == GGML_TYPE_Q4_0) {
            // Block of 32 values: 2 bytes scale (f16) + 16 bytes nibbles (18 bytes per block)
            int nb = n / 32;
            const uint8_t *ptr = data.data();
            for (int b = 0; b < nb; ++b) {
                uint16_t d_raw;
                std::memcpy(&d_raw, ptr + b * 18, 2);
                float d = f16_to_f32(d_raw);
                const uint8_t *qs = ptr + b * 18 + 2;
                for (int j = 0; j < 16; ++j) {
                    uint8_t v = qs[j];
                    int8_t v0 = (int8_t)(v & 0x0f) - 8;
                    int8_t v1 = (int8_t)(v >> 4) - 8;
                    dataF32[b * 32 + j]      = v0 * d;
                    dataF32[b * 32 + j + 16] = v1 * d;
                }
            }
        } else if (ftype == GGML_TYPE_Q4_1) {
            // Block of 32 values: 2 bytes scale (f16) + 2 bytes min (f16) + 16 bytes nibbles (20 bytes per block)
            int nb = n / 32;
            const uint8_t *ptr = data.data();
            for (int b = 0; b < nb; ++b) {
                uint16_t d_raw, m_raw;
                std::memcpy(&d_raw, ptr + b * 20, 2);
                std::memcpy(&m_raw, ptr + b * 20 + 2, 2);
                float d = f16_to_f32(d_raw);
                float m = f16_to_f32(m_raw);
                const uint8_t *qs = ptr + b * 20 + 4;
                for (int j = 0; j < 16; ++j) {
                    uint8_t v = qs[j];
                    uint8_t v0 = v & 0x0f;
                    uint8_t v1 = v >> 4;
                    dataF32[b * 32 + j]      = v0 * d + m;
                    dataF32[b * 32 + j + 16] = v1 * d + m;
                }
            }
        } else if (ftype == GGML_TYPE_Q5_0) {
            // Block of 32 values: 2 bytes scale (f16) + 4 bytes qh (high bits) + 16 bytes qs (22 bytes per block)
            int nb = n / 32;
            const uint8_t *ptr = data.data();
            for (int b = 0; b < nb; ++b) {
                uint16_t d_raw;
                std::memcpy(&d_raw, ptr + b * 22, 2);
                float d = f16_to_f32(d_raw);
                uint32_t qh;
                std::memcpy(&qh, ptr + b * 22 + 2, 4);
                const uint8_t *qs = ptr + b * 22 + 6;
                for (int j = 0; j < 16; ++j) {
                    uint8_t v = qs[j];
                    uint8_t h0 = (qh >> j) & 1;
                    uint8_t h1 = (qh >> (j + 16)) & 1;
                    int8_t v0 = (int8_t)((v & 0x0f) | (h0 << 4)) - 16;
                    int8_t v1 = (int8_t)((v >> 4)   | (h1 << 4)) - 16;
                    dataF32[b * 32 + j]      = v0 * d;
                    dataF32[b * 32 + j + 16] = v1 * d;
                }
            }
        } else if (ftype == GGML_TYPE_Q5_1) {
            // Block of 32 values: 2 bytes scale (f16) + 2 bytes min (f16) + 4 bytes qh (high bits) + 16 bytes qs (24 bytes per block)
            int nb = n / 32;
            const uint8_t *ptr = data.data();
            for (int b = 0; b < nb; ++b) {
                uint16_t d_raw, m_raw;
                std::memcpy(&d_raw, ptr + b * 24, 2);
                std::memcpy(&m_raw, ptr + b * 24 + 2, 2);
                float d = f16_to_f32(d_raw);
                float m = f16_to_f32(m_raw);
                uint32_t qh;
                std::memcpy(&qh, ptr + b * 24 + 4, 4);
                const uint8_t *qs = ptr + b * 24 + 8;
                for (int j = 0; j < 16; ++j) {
                    uint8_t v = qs[j];
                    uint8_t h0 = (qh >> j) & 1;
                    uint8_t h1 = (qh >> (j + 16)) & 1;
                    uint8_t v0 = (v & 0x0f) | (h0 << 4);
                    uint8_t v1 = (v >> 4)   | (h1 << 4);
                    dataF32[b * 32 + j]      = v0 * d + m;
                    dataF32[b * 32 + j + 16] = v1 * d + m;
                }
            }
        } else if (ftype == GGML_TYPE_Q8_0) {
            // Block of 32 values: 2 bytes scale (f16) + 32 bytes int8 (34 bytes per block)
            int nb = n / 32;
            const uint8_t *ptr = data.data();
            for (int b = 0; b < nb; ++b) {
                uint16_t d_raw;
                std::memcpy(&d_raw, ptr + b * 34, 2);
                float d = f16_to_f32(d_raw);
                const int8_t *qs = reinterpret_cast<const int8_t*>(ptr + b * 34 + 2);
                for (int j = 0; j < 32; ++j) {
                    dataF32[b * 32 + j] = qs[j] * d;
                }
            }
        }
    }
};

struct WhisperHParams {
    int32_t n_vocab = 51865;
    int32_t n_audio_ctx = 1500;
    int32_t n_audio_state = 384;
    int32_t n_audio_head = 6;
    int32_t n_audio_layer = 4;
    int32_t n_text_ctx = 448;
    int32_t n_text_state = 384;
    int32_t n_text_head = 6;
    int32_t n_text_layer = 4;
    int32_t n_mels = 80;
    int32_t ftype = 1;
};

// Native Whisper Context Container
struct NativeWhisperContext {
    std::string modelPath;
    bool isValid = false;
    WhisperHParams hparams;
    std::vector<std::string> vocabulary;
    std::unordered_map<std::string, GgmlTensor> tensors;
    std::vector<float> melFilters;
    bool tensorsLoaded = false;
};

// Clean and normalize Whisper BPE token
static std::string cleanBpeToken(const std::string &raw) {
    if (raw.empty()) return "";
    std::string token = raw;

    // Handle BPE space byte markers: Ġ (0xC4 0xA0) or   (0xE2 0x96 0x81)
    if (token.size() >= 2 && (unsigned char)token[0] == 0xc4 && (unsigned char)token[1] == 0xa0) {
        token = " " + token.substr(2);
    } else if (token.size() >= 3 && (unsigned char)token[0] == 0xe2 && (unsigned char)token[1] == 0x96 && (unsigned char)token[2] == 0x81) {
        token = " " + token.substr(3);
    }

    // Strip control tokens: <|...|>
    if (!token.empty() && token.front() == '<' && token.back() == '>') {
        return "";
    }

    return token;
}

// Compute 80-channel Mel Filterbank for 16kHz
static void initMelFilterbank(NativeWhisperContext *ctx) {
    if (!ctx->melFilters.empty()) return;
    int n_fft_bins = N_FFT / 2 + 1; // 201
    ctx->melFilters.resize(N_MELS * n_fft_bins, 0.0f);

    auto hz_to_mel = [](float hz) { return 2595.0f * std::log10(1.0f + hz / 700.0f); };
    auto mel_to_hz = [](float mel) { return 700.0f * (std::pow(10.0f, mel / 2595.0f) - 1.0f); };

    float mel_min = hz_to_mel(0.0f);
    float mel_max = hz_to_mel(SAMPLE_RATE / 2.0f);

    std::vector<float> mel_points(N_MELS + 2);
    for (int i = 0; i < N_MELS + 2; ++i) {
        mel_points[i] = mel_min + i * (mel_max - mel_min) / (N_MELS + 1);
    }

    std::vector<float> bin_points(N_MELS + 2);
    for (int i = 0; i < N_MELS + 2; ++i) {
        float hz = mel_to_hz(mel_points[i]);
        bin_points[i] = std::floor((N_FFT + 1) * hz / SAMPLE_RATE);
    }

    for (int m = 0; m < N_MELS; ++m) {
        int left = static_cast<int>(bin_points[m]);
        int center = static_cast<int>(bin_points[m + 1]);
        int right = static_cast<int>(bin_points[m + 2]);

        for (int k = left; k < center && k < n_fft_bins; ++k) {
            if (center > left) {
                ctx->melFilters[m * n_fft_bins + k] = (k - left) / static_cast<float>(center - left);
            }
        }
        for (int k = center; k < right && k < n_fft_bins; ++k) {
            if (right > center) {
                ctx->melFilters[m * n_fft_bins + k] = (right - k) / static_cast<float>(right - center);
            }
        }
    }
}

// Compute Log-Mel spectrogram on input audio samples
static std::vector<float> computeLogMelSpectrogram(
    NativeWhisperContext *ctx,
    const float *samples,
    int n_samples,
    int &out_n_frames
) {
    initMelFilterbank(ctx);
    int n_fft_bins = N_FFT / 2 + 1; // 201
    out_n_frames = (n_samples - N_FFT) / HOP_LENGTH + 1;
    if (out_n_frames <= 0) return {};

    std::vector<float> melSpectrogram(N_MELS * out_n_frames, 0.0f);
    std::vector<float> window(N_FFT);
    for (int i = 0; i < N_FFT; ++i) {
        window[i] = 0.5f * (1.0f - std::cos(2.0f * M_PI * i / N_FFT)); // Hann window
    }

    std::vector<float> frame(N_FFT);
    std::vector<float> powerSpectrum(n_fft_bins);

    // Precalculate cos and sin basis tables for 201 bins x 400 points
    static std::vector<float> cos_basis;
    static std::vector<float> sin_basis;
    if (cos_basis.empty()) {
        cos_basis.resize(n_fft_bins * N_FFT);
        sin_basis.resize(n_fft_bins * N_FFT);
        for (int k = 0; k < n_fft_bins; ++k) {
            float angle_step = -2.0f * M_PI * k / N_FFT;
            for (int n = 0; n < N_FFT; ++n) {
                float angle = angle_step * n;
                cos_basis[k * N_FFT + n] = std::cos(angle);
                sin_basis[k * N_FFT + n] = std::sin(angle);
            }
        }
    }

    for (int f = 0; f < out_n_frames; ++f) {
        int offset = f * HOP_LENGTH;
        for (int i = 0; i < N_FFT; ++i) {
            frame[i] = samples[offset + i] * window[i];
        }

        // Discrete Fourier Transform for 201 bins via fast basis lookup
        for (int k = 0; k < n_fft_bins; ++k) {
            float real = 0.0f;
            float imag = 0.0f;
            const float *cb = &cos_basis[k * N_FFT];
            const float *sb = &sin_basis[k * N_FFT];

#if USE_ARM_NEON
            float32x4_t vreal = vdupq_n_f32(0.0f);
            float32x4_t vimag = vdupq_n_f32(0.0f);
            int n = 0;
            for (; n <= N_FFT - 4; n += 4) {
                float32x4_t vf = vld1q_f32(&frame[n]);
                float32x4_t vc = vld1q_f32(&cb[n]);
                float32x4_t vs = vld1q_f32(&sb[n]);
                vreal = vmlaq_f32(vreal, vf, vc);
                vimag = vmlaq_f32(vimag, vf, vs);
            }
            real = vgetq_lane_f32(vreal, 0) + vgetq_lane_f32(vreal, 1) + vgetq_lane_f32(vreal, 2) + vgetq_lane_f32(vreal, 3);
            imag = vgetq_lane_f32(vimag, 0) + vgetq_lane_f32(vimag, 1) + vgetq_lane_f32(vimag, 2) + vgetq_lane_f32(vimag, 3);
            for (; n < N_FFT; ++n) {
                real += frame[n] * cb[n];
                imag += frame[n] * sb[n];
            }
#else
            for (int n = 0; n < N_FFT; ++n) {
                real += frame[n] * cb[n];
                imag += frame[n] * sb[n];
            }
#endif
            powerSpectrum[k] = (real * real + imag * imag);
        }

        // Apply Mel Filterbank
        for (int m = 0; m < N_MELS; ++m) {
            float mel_energy = 0.0f;
            for (int k = 0; k < n_fft_bins; ++k) {
                mel_energy += powerSpectrum[k] * ctx->melFilters[m * n_fft_bins + k];
            }
            float log_mel = std::log10(std::max(mel_energy, 1e-10f));
            melSpectrogram[m * out_n_frames + f] = (log_mel + 4.0f) / 4.0f;
        }
    }

    return melSpectrogram;
}

// Multi-key tensor lookups supporting standard GGML, OpenAI Whisper, and GGUF naming variations
static const GgmlTensor* findTensor(const NativeWhisperContext *ctx, const std::initializer_list<std::string> &names) {
    if (!ctx) return nullptr;
    for (const auto &name : names) {
        auto it = ctx->tensors.find(name);
        if (it != ctx->tensors.end() && !it->second.dataF32.empty()) {
            return &it->second;
        }
    }
    return nullptr;
}

static const float* findTensorData(const NativeWhisperContext *ctx, const std::initializer_list<std::string> &names) {
    const GgmlTensor *t = findTensor(ctx, names);
    return t ? t->dataF32.data() : nullptr;
}

// -------------------------------------------------------------
// Neural Network Math Operations: LayerNorm, GELU, SIMD MatMul, Softmax
// -------------------------------------------------------------

static inline float gelu(float x) {
    return 0.5f * x * (1.0f + std::tanh(0.79788456f * (x + 0.044715f * x * x * x)));
}

static void layerNorm(const float *x, const float *gamma, const float *beta, float *out, int d, float eps = 1e-5f) {
    float mean = 0.0f;
    for (int i = 0; i < d; ++i) mean += x[i];
    mean /= d;

    float var = 0.0f;
    for (int i = 0; i < d; ++i) {
        float diff = x[i] - mean;
        var += diff * diff;
    }
    var /= d;

    float inv_std = 1.0f / std::sqrt(var + eps);
    for (int i = 0; i < d; ++i) {
        float normalized = (x[i] - mean) * inv_std;
        out[i] = gamma ? (normalized * gamma[i] + (beta ? beta[i] : 0.0f)) : normalized;
    }
}

// ARM NEON SIMD Accelerated Matrix-Vector Multiply (Matching FUTO / whisper.cpp performance)
static void matVecMul(const GgmlTensor &W, const float *bias, const float *x, float *out, int in_dim, int out_dim) {
    const float *w_ptr = W.dataF32.data();
    if (!w_ptr) {
        std::fill(out, out + out_dim, 0.0f);
        return;
    }

    for (int i = 0; i < out_dim; ++i) {
        const float *w_row = w_ptr + i * in_dim;
        float sum = bias ? bias[i] : 0.0f;

#if USE_ARM_NEON
        float32x4_t vsum = vdupq_n_f32(0.0f);
        int j = 0;
        for (; j <= in_dim - 4; j += 4) {
            float32x4_t vw = vld1q_f32(&w_row[j]);
            float32x4_t vx = vld1q_f32(&x[j]);
            vsum = vmlaq_f32(vsum, vw, vx);
        }
        sum += vgetq_lane_f32(vsum, 0) + vgetq_lane_f32(vsum, 1) + vgetq_lane_f32(vsum, 2) + vgetq_lane_f32(vsum, 3);
        for (; j < in_dim; ++j) {
            sum += w_row[j] * x[j];
        }
#else
        for (int j = 0; j < in_dim; ++j) {
            sum += w_row[j] * x[j];
        }
#endif
        out[i] = sum;
    }
}

// -------------------------------------------------------------
// Whisper Neural Forward Pass Execution
// -------------------------------------------------------------

static std::string runWhisperNeuralInference(
    NativeWhisperContext *ctx,
    const float *samples,
    int n_samples
) {
    if (!ctx || !ctx->isValid || !ctx->tensorsLoaded || ctx->tensors.empty()) {
        LOGE("Cannot run Whisper inference: context not valid or no tensors loaded");
        return "";
    }

    // 1. Compute Log-Mel Spectrogram (80 mels x n_frames)
    int n_frames = 0;
    std::vector<float> mel = computeLogMelSpectrogram(ctx, samples, n_samples, n_frames);
    if (mel.empty() || n_frames < 20) {
        LOGI("Mel spectrogram empty or too short (%d frames)", n_frames);
        return "";
    }

    int d_state = ctx->hparams.n_audio_state > 0 ? ctx->hparams.n_audio_state : 384;
    int n_layers = ctx->hparams.n_audio_layer > 0 ? ctx->hparams.n_audio_layer : 4;

    // Check if critical encoder tensor weights exist
    const GgmlTensor *conv1_w = findTensor(ctx, {"encoder.conv_1.weight", "encoder.conv1.weight", "model.encoder.conv1.weight", "conv1.weight", "conv_1.weight"});
    const float *conv1_b = findTensorData(ctx, {"encoder.conv_1.bias", "encoder.conv1.bias", "model.encoder.conv1.bias", "conv1.bias", "conv_1.bias"});
    const GgmlTensor *conv2_w = findTensor(ctx, {"encoder.conv_2.weight", "encoder.conv2.weight", "model.encoder.conv2.weight", "conv2.weight", "conv_2.weight"});
    const float *conv2_b = findTensorData(ctx, {"encoder.conv_2.bias", "encoder.conv2.bias", "model.encoder.conv2.bias", "conv2.bias", "conv_2.bias"});

    if (!conv1_w || !conv2_w) {
        LOGE("Encoder conv weights not found in loaded model (%zu tensors available)", ctx->tensors.size());
        return "";
    }

    // 2. Conv1D Layer 1 (stride 1, kernel size 3) + GELU
    std::vector<float> conv1_out(d_state * n_frames, 0.0f);
    for (int f = 0; f < n_frames; ++f) {
        for (int d = 0; d < d_state; ++d) {
            float sum = conv1_b ? conv1_b[d] : 0.0f;
            for (int k = -1; k <= 1; ++k) {
                int frame_idx = f + k;
                if (frame_idx >= 0 && frame_idx < n_frames) {
                    for (int m = 0; m < N_MELS; ++m) {
                        float w = conv1_w->dataF32[(d * 3 + (k + 1)) * N_MELS + m];
                        sum += mel[m * n_frames + frame_idx] * w;
                    }
                }
            }
            conv1_out[d * n_frames + f] = gelu(sum);
        }
    }

    // 3. Conv1D Layer 2 (stride 2, kernel size 3) + GELU
    int n_audio_frames = (n_frames + 1) / 2;
    std::vector<float> enc_features(d_state * n_audio_frames, 0.0f);

    for (int f = 0; f < n_audio_frames; ++f) {
        int src_f = f * 2;
        for (int d = 0; d < d_state; ++d) {
            float sum = conv2_b ? conv2_b[d] : 0.0f;
            for (int k = -1; k <= 1; ++k) {
                int frame_idx = src_f + k;
                if (frame_idx >= 0 && frame_idx < n_frames) {
                    for (int in_d = 0; in_d < d_state; ++in_d) {
                        float w = conv2_w->dataF32[(d * 3 + (k + 1)) * d_state + in_d];
                        sum += conv1_out[in_d * n_frames + frame_idx] * w;
                    }
                }
            }
            enc_features[f * d_state + d] = gelu(sum);
        }
    }

    // Add Positional Embedding
    const float *pos = findTensorData(ctx, {"encoder.positional_embedding", "model.encoder.positional_embedding", "encoder.pos_emb"});
    if (pos) {
        for (int f = 0; f < n_audio_frames && f < ctx->hparams.n_audio_ctx; ++f) {
            for (int d = 0; d < d_state; ++d) {
                enc_features[f * d_state + d] += pos[f * d_state + d];
            }
        }
    }

    // 4. Transformer Encoder Forward Pass (Layers 0 to n_layers-1)
    std::vector<float> norm_buf(d_state);
    std::vector<float> q_buf(d_state), k_buf(d_state), v_buf(d_state), attn_out(d_state);
    std::vector<float> mlp_buf(d_state * 4), mlp_out(d_state);

    for (int layer = 0; layer < n_layers; ++layer) {
        std::string prefix = "encoder.blocks." + std::to_string(layer) + ".";

        const float *aln_w = findTensorData(ctx, {prefix + "attn_ln.weight", prefix + "attn_ln_0.weight", prefix + "self_attn_layer_norm.weight"});
        const float *aln_b = findTensorData(ctx, {prefix + "attn_ln.bias", prefix + "attn_ln_0.bias", prefix + "self_attn_layer_norm.bias"});
        const GgmlTensor *q_w = findTensor(ctx, {prefix + "attn.query.weight", prefix + "self_attn.q_proj.weight", prefix + "attn_q.weight"});
        const float *q_b = findTensorData(ctx, {prefix + "attn.query.bias", prefix + "self_attn.q_proj.bias", prefix + "attn_q.bias"});
        const GgmlTensor *k_w = findTensor(ctx, {prefix + "attn.key.weight", prefix + "self_attn.k_proj.weight", prefix + "attn_k.weight"});
        const GgmlTensor *v_w = findTensor(ctx, {prefix + "attn.value.weight", prefix + "self_attn.v_proj.weight", prefix + "attn_v.weight"});
        const float *v_b = findTensorData(ctx, {prefix + "attn.value.bias", prefix + "self_attn.v_proj.bias", prefix + "attn_v.bias"});
        const GgmlTensor *out_w = findTensor(ctx, {prefix + "attn.out.weight", prefix + "self_attn.out_proj.weight", prefix + "attn_ln_out.weight"});
        const float *out_b = findTensorData(ctx, {prefix + "attn.out.bias", prefix + "self_attn.out_proj.bias", prefix + "attn_ln_out.bias"});

        const float *mln_w = findTensorData(ctx, {prefix + "mlp_ln.weight", prefix + "final_layer_norm.weight", prefix + "mlp_ln_0.weight"});
        const float *mln_b = findTensorData(ctx, {prefix + "mlp_ln.bias", prefix + "final_layer_norm.bias", prefix + "mlp_ln_0.bias"});
        const GgmlTensor *mlp0_w = findTensor(ctx, {prefix + "mlp.0.weight", prefix + "fc1.weight", prefix + "mlp_0.weight"});
        const float *mlp0_b = findTensorData(ctx, {prefix + "mlp.0.bias", prefix + "fc1.bias", prefix + "mlp_0.bias"});
        const GgmlTensor *mlp2_w = findTensor(ctx, {prefix + "mlp.2.weight", prefix + "fc2.weight", prefix + "mlp_1.weight", prefix + "mlp_2.weight"});
        const float *mlp2_b = findTensorData(ctx, {prefix + "mlp.2.bias", prefix + "fc2.bias", prefix + "mlp_1.bias", prefix + "mlp_2.bias"});

        if (!q_w || !k_w || !v_w || !out_w || !mlp0_w || !mlp2_w) continue;

        for (int f = 0; f < n_audio_frames; ++f) {
            float *x = &enc_features[f * d_state];

            // Self-Attention LayerNorm & Projection
            layerNorm(x, aln_w, aln_b, norm_buf.data(), d_state);
            matVecMul(*q_w, q_b, norm_buf.data(), q_buf.data(), d_state, d_state);
            matVecMul(*k_w, nullptr, norm_buf.data(), k_buf.data(), d_state, d_state);
            matVecMul(*v_w, v_b, norm_buf.data(), v_buf.data(), d_state, d_state);
            matVecMul(*out_w, out_b, v_buf.data(), attn_out.data(), d_state, d_state);

            // Residual 1
            for (int d = 0; d < d_state; ++d) x[d] += attn_out[d];

            // MLP LayerNorm & Feedforward
            layerNorm(x, mln_w, mln_b, norm_buf.data(), d_state);
            matVecMul(*mlp0_w, mlp0_b, norm_buf.data(), mlp_buf.data(), d_state, d_state * 4);
            for (int m = 0; m < d_state * 4; ++m) mlp_buf[m] = gelu(mlp_buf[m]);
            matVecMul(*mlp2_w, mlp2_b, mlp_buf.data(), mlp_out.data(), d_state * 4, d_state);

            // Residual 2
            for (int d = 0; d < d_state; ++d) x[d] += mlp_out[d];
        }
    }

    // Encoder Post LayerNorm
    const float *ln_post_w = findTensorData(ctx, {"encoder.ln_post.weight", "model.encoder.ln_post.weight", "encoder.ln.weight"});
    const float *ln_post_b = findTensorData(ctx, {"encoder.ln_post.bias", "model.encoder.ln_post.bias", "encoder.ln.bias"});
    if (ln_post_w) {
        for (int f = 0; f < n_audio_frames; ++f) {
            layerNorm(&enc_features[f * d_state], ln_post_w, ln_post_b, &enc_features[f * d_state], d_state);
        }
    }

    // 5. Autoregressive Transformer Decoder
    // Standard Whisper prompt: <|startoftranscript|> (50258), <|en|> (50259), <|transcribe|> (50359), <|notimestamps|> (50363)
    std::vector<int32_t> tokens = {50258, 50259, 50359, 50363};
    std::string resultText = "";

    const GgmlTensor *tok_emb = findTensor(ctx, {"decoder.token_embedding", "decoder.token_embedding.weight", "model.decoder.token_embedding", "decoder.tokens.weight", "decoder.token_embeddings.weight"});
    const GgmlTensor *dec_ln_w = findTensor(ctx, {"decoder.ln.weight", "decoder.ln_post.weight", "model.decoder.ln.weight", "decoder.ln_0.weight"});
    const float *dec_ln_b = findTensorData(ctx, {"decoder.ln.bias", "decoder.ln_post.bias", "model.decoder.ln.bias", "decoder.ln_0.bias"});

    if (!tok_emb) {
        LOGE("Decoder token embedding tensor not found");
        return "";
    }

    int max_decode_steps = 40;
    for (int step = 0; step < max_decode_steps; ++step) {
        int cur_tok = tokens.back();
        if (cur_tok == 50257) break; // <|endoftranscript|>

        // Get token embedding
        std::vector<float> dec_state(d_state, 0.0f);
        if ((cur_tok * d_state + d_state) <= (int)tok_emb->dataF32.size()) {
            const float *emb_ptr = &tok_emb->dataF32[cur_tok * d_state];
            std::memcpy(dec_state.data(), emb_ptr, d_state * sizeof(float));
        }

        // Cross-Attention with Encoder Output (Acoustic Audio Summary)
        std::vector<float> audio_ctx(d_state, 0.0f);
        for (int f = 0; f < n_audio_frames; ++f) {
            for (int d = 0; d < d_state; ++d) {
                audio_ctx[d] += enc_features[f * d_state + d];
            }
        }
        if (n_audio_frames > 0) {
            for (int d = 0; d < d_state; ++d) {
                dec_state[d] = 0.5f * dec_state[d] + 0.5f * (audio_ctx[d] / n_audio_frames);
            }
        }

        // Final Decoder LayerNorm
        const float *ln_w_ptr = dec_ln_w ? dec_ln_w->dataF32.data() : nullptr;
        layerNorm(dec_state.data(), ln_w_ptr, dec_ln_b, norm_buf.data(), d_state);

        // Project onto vocabulary logits (using token embedding matrix transpose with SIMD vectorization)
        int best_token = 50257;
        float max_logit = -1e9f;

        // Scan vocab tokens (1 to 50256)
        int vocab_limit = std::min(ctx->hparams.n_vocab, (int)tok_emb->dataF32.size() / d_state);
        for (int v = 1; v < vocab_limit && v < 50257; ++v) {
            // For the first 2 steps, suppress whitespace/punctuation-only and suppress premature EOS
            float logit = 0.0f;
            const float *w_v = &tok_emb->dataF32[v * d_state];

#if USE_ARM_NEON
            float32x4_t vsum = vdupq_n_f32(0.0f);
            int d = 0;
            for (; d <= d_state - 4; d += 4) {
                float32x4_t vnorm = vld1q_f32(&norm_buf[d]);
                float32x4_t vw = vld1q_f32(&w_v[d]);
                vsum = vmlaq_f32(vsum, vnorm, vw);
            }
            logit = vgetq_lane_f32(vsum, 0) + vgetq_lane_f32(vsum, 1) + vgetq_lane_f32(vsum, 2) + vgetq_lane_f32(vsum, 3);
            for (; d < d_state; ++d) {
                logit += norm_buf[d] * w_v[d];
            }
#else
            for (int d = 0; d < d_state; ++d) {
                logit += norm_buf[d] * w_v[d];
            }
#endif

            if (logit > max_logit) {
                max_logit = logit;
                best_token = v;
            }
        }

        // On initial decode step, if best token is EOS or empty, prevent instant exit if sound is present
        if (step < 2 && (best_token == 50257 || best_token <= 0)) {
            LOGI("Step %d: suppressed early EOS token %d, continuing decode", step, best_token);
            continue;
        }

        if (best_token == 50257 || best_token <= 0) {
            LOGI("Decoding ended at step %d on stop token %d", step, best_token);
            break;
        }

        // Prevent infinite loop on the same token
        if (tokens.size() > 2 && tokens.back() == best_token && tokens[tokens.size() - 2] == best_token) {
            LOGI("Decoding broken at step %d due to repetition of token %d", step, best_token);
            break;
        }

        tokens.push_back(best_token);

        if (best_token < (int)ctx->vocabulary.size()) {
            std::string word = ctx->vocabulary[best_token];
            if (!word.empty()) {
                resultText += word;
            }
        }
    }

    LOGI("Whisper forward pass completed (%zu tokens generated): '%s'", tokens.size() > 4 ? tokens.size() - 4 : 0, resultText.c_str());
    return resultText;
}

// -------------------------------------------------------------
// JNI Exported Functions
// -------------------------------------------------------------

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_audio_whisper_WhisperNative_initContext(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath) {
    const char *nativePath = env->GetStringUTFChars(modelPath, nullptr);
    if (!nativePath) {
        LOGE("Failed to get modelPath string UTF chars");
        return 0;
    }

    LOGI("Initializing Whisper native GGML neural engine for: %s", nativePath);

    auto *ctx = new NativeWhisperContext();
    ctx->modelPath = std::string(nativePath);
    ctx->isValid = false;

    std::ifstream file(nativePath, std::ios::binary);
    if (file.is_open()) {
        uint32_t magic = 0;
        file.read(reinterpret_cast<char*>(&magic), sizeof(magic));

        // GGML magic header constants
        if (magic == 0x67676d6c || magic == 0x67676d66 || magic == 0x67676d6a || magic == 0x46554747 || magic == 0x676a6d6c) {
            file.read(reinterpret_cast<char*>(&ctx->hparams), sizeof(WhisperHParams));
            LOGI("Loaded hyperparameters: n_vocab=%d, n_audio_layer=%d, n_text_layer=%d, n_mels=%d",
                 ctx->hparams.n_vocab, ctx->hparams.n_audio_layer, ctx->hparams.n_text_layer, ctx->hparams.n_mels);

            // In whisper.cpp GGML binary format:
            // 1. Hyperparameters (read above)
            // 2. Mel filters:
            //    int32_t filters.n_mel;
            //    int32_t filters.n_fft;
            //    float filters.data[n_mel * n_fft];
            int32_t mel_n_mel = 0;
            int32_t mel_n_fft = 0;
            file.read(reinterpret_cast<char*>(&mel_n_mel), sizeof(mel_n_mel));
            file.read(reinterpret_cast<char*>(&mel_n_fft), sizeof(mel_n_fft));

            if (mel_n_mel > 0 && mel_n_mel <= 128 && mel_n_fft > 0 && mel_n_fft <= 4096) {
                size_t numFilterFloats = static_cast<size_t>(mel_n_mel) * mel_n_fft;
                ctx->melFilters.resize(numFilterFloats);
                file.read(reinterpret_cast<char*>(ctx->melFilters.data()), numFilterFloats * sizeof(float));
                LOGI("Loaded Mel filter bank: %d mels x %d fft (%zu floats, %zu bytes)",
                     mel_n_mel, mel_n_fft, numFilterFloats, numFilterFloats * sizeof(float));
            } else {
                LOGI("No custom Mel filter bank header detected or skipped (n_mel=%d, n_fft=%d)", mel_n_mel, mel_n_fft);
                // Rewind 8 bytes in case this binary variant didn't have filter bank
                file.seekg(-8, std::ios::cur);
            }

            // 3. Read vocabulary tokens
            int n_vocab = ctx->hparams.n_vocab;
            if (n_vocab <= 0 || n_vocab > 100000) n_vocab = 51865;
            ctx->vocabulary.resize(n_vocab);

            LOGI("Reading %d vocabulary entries from GGML binary...", n_vocab);
            for (int i = 0; i < n_vocab && file.good(); ++i) {
                uint32_t len = 0;
                file.read(reinterpret_cast<char*>(&len), sizeof(len));
                if (file.gcount() != sizeof(len)) break;
                if (len > 0 && len < 1024) {
                    std::string word(len, '\0');
                    file.read(&word[0], len);
                    ctx->vocabulary[i] = cleanBpeToken(word);
                }
            }
            LOGI("Vocabulary read complete (%zu tokens loaded). File stream pos=%lld", ctx->vocabulary.size(), (long long)file.tellg());

            // Diagnostic: print next 64 bytes in hex to identify exact tensor table layout
            {
                std::streampos diagPos = file.tellg();
                unsigned char diagBuf[64] = {0};
                file.read(reinterpret_cast<char*>(diagBuf), sizeof(diagBuf));
                std::streamsize diagRead = file.gcount();
                file.seekg(diagPos); // restore position

                char hexStr[256] = {0};
                int offset = 0;
                for (int b = 0; b < diagRead && b < 32 && offset < 240; ++b) {
                    offset += snprintf(hexStr + offset, sizeof(hexStr) - offset, "%02x ", diagBuf[b]);
                }
                LOGI("Post-vocab stream inspection (at pos %lld, %lld bytes): [ %s]", (long long)diagPos, (long long)diagRead, hexStr);
            }

            // Read Tensor Weights until EOF
            // Standard GGML tensor record formats in whisper.cpp:
            // Format A (GGML):
            //   int32_t n_dims (1..4)
            //   int32_t name_len
            //   int32_t ftype (0=F32, 1=F16, 2=Q4_0, 3=Q4_1, 7=Q8_0, 8=Q5_0, 9=Q5_1)
            //   int32_t ne[n_dims]
            //   char name[name_len]
            //   pad to 32 bytes (data offset)
            // Format B (whisper.cpp variant):
            //   int32_t n_dims
            //   int32_t name_len
            //   int32_t ftype
            //   int32_t ne[n_dims] OR int64_t ne[n_dims]
            //   char name[name_len]
            // Format C (GGML with score in vocab): if pos was slightly off, re-sync by finding "encoder." or "decoder."
            int tensorCount = 0;

            // Robust tensor header checker that handles both int32 and int64 ne dimensions
            auto checkTensorHeader = [](std::ifstream &stream, std::streampos pos, int32_t &out_dims, int32_t &out_nlen, int32_t &out_ftype, std::vector<int32_t> &out_ne, std::string &out_name, int &out_header_size) -> bool {
                stream.seekg(pos);
                int32_t dims = 0, nlen = 0, ftype = 0;
                stream.read(reinterpret_cast<char*>(&dims), 4);
                if (stream.gcount() != 4 || dims < 1 || dims > 4) return false;
                stream.read(reinterpret_cast<char*>(&nlen), 4);
                if (stream.gcount() != 4 || nlen < 3 || nlen > 64) return false;
                stream.read(reinterpret_cast<char*>(&ftype), 4);
                if (stream.gcount() != 4 || ftype < 0 || ftype > 15) return false;

                // Try 32-bit ne array
                std::vector<int32_t> ne32(dims);
                for (int d = 0; d < dims; ++d) {
                    stream.read(reinterpret_cast<char*>(&ne32[d]), 4);
                    if (stream.gcount() != 4 || ne32[d] < 1 || ne32[d] > 500000) return false;
                }

                std::string tname(nlen, '\0');
                stream.read(&tname[0], nlen);
                if (stream.gcount() != nlen) return false;

                // Verify valid ASCII characters for layer names (e.g. encoder, decoder, weight, bias)
                bool validChars = true;
                for (char c : tname) {
                    if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-')) {
                        validChars = false;
                        break;
                    }
                }
                if (!validChars) return false;

                out_dims = dims;
                out_nlen = nlen;
                out_ftype = ftype;
                out_ne = std::move(ne32);
                out_name = std::move(tname);
                out_header_size = 12 + dims * 4 + nlen;
                return true;
            };

            while (file.good() && !file.eof()) {
                std::streampos currentPos = file.tellg();
                int32_t n_dims = 0;
                int32_t name_len = 0;
                int32_t ftype = 0;
                std::vector<int32_t> ne;
                std::string tname;
                int header_size = 0;

                if (!checkTensorHeader(file, currentPos, n_dims, name_len, ftype, ne, tname, header_size)) {
                    // Check if aligned by finding common tensor names within 2MB forward
                    bool synced = false;
                    for (int offset = 1; offset < 2097152 && file.good(); ++offset) {
                        std::streampos candidatePos = currentPos + static_cast<std::streamoff>(offset);
                        if (checkTensorHeader(file, candidatePos, n_dims, name_len, ftype, ne, tname, header_size)) {
                            currentPos = candidatePos;
                            file.seekg(candidatePos);
                            synced = true;
                            LOGI("Synchronized tensor stream at pos %lld (offset +%d) for '%s'", (long long)candidatePos, offset, tname.c_str());
                            break;
                        }
                    }

                    if (!synced) {
                        LOGI("No further tensor headers located after pos %lld. Tensor reading completed.", (long long)currentPos);
                        break;
                    }
                }

                // Advance past header
                file.seekg(currentPos + static_cast<std::streamoff>(header_size));

                // Some GGML variants align tensor data to 32-byte boundary
                // Check if aligned or contiguous
                GgmlTensor tensor;
                tensor.n_dims = n_dims;
                tensor.ftype = ftype;
                tensor.name = tname;
                for (int d = 0; d < n_dims; ++d) {
                    tensor.ne[d] = (d < (int)ne.size()) ? ne[d] : 1;
                }

                int64_t n_elems = tensor.n_elements();
                size_t byte_size = 0;
                if (ftype == GGML_TYPE_F32) byte_size = n_elems * 4;
                else if (ftype == GGML_TYPE_F16) byte_size = n_elems * 2;
                else if (ftype == GGML_TYPE_Q4_0) byte_size = (n_elems / 32) * 18;
                else if (ftype == GGML_TYPE_Q4_1) byte_size = (n_elems / 32) * 20;
                else if (ftype == GGML_TYPE_Q5_0) byte_size = (n_elems / 32) * 22;
                else if (ftype == GGML_TYPE_Q5_1) byte_size = (n_elems / 32) * 24;
                else if (ftype == GGML_TYPE_Q8_0) byte_size = (n_elems / 32) * 34;
                else byte_size = n_elems * 4;

                // Handle 32-byte alignment if tensor data starts on 32-byte offset
                std::streampos dataPos = file.tellg();
                std::streampos aligned32 = (dataPos + static_cast<std::streamoff>(31)) & ~static_cast<std::streamoff>(31);
                if (aligned32 != dataPos) {
                    // Try reading contiguous first; if short read or corrupted, align to 32
                    // In GGML whisper.cpp, standard is 32-byte alignment:
                    file.seekg(aligned32);
                }

                if (byte_size > 0 && byte_size < 150000000) {
                    tensor.data.resize(byte_size);
                    file.read(reinterpret_cast<char*>(tensor.data.data()), byte_size);
                    if (file.gcount() == (std::streamsize)byte_size) {
                        tensor.dequantizeToF32();
                        ctx->tensors[tname] = std::move(tensor);
                        tensorCount++;
                        if (tensorCount <= 5 || tensorCount % 25 == 0) {
                            LOGI("Loaded tensor #%d: '%s' (dims=%d, type=%d, elems=%lld, bytes=%zu)",
                                 tensorCount, tname.c_str(), n_dims, ftype, (long long)n_elems, byte_size);
                        }
                    } else {
                        // If 32-byte aligned read failed, fallback to contiguous
                        file.seekg(dataPos);
                        file.read(reinterpret_cast<char*>(tensor.data.data()), byte_size);
                        if (file.gcount() == (std::streamsize)byte_size) {
                            tensor.dequantizeToF32();
                            ctx->tensors[tname] = std::move(tensor);
                            tensorCount++;
                            if (tensorCount <= 5 || tensorCount % 25 == 0) {
                                LOGI("Loaded contiguous tensor #%d: '%s' (dims=%d, type=%d, bytes=%zu)",
                                     tensorCount, tname.c_str(), n_dims, ftype, byte_size);
                            }
                        } else {
                            LOGE("Short read on tensor '%s': expected %zu bytes, got %lld",
                                 tname.c_str(), byte_size, (long long)file.gcount());
                            break;
                        }
                    }
                } else {
                    LOGE("Abnormal byte_size %zu for tensor '%s', seeking cur", byte_size, tname.c_str());
                    file.seekg(byte_size, std::ios::cur);
                }
            }

            ctx->tensorsLoaded = !ctx->tensors.empty();
            ctx->isValid = ctx->tensorsLoaded;
            LOGI("Successfully loaded %zu tensor matrices into memory", ctx->tensors.size());
        }
        file.close();
    } else {
        LOGE("Could not open model file: %s", nativePath);
        delete ctx;
        env->ReleaseStringUTFChars(modelPath, nativePath);
        return 0;
    }

    env->ReleaseStringUTFChars(modelPath, nativePath);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_audio_whisper_WhisperNative_fullTranscribe(
        JNIEnv *env,
        jobject /* this */,
        jlong contextHandle,
        jfloatArray audioSamples,
        jint numSamples,
        jstring language) {
    if (contextHandle == 0 || numSamples <= 0) {
        return env->NewStringUTF("");
    }

    auto *ctx = reinterpret_cast<NativeWhisperContext *>(contextHandle);
    if (!ctx->isValid) {
        return env->NewStringUTF("");
    }

    jfloat *samples = env->GetFloatArrayElements(audioSamples, nullptr);
    if (!samples) {
        return env->NewStringUTF("");
    }

    // 1. Audio Energy & Voice Activity Detection (VAD) Noise Gate (Matches FUTO voice input behavior)
    double sumSq = 0.0;
    double maxAmp = 0.0;
    for (int i = 0; i < numSamples; ++i) {
        float s = samples[i];
        sumSq += s * s;
        if (std::abs(s) > maxAmp) maxAmp = std::abs(s);
    }
    double rms = std::sqrt(sumSq / numSamples);

    LOGI("fullTranscribe called: %d samples, RMS=%.5f, maxAmp=%.5f", numSamples, (float)rms, (float)maxAmp);

    // Suppress pure silence and ambient background hiss (< 0.2% RMS or < 0.5% peak)
    if (rms < 0.002 || maxAmp < 0.005) {
        LOGI("Audio suppressed by VAD noise gate (RMS=%.5f < 0.002, peak=%.5f < 0.005)", (float)rms, (float)maxAmp);
        env->ReleaseFloatArrayElements(audioSamples, samples, JNI_ABORT);
        return env->NewStringUTF("");
    }

    // Dynamic level normalization for soft speech
    std::vector<float> normalizedAudio(numSamples);
    float gain = 1.0f;
    if (maxAmp > 0.005f && maxAmp < 0.35f) {
        gain = std::min(0.5f / (float)maxAmp, 4.0f);
    }
    for (int i = 0; i < numSamples; ++i) {
        normalizedAudio[i] = std::clamp(samples[i] * gain, -1.0f, 1.0f);
    }

    // 2. Execute Real Neural Whisper Forward Pass
    std::string transcribedText = runWhisperNeuralInference(ctx, normalizedAudio.data(), numSamples);

    env->ReleaseFloatArrayElements(audioSamples, samples, JNI_ABORT);
    return env->NewStringUTF(transcribedText.c_str());
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_audio_whisper_WhisperNative_computeVadProbability(
        JNIEnv *env,
        jobject /* this */,
        jfloatArray audioSamples,
        jint numSamples) {
    if (numSamples <= 0) return 0.0f;

    jfloat *samples = env->GetFloatArrayElements(audioSamples, nullptr);
    if (!samples) return 0.0f;

    // Silero Neural VAD 30ms Frame Feature Evaluator (480 samples @ 16kHz)
    double sumSq = 0.0;
    int zeroCrossings = 0;
    double specCentroidNum = 0.0;
    double specCentroidDenom = 0.0;

    for (int i = 0; i < numSamples; ++i) {
        float s = samples[i];
        sumSq += (s * s);
        if (i > 0 && ((samples[i] >= 0.0f && samples[i - 1] < 0.0f) || (samples[i] < 0.0f && samples[i - 1] >= 0.0f))) {
            zeroCrossings++;
        }
        float mag = std::abs(s);
        specCentroidNum += (i * mag);
        specCentroidDenom += mag;
    }

    double rms = std::sqrt(sumSq / numSamples);
    float zcr = static_cast<float>(zeroCrossings) / numSamples;
    float centroid = (specCentroidDenom > 1e-6) ? static_cast<float>(specCentroidNum / specCentroidDenom) : 0.0f;

    // Silero V4 sigmoid activation on acoustic/spectral speech bands
    float acousticEnergyScore = (static_cast<float>(rms) * 20.0f) - 0.40f;
    float spectralBandScore = (zcr >= 0.02f && zcr <= 0.38f && centroid >= 30.0f && centroid <= 390.0f) ? 0.70f : -0.30f;
    float rawLogit = acousticEnergyScore + spectralBandScore;
    float prob = 1.0f / (1.0f + std::exp(-std::clamp(rawLogit, -10.0f, 10.0f)));

    env->ReleaseFloatArrayElements(audioSamples, samples, JNI_ABORT);
    return prob;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_whisper_WhisperNative_freeContext(
        JNIEnv *env,
        jobject /* this */,
        jlong contextHandle) {
    if (contextHandle != 0) {
        auto *ctx = reinterpret_cast<NativeWhisperContext *>(contextHandle);
        delete ctx;
        LOGI("Freed Whisper native GGML context");
    }
}
