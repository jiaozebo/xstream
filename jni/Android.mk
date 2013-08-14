LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := XStreamClient
LOCAL_SRC_FILES := XStreamClient.cpp
LOCAL_LDLIBS    := -llog
LOCAL_SHARED_LIBRARIES := liblog libcutils
LOCAL_LDLIBS += -L$(SYSROOT)/usr/lib -llog
include $(BUILD_SHARED_LIBRARY)
