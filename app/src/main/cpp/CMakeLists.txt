cmake_minimum_required(VERSION 3.22.1)

project("whisper-jni")

# Enable C++17 and optimization flags matching FUTO / whisper.cpp
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_options(-O3 -flto -march=armv8-a+simd -ffast-math)
elseif (ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_options(-O3 -flto -march=armv7-a -mfpu=neon -mfloat-abi=softfp -ffast-math)
else()
    add_compile_options(-O3 -flto -ffast-math)
endif()

add_library(
        whisper-jni
        SHARED
        whisper_jni.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        whisper-jni
        ${log-lib}
        android
)
