package com.serenegiant.audiovideosample;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

import com.serenegiant.encoder.MediaVideoEncoderRunable;
import com.serenegiant.glutils.GLDrawer2D;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Sub class of GLSurfaceView to display camera preview and write video frame to capturing surface
 */
public final class CameraGLView extends GLSurfaceView {


    private static final String TAG = CameraGLView.class.getSimpleName();

    private final CameraSurfaceRenderer mRenderer;
    private boolean mHasSurface;
    private CameraHandler mCameraHandler = null;
    private int mVideoWidth, mVideoHeight;


    public CameraGLView(final Context context) {
        this(context, null, 0);
    }

    public CameraGLView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraGLView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs);

        mRenderer = new CameraSurfaceRenderer(this);
        // 2.0
        setEGLContextClientVersion(2);
        // render
        setRenderer(mRenderer);
    }

    @Override
    public void onResume() {

        super.onResume();
        // surface是否创建
        if (mHasSurface) {
            // 开始预览
            if (mCameraHandler == null) {
                startPreview(getWidth(), getHeight());
            }
        }
    }

    @Override
    public void onPause() {
        //
        if (mCameraHandler != null) {
            // 停止预览
            mCameraHandler.stopPreview(false);
        }
        super.onPause();
    }


    public void setVideoSize(final int width, final int height) {
        // Video的大小
        mVideoWidth = width;
        mVideoHeight = height;
        //
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.updateViewport();
            }
        });
    }


    /**
     * surfaceTexture
     *
     * @return
     */
    public SurfaceTexture getSurfaceTexture() {
        return mRenderer != null ? mRenderer.mSTexture : null;
    }

    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {

        if (mCameraHandler != null) {
            // wait for finish previewing here
            // otherwise camera try to display on un-exist Surface and some error will occure
            // 停止预览
            mCameraHandler.stopPreview(true);
        }
        mCameraHandler = null;
        mHasSurface = false;
        mRenderer.onSurfaceDestroyed();
        super.surfaceDestroyed(holder);
    }

    /**
     * 具体是什么情况????????
     *
     * @param encoder
     */
    public void setVideoEncoder(final MediaVideoEncoderRunable encoder) {

        queueEvent(new Runnable() {
            @Override
            public void run() {
                synchronized (mRenderer) {
                    if (encoder != null) {
                        encoder.setEglContext(EGL14.eglGetCurrentContext(), mRenderer.mTexId);
                    }
                    mRenderer.mVideoEncoder = encoder;
                }
            }
        });
    }

    //********************************************************************************

    private synchronized void startPreview(final int width, final int height) {
        if (mCameraHandler == null) {
            final CameraThread thread = new CameraThread(this);
            thread.start();
            mCameraHandler = thread.getHandler();
        }
        mCameraHandler.startPreview(1280, 720/*width, height*/);
    }

    /**
     * GLSurfaceViewのRenderer
     */
    private static final class CameraSurfaceRenderer
            implements GLSurfaceView.Renderer,
            SurfaceTexture.OnFrameAvailableListener {    // API >= 11

        private final WeakReference<CameraGLView> mWeakParent;
        private SurfaceTexture mSTexture;    // API >= 11
        private int mTexId;
        private GLDrawer2D mDrawer;
        private final float[] mStMatrix = new float[16];
        private final float[] mMvpMatrix = new float[16];
        private MediaVideoEncoderRunable mVideoEncoder;

        public CameraSurfaceRenderer(final CameraGLView parent) {

            mWeakParent = new WeakReference<CameraGLView>(parent);
            // 初始化矩阵
            Matrix.setIdentityM(mMvpMatrix, 0);
        }

        @Override
        public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {

            // This renderer required OES_EGL_image_external extension
            final String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);    // API >= 8
//			if (DEBUG) Log.i(TAG, "onSurfaceCreated:Gl extensions: " + extensions);
            if (!extensions.contains("OES_EGL_image_external"))
                throw new RuntimeException("This system does not support OES_EGL_image_external.");

            // 生成纹理Id
            mTexId = GLDrawer2D.initTex();
            // create SurfaceTexture with texture ID.
            // 通过纹理Id，创建SurfaceTexture
            mSTexture = new SurfaceTexture(mTexId);
            //
            mSTexture.setOnFrameAvailableListener(this);
            // clear screen with yellow color so that you can see rendering rectangle
            GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
            final CameraGLView parent = mWeakParent.get();
            if (parent != null) {
                parent.mHasSurface = true;
            }
            // create object for preview display
            mDrawer = new GLDrawer2D();
            // 矩阵初始化
            mDrawer.setMatrix(mMvpMatrix, 0);
        }

        @Override
        public void onSurfaceChanged(final GL10 unused, final int width, final int height) {

            // if at least with or height is zero, initialization of this view is still progress.
            if ((width == 0) || (height == 0)) return;
            updateViewport();
            final CameraGLView parent = mWeakParent.get();
            if (parent != null) {
                parent.startPreview(width, height);
            }
        }

        /**
         * when GLSurface context is soon destroyed
         */
        public void onSurfaceDestroyed() {

            if (mDrawer != null) {
                mDrawer.release();
                mDrawer = null;
            }
            if (mSTexture != null) {
                mSTexture.release();
                mSTexture = null;
            }
            GLDrawer2D.deleteTex(mTexId);
        }

        private final void updateViewport() {
            final CameraGLView parent = mWeakParent.get();
            if (parent != null) {
                final int view_width = parent.getWidth();
                final int view_height = parent.getHeight();
                GLES20.glViewport(0, 0, view_width, view_height);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                final double video_width = parent.mVideoWidth;
                final double video_height = parent.mVideoHeight;
                if (video_width == 0 || video_height == 0) return;
                Matrix.setIdentityM(mMvpMatrix, 0);

                if (mDrawer != null) {
                    mDrawer.setMatrix(mMvpMatrix, 0);
                }
            }
        }

        private volatile boolean requesrUpdateTex = false;
        private boolean flip = true;

        /**
         * drawing to GLSurface
         * we set renderMode to GLSurfaceView.RENDERMODE_WHEN_DIRTY,
         * this method is only called when #requestRender is called(= when texture is required to update)
         * if you don't set RENDERMODE_WHEN_DIRTY, this method is called at maximum 60fps
         */
        @Override
        public void onDrawFrame(final GL10 unused) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            if (requesrUpdateTex) {
                requesrUpdateTex = false;
                // update texture(came from camera)
                // 从摄像机更新数据
                mSTexture.updateTexImage();
                // get texture matrix
                mSTexture.getTransformMatrix(mStMatrix);
            }
            // 绘制纹理矩形
            // draw to preview screen
            mDrawer.draw(mTexId, mStMatrix);
            flip = !flip;
            if (flip) {    // ~30fps
                synchronized (this) {
                    if (mVideoEncoder != null) {
                        // notify to capturing thread that the camera frame is available.
                        mVideoEncoder.frameAvailableSoon(mStMatrix, mMvpMatrix);
                    }
                }
            }
        }

        @Override
        public void onFrameAvailable(final SurfaceTexture st) {
            // 有新的数据帧有用
            requesrUpdateTex = true;
        }
    }

    /**
     * Handler class for asynchronous camera operation
     */
    private static final class CameraHandler extends Handler {
        private static final int MSG_PREVIEW_START = 1;
        private static final int MSG_PREVIEW_STOP = 2;
        private CameraThread mThread;

        public CameraHandler(final CameraThread thread) {
            mThread = thread;
        }

        public void startPreview(final int width, final int height) {
            sendMessage(obtainMessage(MSG_PREVIEW_START, width, height));
        }

        /**
         * request to stop camera preview
         *
         * @param needWait need to wait for stopping camera preview
         */
        public void stopPreview(final boolean needWait) {
            synchronized (this) {
                sendEmptyMessage(MSG_PREVIEW_STOP);
                if (needWait && mThread.mIsRunning) {
                    try {
                        wait();
                    } catch (final InterruptedException e) {
                    }
                }
            }
        }

        /**
         * message handler for camera thread
         */
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case MSG_PREVIEW_START:
                    mThread.startPreview(msg.arg1, msg.arg2);
                    break;
                case MSG_PREVIEW_STOP:
                    mThread.stopPreview();
                    synchronized (this) {
                        notifyAll();
                    }
                    Looper.myLooper().quit();
                    mThread = null;
                    break;
                default:
                    throw new RuntimeException("unknown message:what=" + msg.what);
            }
        }
    }

    /**
     * Thread for asynchronous operation of camera preview
     */
    private static final class CameraThread extends Thread {
        private final Object mReadyFence = new Object();
        private final WeakReference<CameraGLView> mWeakParent;
        private CameraHandler mHandler;
        private volatile boolean mIsRunning = false;
        private Camera mCamera;
        private boolean mIsFrontFace;

        public CameraThread(final CameraGLView parent) {
            super("Camera thread");
            mWeakParent = new WeakReference<CameraGLView>(parent);
        }

        public CameraHandler getHandler() {
            synchronized (mReadyFence) {
                try {
                    mReadyFence.wait();
                } catch (final InterruptedException e) {
                }
            }
            return mHandler;
        }

        /**
         * message loop
         * prepare Looper and create Handler for this thread
         */
        @Override
        public void run() {

            Looper.prepare();
            synchronized (mReadyFence) {
                mHandler = new CameraHandler(this);
                mIsRunning = true;
                mReadyFence.notify();
            }
            Looper.loop();

            synchronized (mReadyFence) {
                mHandler = null;
                mIsRunning = false;
            }
        }

        /**
         * start camera preview
         *
         * @param width
         * @param height
         */
        private final void startPreview(final int width, final int height) {

            final CameraGLView parent = mWeakParent.get();
            if ((parent != null) && (mCamera == null)) {
                // This is a sample project so just use 0 as camera ID.
                // it is better to selecting camera is available
                try {
                    // 打开后置摄像头
                    mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
                    //
                    final Camera.Parameters params = mCamera.getParameters();
                    //
                    final List<String> focusModes = params.getSupportedFocusModes();
                    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                        // 聚焦
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                        // 自动聚焦
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    }
                    // let's try fastest frame rate. You will get near 60fps, but your device become hot.
//                    final List<int[]> supportedFpsRange = params.getSupportedPreviewFpsRange();
//                    //
//                    final int[] max_fps = supportedFpsRange.get(supportedFpsRange.size() - 1);
//                    params.setPreviewFpsRange(max_fps[0], max_fps[1]);
                    params.setRecordingHint(true);
                    // 找最接近的数值
                    final Camera.Size closestSize = getClosestSupportedSize(
                            params.getSupportedPreviewSizes(), width, height);
                    params.setPreviewSize(closestSize.width, closestSize.height);
                    // request closest picture size for an aspect ratio issue on Nexus7
                    final Camera.Size pictureSize = getClosestSupportedSize(
                            params.getSupportedPictureSizes(), width, height);
                    params.setPictureSize(pictureSize.width, pictureSize.height);
                    //
                    mCamera.setParameters(params);
                    // get the actual preview size
                    final Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
                    // adjust view size with keeping the aspect ration of camera preview.
                    // here is not a UI thread and we should request parent view to execute.
                    parent.post(new Runnable() {
                        @Override
                        public void run() {
                            parent.setVideoSize(previewSize.width, previewSize.height);
                        }
                    });
                    final SurfaceTexture st = parent.getSurfaceTexture();
                    st.setDefaultBufferSize(previewSize.width, previewSize.height);
                    mCamera.setPreviewTexture(st);
                } catch (final IOException e) {
                    Log.e(TAG, "startPreview:", e);
                    if (mCamera != null) {
                        mCamera.release();
                        mCamera = null;
                    }
                } catch (final RuntimeException e) {
                    Log.e(TAG, "startPreview:", e);
                    if (mCamera != null) {
                        mCamera.release();
                        mCamera = null;
                    }
                }
                if (mCamera != null) {
                    mCamera.startPreview();
                }
            }
        }

        private static Camera.Size getClosestSupportedSize(List<Camera.Size> supportedSizes, final int requestedWidth, final int requestedHeight) {
            return (Camera.Size) Collections.min(supportedSizes, new Comparator<Camera.Size>() {

                private int diff(final Camera.Size size) {
                    return Math.abs(requestedWidth - size.width) + Math.abs(requestedHeight - size.height);
                }

                @Override
                public int compare(final Camera.Size lhs, final Camera.Size rhs) {
                    return diff(lhs) - diff(rhs);
                }
            });

        }

        /**
         * stop camera preview
         */
        private void stopPreview() {

            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
            final CameraGLView parent = mWeakParent.get();
            if (parent == null) return;
            parent.mCameraHandler = null;
        }
    }
}
