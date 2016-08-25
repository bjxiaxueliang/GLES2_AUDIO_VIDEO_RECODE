package com.serenegiant.xiaxl.gl_recoder;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.serenegiant.xiaxl.MainGLSurfaceView;
import com.serenegiant.xiaxl.egl.SohuEGLManager;
import com.serenegiant.xiaxl.gl_widget.GLTextureRect;


/**
 * RenderRunnable
 */
public final class RecoderGLRenderRunnable implements Runnable {

    private static final String TAG = RecoderGLRenderRunnable.class.getSimpleName();

    private final Object mSync = new Object();
    private EGLContext mEGLContext;

    private MainGLSurfaceView mGLSurfaceView;

    private Object mSurface;
    private int mTexId = -1;

    // 最终变换矩阵，从glThread中拷贝过来的最终变换矩阵
    private float[] mMatrix = new float[16];

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
    public static final RecoderGLRenderRunnable createHandler(final String name) {

        final RecoderGLRenderRunnable handler = new RecoderGLRenderRunnable();
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
    public final void setEglContext(final EGLContext eglContext, MainGLSurfaceView glSurfaceView, final int texId, final Object surface) {
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
            mGLSurfaceView = glSurfaceView;
            mTexId = texId;
            mSurface = surface;
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
        draw(mTexId, mvp_matrix);
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
            mTexId = texId;

            // 拷贝最终变换矩阵
            if (mvpMatrix != null) {
                mMatrix = mvpMatrix.clone();
            } else {
                Matrix.setIdentityM(mMatrix, 0);
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
    private GLTextureRect mRenderGLTextureRect;

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
                    // 清屏颜色为黑色
                    GLES20.glClearColor(0, 0, 0, 0);
                    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT
                            | GLES20.GL_COLOR_BUFFER_BIT);

                    mRenderGLTextureRect.draw(mTexId,mMatrix);
                    mSohuEgl.swapMyEGLBuffers();
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
        mSohuEgl = new SohuEGLManager(mEGLContext, mSurface);
        //
        mRenderGLTextureRect = new GLTextureRect(mGLSurfaceView.mCameraPreviewWidth, mGLSurfaceView.mCameraPreviewHeight);
        mSurface = null;
        mSync.notifyAll();
    }

    /**
     *
     */
    private final void releaseEGL() {
        if (mSohuEgl != null) {
            mSohuEgl.release();
            mSohuEgl = null;
        }
    }

}
