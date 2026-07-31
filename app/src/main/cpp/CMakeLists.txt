cmake_minimum_required(VERSION 3.22.1)
project(aura_mnn_jni)

add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libMNN.so"
    INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_LIST_DIR}/mnn/include"
)

add_library(aura_mnn_jni SHARED aura_mnn_jni.cpp)
target_compile_features(aura_mnn_jni PRIVATE cxx_std_17)
target_link_libraries(aura_mnn_jni MNN log)
