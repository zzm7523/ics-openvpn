cmake_minimum_required(VERSION 3.4.1)

# Git version string
include(GetGitRevisionDescription.cmake)
git_describe(OPENVPN_GIT "D:/open_my_work/openvpn" "--tags" "--always" "--long")
message("OpenVPN 6.x version ${OPENVPN_GIT}")

set(CMAKE_C_FLAGS_DEBUG "-D_DEBUG -DPACKET_BUFFER_LIST_CHECK -DPACKET_BUFFER_RANDOM_DROP -DTHREAD_ACCESS_CHECK")

# STATIC or SHARED
set(SSLLIBTYPE STATIC)
set(OPENSSL_PATH "D:/open_my_work/ics-openvpn/main/src/main/cpp/openssl")

#link_directories(
#        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
#        "D:/open_my_work/ics-openvpn/main/.cxx/cmake/uiRelease/armeabi-v7a"
#)

#add_subdirectory(lzo)
include(tools.cmake)
include(lzo.cmake)
include(lz4.cmake)
include(D:/open_my_work/ics-openvpn/main/src/main/cpp/openssl/openssl.cmake)

add_library(opvpnutil SHARED opvpnutil/jniglue.c opvpnutil/scan_ifs.c)
target_compile_definitions(opvpnutil PRIVATE -DTARGET_ARCH_ABI=\"${ANDROID_ABI}\"
        -DOPENVPN_GIT_REVISION=\"${OPENVPN_GIT}\"
        )
target_link_libraries(opvpnutil log)

#if (NOT ${CMAKE_LIBRARY_OUTPUT_DIRECTORY} MATCHES "build/intermediates/cmake/.*skeleton.*/")
#    add_library(osslspeedtest SHARED opvpnutil/sslspeed.c)
#    target_link_libraries(osslspeedtest log crypto ssl)
#else ()
#    message("Not budiling SSLSpeedTest for output dir ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
#endif ()

set(openvpn_srcs
        src/compat/compat-basename.c
        src/compat/compat-daemon.c
        src/compat/compat-dirname.c
        src/compat/compat-gettimeofday.c
        src/compat/compat-inet_ntop.c
        src/compat/compat-inet_pton.c
        src/openvpn/base64.c
        src/openvpn/buffer.c
        src/openvpn/clinat.c
        src/openvpn/console.c
        src/openvpn/crypto.c
        src/openvpn/crypto_openssl.c
        src/openvpn/cryptoapi.c
        src/openvpn/dhcp.c
        src/openvpn/error.c
        src/openvpn/event.c
        src/openvpn/fdmisc.c
        src/openvpn/forward.c
        src/openvpn/fragment.c
        src/openvpn/gmed_api.c
        src/openvpn/gremlin.c
        src/openvpn/helper.c
        src/openvpn/httpdigest.c
        src/openvpn/init.c
        src/openvpn/interval.c
        src/openvpn/list.c
        src/openvpn/lladdr.c
        src/openvpn/lzo.c
        src/openvpn/manage.c
        src/openvpn/masquerade.c
        src/openvpn/mbuf.c
        src/openvpn/misc.c
        src/openvpn/mroute.c
        src/openvpn/mss.c
        src/openvpn/mstats.c
        src/openvpn/mtcp.c
        src/openvpn/mtu.c
        src/openvpn/mudp.c
        src/openvpn/multi.c
        src/openvpn/multi_crypto.c
        src/openvpn/ntlm.c
        src/openvpn/occ.c
        src/openvpn/openvpn.c
        src/openvpn/options.c
        src/openvpn/otime.c
        src/openvpn/packet_buffer.c
        src/openvpn/packet_id.c
        src/openvpn/perf.c
        src/openvpn/pf.c
        src/openvpn/ping.c
        src/openvpn/pkcs11.c
        src/openvpn/pkcs11_openssl.c
        src/openvpn/platform.c
        src/openvpn/plugin.c
        src/openvpn/pool.c
        src/openvpn/proto.c
        src/openvpn/proxy.c
        src/openvpn/ps.c
        src/openvpn/push.c
        src/openvpn/reliable.c
        src/openvpn/route.c
        src/openvpn/schedule.c
        src/openvpn/session_id.c
        src/openvpn/shaper.c
        src/openvpn/sig.c
        src/openvpn/socket.c
        src/openvpn/socks.c
        src/openvpn/ssl.c
        src/openvpn/ssl_openssl.c
        src/openvpn/ssl_verify.c
        src/openvpn/ssl_verify_openssl.c
        src/openvpn/status.c
        src/openvpn/thread.c
        src/openvpn/tun.c
        )

PREPEND(openvpn_srcs_with_path "D:/open_my_work/openvpn" ${openvpn_srcs})

add_library(openvpn SHARED ${openvpn_srcs_with_path})
target_include_directories(openvpn PRIVATE
        lzo/include
        D:/open_my_work/openvpn/android-config
        D:/open_my_work/openvpn/include
        D:/open_my_work/openvpn/src/compat
        D:/open_my_work/openvpn/src/openvpn
        )
target_compile_definitions(openvpn PRIVATE
        -DHAVE_CONFIG_H
        -DTARGET_ABI=\"${ANDROID_ABI}\"
        -DOPENSSL_API_COMPAT=0x10000000L
        )
target_compile_definitions(openvpn PRIVATE
        -DENABLE_CRYPTO_OPENSSL=1
        )
target_link_libraries(openvpn crypto ssl lzo)

add_executable(libovpnexec.so minivpn/minivpn.c)
target_compile_options(libovpnexec.so PRIVATE -fPIE)
target_link_libraries(libovpnexec.so PRIVATE openvpn)

add_executable(pie_openvpn.${ANDROID_ABI} minivpn/minivpn.c)
target_compile_options(pie_openvpn.${ANDROID_ABI} PRIVATE -fPIE)
target_link_libraries(pie_openvpn.${ANDROID_ABI} PRIVATE openvpn)

add_executable(libfatopenvpn.so ${openvpn_srcs_with_path})
target_compile_options(libfatopenvpn.so PRIVATE -fPIE)
target_include_directories(libfatopenvpn.so PRIVATE
        lzo/include
        D:/open_my_work/openvpn/android-config
        D:/open_my_work/openvpn/include
        D:/open_my_work/openvpn/src/compat
        D:/open_my_work/openvpn/src/openvpn
        )
target_compile_definitions(libfatopenvpn.so PRIVATE
        -DHAVE_CONFIG_H
        -DTARGET_ABI=\"${ANDROID_ABI}\"
        -DOPENSSL_API_COMPAT=0x10000000L
        )
target_compile_definitions(libfatopenvpn.so PRIVATE
        -DENABLE_CRYPTO_OPENSSL=1
        )
target_link_libraries(libfatopenvpn.so PRIVATE crypto ssl lzo)

# Hack to copy OpenVPN binaries to assets directory
set(OVPN_ASSET_DIR ${CMAKE_SOURCE_DIR}/../assets)

add_custom_target(makeassetdir ALL
        COMMAND ${CMAKE_COMMAND} -E make_directory ${OVPN_ASSET_DIR})

add_custom_command(TARGET pie_openvpn.${ANDROID_ABI} POST_BUILD
        COMMAND
        ${CMAKE_COMMAND} -E copy
        ${CMAKE_CURRENT_BINARY_DIR}/pie_openvpn.${ANDROID_ABI}
        ${OVPN_ASSET_DIR}
        )

add_custom_command(TARGET libovpnexec.so POST_BUILD
        COMMAND
        ${CMAKE_COMMAND} -E copy
        ${CMAKE_CURRENT_BINARY_DIR}/libovpnexec.so
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
        )

add_custom_command(TARGET libfatopenvpn.so POST_BUILD
        COMMAND
        ${CMAKE_COMMAND} -E copy
        ${CMAKE_CURRENT_BINARY_DIR}/libfatopenvpn.so
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
        )

# Hack that these targets are really executed
add_dependencies(opvpnutil pie_openvpn.${ANDROID_ABI})
add_dependencies(pie_openvpn.${ANDROID_ABI} makeassetdir)
