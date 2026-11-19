LOCAL_PATH := $(call my-dir)

# xHook (github.com/iqiyi/xHook, MIT) — its sources are vendored into
# $(LOCAL_PATH)/xhook by CI (see .github/workflows/build.yml). Built as a static
# lib so the final module is one self-contained .so (a Zygisk module can't carry
# a runtime .so dependency). -Werror is dropped: a newer NDK warns on its C.
include $(CLEAR_VARS)
LOCAL_MODULE            := xhook
LOCAL_SRC_FILES         := xhook/xhook.c \
                           xhook/xh_core.c \
                           xhook/xh_elf.c \
                           xhook/xh_jni.c \
                           xhook/xh_log.c \
                           xhook/xh_util.c \
                           xhook/xh_version.c
LOCAL_C_INCLUDES        := $(LOCAL_PATH)/xhook
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/xhook
LOCAL_CFLAGS            := -Wall -fvisibility=hidden
LOCAL_CONLYFLAGS        := -std=c11
LOCAL_LDLIBS            := -llog
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE            := qecastshim
LOCAL_SRC_FILES         := shim.cpp
LOCAL_C_INCLUDES        := $(LOCAL_PATH)
LOCAL_CPPFLAGS          := -std=c++17 -fvisibility=hidden -Wall -Wextra
LOCAL_STATIC_LIBRARIES  := xhook
LOCAL_LDLIBS            := -llog
include $(BUILD_SHARED_LIBRARY)
