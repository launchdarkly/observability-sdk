cmake_minimum_required(VERSION 3.22.1)
project(tile_hash_jni C)

add_library(session_replay_c STATIC IMPORTED)
set_target_properties(session_replay_c PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/libsession_replay_c.a
    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/include
)

add_library(tile_hash SHARED
    tile_hash_jni.c
)

target_include_directories(tile_hash PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/include
)

target_compile_options(tile_hash PRIVATE -O3)
target_link_options(tile_hash PRIVATE -Wl,-z,max-page-size=16384)

find_library(jnigraphics-lib jnigraphics)

target_link_libraries(tile_hash session_replay_c ${jnigraphics-lib})
