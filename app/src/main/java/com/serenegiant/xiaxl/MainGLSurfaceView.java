package com.serenegiant.xiaxl;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.AttributeSet;
import android.view.SurfaceHolder;

import com.serenegiant.xiaxl.gl_util.GLMatrixState;
import com.serenegiant.xiaxl.gl_util.GLTextureUtil;
import com.serenegiant.xiaxl.gl_widget.GLTextureRect;
import com.serenegiant.xiaxl.media_encoder.MediaVideoEncoderRunable;
import com.serenegiant.xiaxl.util.CameraHelper;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * Opengl回显Camera数据的页面
 */
public final class MainGLSurfaceView extends GLSurfaceView {
    private static final String TAG = MainGLSurfaceView.class.getSimpleName();

    //
    public int mCameraPreviewWidth = 1920;
    public int mCameraPreviewHeight = 1080;

    // 渐变矩形的纹理id
    private int mTextureId = 100;
    // 用于与Camera绑定的 SurfaceTexture
    private SurfaceTexture mSurfaceTexture = null;
    //
    private GLTextureRect mGLTextureRect;

    // 渲染器
    private final GLSceneRenderer mRenderer;
    //-----------------------
    // camera
    private CameraHelper mCameraHelper = CameraHelper.getInstance();


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

        if (mSurfaceTexture != null) {
            // 开启摄像机预览
            mCameraHelper.startPreview(Camera.CameraInfo.CAMERA_FACING_BACK, mSurfaceTexture);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // 停止预览
        mCameraHelper.stopPreview();
    }


    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {
        //
        if (mCameraHelper != null) {
            // 停止预览
            mCameraHelper.stopPreview();
            mCameraHelper = null;
        }
        //
        mRenderer.onSurfaceDestroyed();
        //
        super.surfaceDestroyed(holder);
    }


    /**
     * GLSurfaceView Renderer
     */
    private final class GLSceneRenderer
            implements GLSurfaceView.Renderer {


        //
        private MediaVideoEncoderRunable mMediaVideoEncoderRunable;


        public GLSceneRenderer() {

        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // 清屏颜色为黑色
            GLES20.glClearColor(0, 0, 0, 0);

            // 初始化矩阵
            GLMatrixState.setInitStack();


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


            // 开启摄像机预览
            mCameraHelper.startPreview(Camera.CameraInfo.CAMERA_FACING_BACK, mSurfaceTexture);

            //
            mCameraPreviewWidth = mCameraHelper.getPreviewWidth();
            mCameraPreviewHeight = mCameraHelper.getPreviewHeight();


            // create object for preview display
            mGLTextureRect = new GLTextureRect(mCameraPreviewWidth, mCameraPreviewHeight);

        }

        @Override
        public void onSurfaceChanged(final GL10 unused, final int width, final int height) {
            // 设置视窗大小及位置
            GLES20.glViewport(0, 0, width, height);


            // 计算GLSurfaceView的宽高比
            float ratio = (float) width / height;
            // 设置camera位置
            GLMatrixState.setCamera(
                    //
                    0, // 人眼位置的X
                    0, // 人眼位置的Y
                    1, // 人眼位置的Z
                    //
                    0, // 人眼球看的点X
                    0, // 人眼球看的点Y
                    0, // 人眼球看的点Z
                    // up向量
                    0,
                    1,
                    0);
            // 调用此方法计算产生透视投影矩阵
            GLMatrixState.setProjectFrustum(-ratio, ratio, -1, 1, 1, 20);

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
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            //-----------取camera数据end-------------

            // 清除深度缓冲与颜色缓冲
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT
                    | GLES20.GL_COLOR_BUFFER_BIT);


            // 进行渐变矩形的绘制
            GLMatrixState.pushMatrix();
            GLMatrixState.translate(0, 0, -1);

            //适配Nexus 5X（后置摄像头装反了）
            if (Build.MODEL.equals("Nexus 5X") && mCameraHelper.getCurrentPreviewCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK) {
                GLMatrixState.rotate(-270, 0, 0, 1);
            } else {
                // 注: 因为正常获取的摄像头数据是旋转了90度的，所以这里要旋转回来
                GLMatrixState.rotate(-90, 0, 0, 1);
            }
            // 最总变化矩阵
            float[] mVpMatrix = GLMatrixState.getFinalMatrix();
            // 绘制纹理矩形
            mGLTextureRect.draw(mTextureId, mVpMatrix);

            //---------------视频写入----------------
            // 减少一半的视频数据写入
            flip = !flip;
            if (flip) {    // ~30fps
                synchronized (this) {
                    if (mMediaVideoEncoderRunable != null) {
                        // notify to capturing thread that the camera frame is available.
                        mMediaVideoEncoderRunable.frameAvailableSoon(mVpMatrix);
                    }
                }
            }
            // 恢复变换矩阵
            GLMatrixState.popMatrix();
        }
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
                        mediaVideoEncoderRunable.setEglContext(EGL14.eglGetCurrentContext(), MainGLSurfaceView.this, mTextureId);
                    }
                    mRenderer.mMediaVideoEncoderRunable = mediaVideoEncoderRunable;
                }
            }
        });
    }

}
