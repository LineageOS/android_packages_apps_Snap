LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-recyclerview
LOCAL_STATIC_JAVA_LIBRARIES += xmp_toolkit
LOCAL_STATIC_JAVA_LIBRARIES += androidx.heifwriter_heifwriter
LOCAL_STATIC_JAVA_LIBRARIES += zxing-core

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += $(call all-java-files-under, src_pd)
LOCAL_SRC_FILES += $(call all-java-files-under, src_pd_gcam)
LOCAL_SRC_FILES += $(call all-java-files-under, src_wrapper)
LOCAL_SRC_FILES += $(call all-java-files-under, quickReader/src)
LOCAL_SRC_FILES += $(call all-renderscript-files-under, rs)

LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res
LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/quickReader/res
LOCAL_RESOURCE_DIR += frameworks/support/v7/recyclerview/res

include $(LOCAL_PATH)/version.mk
LOCAL_AAPT_FLAGS := \
        --auto-add-overlay \
        --version-name "$(version_name_package)" \
        --version-code $(version_code_package) \
        --extra-packages me.dm7.barcodescanner.core \
        --extra-packages me.dm7.barcodescanner.zxing

LOCAL_STATIC_JAVA_AAR_LIBRARIES += \
    qreader-core \
    qreader-zxing

LOCAL_JAVA_LIBRARIES := org.lineageos.platform.internal

LOCAL_PACKAGE_NAME := Snap
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_CERTIFICATE := platform

LOCAL_AAPT_FLAGS += --rename-manifest-package org.lineageos.snap

#LOCAL_SDK_VERSION := current
LOCAL_RENDERSCRIPT_TARGET_API := 23

LOCAL_OVERRIDES_PACKAGES := Camera2

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

# If this is an unbundled build (to install separately) then include
# the libraries in the APK, otherwise just put them in /system/lib and
# leave them out of the APK

ifneq (,$(TARGET_BUILD_APPS))
  LOCAL_JNI_SHARED_LIBRARIES := libjni_snapmosaic libjni_snaptinyplanet libjni_snapimageutil
else
  LOCAL_REQUIRED_MODULES := libjni_snapmosaic libjni_snaptinyplanet libjni_snapimageutil
endif

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += \
    qreader-core:quickReader/libs/core-1.9.8.aar \
    qreader-zxing:quickReader/libs/zxing-1.9.8.aar \
    zxing-core:quickReader/libs/zxing-core-g-3.3.3.jar

include $(BUILD_MULTI_PREBUILT)

include $(call all-makefiles-under, $(LOCAL_PATH))
