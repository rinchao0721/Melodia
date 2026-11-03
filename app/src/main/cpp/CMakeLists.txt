cmake_minimum_required(VERSION 3.22.1)
project(melodia_native C CXX)

set(LIBUSB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libusb)

# libusb 按 LGPL-2.1 单独编成动态库，源码与许可证见 libusb/
add_library(usb-1.0 SHARED
    ${LIBUSB_DIR}/libusb/core.c
    ${LIBUSB_DIR}/libusb/descriptor.c
    ${LIBUSB_DIR}/libusb/hotplug.c
    ${LIBUSB_DIR}/libusb/io.c
    ${LIBUSB_DIR}/libusb/sync.c
    ${LIBUSB_DIR}/libusb/strerror.c
    ${LIBUSB_DIR}/libusb/os/linux_usbfs.c
    ${LIBUSB_DIR}/libusb/os/events_posix.c
    ${LIBUSB_DIR}/libusb/os/threads_posix.c
    ${LIBUSB_DIR}/libusb/os/linux_netlink.c)
target_include_directories(usb-1.0
    PRIVATE ${LIBUSB_DIR}/android ${LIBUSB_DIR}/libusb/os
    PUBLIC ${LIBUSB_DIR}/libusb)
target_compile_options(usb-1.0 PRIVATE -fvisibility=hidden -pthread)
target_link_libraries(usb-1.0 log)

add_library(melodia_usbaudio SHARED
    usbaudio/usb_audio_stream.cpp
    usbaudio/usb_audio_jni.cpp)
target_compile_features(melodia_usbaudio PRIVATE cxx_std_17)
target_compile_options(melodia_usbaudio PRIVATE -Wall -Wextra -fvisibility=hidden)
target_link_libraries(melodia_usbaudio usb-1.0 log)
