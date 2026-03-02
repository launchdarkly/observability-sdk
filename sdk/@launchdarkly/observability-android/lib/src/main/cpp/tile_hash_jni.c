#include <jni.h>
#include "tile_hash.h"

JNIEXPORT jlongArray JNICALL
Java_com_launchdarkly_observability_replay_capture_TileSignatureManager_nativeComputeAllTileHashes(
    JNIEnv *env, jobject thiz,
    jintArray pixels, jint width, jint height,
    jint tileWidth, jint tileHeight)
{
    const int tilesX = (width + tileWidth - 1) / tileWidth;
    const int tilesY = (height + tileHeight - 1) / tileHeight;
    const int tileCount = tilesX * tilesY;
    const int bytesPerRow = width * 4;

    jlongArray out = (*env)->NewLongArray(env, tileCount * 2);
    if (!out) return NULL;

    jint *arr = (*env)->GetPrimitiveArrayCritical(env, pixels, NULL);
    if (!arr) return NULL;

    jlong *outBuf = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    if (!outBuf) {
        (*env)->ReleasePrimitiveArrayCritical(env, pixels, arr, JNI_ABORT);
        return NULL;
    }

    const uint8_t *ptr = (const uint8_t *)arr;
    int idx = 0;
    for (int ty = 0; ty < tilesY; ty++) {
        int startY = ty * tileHeight;
        int endY = startY + tileHeight;
        if (endY > height) endY = height;

        for (int tx = 0; tx < tilesX; tx++) {
            int startX = tx * tileWidth;
            int endX = startX + tileWidth;
            if (endX > width) endX = width;

            TileHashResult r = tile_hash(ptr, startX, startY, endX, endY, bytesPerRow);
            outBuf[idx]     = r.hashLo;
            outBuf[idx + 1] = r.hashHi;
            idx += 2;
        }
    }

    (*env)->ReleasePrimitiveArrayCritical(env, out, outBuf, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, pixels, arr, JNI_ABORT);
    return out;
}
