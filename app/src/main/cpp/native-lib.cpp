#include <jni.h>
#include <string>
#include <cstring>
#include <cmath>
#include <android/bitmap.h>
#include <android/log.h>

#define LOG_TAG "CoolWNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Glyph Data (5x7 dot matrix) ──
static const char GLYPHS[128][7][6] = {
    ['0'] = {" ### ","#   #","#   #","#   #","#   #","#   #"," ### "},
    ['1'] = {"  #  "," ##  ","  #  ","  #  ","  #  ","  #  "," ### "},
    ['2'] = {" ### ","#   #","    #","   # ","  #  "," #   ","#####"},
    ['3'] = {" ### ","#   #","    #","  ## ","    #","#   #"," ### "},
    ['4'] = {"   # ","  ## "," # # ","#  # ","#####","   # ","   # "},
    ['5'] = {"#####","#    ","#### ","    #","    #","#   #"," ### "},
    ['6'] = {"  ## "," #   ","#### ","#   #","#   #","#   #"," ### "},
    ['7'] = {"#####","    #","   # ","  #  "," #   "," #   "," #   "},
    ['8'] = {" ### ","#   #","#   #"," ### ","#   #","#   #"," ### "},
    ['9'] = {" ### ","#   #","#   #"," ####","    #","   # "," ##  "},
    [':'] = {"     ","  #  ","     ","     ","     ","  #  ","     "},
    [' '] = {"     ","     ","     ","     ","     ","     ","     "},
    ['S'] = {" ####","#    "," ### ","    #","    #","#   #"," ### "},
    ['T'] = {"#####","  #  ","  #  ","  #  ","  #  ","  #  ","  #  "},
    ['E'] = {"#####","#    ","#### ","#    ","#    ","#    ","#####"},
    ['P'] = {"#### ","#   #","#   #","#### ","#    ","#    ","#    "},
    ['C'] = {" ### ","#   #","#    ","#    ","#    ","#   #"," ### "},
    ['O'] = {" ### ","#   #","#   #","#   #","#   #","#   #"," ### "},
    ['L'] = {"#    ","#    ","#    ","#    ","#    ","#    ","#####"},
    ['W'] = {"#   #","#   #","#   #","# # #","# # #","## ##","#   #"},
    ['D'] = {"#### ","#   #","#   #","#   #","#   #","#   #","#### "},
    ['A'] = {" ### ","#   #","#   #","#####","#   #","#   #","#   #"},
    ['N'] = {"#   #","##  #","# # #","#  ##","#   #","#   #","#   #"},
    ['G'] = {" ### ","#   #","#    ","# ###","#   #","#   #"," ### "},
    ['H'] = {"#   #","#   #","#   #","#####","#   #","#   #","#   #"},
    ['I'] = {" ### ","  #  ","  #  ","  #  ","  #  ","  #  "," ### "},
    ['K'] = {"#   #","#  # ","# #  ","##   ","# #  ","#  # ","#   #"},
    ['M'] = {"#   #","## ##","# # #","#   #","#   #","#   #","#   #"},
    ['R'] = {"#### ","#   #","#   #","#### ","# #  ","#  # ","#   #"},
    ['U'] = {"#   #","#   #","#   #","#   #","#   #","#   #"," ### "},
    ['%'] = {"#   #","   # ","  #  ","  #  ","  #  "," #   ","#   #"},
};

static const int GLYPH_ROWS = 7;
static const int GLYPH_COLS = 5;

// ── Inline pixel helpers ──
static inline void setPixel(uint32_t* pixels, int w, int x, int y, uint32_t color) {
    if (x >= 0 && x < w && y >= 0) pixels[y * w + x] = color;
}

static inline void fillCircle(uint32_t* pixels, int bw, int bh,
                               float cx, float cy, float r, uint32_t color) {
    int x0 = (int)(cx - r - 1); if (x0 < 0) x0 = 0;
    int y0 = (int)(cy - r - 1); if (y0 < 0) y0 = 0;
    int x1 = (int)(cx + r + 1); if (x1 >= bw) x1 = bw - 1;
    int y1 = (int)(cy + r + 1); if (y1 >= bh) y1 = bh - 1;
    float r2 = r * r;
    for (int y = y0; y <= y1; y++) {
        float dy = y - cy;
        float dy2 = dy * dy;
        for (int x = x0; x <= x1; x++) {
            float dx = x - cx;
            if (dx * dx + dy2 <= r2) {
                pixels[y * bw + x] = color;
            }
        }
    }
}

// Anti-aliased circle for smoother rendering
static inline void fillCircleAA(uint32_t* pixels, int bw, int bh,
                                  float cx, float cy, float r, uint32_t color) {
    int x0 = (int)(cx - r - 2); if (x0 < 0) x0 = 0;
    int y0 = (int)(cy - r - 2); if (y0 < 0) y0 = 0;
    int x1 = (int)(cx + r + 2); if (x1 >= bw) x1 = bw - 1;
    int y1 = (int)(cy + r + 2); if (y1 >= bh) y1 = bh - 1;

    uint8_t ca = (color >> 24) & 0xFF;
    uint8_t cr = (color >> 16) & 0xFF;
    uint8_t cg = (color >> 8) & 0xFF;
    uint8_t cb = color & 0xFF;

    for (int y = y0; y <= y1; y++) {
        float dy = y - cy;
        for (int x = x0; x <= x1; x++) {
            float dx = x - cx;
            float dist = sqrtf(dx * dx + dy * dy);
            if (dist <= r + 0.5f) {
                float alpha;
                if (dist <= r - 0.5f) {
                    alpha = 1.0f;
                } else {
                    alpha = r + 0.5f - dist;
                }
                uint8_t a = (uint8_t)(ca * alpha);
                uint32_t existing = pixels[y * bw + x];
                uint8_t er = (existing >> 16) & 0xFF;
                uint8_t eg = (existing >> 8) & 0xFF;
                uint8_t eb = existing & 0xFF;
                uint8_t ea = (existing >> 24) & 0xFF;
                float af = a / 255.0f;
                uint8_t nr = (uint8_t)(cr * af + er * (1.0f - af));
                uint8_t ng = (uint8_t)(cg * af + eg * (1.0f - af));
                uint8_t nb = (uint8_t)(cb * af + eb * (1.0f - af));
                uint8_t na = (uint8_t)(a + ea * (1.0f - af));
                pixels[y * bw + x] = (na << 24) | (nr << 16) | (ng << 8) | nb;
            }
        }
    }
}

// ── Convert ARGB int to native ABGR (Android Bitmap pixel format) ──
static inline uint32_t argbToNative(jint color) {
    uint8_t a = (color >> 24) & 0xFF;
    uint8_t r = (color >> 16) & 0xFF;
    uint8_t g = (color >> 8) & 0xFF;
    uint8_t b = color & 0xFF;
    return (a << 24) | (b << 16) | (g << 8) | r;  // ABGR
}

static inline char toUpper(char c) {
    return (c >= 'a' && c <= 'z') ? (c - 32) : c;
}

// ══════════════════════════════════════════════════════════════
// JNI: stringFromJNI (keep existing)
// ══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jstring JNICALL
Java_com_kythonlk_coolw_MainActivity_stringFromJNI(JNIEnv* env, jobject) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

// ══════════════════════════════════════════════════════════════
// JNI: renderDotMatrix — renders text into an Android Bitmap
// ══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL
Java_com_kythonlk_coolw_DotMatrixRenderer_nativeRenderText(
        JNIEnv* env, jobject,
        jobject bitmap,
        jstring text,
        jint activeColor, jint inactiveColor,
        jfloat dotRadius, jfloat dotSpacing, jfloat charSpacing,
        jboolean drawInactive) {

    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    uint32_t* px = (uint32_t*)pixels;
    int bw = info.width;
    int bh = info.height;

    // Clear to transparent
    memset(px, 0, bw * bh * 4);

    uint32_t activeNative = argbToNative(activeColor);
    uint32_t inactiveNative = argbToNative(inactiveColor);

    const char* str = env->GetStringUTFChars(text, nullptr);
    int len = env->GetStringUTFLength(text);

    float startX = dotSpacing;
    float charWidth = GLYPH_COLS * dotSpacing;
    float inactiveRadius = dotRadius * 0.4f;

    for (int i = 0; i < len; i++) {
        char c = toUpper(str[i]);
        if (c < 0 || c > 127) c = ' ';

        const char (*glyph)[6] = GLYPHS[(int)c];
        // Check if glyph is empty (all zeros = unsupported char), use space
        if (glyph[0][0] == 0) glyph = GLYPHS[(int)' '];

        for (int r = 0; r < GLYPH_ROWS; r++) {
            for (int col = 0; col < GLYPH_COLS; col++) {
                float cx = startX + col * dotSpacing;
                float cy = dotSpacing + r * dotSpacing;
                bool active = (glyph[r][col] == '#');

                if (active) {
                    fillCircleAA(px, bw, bh, cx, cy, dotRadius, activeNative);
                } else if (drawInactive) {
                    fillCircleAA(px, bw, bh, cx, cy, inactiveRadius, inactiveNative);
                }
            }
        }
        startX += charWidth + charSpacing;
    }

    env->ReleaseStringUTFChars(text, str);
    AndroidBitmap_unlockPixels(env, bitmap);
}

// ══════════════════════════════════════════════════════════════
// JNI: calcDotMatrixSize — returns [width, height] for allocation
// ══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jintArray JNICALL
Java_com_kythonlk_coolw_DotMatrixRenderer_nativeCalcSize(
        JNIEnv* env, jobject,
        jint numChars, jfloat dotSpacing, jfloat charSpacing) {

    float charWidth = GLYPH_COLS * dotSpacing;
    int totalWidth = (int)(numChars * charWidth + (numChars - 1) * charSpacing + dotSpacing * 2);
    int totalHeight = (int)(GLYPH_ROWS * dotSpacing + dotSpacing * 2);

    jintArray result = env->NewIntArray(2);
    jint buf[2] = { totalWidth, totalHeight };
    env->SetIntArrayRegion(result, 0, 2, buf);
    return result;
}

// ══════════════════════════════════════════════════════════════
// JNI: drawStepsRing — renders the circular progress ring + step count
// ══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL
Java_com_kythonlk_coolw_NothingStepsWidget_nativeDrawStepsRing(
        JNIEnv* env, jobject,
        jobject bitmap,
        jint steps, jint goal,
        jint ringColor, jint trackColor,
        jfloat strokeWidth) {

    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    uint32_t* px = (uint32_t*)pixels;
    int size = info.width;
    memset(px, 0, size * size * 4);

    float margin = 15.0f;
    float cx = size / 2.0f;
    float cy = size / 2.0f;
    float radius = cx - margin;
    float halfStroke = strokeWidth / 2.0f;

    uint32_t trackNative = argbToNative(trackColor);
    uint32_t ringNative = argbToNative(ringColor);

    float progress = (goal > 0) ? ((float)steps / (float)goal) : 0.0f;
    if (progress > 1.0f) progress = 1.0f;
    float sweepRad = progress * 2.0f * M_PI;

    // Draw track circle and progress arc
    for (int y = 0; y < size; y++) {
        float dy = y - cy;
        for (int x = 0; x < size; x++) {
            float dx = x - cx;
            float dist = sqrtf(dx * dx + dy * dy);
            float diff = fabsf(dist - radius);

            if (diff <= halfStroke + 0.5f) {
                float alpha = 1.0f;
                if (diff > halfStroke - 0.5f) {
                    alpha = halfStroke + 0.5f - diff;
                }
                if (alpha <= 0.0f) continue;

                // Check if this pixel is in the progress arc
                // Arc starts at -90 degrees (top), goes clockwise
                float angle = atan2f(dy, dx) + M_PI / 2.0f;
                if (angle < 0) angle += 2.0f * M_PI;

                uint32_t color;
                if (angle <= sweepRad) {
                    color = ringNative;
                } else {
                    color = trackNative;
                }

                // Apply alpha
                uint8_t ca = (color >> 24) & 0xFF;
                uint8_t a = (uint8_t)(ca * alpha);
                color = (a << 24) | (color & 0x00FFFFFF);
                px[y * size + x] = color;
            }
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}