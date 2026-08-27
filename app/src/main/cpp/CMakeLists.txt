cmake_minimum_required(VERSION 3.22.1)
project(aura_mnn_jni)

add_library(aura_mnn_jni SHARED aura_mnn_jni.cpp)
target_compile_features(aura_mnn_jni PRIVATE cxx_std_17)
target_link_libraries(aura_mnn_jni log dl)

# llama.cpp 本地推理（阶段② #56/#73）。源码放仓库外（LLAMA_CPP_DIR 环境变量，
# 如 ~/llama.cpp-src），避免撑大仓库；未提供时跳过该 so，app 其余功能不受影响。
set(LLAMA_CPP_DIR "$ENV{LLAMA_CPP_DIR}")
if(EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(STATUS "llama.cpp: building from ${LLAMA_CPP_DIR}")
    set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
    set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    add_subdirectory("${LLAMA_CPP_DIR}" llama.cpp-build EXCLUDE_FROM_ALL)
    add_library(llama_jni SHARED llama_jni.cpp)
    target_compile_features(llama_jni PRIVATE cxx_std_17)
    target_include_directories(llama_jni PRIVATE "${LLAMA_CPP_DIR}/include")
    target_link_libraries(llama_jni log llama)
else()
    message(STATUS "llama.cpp: LLAMA_CPP_DIR not set, skipping llama_jni")
endif()
