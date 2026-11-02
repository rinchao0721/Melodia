cmake_minimum_required(VERSION 3.20)
project(melodia_installer LANGUAGES CXX RC)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# 静态 CRT：卸载程序复制到临时目录、安装程序首次运行时都不能依赖外部 VC++ 运行库
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

add_library(installer_common STATIC
    common/Elevate.cpp
    common/MsiEngine.cpp
    common/Sys.cpp
    common/Ui.cpp
)
target_include_directories(installer_common PUBLIC common)
target_compile_definitions(installer_common PUBLIC
    WIN32_LEAN_AND_MEAN NOMINMAX UNICODE _UNICODE _WIN32_WINNT=0x0A00 WINVER=0x0A00 _WIN32_MSI=500
)
target_compile_options(installer_common PUBLIC /EHsc /permissive- /utf-8 /W4)
target_link_libraries(installer_common PUBLIC
    ws2_32 msi dwmapi gdiplus shell32 ole32 advapi32 user32 gdi32 comctl32 uxtheme
)

add_executable(uninstall WIN32
    uninstall/main.cpp
    uninstall/uninstall.rc
    common/app.manifest
)
target_link_libraries(uninstall PRIVATE installer_common)

add_executable(msipatch tools/msipatch.cpp)
target_compile_definitions(msipatch PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX UNICODE _UNICODE _WIN32_MSI=500)
target_compile_options(msipatch PRIVATE /EHsc /permissive- /utf-8 /W4)
target_link_libraries(msipatch PRIVATE msi)

# 安装程序内嵌 MSI，只在打包流程传入 MSI 路径时构建
if(MELODIA_SETUP_MSI)
    if(NOT EXISTS "${MELODIA_SETUP_MSI}")
        message(FATAL_ERROR "找不到要内嵌的 MSI：${MELODIA_SETUP_MSI}")
    endif()
    if(NOT MELODIA_VERSION MATCHES "^[0-9]+\\.[0-9]+\\.[0-9]+$")
        message(FATAL_ERROR "MELODIA_VERSION 需形如 1.2.3：${MELODIA_VERSION}")
    endif()
    if(NOT MELODIA_DISPLAY_VERSION)
        set(MELODIA_DISPLAY_VERSION "${MELODIA_VERSION}")
    endif()

    get_filename_component(MELODIA_ICON "${CMAKE_CURRENT_SOURCE_DIR}/../../icons/melodia.ico" ABSOLUTE)
    get_filename_component(MELODIA_LICENSE "${CMAKE_CURRENT_SOURCE_DIR}/../../../LICENSE" ABSOLUTE)
    if(NOT EXISTS "${MELODIA_LICENSE}")
        message(FATAL_ERROR "找不到许可证文件：${MELODIA_LICENSE}")
    endif()
    file(TO_CMAKE_PATH "${MELODIA_SETUP_MSI}" MELODIA_SETUP_MSI_PATH)
    string(REPLACE "." "," MELODIA_VERSION_COMMA "${MELODIA_VERSION}")

    configure_file(setup/setup.rc.in "${CMAKE_CURRENT_BINARY_DIR}/setup.rc" @ONLY)
    configure_file(setup/SetupConfig.h.in "${CMAKE_CURRENT_BINARY_DIR}/SetupConfig.h" @ONLY)
    # MSI 内容变化时重新编译资源
    set_source_files_properties("${CMAKE_CURRENT_BINARY_DIR}/setup.rc" PROPERTIES
        OBJECT_DEPENDS "${MELODIA_SETUP_MSI_PATH};${MELODIA_LICENSE};${MELODIA_ICON}"
    )

    add_executable(setup WIN32
        setup/main.cpp
        "${CMAKE_CURRENT_BINARY_DIR}/setup.rc"
        common/app.manifest
    )
    target_include_directories(setup PRIVATE "${CMAKE_CURRENT_BINARY_DIR}")
    target_link_libraries(setup PRIVATE installer_common)
endif()
