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

package jogamp.newt.driver.linux.kms;

import java.net.URLConnection;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import com.jogamp.nativewindow.AbstractGraphicsDevice;
import com.jogamp.nativewindow.NativeWindowException;
import com.jogamp.nativewindow.NativeWindowFactory;
import com.jogamp.nativewindow.util.PixelFormat;

import com.jogamp.common.nio.Buffers;
import com.jogamp.common.util.IOUtil;
import com.jogamp.opengl.egl.EGL;
import com.jogamp.opengl.egl.EGLExt;
import com.jogamp.opengl.util.PNGPixelRect;
import com.jogamp.opengl.GLProfile;

import jogamp.newt.DisplayImpl;
import jogamp.newt.NEWTJNILibLoader;
import jogamp.newt.PointerIconImpl;
import jogamp.newt.driver.linux.LinuxMouseTracker;
import jogamp.opengl.egl.EGLDisplayUtil;

public class DisplayDriver extends DisplayImpl {
    static final PNGPixelRect defaultPointerIconImage;

    static {
        NEWTJNILibLoader.loadNEWT();
        GLProfile.initSingleton();
        
        System.out.println("********************************************   KMS DisplayDriver start");

        if (!DisplayDriver.initIDs()) {
            throw new NativeWindowException("Failed to initialize linux.kms Display jmethodIDs");
        }
        
        System.out.println("********************************************   KMS DisplayDriver initIDS");
        
        if (!ScreenDriver.initIDs()) {
            throw new NativeWindowException("Failed to initialize linux.kms Screen jmethodIDs");
        }
        if (!WindowDriver.initIDs()) {
            throw new NativeWindowException("Failed to initialize linux.kms Window jmethodIDs");
        }

        PNGPixelRect image = null;
        if( DisplayImpl.isPNGUtilAvailable() ) {
            final IOUtil.ClassResources res = new IOUtil.ClassResources(new String[] { "newt/data/pointer-grey-alpha-16x24.png" }, DisplayDriver.class.getClassLoader(), null);
            try {
                final URLConnection urlConn = res.resolve(0);
                if( null != urlConn ) {
                    image = PNGPixelRect.read(urlConn.getInputStream(), PixelFormat.BGRA8888, false /* directBuffer */, 0 /* destMinStrideInBytes */, false /* destIsGLOriented */);
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
        defaultPointerIconImage = image;
        System.out.println("********************************************   KMS DisplayDriver initialized");
    }

    public static void initSingleton() {
        System.out.println("********************************************   KMS DisplayDriver initSingleton");
        // just exist to ensure static init has been run
    }


    public DisplayDriver() {
        kmsHandle = 0;
        activePointerIcon = 0;
        activePointerIconVisible = false;
    }

    @Override
    protected void createNativeImpl() {
        kmsHandle = OpenKMSDisplay0();
        System.out.println("<3 <3 <3 Create native KMS implementation "+kmsHandle);

        //long[] handleBuffer = new long[1];
        //handleBuffer[0] = kmsHandle;
        //EGL.eglGetPlatformDisplay(EGLExt.EGL_PLATFORM_GBM_KHR, LongBuffer.wrap(handleBuffer), null);

        aDevice = EGLDisplayUtil.eglCreateEGLGraphicsDevice(kmsHandle, NativeWindowFactory.TYPE_EGL);

        //aDevice = EGLDisplayUtil.eglCreateEGLGraphicsDevice(kmsHandle, AbstractGraphicsDevice.DEFAULT_CONNECTION, AbstractGraphicsDevice.DEFAULT_UNIT);
        aDevice.open();

        if( null != defaultPointerIconImage ) {
            defaultPointerIcon = (PointerIconImpl) createPointerIcon(defaultPointerIconImage, 0, 0);
        } else {
            defaultPointerIcon = null;
        }
        if( DEBUG_POINTER_ICON ) {
            System.err.println("Display.PointerIcon.createDefault: "+defaultPointerIcon);
        }
        if( null != defaultPointerIcon ) {
            final LinuxMouseTracker lmt = LinuxMouseTracker.getSingleton();
            setPointerIconActive(defaultPointerIcon.getHandle(), lmt.getLastX(), lmt.getLastY());
        }
    }
    private PointerIconImpl defaultPointerIcon = null;

    @Override
    protected void closeNativeImpl(final AbstractGraphicsDevice aDevice) {
        aDevice.close();
        CloseKMSDisplay0(kmsHandle);
        kmsHandle = 0;
    }

    /* pp */ final long getKMSHandle() { return kmsHandle; }

    @Override
    protected void dispatchMessagesNative() {
        DispatchMessages0();
    }

    // @Override
    // public final PixelFormat getNativePointerIconPixelFormat() { return PixelFormat.BGRA8888; }

    @Override
    protected final long createPointerIconImpl(final PixelFormat pixelformat, final int width, final int height, final ByteBuffer pixels, final int hotX, final int hotY) {
        return CreatePointerIcon(kmsHandle, pixels, width, height, hotX, hotY);
    }

    @Override
    protected final void destroyPointerIconImpl(final long displayHandle, final long planeHandle) {
        DestroyPointerIcon0(planeHandle);
    }

    /* pp */ void setPointerIconActive(long planeHandle, final int x, final int y) {
        synchronized(pointerIconSync) {
            if( DEBUG_POINTER_ICON ) {
                System.err.println("Display.PointerIcon.set.0: active ["+toHexString(activePointerIcon)+", visible "+activePointerIconVisible+"] -> "+toHexString(planeHandle));
            }
            if( 0 != activePointerIcon && activePointerIconVisible ) {
                SetPointerIcon0(kmsHandle, activePointerIcon, false, x, y);
            }
            if( 0 == planeHandle && null != defaultPointerIcon ) {
                planeHandle = defaultPointerIcon.getHandle();
            }
            if( 0 != planeHandle ) {
                SetPointerIcon0(kmsHandle, planeHandle, true, x, y);
                activePointerIconVisible = true;
            } else {
                activePointerIconVisible = false;
            }
            activePointerIcon = planeHandle;
            if( DEBUG_POINTER_ICON ) {
                System.err.println("Display.PointerIcon.set.X: active ["+toHexString(activePointerIcon)+", visible "+activePointerIconVisible+"]");
            }
        }
    }
    /* pp */ void setActivePointerIconVisible(final boolean visible, final int x, final int y) {
        synchronized(pointerIconSync) {
            if( DEBUG_POINTER_ICON ) {
                System.err.println("Display.PointerIcon.visible: active ["+toHexString(activePointerIcon)+", visible "+activePointerIconVisible+"] -> visible "+visible);
            }
            if( activePointerIconVisible != visible ) {
                if( 0 != activePointerIcon ) {
                    SetPointerIcon0(kmsHandle, activePointerIcon, visible, x, y);
                }
                activePointerIconVisible = visible;
            }
        }
    }
    /* pp */ void moveActivePointerIcon(final int x, final int y) {
        synchronized(pointerIconSync) {
            if( DEBUG_POINTER_ICON ) {
                System.err.println("Display.PointerIcon.move: active ["+toHexString(activePointerIcon)+", visible "+activePointerIconVisible+"], "+x+"/"+y);
            }
            if( 0 != activePointerIcon && activePointerIconVisible ) {
                MovePointerIcon0(activePointerIcon, x, y);
            }
        }
    }

    //----------------------------------------------------------------------
    // Internals only
    //

    protected static native boolean initIDs();
    private static native long OpenKMSDisplay0();
    private static native void CloseKMSDisplay0(long handle);

    private static long CreatePointerIcon(final long kmsHandle, final Buffer pixels, final int width, final int height, final int hotX, final int hotY) {
        final boolean pixels_is_direct = Buffers.isDirect(pixels);
        return CreatePointerIcon0(pixels_is_direct ? pixels : Buffers.getArray(pixels),
                                  pixels_is_direct ? Buffers.getDirectBufferByteOffset(pixels) : Buffers.getIndirectBufferByteOffset(pixels),
                                  pixels_is_direct,
                                  width, height, hotX, hotY);
    }
    private static native long CreatePointerIcon0(Object pixels, int pixels_byte_offset, boolean pixels_is_direct, int width, int height, int hotX, int hotY);
    private static native void DestroyPointerIcon0(long handle);
    private static native void SetPointerIcon0(long kmsHandle, long handle, boolean enable, int x, int y);
    private static native void MovePointerIcon0(long handle, int x, int y);

    private static native void DispatchMessages0();

    private long kmsHandle;
    private long activePointerIcon;
    private boolean activePointerIconVisible;
    private final Object pointerIconSync = new Object();
}

