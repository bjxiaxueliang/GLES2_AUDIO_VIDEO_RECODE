package com.serenegiant.glutils;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.serenegiant.encoder.SohuEGLManager;


public final class RenderRunnable implements Runnable {

    private static final String TAG = RenderRunnable.class.getSimpleName();

    private final Object mSync = new Object();
    private EGLContext mEGLContext;

    private Object mSurface;
    private int mTexId = -1;
    private float[] mMatrix = new float[32];

    private boolean mRequestSetEglContext;

    // 是否需要释放资源
    private boolean mRequestRelease;

    // 需要绘制的次数
    private int mRequestDraw;

    /**
     * 创建线程,开启这个Runable
     *
     * @param name
     * @return
     */
    public static final RenderRunnable createHandler(final String name) {

        final RenderRunnable handler = new RenderRunnable();
        synchronized (handler.mSync) {
            new Thread(handler, !TextUtils.isEmpty(name) ? name : TAG).start();
            try {
                handler.mSync.wait();
            } catch (final InterruptedException e) {
            }
        }
        return handler;
    }

    /**
     * 开始录制时，调用该方法,设置一些数据
     *
     * @param eglContext
     * @param texId
     * @param surface
     */
    public final void setEglContext(final EGLContext eglContext, final int texId, final Object surface) {
        //
        if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture) && !(surface instanceof SurfaceHolder)) {
            throw new RuntimeException("unsupported window type:" + surface);
        }
        //
        synchronized (mSync) {
            // 释放资源
            if (mRequestRelease) {
                return;
            }
            //
            mEGLContext = eglContext;
            mTexId = texId;
            mSurface = surface;
            //
            mRequestSetEglContext = true;
            Matrix.setIdentityM(mMatrix, 0);
            Matrix.setIdentityM(mMatrix, 16);
            //
            mSync.notifyAll();
            try {
                mSync.wait();
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 运行在GLThread
     *
     * @param tex_matrix
     * @param mvp_matrix
     */
    public final void draw(final float[] tex_matrix, final float[] mvp_matrix) {
        draw(mTexId, tex_matrix, mvp_matrix);
    }

    /**
     * 运行在GLThread
     *
     * @param texId
     * @param texMatrix
     * @param mvpMatrix
     */
    public final void draw(final int texId, final float[] texMatrix, final float[] mvpMatrix) {
        synchronized (mSync) {
            // 释放资源
            if (mRequestRelease) {
                return;
            }
            //
            mTexId = texId;
            //
            if ((texMatrix != null) && (texMatrix.length >= 16)) {
                System.arraycopy(texMatrix, 0, mMatrix, 0, 16);
            } else {
                Matrix.setIdentityM(mMatrix, 0);
            }
            if ((mvpMatrix != null) && (mvpMatrix.length >= 16)) {
                System.arraycopy(mvpMatrix, 0, mMatrix, 16, 16);
            } else {
                Matrix.setIdentityM(mMatrix, 16);
            }
            mRequestDraw++;
            mSync.notifyAll();
        }
    }

    /**
     * 释放资源
     */
    public final void release() {
        synchronized (mSync) {
            if (mRequestRelease) {
                return;
            }
            mRequestRelease = true;
            mSync.notifyAll();
            try {
                mSync.wait();
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private SohuEGLManager mSohuEgl;
    private GLDrawer2D mDrawer;

    @Override
    public final void run() {

        synchronized (mSync) {
            mRequestSetEglContext = mRequestRelease = false;
            mRequestDraw = 0;
            mSync.notifyAll();
        }
        boolean localRequestDraw;
        // 无限循环
        for (; ; ) {
            //
            synchronized (mSync) {
                // 是否需要释放资源
                if (mRequestRelease) {
                    break;
                }
                //
                if (mRequestSetEglContext) {
                    mRequestSetEglContext = false;
                    internalPrepare();
                }
                localRequestDraw = mRequestDraw > 0;
                if (localRequestDraw) {
                    mRequestDraw--;

                }
            }
            if (localRequestDraw) {
                if ((mSohuEgl != null) && mTexId >= 0) {
                    GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    mDrawer.setMatrix(mMatrix, 16);
                    mDrawer.draw(mTexId, mMatrix);
                    mSohuEgl.swapMyEGLBuffers();
                }
            } else {
                synchronized (mSync) {
                    try {
                        mSync.wait();
                    } catch (final InterruptedException e) {
                        break;
                    }
                }
            }
        }
        synchronized (mSync) {
            mRequestRelease = true;
            internalRelease();
            mSync.notifyAll();
        }

    }

    private final void internalPrepare() {
        //
        internalRelease();
        //
        mSohuEgl = new SohuEGLManager(mEGLContext, mSurface);
        //
        mDrawer = new GLDrawer2D();
        mSurface = null;
        mSync.notifyAll();
    }

    /**
     *
     */
    private final void internalRelease() {
        if (mDrawer != null) {
            mDrawer.release();
            mDrawer = null;
        }
        if (mSohuEgl != null) {
            mSohuEgl.release();
            mSohuEgl = null;
        }
    }

}
