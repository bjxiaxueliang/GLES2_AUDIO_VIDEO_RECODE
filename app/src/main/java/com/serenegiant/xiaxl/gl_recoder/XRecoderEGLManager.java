package com.serenegiant.xiaxl.gl_recoder;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.serenegiant.xiaxl.LogUtils;

/**
 * EGL 相关配置
 */
public class XRecoderEGLManager {

    private static final String TAG = XRecoderEGLManager.class.getSimpleName();
    //
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    //
    private EGLContext mEglContext = EGL14.EGL_NO_CONTEXT;
    private EGLDisplay mEglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLSurface mEglSurface = EGL14.EGL_NO_SURFACE;


    /**
     * 构造方法
     *
     * @param xShowEglContext
     */
    public XRecoderEGLManager(final EGLContext xShowEglContext, final Object mediaCodecsurface) {

        initXRecoderEGL(xShowEglContext, mediaCodecsurface);
    }

    /**
     * 初始化EGL
     *
     * @param xShowEglContext
     */
    private void initXRecoderEGL(final EGLContext xShowEglContext, final Object mediaCodecsurface) {

        if (!(mediaCodecsurface instanceof SurfaceView)
                && !(mediaCodecsurface instanceof Surface)
                && !(mediaCodecsurface instanceof SurfaceHolder)
                && !(mediaCodecsurface instanceof SurfaceTexture)) {
            throw new IllegalArgumentException("unsupported surface");
        }


        //--------------------mEGLDisplay-----------------------
        // EGL Display
        if (mEglDisplay != EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("EGL already set up");
        }
        //
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        // 初始化
        final int[] version = new int[2];
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            mEglDisplay = null;
            throw new RuntimeException("eglInitialize failed");
        }
        //--------------------mEglConfig-----------------------

        // Configure EGL for recording and OpenGL ES 2.0.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                //
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                // 录制android
                EGL_RECORDABLE_ANDROID,
                1,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        EGL14.eglChooseConfig(mEglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0);
        if (configs[0] == null) {
            throw new RuntimeException("chooseConfig failed");
        }

        //--------------------mEglContext-----------------------
        EGLContext shareEglContext = xShowEglContext;
        if (shareEglContext == null) {
            shareEglContext = EGL14.EGL_NO_CONTEXT;
        }
        //
        final int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };

        // eglDisplay 即是之前创建的显示设备，
        // share_context 指定一个共享的EGL Context，共享后，2个EGLContext可以相互使用对方创建的 texture 等资源，默认情况下是不共享的
        mEglContext = EGL14.eglCreateContext(mEglDisplay, configs[0], shareEglContext, attrib_list, 0);
        checkMyEGLError("eglCreateContext");


        //-------------------------mEglSurface-------------------------
        //
        final int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        //
        try {
            mEglSurface = EGL14.eglCreateWindowSurface(mEglDisplay, configs[0], mediaCodecsurface, surfaceAttribs, 0);
        } catch (final IllegalArgumentException e) {
            Log.e(TAG, "eglCreateWindowSurface", e);
        }
        //-----------------------------
        makeMyEGLCurrentSurface();
    }

    /**
     * change context to draw this window surface
     *
     * @return
     */
    private boolean makeMyEGLCurrentSurface() {
        //
        if (mEglDisplay == null) {
            LogUtils.e(TAG, "mEglDisplay == null");
            return false;
        }
        if (mEglSurface == null || mEglSurface == EGL14.EGL_NO_SURFACE) {
            final int error = EGL14.eglGetError();
            if (error == EGL14.EGL_BAD_NATIVE_WINDOW) {
                LogUtils.e(TAG, "makeMyEGLCurrentSurface:returned EGL_BAD_NATIVE_WINDOW.");
            }
            return false;
        }
        // EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx
        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            Log.w(TAG, "eglMakeCurrent:" + EGL14.eglGetError());
            return false;
        }
        return true;
    }


    /**
     * 交换buffer数据
     *
     * @return
     */
    public int swapMyEGLBuffers() {
        //
        boolean result = EGL14.eglSwapBuffers(mEglDisplay, mEglSurface);
        //
        if (!result) {
            final int err = EGL14.eglGetError();
            return err;
        }
        //
        return EGL14.EGL_SUCCESS;
    }

    /**
     * 释放资源
     */
    public void release() {

        // -------mEglSurface----------
        EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(mEglDisplay, mEglSurface);

        // -------mEglContext----------
        EGL14.eglDestroyContext(mEglDisplay, mEglContext);
        EGL14.eglTerminate(mEglDisplay);
        EGL14.eglReleaseThread();
        //
        mEglSurface = EGL14.EGL_NO_SURFACE;
        mEglDisplay = EGL14.EGL_NO_DISPLAY;
        mEglContext = EGL14.EGL_NO_CONTEXT;
    }


    /**
     * 查错
     *
     * @param msg
     */
    private void checkMyEGLError(final String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }
}
