cmake_minimum_required(VERSION 3.22.1)
project(tilehash C)

add_library(tilehash SHARED
    tile_hash.c
    tile_hash_jni.c
)

target_include_directories(tilehash PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)
target_link_libraries(tilehash ${log-lib})
