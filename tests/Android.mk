LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests

LOCAL_SDK_VERSION := 16

LOCAL_STATIC_JAVA_LIBRARIES := littlemock dexmaker

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := SnapTests

LOCAL_INSTRUMENTATION_FOR := Snap

include $(BUILD_PACKAGE)
