@rem @echo off
set ANDROID_NDK=E:\Android\sdk\ndk\21.3.6528147
set ANDROID_PLATFORM=android-21
set CMAKE=E:\Android\sdk\cmake\3.10.2.4988404\bin\cmake.exe
set CMAKE_BUILD_TYPE=Release
set CMAKE_MAKE_PROGRAM=E:\Android\sdk\cmake\3.10.2.4988404\bin\ninja.exe
set CMAKE_TOOLCHAIN_FILE=E:\Android\sdk\ndk\21.3.6528147\build\cmake\android.toolchain.cmake

@rem ics-openvpn arm64-v8a
set ANDROID_ABI=arm64-v8a
set SOURCE_DIR=D:\open_my_work\ics-openvpn\main\src\main\cpp
set BUILD_DIR=D:\open_my_work\ics-openvpn\main\.cxx\cmake\uiRelease\%ANDROID_ABI%
set OUTPUT_DIR=D:\open_my_work\ics-openvpn\main\src\main\jniLibs\%ANDROID_ABI%

%CMAKE% -DANDROID_NDK=%ANDROID_NDK% -DCMAKE_BUILD_TYPE=%CMAKE_BUILD_TYPE% -DCMAKE_MAKE_PROGRAM=%CMAKE_MAKE_PROGRAM% -DCMAKE_GENERATOR=Ninja -DCMAKE_TOOLCHAIN_FILE=%CMAKE_TOOLCHAIN_FILE% -DANDROID_ABI=%ANDROID_ABI% -DANDROID_PLATFORM=%ANDROID_PLATFORM% -H%SOURCE_DIR% -B%BUILD_DIR% -DCMAKE_LIBRARY_OUTPUT_DIRECTORY=%OUTPUT_DIR% 

%CMAKE_MAKE_PROGRAM% -C %BUILD_DIR% %1 %2 %3
