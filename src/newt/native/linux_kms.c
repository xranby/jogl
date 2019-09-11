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

#include <EGL/egl.h>

void EGLUtil_SwapWindow( EGLDisplay eglDisplay, EGLSurface eglSurface );

/** 
 * See references in header file.
 */
#include "linux_kms.h"

#include "jogamp_newt_driver_linux_kms_DisplayDriver.h"
#include "jogamp_newt_driver_linux_kms_ScreenDriver.h"
#include "jogamp_newt_driver_linux_kms_WindowDriver.h"


static jmethodID setScreenSizeID = NULL;

static jmethodID sizeChangedID = NULL;
static jmethodID positionChangedID = NULL;
static jmethodID visibleChangedID = NULL;
static jmethodID windowDestroyNotifyID = NULL;

/**
 * Display
 */

JNIEXPORT jboolean JNICALL Java_jogamp_newt_driver_linux_kms_DisplayDriver_initIDs
  (JNIEnv *env, jclass clazz)
{
    DBG_PRINT( "KMS.Display initIDs ok\n" );
    return JNI_TRUE;
}

static fd_set fds;
static drmEventContext evctx = {
			.version = DRM_EVENT_CONTEXT_VERSION,
			.page_flip_handler = page_flip_handler,
	};
static struct gbm_bo *bo;
static struct drm_fb *fb;
static struct gbm_bo *next_bo;
static int waiting_for_flip = 1;

JNIEXPORT jlong JNICALL Java_jogamp_newt_driver_linux_kms_DisplayDriver_OpenKMSDisplay0
  (JNIEnv *env, jclass clazz)
{
    int ret;

    // 1
    ret = init_drm();
    if (ret) {
    	DBG_PRINT("failed to initialize DRM\n");
    	return JNI_FALSE;
    }


    FD_ZERO(&fds);
    FD_SET(0, &fds);
    FD_SET(drm.fd, &fds);

    // 2
    ret = init_gbm();
    if (ret) {
    	DBG_PRINT("failed to initialize GBM\n");
    	return JNI_FALSE;
    }

    // 3
    ret = init_gl();
    if (ret) {
       	DBG_PRINT("failed to initialize EGL\n");
       	return ret;
    }

    DBG_PRINT( "KMS.Display gbm.dev 0x%08x\n", (void*)(intptr_t)gbm.dev);
    DBG_PRINT( "KMS.Display gbm.surface 0x%08x\n", (void*)(intptr_t)gbm.surface);

    DBG_PRINT( "KMS.Display gl.display 0x%08x\n", (void*)(intptr_t)gl.display);
    DBG_PRINT( "KMS.Display gl.config 0x%08x\n", (void*)(intptr_t)gl.config);
    DBG_PRINT( "KMS.Display gl.surface 0x%08x\n", (void*)(intptr_t)gl.surface);
    DBG_PRINT( "KMS.Display gl.context 0x%08x\n", (void*)(intptr_t)gl.context);

    return (jlong) (intptr_t) gbm.dev;
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
  (JNIEnv *env, jobject obj, jlong display, jint width, jint height)
{
   return (jlong) (intptr_t) gbm.surface;
}

JNIEXPORT void JNICALL Java_jogamp_newt_driver_linux_kms_WindowDriver_CloseWindow0
  (JNIEnv *env, jobject obj, jlong display, jlong window)
{
    return;
}


JNIEXPORT void JNICALL Java_jogamp_newt_driver_linux_kms_WindowDriver_SwapWindow
  (JNIEnv *env, jobject obj, jlong display, jlong window)
{
    EGLDisplay dpy  = (EGLDisplay) (intptr_t) display;
    EGLSurface surf = (EGLSurface) (intptr_t) window;

    DBG_PRINT( "[SwapWindow] dpy %p, win %p\n", dpy, surf);

    EGLUtil_SwapWindow( dpy, surf );

    next_bo = gbm_surface_lock_front_buffer(gbm.surface);
    fb = drm_fb_get_from_bo(next_bo);

    /*
     * Here you could also update drm plane layers if you want
     * hw composition
     */

    int ret = drmModePageFlip(drm.fd, drm.crtc_id, fb->fb_id,
    		DRM_MODE_PAGE_FLIP_EVENT, &waiting_for_flip);
    if (ret) {
    	DBG_PRINT("failed to queue page flip: %s\n", strerror(errno));
    }

    while (waiting_for_flip) {
    	ret = select(drm.fd + 1, &fds, NULL, NULL, NULL);
    	if (ret < 0) {
    		DBG_PRINT("select err: %s\n", strerror(errno));
    	} else if (ret == 0) {
    		DBG_PRINT("select timeout!\n");
    	} else if (FD_ISSET(0, &fds)) {
    		DBG_PRINT("user interrupted!\n");
    		break;
    	}
    	drmHandleEvent(drm.fd, &evctx);
   	}

    /* release last buffer to render on again: */
    gbm_surface_release_buffer(gbm.surface, bo);
    bo = next_bo;

    DBG_PRINT( "[SwapWindow] X\n");
}
