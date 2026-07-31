cmake_minimum_required(VERSION 3.22.1)
project(aura_mnn_jni)

add_library(aura_mnn_jni SHARED aura_mnn_jni.cpp)
target_compile_features(aura_mnn_jni PRIVATE cxx_std_17)
target_link_libraries(aura_mnn_jni log dl)
