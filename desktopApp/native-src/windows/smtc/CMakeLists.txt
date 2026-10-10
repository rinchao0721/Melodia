cmake_minimum_required(VERSION 3.20)
project(melodia_smtc LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# 静态 CRT，用户机器无需安装 VC++ 运行库
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

add_library(melodia_smtc SHARED smtc.cpp)
target_compile_definitions(melodia_smtc PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX UNICODE _UNICODE)
target_compile_options(melodia_smtc PRIVATE /EHsc /permissive- /utf-8 /W4)
# C++/WinRT 头文件随 Windows SDK 提供，运行时只依赖系统自带的 WindowsApp
target_link_libraries(melodia_smtc PRIVATE WindowsApp)
