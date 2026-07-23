cmake_minimum_required(VERSION 3.22.1)
project(spotamp_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(LLAMA_ROOT ${CMAKE_CURRENT_LIST_DIR}/llama.cpp)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
add_subdirectory(${LLAMA_ROOT} ${CMAKE_CURRENT_BINARY_DIR}/llama-build)

add_library(llama-android SHARED llama_android.cpp)
target_include_directories(llama-android PRIVATE ${LLAMA_ROOT}/include ${LLAMA_ROOT}/ggml/include)
target_link_libraries(llama-android PRIVATE llama llama-common android log)
