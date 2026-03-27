cmake_minimum_required(VERSION 3.22.1)
project(tile_hash C)

add_library(tile_hash SHARED
    nearest_divisor.c
    tile_hash.c
    tile_hash_jni.c
)

target_compile_options(tile_hash PRIVATE -O3)
target_link_options(tile_hash PRIVATE -Wl,-z,max-page-size=16384)

find_library(jnigraphics-lib jnigraphics)

target_link_libraries(tile_hash ${jnigraphics-lib})
