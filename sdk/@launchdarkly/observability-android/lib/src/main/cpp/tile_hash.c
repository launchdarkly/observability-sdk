#include "tile_hash.h"
#include <string.h>

TileHashResult tile_hash(const uint8_t *ptr, int startX, int startY, int endX, int endY, int bytesPerRow) {
    uint64_t hashLo = 5163949831757626579ULL;
    uint64_t hashHi = 4657936482115123397ULL;
    const uint64_t primeLo = 1238197591667094937ULL;
    const uint64_t primeHi = 1700294137212722571ULL;

    const int pixelCount = endX - startX;
    const int pairCount = pixelCount >> 1;
    const int hasTrailingPixel = pixelCount & 1;

    for (int y = startY; y < endY; y++) {
        const uint8_t *p = ptr + y * bytesPerRow + startX * 4;

        for (int i = 0; i < pairCount; i++) {
            uint64_t v;
            memcpy(&v, p, 8);
            hashLo = (hashLo ^ v) * primeLo;
            hashHi = (hashHi ^ v) * primeHi;
            p += 8;
        }

        if (hasTrailingPixel) {
            uint64_t v = 0;
            memcpy(&v, p, 4);
            hashLo = (hashLo ^ v) * primeLo;
            hashHi = (hashHi ^ v) * primeHi;
        }
    }

    TileHashResult result;
    result.hashLo = (int64_t)hashLo;
    result.hashHi = (int64_t)hashHi;
    return result;
}
