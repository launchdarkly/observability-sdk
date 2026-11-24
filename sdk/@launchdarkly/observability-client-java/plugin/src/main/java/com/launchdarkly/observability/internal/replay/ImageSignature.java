package com.launchdarkly.observability.internal.replay;

import java.awt.Rectangle;
import java.util.Arrays;

/**
 * A frame's pixels as a grid of tile hashes, which tells what changed between two frames without
 * keeping either. The Android plugin computes the same in native code; this is plain Java over the
 * image's {@code int} pixels, a few milliseconds for a full-HD frame.
 */
final class ImageSignature {
  static final int TILE_SIZE = 32;

  final int rows;
  final int columns;
  final int tileSize;
  final long[] tiles;

  private ImageSignature(int rows, int columns, int tileSize, long[] tiles) {
    this.rows = rows;
    this.columns = columns;
    this.tileSize = tileSize;
    this.tiles = tiles;
  }

  /** The signature of an image of {@code width} by {@code height} pixels, row by row in {@code pixels}. */
  static ImageSignature of(int[] pixels, int width, int height) {
    int tileSize = TILE_SIZE;
    int columns = (width + tileSize - 1) / tileSize;
    int rows = (height + tileSize - 1) / tileSize;
    long[] tiles = new long[rows * columns];
    Arrays.fill(tiles, 0xcbf29ce484222325L); // FNV-1a's offset basis
    for (int y = 0; y < height; y++) {
      int rowBase = (y / tileSize) * columns;
      int offset = y * width;
      for (int column = 0; column < columns; column++) {
        int start = column * tileSize;
        int end = Math.min(width, start + tileSize);
        long h = tiles[rowBase + column];
        for (int x = start; x < end; x++) {
          h = (h ^ pixels[offset + x]) * 0x100000001b3L;
        }
        tiles[rowBase + column] = h;
      }
    }
    return new ImageSignature(rows, columns, tileSize, tiles);
  }

  /**
   * The smallest tile-aligned rectangle holding every tile that differs from {@code previous}: the
   * whole grid if there is no previous frame or the grids differ, and null if nothing changed.
   */
  Rectangle diff(ImageSignature previous) {
    if (previous == null || previous.rows != rows || previous.columns != columns) {
      return new Rectangle(0, 0, columns * tileSize, rows * tileSize);
    }
    int minRow = Integer.MAX_VALUE;
    int maxRow = -1;
    int minColumn = Integer.MAX_VALUE;
    int maxColumn = -1;
    for (int i = 0; i < tiles.length; i++) {
      if (tiles[i] != previous.tiles[i]) {
        int row = i / columns;
        int column = i % columns;
        minRow = Math.min(minRow, row);
        maxRow = Math.max(maxRow, row);
        minColumn = Math.min(minColumn, column);
        maxColumn = Math.max(maxColumn, column);
      }
    }
    if (maxRow < 0) {
      return null;
    }
    return new Rectangle(minColumn * tileSize, minRow * tileSize, (maxColumn - minColumn + 1) * tileSize,
        (maxRow - minRow + 1) * tileSize);
  }

  /** The whole frame as one 128-bit value, which names its image in the replay. */
  ExportFrame.Signature digest() {
    long low = 0x9e3779b97f4a7c15L ^ rows;
    long high = 0xc2b2ae3d27d4eb4fL ^ columns;
    for (long tile : tiles) {
      low = mix(low ^ tile);
      high = mix(high + tile * 0x165667b19e3779f9L);
    }
    return new ExportFrame.Signature(mix(low ^ tileSize), mix(high ^ tiles.length));
  }

  /** The finalizer of SplitMix64, which spreads every input bit over the output. */
  static long mix(long z) {
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return z ^ (z >>> 31);
  }
}
