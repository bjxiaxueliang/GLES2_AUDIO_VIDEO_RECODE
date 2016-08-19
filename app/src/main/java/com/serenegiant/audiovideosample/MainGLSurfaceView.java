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

import com.serenegiant.audiovideosample.gl_util.GLTextureUtil;
import com.serenegiant.audiovideosample.gl_widget.GLDrawer2D;
import com.serenegiant.audiovideosample.media_encoder.MediaVideoEncoderRunable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * Opengl回显Camera数据的页面
 */
public final class MainGLSurfaceView extends GLSurfaceView {
    private static final String TAG = MainGLSurfaceView.class.getSimpleName();

    // render
    private final GLSceneRenderer mRenderer;

    // 渐变矩形的纹理id
    private int mTextureId = 100;
    // 用于与Camera绑定的 SurfaceTexture
    private SurfaceTexture mSurfaceTexture = null;
    //
    private GLDrawer2D mDrawer;


    private boolean mHasSurface;
    private CameraHandler mCameraHandler = null;


    public MainGLSurfaceView(final Context context) {
        this(context, null, 0);
    }

    public MainGLSurfaceView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MainGLSurfaceView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs);
        // render
        mRenderer = new GLSceneRenderer();
        // 2.0
        setEGLContextClientVersion(2);
        // render
        setRenderer(mRenderer);
        // 脏渲染模式
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onResume() {

        super.onResume();
        // surface是否创建
        if (mHasSurface) {
            // 开始预览
            if (mCameraHandler == null) {
                startPreview();
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


    /**
     * surfaceTexture
     *
     * @return
     */
    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {

        if (mCameraHandler != null) {
            // 停止预览
            mCameraHandler.stopPreview(true);
        }
        mCameraHandler = null;
        mHasSurface = false;
        mRenderer.onSurfaceDestroyed();
        super.surfaceDestroyed(holder);
    }

    /**
     * 开始录制视频时，由主线程||异步线程回调回来的
     *
     * @param mediaVideoEncoderRunable
     */
    public void setVideoEncoder(final MediaVideoEncoderRunable mediaVideoEncoderRunable) {

        queueEvent(new Runnable() {
            @Override
            public void run() {
                synchronized (mRenderer) {
                    // 这里是获取了一个GLThread的EGL14.eglGetCurrentContext()
                    if (mediaVideoEncoderRunable != null) {
                        mediaVideoEncoderRunable.setEglContext(EGL14.eglGetCurrentContext(), mTextureId);
                    }
                    mRenderer.mMediaVideoEncoderRunable = mediaVideoEncoderRunable;
                }
            }
        });
    }

    //********************************************************************************

    private synchronized void startPreview() {
        if (mCameraHandler == null) {
            final CameraThread thread = new CameraThread(this);
            thread.start();
            mCameraHandler = thread.getHandler();
        }
        mCameraHandler.startPreview(1280, 720);
    }

    /**
     * GLSurfaceView Renderer
     */
    private final class GLSceneRenderer
            implements GLSurfaceView.Renderer {




        private final float[] mStMatrix = new float[16];
        private final float[] mMvpMatrix = new float[16];


        private MediaVideoEncoderRunable mMediaVideoEncoderRunable;

        public GLSceneRenderer() {

        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // 清屏颜色为黑色
            GLES20.glClearColor(0, 0, 0, 0);
            // 初始化矩阵
            Matrix.setIdentityM(mMvpMatrix, 0);

            // 生成纹理Id
            mTextureId = GLTextureUtil.createOESTextureID();
            // 通过纹理Id，创建SurfaceTexture(该mSurfaceTexture与Camera进行绑定)
            mSurfaceTexture = new SurfaceTexture(mTextureId);
            mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    // 请求进行下次渲染
                    MainGLSurfaceView.this.requestRender();
                }
            });
            //
            MainGLSurfaceView.this.mHasSurface = true;

            // create object for preview display
            mDrawer = new GLDrawer2D();
            // 矩阵初始化
            mDrawer.setMatrix(mMvpMatrix, 0);
        }

        @Override
        public void onSurfaceChanged(final GL10 unused, final int width, final int height) {


            updateViewport();

            MainGLSurfaceView.this.startPreview();

        }

        /**
         * when GLSurface context is soon destroyed
         */
        public void onSurfaceDestroyed() {

            if (mSurfaceTexture != null) {
                mSurfaceTexture.release();
                mSurfaceTexture = null;
            }
            GLTextureUtil.deleteTex(mTextureId);
        }

        private final void updateViewport() {


            final int view_width = MainGLSurfaceView.this.getWidth();
            final int view_height = MainGLSurfaceView.this.getHeight();
            GLES20.glViewport(0, 0, view_width, view_height);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);


            Matrix.setIdentityM(mMvpMatrix, 0);

            if (mDrawer != null) {
                mDrawer.setMatrix(mMvpMatrix, 0);
            }

        }

        /**
         * 有摄像头数据后requesrUpdateTex为true
         */
        private boolean flip = true;

        @Override
        public void onDrawFrame(final GL10 unused) {
            //------------取camera数据begin------------
            // 如果camera数据可用，手动取一次
            try {
                // 从摄像机更新数据
                if (mSurfaceTexture != null) {
                    mSurfaceTexture.updateTexImage();
                    // get texture matrix
                    mSurfaceTexture.getTransformMatrix(mStMatrix);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            //-----------取camera数据end-------------

            // 清除深度缓冲与颜色缓冲
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT
                    | GLES20.GL_COLOR_BUFFER_BIT);


            // 绘制纹理矩形
            mDrawer.draw(mTextureId, mStMatrix);

            //---------------视频写入----------------
            // 减少一半的视频数据写入
            flip = !flip;
            if (flip) {    // ~30fps
                synchronized (this) {
                    if (mMediaVideoEncoderRunable != null) {
                        // notify to capturing thread that the camera frame is available.
                        mMediaVideoEncoderRunable.frameAvailableSoon(mStMatrix, mMvpMatrix);
                    }
                }
            }
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
        private final WeakReference<MainGLSurfaceView> mWeakParent;
        private CameraHandler mHandler;
        private volatile boolean mIsRunning = false;
        private Camera mCamera;

        public CameraThread(final MainGLSurfaceView parent) {
            super("Camera thread");
            mWeakParent = new WeakReference<MainGLSurfaceView>(parent);
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

            final MainGLSurfaceView parent = mWeakParent.get();
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
            final MainGLSurfaceView parent = mWeakParent.get();
            if (parent == null) return;
            parent.mCameraHandler = null;
        }
    }
}
