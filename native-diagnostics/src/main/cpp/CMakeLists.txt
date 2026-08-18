cmake_minimum_required(VERSION 3.22.1)
project(dpcaio_native_trace LANGUAGES CXX)

add_library(dpcaio_native_trace SHARED native_trace.cpp)
set_target_properties(dpcaio_native_trace PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED YES)
find_library(log-lib log)
target_link_libraries(dpcaio_native_trace ${log-lib})
