#include <jni.h>
#include <android/bitmap.h>
#include <limits.h>
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
    if (layout.rows <= 0 || layout.columns <= 0) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return NULL;
    }

    if ((size_t)layout.rows > SIZE_MAX / (size_t)layout.columns) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return NULL;
    }
    size_t totalTiles = (size_t)layout.rows * (size_t)layout.columns;

    if (totalTiles > SIZE_MAX / sizeof(TileHashResult)) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return NULL;
    }

    TileHashResult *results = (TileHashResult *)malloc(totalTiles * sizeof(TileHashResult));
    if (!results) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return NULL;
    }

    tile_compute_all(pixels,
                     (int)info.width, (int)info.height,
                     (int)info.stride,
                     layout, results);

    AndroidBitmap_unlockPixels(env, bitmap);

    if (totalTiles > (SIZE_MAX - 4U) / 2U) {
        free(results);
        return NULL;
    }
    size_t totalLongs = 4U + totalTiles * 2U;
    if (totalLongs > (size_t)INT_MAX) {
        free(results);
        return NULL;
    }

    const jsize arrayLen = (jsize)totalLongs;
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
    (*env)->SetLongArrayRegion(env, out, 4, (jsize)(totalTiles * 2U), (const jlong *)results);

    free(results);
    return out;
}
