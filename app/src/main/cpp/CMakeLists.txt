# CMakeLists.txt for FluidSynth JNI wrapper
# Uses dlopen/dlsym to load FluidSynth at runtime
# libfluidsynth.so is included via jniLibs folder

cmake_minimum_required(VERSION 3.18.1)

project("fluidsynth_jni")

# FluidSynth headers (downloaded from official repo)
set(FLUIDSYNTH_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/include)

# Create our JNI wrapper library
add_library(fluidsynth_jni SHARED
    fluidsynth_jni.cpp
)

# Add FluidSynth headers to include path
target_include_directories(fluidsynth_jni PRIVATE
    ${FLUIDSYNTH_INCLUDE_DIR}
)

# Find the log library for Android logging
find_library(log-lib log)

# We use dlopen/dlsym to load FluidSynth at runtime
# libfluidsynth.so is packaged via jniLibs/arm64-v8a/
target_link_libraries(fluidsynth_jni
    ${log-lib}
    dl  # For dlopen/dlsym
)

# C++17 standard
set_target_properties(fluidsynth_jni PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

# Compiler flags
target_compile_options(fluidsynth_jni PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter  # JNI functions have unused env/clazz params
    -Werror=return-type
    -fvisibility=hidden  # Hide internal symbols
)

# Export JNI functions
target_compile_definitions(fluidsynth_jni PRIVATE
    FLUIDSYNTH_NOT_A_DLL  # Prevent DLL import/export macros on Android
)

# 16KB page alignment for newer Android devices
target_link_options(fluidsynth_jni PRIVATE
    -Wl,-z,max-page-size=16384
)
