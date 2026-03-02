#ifndef TILE_HASH_H
#define TILE_HASH_H

#include <stdint.h>

typedef struct {
    int64_t hashLo;
    int64_t hashHi;
} TileHashResult;

TileHashResult tile_hash(const uint8_t *ptr, int startX, int startY, int endX, int endY, int bytesPerRow);

#endif
