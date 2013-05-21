LOCAL_PATH := $(call my-dir)

#====================================== libvorbisidec

include $(CLEAR_VARS)

LOCAL_SRC_FILES = \
	tremor/bitwise.c \
	tremor/codebook.c \
	tremor/dsp.c \
	tremor/floor0.c \
	tremor/floor1.c \
	tremor/floor_lookup.c \
	tremor/framing.c \
	tremor/info.c \
	tremor/mapping0.c \
	tremor/mdct.c \
	tremor/misc.c \
	tremor/res012.c \
	tremor/vorbisfile.c

LOCAL_CFLAGS+= -O2 -fsigned-char

ifeq ($(TARGET_ARCH),arm)
LOCAL_CFLAGS+= -D_ARM_ASSEM_
endif
	
zLOCAL_C_INCLUDES:= \
	$(LOCAL_PATH)/tremor

LOCAL_ARM_MODE := arm

LOCAL_MODULE := libvorbisidec

include $(BUILD_STATIC_LIBRARY)

#====================================== noisetracks

include $(CLEAR_VARS)

LOCAL_MODULE           := nt

LOCAL_CFLAGS           := -I$(LOCAL_PATH)/tremor
LOCAL_C_INCLUDES       := $(LOCAL_PATH)/tremor
LOCAL_SRC_FILES        := VorbisDecoder.cpp

LOCAL_STATIC_LIBRARIES := libvorbisidec

include $(BUILD_SHARED_LIBRARY)
