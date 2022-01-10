package com.serenegiant.xiaxl.gl_recoder;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.serenegiant.xiaxl.XShowGLSurfaceView;
import com.serenegiant.xiaxl.gl_widget.XTextureGLRect;


/**
 * RenderRunnable
 */
public final class XRecoderGLRenderRunnable implements Runnable {

    private static final String TAG = XRecoderGLRenderRunnable.class.getSimpleName();

    private final Object mSync = new Object();

    /**
     *
     */
    // 这个eglContext来自GLThread的eglContext
    private EGLContext xShowEGLContext;
    // XShowGLSurfaceView
    private XShowGLSurfaceView xShowGLSurfaceView;
    // 纹理id
    private int xShowGLTexId = -1;
    /**
     * 由MediaCodec创建的输入surface
     */
    private Object xRecoderSurface;


    // 最终变换矩阵，从glThread中拷贝过来的最终变换矩阵
    private float[] mRecoderMatrix = new float[16];

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
    public static final XRecoderGLRenderRunnable createHandler(final String name) {

        final XRecoderGLRenderRunnable handler = new XRecoderGLRenderRunnable();
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
     * @param xShowEGLContext
     * @param xShowGLTexId
     * @param xRecoderSurface
     */
    public final void setEglContext(final EGLContext xShowEGLContext, XShowGLSurfaceView xShowGLSurfaceView, final int xShowGLTexId, final Object xRecoderSurface) {
        //
        if (!(xRecoderSurface instanceof Surface) && !(xRecoderSurface instanceof SurfaceTexture) && !(xRecoderSurface instanceof SurfaceHolder)) {
            throw new RuntimeException("unsupported window type:" + xRecoderSurface);
        }
        //
        synchronized (mSync) {
            // 释放资源
            if (mRequestRelease) {
                return;
            }
            //
            this.xShowEGLContext = xShowEGLContext;
            this.xShowGLSurfaceView = xShowGLSurfaceView;
            this.xShowGLTexId = xShowGLTexId;
            this.xRecoderSurface = xRecoderSurface;
            //
            mRequestSetEglContext = true;
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
     * @param mvp_matrix
     */
    public final void draw(final float[] mvp_matrix) {
        draw(xShowGLTexId, mvp_matrix);
    }

    /**
     * 运行在GLThread
     *
     * @param texId
     * @param mvpMatrix
     */
    public final void draw(final int texId, final float[] mvpMatrix) {
        synchronized (mSync) {
            // 释放资源
            if (mRequestRelease) {
                return;
            }
            //
            xShowGLTexId = texId;

            // 拷贝最终变换矩阵
            if (mvpMatrix != null) {
                mRecoderMatrix = mvpMatrix.clone();
            } else {
                Matrix.setIdentityM(mRecoderMatrix, 0);
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

    private XRecoderEGLManager mXRecoderEglManager;
    private XTextureGLRect mXRecoderGLRect;

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
                if ((mXRecoderEglManager != null) && xShowGLTexId >= 0) {
                    // 清屏颜色为黑色
                    GLES20.glClearColor(0, 0, 0, 0);
                    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT
                            | GLES20.GL_COLOR_BUFFER_BIT);

                    mXRecoderGLRect.draw(xShowGLTexId, mRecoderMatrix);
                    mXRecoderEglManager.swapMyEGLBuffers();
                }
            } else {
                //--------进入等待状态-----------
                synchronized (mSync) {
                    try {
                        mSync.wait();
                    } catch (final InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }
        }
        synchronized (mSync) {
            mRequestRelease = true;
            releaseEGL();
            mSync.notifyAll();
        }

    }

    private final void internalPrepare() {
        //
        releaseEGL();
        //
        mXRecoderEglManager = new XRecoderEGLManager(xShowEGLContext, xRecoderSurface);
        //
        mXRecoderGLRect = new XTextureGLRect(xShowGLSurfaceView.mCameraPreviewWidth, xShowGLSurfaceView.mCameraPreviewHeight);
        xRecoderSurface = null;
        mSync.notifyAll();
    }

    /**
     *
     */
    private final void releaseEGL() {
        if (mXRecoderEglManager != null) {
            mXRecoderEglManager.release();
            mXRecoderEglManager = null;
        }
    }

}
