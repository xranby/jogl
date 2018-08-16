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

import com.jogamp.common.nio.Buffers;
import com.jogamp.common.nio.PointerBuffer;
import com.jogamp.nativewindow.*;

import com.jogamp.nativewindow.util.Point;
import com.jogamp.nativewindow.util.Rectangle;
import com.jogamp.nativewindow.util.RectangleImmutable;
import com.jogamp.common.util.Bitfield;
import com.jogamp.nativewindow.egl.EGLGraphicsDevice;

import com.jogamp.newt.Display;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.newt.Window;
import com.jogamp.newt.event.MouseEvent;

import com.jogamp.opengl.*;
import com.jogamp.opengl.egl.EGL;
import com.jogamp.opengl.egl.EGLExt;
import jogamp.newt.PointerIconImpl;
import jogamp.newt.WindowImpl;
import jogamp.newt.driver.KeyTracker;
import jogamp.newt.driver.MouseTracker;
import jogamp.newt.driver.linux.LinuxEventDeviceTracker;
import jogamp.newt.driver.linux.LinuxMouseTracker;
import jogamp.newt.driver.x11.X11UnderlayTracker;
import jogamp.opengl.GLGraphicsConfigurationUtil;
import jogamp.opengl.egl.EGLContext;
import jogamp.opengl.egl.EGLDisplayUtil;
import jogamp.opengl.egl.EGLGraphicsConfiguration;
import jogamp.opengl.egl.EGLGraphicsConfigurationFactory;

import java.nio.IntBuffer;


public class WindowDriver extends WindowImpl {
    private static final String WINDOW_CLASS_NAME = "NewtWindow";

    public static long hackHandle() {
        System.out.println("********************************************   KMS WindowyDriver HACK handle ");

        Display display = NewtFactory.createDisplay(NativeWindowFactory.TYPE_KMS, null, false);
        System.out.println("********************************************   KMS WindowyDriver HACK handle display ");
        Screen screen = NewtFactory.createScreen(display, 0);
        System.out.println("********************************************   KMS WindowyDriver HACK handle screen");
        Window window = NewtFactory.createWindow(screen, new Capabilities());
        System.out.println("********************************************   KMS WindowyDriver HACK handle window" + window);

        window.setVisible(true);
        //System.out.println("********************************************   KMS WindowyDriver HACK handle window visible");
        System.out.println("********************************************   KMS WindowyDriver HACK handle "+window.getDisplayHandle());

        return window.getWindowHandle();
    }

    static {
        DisplayDriver.initSingleton();

        System.out.println("********************************************   KMS WindowyDriver initialized");
    }

    public WindowDriver() {

        /*
         * track using the /dev/event files directly
         * using the LinuxMouseTracker
         */
        mouseTracker = LinuxMouseTracker.getSingleton();
        keyTracker = LinuxEventDeviceTracker.getSingleton(); 
        layer = -1;
        nativeWindowHandle = 0;
        windowHandleClose = 0;
    }

    /**
     * Clamp given rectangle to given screen bounds.
     *
     * @param screen
     * @param rect the {@link RectangleImmutable} in pixel units
     * @param definePosSize if {@code true} issue {@link #definePosition(int, int)} and {@link #defineSize(int, int)}
     *                      if either has changed.
     * @return If position or size has been clamped a new {@link RectangleImmutable} instance w/ clamped values
     *         will be returned, otherwise the given {@code rect} is returned.
     */
    private RectangleImmutable clampRect(final ScreenDriver screen, final RectangleImmutable rect, final boolean definePosSize) {
        int x = rect.getX();
        int y = rect.getY();
        int w = rect.getWidth();
        int h = rect.getHeight();
        final int s_w = screen.getWidth();
        final int s_h = screen.getHeight();
        boolean modPos = false;
        boolean modSize = false;
        if( 0 > x ) {
            x = 0;
            modPos = true;
        }
        if( 0 > y ) {
            y = 0;
            modPos = true;
        }
        if( s_w < x + w ) {
            if( 0 < x ) {
                x = 0;
                modPos = true;
            }
            if( s_w < w ) {
                w = s_w;
                modSize = true;
            }
        }
        if( s_h < y + h ) {
            if( 0 < y ) {
                y = 0;
                modPos = true;
            }
            if( s_h < h ) {
                h = s_h;
                modSize = true;
            }
        }
        if( modPos || modSize ) {
            if( definePosSize ) {
                if( modPos ) {
                    definePosition(x, y);
                }
                if( modSize ) {
                    defineSize(w, h);
                }
            }
            return new Rectangle(x, y, w, h);
        } else {
            return rect;
        }
    }

    @Override
    protected boolean canCreateNativeImpl() {
        // clamp if required incl. redefinition of position and size
        clampRect((ScreenDriver) getScreen(), new Rectangle(getX(), getY(), getWidth(), getHeight()), true);
        return true; // default: always able to be created
    }

    @Override
    protected void createNativeImpl() {
        if(0!=getParentWindowHandle()) {
            throw new RuntimeException("Window parenting not supported (yet)");
        }
        synchronized( layerSync ) {
            if( layerCount >= MAX_LAYERS ) {
                throw new RuntimeException("Max windows reached: "+layerCount+" ( "+MAX_LAYERS+" )");
            }
            for(int i=0; 0 > layer && i<MAX_LAYERS; i++) {
                if( !usedLayers.get(nextLayer) ) {
                    layer = nextLayer;
                    usedLayers.set(layer);
                    layerCount++;
                }
                nextLayer++;
                if( MAX_LAYERS == nextLayer ) {
                    nextLayer=0;
                }
            }
            // System.err.println("XXX.Open capacity "+usedLayers.capacity()+", count "+usedLayers.getBitCount());
        }
        if( 0 > layer ) {
            throw new InternalError("Could not find a free layer: count "+layerCount+", max "+MAX_LAYERS);
        }
        final ScreenDriver screen = (ScreenDriver) getScreen();
        final DisplayDriver display = (DisplayDriver) screen.getDisplay();

        // Create own screen/device resource instance allowing independent ownership,
        // while still utilizing shared EGL resources.
        final AbstractGraphicsScreen aScreen = screen.getGraphicsScreen();
        final EGLGraphicsDevice aDevice = (EGLGraphicsDevice) aScreen.getDevice();
        final EGLGraphicsDevice eglDevice = EGLDisplayUtil.eglCreateEGLGraphicsDevice(aDevice.getNativeDisplayID(), aDevice.getConnection(), aDevice.getUnitID());
        eglDevice.open();
        final DefaultGraphicsScreen eglScreen = new DefaultGraphicsScreen(eglDevice, aScreen.getIndex());

        final AbstractGraphicsConfiguration cfg = GraphicsConfigurationFactory.getFactory(getScreen().getDisplay().getGraphicsDevice(), capsRequested).chooseGraphicsConfiguration(
                capsRequested, capsRequested, capabilitiesChooser, eglScreen, VisualIDHolder.VID_UNDEFINED);
        if (null == cfg) {
            throw new NativeWindowException("Error choosing GraphicsConfiguration creating window: "+this);
        }
        final Capabilities chosenCaps = (Capabilities) cfg.getChosenCapabilities();
        // FIXME: Pass along opaque flag, since EGL doesn't determine it
        if(capsRequested.isBackgroundOpaque() != chosenCaps.isBackgroundOpaque()) {
            chosenCaps.setBackgroundOpaque(capsRequested.isBackgroundOpaque());
        }
        setGraphicsConfiguration(cfg);

        /*
        gbm.surface = gbm_surface_create(gbm.dev,
			drm.mode->hdisplay, drm.mode->vdisplay,
			GBM_FORMAT_XRGB8888,
GBM_BO_USE_SCANOUT | GBM_BO_USE_RENDERING);
        */
        long nativeSurface = CreateWindow0(display.getKMSHandle(), layer,
                                           getX(), getY(), getWidth(), getHeight(),
                                           chosenCaps.isBackgroundOpaque(), chosenCaps.getAlphaBits());
        System.out.println("==========================   native device handle "+eglDevice.getHandle()+" window surface "+nativeSurface);

        System.out.println("EGL Version"+ EGL.eglQueryString(eglDevice.getHandle(), EGL.EGL_VERSION));
        System.out.println("EGL Vendor "+ EGL.eglQueryString(eglDevice.getHandle(), EGL.EGL_VENDOR));
        System.out.println("EGL Extensions "+ EGL.eglQueryString(eglDevice.getHandle(), EGL.EGL_EXTENSIONS));

        EGL.eglBindAPI(EGL.EGL_OPENGL_API);
        System.out.println("eglBindAPI       error= "+"0x"+Long.toHexString(EGL.eglGetError()));


        // CONFIG
        int[] configBuffer = new int[13];
        configBuffer[0]=EGL.EGL_SURFACE_TYPE;
        configBuffer[1]=EGL.EGL_WINDOW_BIT;
        configBuffer[2]=EGL.EGL_RED_SIZE;
        configBuffer[3]=1;
        configBuffer[4]=EGL.EGL_GREEN_SIZE;
        configBuffer[5]=1;
        configBuffer[6]=EGL.EGL_BLUE_SIZE;
        configBuffer[7]=1;
        configBuffer[8]=EGL.EGL_ALPHA_SIZE;
        configBuffer[9]=1;
        configBuffer[10]=EGL.EGL_RENDERABLE_TYPE;
        configBuffer[11]=EGL.EGL_OPENGL_BIT;
        configBuffer[12]=EGL.EGL_NONE;

        final IntBuffer numConfigs = Buffers.newDirectIntBuffer(1);
        EGL.eglGetConfigs(eglDevice.getHandle(), null, 0, numConfigs);
        System.out.println("eglGetConfigs       error= "+"0x"+Long.toHexString(EGL.eglGetError()));

        System.out.println("eglGetConfigs       0x3 using handle "+"0x" + Long.toHexString(eglDevice.getHandle()));

        System.out.println("eglGetConfigs       numConfigs = "+numConfigs.get(0));
        //System.out.println(EGL.eglGetError());
        final PointerBuffer configs = PointerBuffer.allocateDirect(numConfigs.get(0));
        EGL.eglChooseConfig(eglDevice.getHandle(), Buffers.newDirectIntBuffer(configBuffer), configs, configs.capacity(), numConfigs);
        System.out.println("eglChooseConfig       error= "+"0x"+Long.toHexString(EGL.eglGetError()));
        //System.out.println(EGL.eglGetError());

        // CONTEXT
        int[] contextBuffer = new int[3];
        contextBuffer[0] = EGL.EGL_CONTEXT_CLIENT_VERSION;
        contextBuffer[1] = 2;
        contextBuffer[2] = EGL.EGL_NONE;
        long eglContext = EGL.eglCreateContext(eglDevice.getHandle(),configs.get(),EGL.EGL_NO_CONTEXT,Buffers.newDirectIntBuffer(contextBuffer));
        System.out.println("eglCreateContext       error= "+"0x"+Long.toHexString(EGL.eglGetError()));

        nativeWindowHandle = eglDevice.getHandle();
        setWindowHandle(nativeWindowHandle);
        if (nativeWindowHandle == 0) {
            throw new NativeWindowException("Error creating egl window: "+cfg);
        }

        // EGL Surface
        long eglWindowSurface=EGL.eglCreateWindowSurface(eglDevice.getHandle(), configs.get(), nativeSurface, null);
        System.out.println("eglSurface       error= "+"0x"+Long.toHexString(EGL.eglGetError()));

        System.out.println("eglSurface       0x3 using handle "+"0x"+Long.toHexString(nativeSurface));

        //nativeWindowHandle= eglDevice.getHandle();
        //nativeWindowHandle = EGL.eglGetPlatformDisplay(EGLExt.EGL_PLATFORM_GBM_KHR, Buffers.newDirectLongBuffer(handleBuffer), null);
        //EGL.eglMakeCurrent(display.getKMSHandle(), nativeWindowHandle, nativeWindowHandle, context);


        // CONNECT CONTEXT TO SURFACE
        EGL.eglMakeCurrent(eglDevice.getHandle(),eglWindowSurface,eglWindowSurface,eglContext);
        System.out.println("eglMakeCurrent       error= "+"0x"+Long.toHexString(EGL.eglGetError()));




        if (0 == getWindowHandle()) {
            throw new NativeWindowException("Error native Window Handle is null");
        }
        windowHandleClose = nativeWindowHandle;

        addWindowListener(keyTracker);
        addWindowListener(mouseTracker);


        focusChanged(false, true);
    }

    @Override
    protected void closeNativeImpl() {
        final DisplayDriver display = (DisplayDriver) getScreen().getDisplay();
        final EGLGraphicsDevice eglDevice = (EGLGraphicsDevice) getGraphicsConfiguration().getScreen().getDevice();

        removeWindowListener(mouseTracker);
        removeWindowListener(keyTracker);

        if(0!=windowHandleClose) {
            CloseWindow0(display.getKMSHandle(), windowHandleClose);
        }

        eglDevice.close();

        synchronized( layerSync ) {
            usedLayers.clear(layer);
            layerCount--;
            layer = -1;
            // System.err.println("XXX.Close capacity "+usedLayers.capacity()+", count "+usedLayers.getBitCount());
        }
    }

    @Override
    protected void requestFocusImpl(final boolean reparented) {
        focusChanged(false, true);
    }

    @Override
    protected final int getSupportedReconfigMaskImpl() {
        return minimumReconfigStateMask |
               // STATE_MASK_UNDECORATED |
               // STATE_MASK_ALWAYSONTOP |
               // STATE_MASK_ALWAYSONBOTTOM |
               // STATE_MASK_STICKY |
               // STATE_MASK_RESIZABLE |
               // STATE_MASK_MAXIMIZED_VERT |
               // STATE_MASK_MAXIMIZED_HORZ |
               STATE_MASK_POINTERVISIBLE |
               STATE_MASK_POINTERCONFINED;
    }

    @Override
    protected boolean reconfigureWindowImpl(final int x, final int y, final int width, final int height, final int flags) {
        final RectangleImmutable rect = clampRect((ScreenDriver) getScreen(), new Rectangle(x, y, width, height), false);
        // reconfigure0 will issue position/size changed events if required
        reconfigure0(nativeWindowHandle, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), flags);

        return true;
    }

    @Override
    protected Point getLocationOnScreenImpl(final int x, final int y) {
        return new Point(x,y);
    }

    @Override
    protected final void doMouseEvent(final boolean enqueue, final boolean wait, final short eventType, final int modifiers,
                                      final int x, final int y, final short button, final float[] rotationXYZ, final float rotationScale) {
        if( MouseEvent.EVENT_MOUSE_MOVED == eventType || MouseEvent.EVENT_MOUSE_DRAGGED == eventType ) {
            final DisplayDriver display = (DisplayDriver) getScreen().getDisplay();
            display.moveActivePointerIcon(getX() + x, getY() + y);
        }
        super.doMouseEvent(enqueue, wait, eventType, modifiers, x, y, button, rotationXYZ, rotationScale);
    }

    @Override
    protected void setPointerIconImpl(final PointerIconImpl pi) {
        final DisplayDriver display = (DisplayDriver) getScreen().getDisplay();
        display.setPointerIconActive(null != pi ? pi.validatedHandle() : 0, mouseTracker.getLastX(), mouseTracker.getLastY());
    }

    @Override
    protected boolean setPointerVisibleImpl(final boolean pointerVisible) {
        final DisplayDriver display = (DisplayDriver) getScreen().getDisplay();
        display.setActivePointerIconVisible(pointerVisible, mouseTracker.getLastX(), mouseTracker.getLastY());
        return true;
    }

    //----------------------------------------------------------------------
    // Internals only
    //
    private MouseTracker mouseTracker;
    private KeyTracker keyTracker;

    protected static native boolean initIDs();
    private        native long CreateWindow0(long kmsDisplay, int layer, int x, int y, int width, int height, boolean opaque, int alphaBits);
    private        native void CloseWindow0(long kmsDisplay, long eglWindowHandle);
    private        native void reconfigure0(long eglWindowHandle, int x, int y, int width, int height, int flags);

    private int    layer;
    private long   nativeWindowHandle;
    private long   windowHandleClose;

    private static int nextLayer = 0;
    private static int layerCount = 0;
    private static final int MAX_LAYERS = 32;
    private static final Bitfield usedLayers = Bitfield.Factory.create(MAX_LAYERS);
    private static final Object layerSync = new Object();
}
