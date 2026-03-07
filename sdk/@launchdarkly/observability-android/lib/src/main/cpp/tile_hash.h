#ifndef TILE_HASH_H
#define TILE_HASH_H

#include <stdint.h>

typedef struct {
    int64_t hashLo;
    int64_t hashHi;
} TileHashResult;

typedef struct {
    int rows;
    int columns;
    int tileWidth;
    int tileHeight;
} TileLayout;

/// Computes a fast non-cryptographic hash over the pixel rectangle
/// [startX, endX) x [startY, endY) in a 4-bytes-per-pixel bitmap.
TileHashResult tile_hash(const void *data,
                         int startX, int startY,
                         int endX,   int endY,
                         int bytesPerRow);

/// Always-scalar variant of tile_hash_w64, for parity testing.
TileHashResult tile_hash_w64_scalar(const unsigned char *rowPtr,
                                     int rows,
                                     int bytesPerRow);

#if defined(__ARM_NEON) && defined(__OPTIMIZE__)
/// Always-NEON variant of tile_hash_w64, for parity testing.
TileHashResult tile_hash_w64_neon(const unsigned char *rowPtr,
                                   int rows,
                                   int bytesPerRow);
#endif

/// Computes tile layout (tile dimensions, row/column counts) for an image.
TileLayout tile_compute_layout(int imageWidth, int imageHeight);

/// Hashes every tile in the image and writes results to `out`.
/// `out` must have space for layout.rows * layout.columns elements.
void tile_compute_all(const void *data,
                      int imageWidth, int imageHeight,
                      int bytesPerRow,
                      TileLayout layout,
                      TileHashResult *out);

#endif
