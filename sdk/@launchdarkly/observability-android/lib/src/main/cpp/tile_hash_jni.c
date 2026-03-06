#include <jni.h>
#include <android/bitmap.h>
#include <stdlib.h>
#include "tile_hash.h"

/*
 * Returned LongArray layout:
 *   [0] rows
 *   [1] columns
 *   [2] tileWidth
 *   [3] tileHeight
 *   [4 + i*2]   hashLo for tile i
 *   [4 + i*2+1] hashHi for tile i
 */
JNIEXPORT jlongArray JNICALL
Java_com_launchdarkly_observability_replay_capture_TileHashNative_nativeCompute(
    JNIEnv *env, jclass clazz, jobject bitmap)
{
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
        return NULL;

    if (info.width == 0 || info.height == 0)
        return NULL;

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return NULL;

    void *pixels;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
        return NULL;

    TileLayout layout = tile_compute_layout((int)info.width, (int)info.height);
    int totalTiles = layout.rows * layout.columns;

    TileHashResult *results = (TileHashResult *)malloc((size_t)totalTiles * sizeof(TileHashResult));
    if (!results) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return NULL;
    }

    tile_compute_all(pixels,
                     (int)info.width, (int)info.height,
                     (int)info.stride,
                     layout, results);

    AndroidBitmap_unlockPixels(env, bitmap);

    const int arrayLen = 4 + totalTiles * 2;
    jlongArray out = (*env)->NewLongArray(env, arrayLen);
    if (!out) {
        free(results);
        return NULL;
    }

    jlong header[4] = {
        layout.rows,
        layout.columns,
        layout.tileWidth,
        layout.tileHeight,
    };
    (*env)->SetLongArrayRegion(env, out, 0, 4, header);

    /* TileHashResult is {int64_t, int64_t} — same layout as jlong[2] */
    (*env)->SetLongArrayRegion(env, out, 4, totalTiles * 2, (const jlong *)results);

    free(results);
    return out;
}
