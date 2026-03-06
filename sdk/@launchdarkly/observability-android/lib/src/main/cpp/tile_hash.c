#include "tile_hash.h"

#if defined(__ARM_NEON) && defined(__OPTIMIZE__)
#include <arm_neon.h>
#endif

#define TILE_W 64
#define TILE_ROW_BYTES (TILE_W * 4)

typedef uint64_t unaligned_u64 __attribute__((aligned(1)));
typedef uint32_t unaligned_u32 __attribute__((aligned(1)));

static int nearest_divisor(int value, int preferred, int rangeLo, int rangeHi) {
    if (value <= 0) return preferred;
    if (preferred >= rangeLo && preferred <= rangeHi &&
        preferred > 0 && value % preferred == 0)
        return preferred;

    int maxDist = rangeHi - preferred;
    if (preferred - rangeLo > maxDist) maxDist = preferred - rangeLo;
    if (maxDist <= 0) return preferred;

    for (int offset = 1; offset <= maxDist; offset++) {
        int pos = preferred + offset;
        if (pos >= rangeLo && pos <= rangeHi && pos > 0 && value % pos == 0)
            return pos;
        int neg = preferred - offset;
        if (neg >= rangeLo && neg <= rangeHi && neg > 0 && value % neg == 0)
            return neg;
    }
    return preferred;
}

TileHashResult tile_hash_w64_scalar(const unsigned char *rowPtr,
                                     int rows,
                                     int bytesPerRow) {
    uint64_t h0 = UINT64_C(0x517cc1b727220a95);
    uint64_t h1 = UINT64_C(0x6c62272e07bb0142);
    uint64_t h2 = UINT64_C(0x9e3779b97f4a7c15);
    uint64_t h3 = UINT64_C(0xbf58476d1ce4e5b9);

    for (int y = 0; y < rows; y++) {
        const unsigned char *p = rowPtr;
        for (int i = 0; i < 8; i++) {
            h0 += *(const unaligned_u64 *)(p);
            h1 += *(const unaligned_u64 *)(p + 8);
            h2 += *(const unaligned_u64 *)(p + 16);
            h3 += *(const unaligned_u64 *)(p + 24);
            p += 32;
        }
        h0 ^= h2; h1 ^= h3;
        h2 += h0; h3 += h1;
        rowPtr += bytesPerRow;
    }

    h0 ^= h2; h1 ^= h3;
    h0 ^= h0 >> 33; h0 *= UINT64_C(0xff51afd7ed558ccd); h0 ^= h0 >> 33;
    h1 ^= h1 >> 29; h1 *= UINT64_C(0xc4ceb9fe1a85ec53); h1 ^= h1 >> 29;

    TileHashResult result;
    result.hashLo = (int64_t)h0;
    result.hashHi = (int64_t)h1;
    return result;
}

#if defined(__ARM_NEON)
TileHashResult tile_hash_w64_neon(const unsigned char *rowPtr,
                                   int rows,
                                   int bytesPerRow) {
    uint64x2_t s0 = vcombine_u64(vcreate_u64(UINT64_C(0x517cc1b727220a95)),
                                  vcreate_u64(UINT64_C(0x6c62272e07bb0142)));
    uint64x2_t s1 = vcombine_u64(vcreate_u64(UINT64_C(0x9e3779b97f4a7c15)),
                                  vcreate_u64(UINT64_C(0xbf58476d1ce4e5b9)));

    for (int y = 0; y < rows; y++) {
        const unsigned char *p = rowPtr;
        for (int i = 0; i < 8; i++) {
            s0 = vaddq_u64(s0, vld1q_u64((const uint64_t *)p));
            s1 = vaddq_u64(s1, vld1q_u64((const uint64_t *)(p + 16)));
            p += 32;
        }
        s0 = veorq_u64(s0, s1);
        s1 = vaddq_u64(s1, s0);
        rowPtr += bytesPerRow;
    }

    uint64_t h0 = vgetq_lane_u64(s0, 0);
    uint64_t h1 = vgetq_lane_u64(s0, 1);
    uint64_t h2 = vgetq_lane_u64(s1, 0);
    uint64_t h3 = vgetq_lane_u64(s1, 1);

    h0 ^= h2; h1 ^= h3;
    h0 ^= h0 >> 33; h0 *= UINT64_C(0xff51afd7ed558ccd); h0 ^= h0 >> 33;
    h1 ^= h1 >> 29; h1 *= UINT64_C(0xc4ceb9fe1a85ec53); h1 ^= h1 >> 29;

    TileHashResult result;
    result.hashLo = (int64_t)h0;
    result.hashHi = (int64_t)h1;
    return result;
}
#endif

static inline TileHashResult tile_hash_w64(const unsigned char *rowPtr,
                                            int rows,
                                            int bytesPerRow) {
#if defined(__ARM_NEON) && defined(__OPTIMIZE__)
    return tile_hash_w64_neon(rowPtr, rows, bytesPerRow);
#else
    return tile_hash_w64_scalar(rowPtr, rows, bytesPerRow);
#endif
}

TileHashResult tile_hash(const void *data,
                         int startX, int startY,
                         int endX,   int endY,
                         int bytesPerRow) {
    const int byteWidth = (endX - startX) * 4;
    const int quads     = byteWidth >> 5;
    const int remBytes  = byteWidth & 31;
    const int rem8      = remBytes >> 3;
    const int tail      = remBytes & 4;

    const unsigned char *rowPtr = (const unsigned char *)data
                                + (size_t)startY * bytesPerRow
                                + (size_t)startX * 4;

    uint64_t h0 = UINT64_C(0x517cc1b727220a95);
    uint64_t h1 = UINT64_C(0x6c62272e07bb0142);
    uint64_t h2 = UINT64_C(0x9e3779b97f4a7c15);
    uint64_t h3 = UINT64_C(0xbf58476d1ce4e5b9);

    for (int y = startY; y < endY; y++) {
        const unsigned char *p = rowPtr;

        for (int i = 0; i < quads; i++) {
            h0 += *(const unaligned_u64 *)(p);
            h1 += *(const unaligned_u64 *)(p + 8);
            h2 += *(const unaligned_u64 *)(p + 16);
            h3 += *(const unaligned_u64 *)(p + 24);
            p += 32;
        }

        if (rem8 >= 1) h0 += *(const unaligned_u64 *)(p);
        if (rem8 >= 2) h1 += *(const unaligned_u64 *)(p + 8);
        if (rem8 >= 3) h2 += *(const unaligned_u64 *)(p + 16);
        if (tail) h3 += (uint64_t)(*(const unaligned_u32 *)(p + rem8 * 8));

        h0 ^= h2; h1 ^= h3;
        h2 += h0; h3 += h1;

        rowPtr += bytesPerRow;
    }

    h0 ^= h2; h1 ^= h3;
    h0 ^= h0 >> 33; h0 *= UINT64_C(0xff51afd7ed558ccd); h0 ^= h0 >> 33;
    h1 ^= h1 >> 29; h1 *= UINT64_C(0xc4ceb9fe1a85ec53); h1 ^= h1 >> 29;

    TileHashResult result;
    result.hashLo = (int64_t)h0;
    result.hashHi = (int64_t)h1;
    return result;
}

TileLayout tile_compute_layout(int imageWidth, int imageHeight) {
    TileLayout layout;
    layout.tileWidth  = TILE_W;
    layout.tileHeight = nearest_divisor(imageHeight, 22, 22, 44);
    layout.columns = (imageWidth + TILE_W - 1) / TILE_W;
    layout.rows    = (imageHeight + layout.tileHeight - 1) / layout.tileHeight;
    return layout;
}

void tile_compute_all(const void *data,
                      int imageWidth, int imageHeight,
                      int bytesPerRow,
                      TileLayout layout,
                      TileHashResult *out) {
    const int fullCols = imageWidth / TILE_W;
    int idx = 0;

    for (int row = 0; row < layout.rows; row++) {
        int startY = row * layout.tileHeight;
        int tileRows = layout.tileHeight;
        if (startY + tileRows > imageHeight) tileRows = imageHeight - startY;

        for (int col = 0; col < fullCols; col++) {
            const unsigned char *rowPtr = (const unsigned char *)data
                                        + (size_t)startY * bytesPerRow
                                        + (size_t)(col * TILE_W) * 4;
            out[idx] = tile_hash_w64(rowPtr, tileRows, bytesPerRow);
            idx++;
        }

        if (fullCols < layout.columns) {
            int startX = fullCols * TILE_W;
            out[idx] = tile_hash(data, startX, startY, imageWidth, startY + tileRows, bytesPerRow);
            idx++;
        }
    }
}
