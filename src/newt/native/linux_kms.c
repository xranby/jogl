/**
 * Copyright 2012 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>

/** 
 * See references in header file.
 */
#include "linux_kms.h"

#include "jogamp_newt_driver_linux_kms_DisplayDriver.h"
#include "jogamp_newt_driver_linux_kms_ScreenDriver.h"
#include "jogamp_newt_driver_linux_kms_WindowDriver.h"
#define VERBOSE_ON 1

#ifdef VERBOSE_ON
    #define DBG_PRINT(...) fprintf(stderr, __VA_ARGS__); fflush(stderr) 
#else
    #define DBG_PRINT(...)
#endif

static jmethodID setScreenSizeID = NULL;

static jmethodID sizeChangedID = NULL;
static jmethodID positionChangedID = NULL;
static jmethodID visibleChangedID = NULL;
static jmethodID windowDestroyNotifyID = NULL;


static const struct drm *drm;
static const struct gbm *gbm;
/**
 * Display
 */

JNIEXPORT jboolean JNICALL Java_jogamp_newt_driver_linux_kms_DisplayDriver_initIDs
  (JNIEnv *env, jclass clazz)
{
    DBG_PRINT( "KMS.Display initIDs ok\n" );
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL Java_jogamp_newt_driver_linux_kms_DisplayDriver_OpenKMSDisplay0
  (JNIEnv *env, jclass clazz)
{
    const char *device = "/dev/dri/card0";
    uint64_t modifier = DRM_FORMAT_MOD_INVALID;

    drm = init_drm_legacy(device);
    if (!drm) {
    	DBG_PRINT("failed to initialize DRM\n");
    	return JNI_FALSE;
    }

    gbm = init_gbm(drm->fd, drm->mode->hdisplay, drm->mode->vdisplay,
    	modifier);
    if (!gbm) {
    	DBG_PRINT("failed to initialize GBM\n");
    	return JNI_FALSE;
    }

    DBG_PRINT( "KMS.Display Open %p\n", (void*)(intptr_t)gbm->dev);
    return (jlong) (intptr_t) gbm->dev;
}

JNIEXPORT void JNICALL Java_jogamp_newt_driver_linux_kms_DisplayDriver_CloseKMSDisplay0
  (JNIEnv *env, jclass clazz, jlong display)
{
   DBG_PRINT( "KMS.Display Close %p\n", (void*)(intptr_t)display);
}

/*JNIEXPORT void JNICALL Java_jogamp_newt_driver_linux_kms_DisplayDriver_GetKMSDev0
  (JNIEnv *env, jclass clazz, jlong display)
{
   DBG_PRINT( "KMS.Display Close %p\n", (void*)(intptr_t)display);
   return (jlong) (intptr_t) gbm->dev;
}*/

JNIEXPORT void JNICALL Java_jogamp_newt_driver_linux_kms_DisplayDriver_DispatchMessages0
  (JNIEnv *env, jclass clazz)
{
}


JNIEXPORT jlong JNICALL Java_jogamp_newt_driver_linux_kms_DisplayDriver_CreatePointerIcon0
  (JNIEnv *env, jclass clazz, jobject pixels, jint pixels_byte_offset, jboolean pixels_is_direct, jint width, jint height, jint hotX, jint hotY)
{
    return 0;
}

JNIEXPORT void JNICALL Java_jogamp_newt_driver_linux_kms_DisplayDriver_DestroyPointerIcon0
  (JNIEnv *env, jclass clazz, jlong handle)
{
    return;
}

JNIEXPORT void JNICALL Java_jogamp_newt_driver_linux_kms_DisplayDriver_SetPointerIcon0
  (JNIEnv *env, jclass clazz, jlong display, jlong handle, jboolean enable, jint x, jint y)
{
    return;
}

JNIEXPORT void JNICALL Java_jogamp_newt_driver_linux_kms_DisplayDriver_MovePointerIcon0
  (JNIEnv *env, jclass clazz, jlong handle, jint x, jint y)
{
    return; 
}

/**
 * Screen
 */

JNIEXPORT jboolean JNICALL Java_jogamp_newt_driver_linux_kms_ScreenDriver_initIDs
  (JNIEnv *env, jclass clazz)
{
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_jogamp_newt_driver_linux_kms_ScreenDriver_initNative
  (JNIEnv *env, jobject obj)
{
}

/**
 * Window
 */

JNIEXPORT jboolean JNICALL Java_jogamp_newt_driver_linux_kms_WindowDriver_initIDs
  (JNIEnv *env, jclass clazz)
{
    sizeChangedID = (*env)->GetMethodID(env, clazz, "sizeChanged", "(ZIIZ)V");
    positionChangedID = (*env)->GetMethodID(env, clazz, "positionChanged", "(ZII)V");
    visibleChangedID = (*env)->GetMethodID(env, clazz, "visibleChanged", "(ZZ)V");
    windowDestroyNotifyID = (*env)->GetMethodID(env, clazz, "windowDestroyNotify", "(Z)Z");
    if (sizeChangedID == NULL ||
        positionChangedID == NULL ||
        visibleChangedID == NULL ||
        windowDestroyNotifyID == NULL) {
        DBG_PRINT( "initIDs failed\n" );
        return JNI_FALSE;
    }
    DBG_PRINT( "KMS.Window initIDs ok\n" );
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL Java_jogamp_newt_driver_linux_kms_WindowDriver_CreateWindow0
  (JNIEnv *env, jobject obj, jlong display, jint layer, jint x, jint y, jint width, jint height, jboolean opaque, jint alphaBits)
{
   /*gbm.surface = gbm_surface_create(gbm.dev,
            drm.mode->hdisplay, drm.mode->vdisplay,
   			GBM_FORMAT_XRGB8888,
            GBM_BO_USE_SCANOUT | GBM_BO_USE_RENDERING);*/
   return gbm->surface;
}

JNIEXPORT void JNICALL Java_jogamp_newt_driver_linux_kms_WindowDriver_CloseWindow0
  (JNIEnv *env, jobject obj, jlong display, jlong window)
{
    return;
}

