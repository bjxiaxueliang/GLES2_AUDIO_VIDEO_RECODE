package com.serenegiant.xiaxl.util;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import com.serenegiant.xiaxl.LogUtils;

import java.io.IOException;
import java.util.List;

/**
 * create by xiaxl 2016.08.12
 * <p>
 * 用opengl 预览摄像头的一些封装
 * <p>
 * 用opengl预览摄像头，用egl获取opengl绘制的buffer帧进行编码，生成MP4,从而完成视频录制
 */
public class CameraHelper {
    private static final String TAG = CameraHelper.class.getSimpleName();

    // instance
    private static CameraHelper INSTANCE = new CameraHelper();

    //
    public static synchronized CameraHelper getInstance() {
        return INSTANCE;
    }

    // 摄像头对象
    private Camera mCamera = null;
    // 当前预览摄像头id，默认摄像头为后置摄像头
    private int mCurrentPreviewCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;


    /**
     * 获取摄像头的数据
     *
     * @return
     */
    public int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    /**
     * 获取当前预览摄像头id
     *
     * @return
     */
    public int getCurrentPreviewCameraId() {
        return mCurrentPreviewCameraId;
    }

    /**
     * 获取预览区域的宽度
     *
     * @return
     */
    public int getPreviewWidth() {
        if (mCamera != null) {
            final Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
            return previewSize.width;
        }
        return 0;

    }

    /**
     * 获取预览区域的高度
     *
     * @return
     */
    public int getPreviewHeight() {
        if (mCamera != null) {
            final Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
            return previewSize.height;
        }
        return 0;
    }

    /**
     * 开启摄像头
     *
     * @param cameraId              开启的摄像头的id
     * @param previewSurfaceTexture 预览的SurfaceTexture
     */
    public void startPreview(int cameraId, SurfaceTexture previewSurfaceTexture) {
        LogUtils.e(TAG, "---startPreview: " + cameraId);
        if (previewSurfaceTexture == null) {
            LogUtils.e(TAG, "previewSurfaceTexture is null");
            return;
        }
        //
        if (cameraId != Camera.CameraInfo.CAMERA_FACING_FRONT && cameraId != Camera.CameraInfo.CAMERA_FACING_BACK) {
            LogUtils.e(TAG, "Invalid cameraId: " + cameraId);
            return;
        }
        // 关闭摄像头
        stopPreview();

        //---------------------开启摄像头----------------------
        // 摄像头Info
        Camera.CameraInfo info = new Camera.CameraInfo();
        // 获取摄像头数量
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == cameraId) {
                // 打开摄像头
                mCamera = Camera.open(i);
                // 摄像头Id赋值
                this.mCurrentPreviewCameraId = cameraId;
                break;
            }
        }
        //---------------------聚焦----------------------
        //
        final Camera.Parameters params = mCamera.getParameters();
        //
        final List<String> focusModes = params.getSupportedFocusModes();
        // 连续聚焦
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        // 自动聚焦
        else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        //-------------------------------------------
        // 可以帮助减少启动录制的时间，如果用opengl预览，用egl获取buffer，用mediacodec录制编码视频，这里好像没有用了
        params.setRecordingHint(true);
        //--------------------预览区域大小-----------------------
        // 推荐的预览区域大小
        Camera.Size ppsfv = params.getPreferredPreviewSizeForVideo();
        if (ppsfv != null) {
            LogUtils.d(TAG, "Camera preferred preview size for video is: " + ppsfv.width + "x" + ppsfv.height);
        }
        // 支持的预览区域大小
        for (Camera.Size size : params.getSupportedPreviewSizes()) {
            LogUtils.d(TAG, "supported: " + size.width + "x" + size.height);
        }
        // 这里采用推荐的预览区域大小
        if (ppsfv != null) {
            params.setPreviewSize(ppsfv.width, ppsfv.height);
        }
        //--------------------设置params-----------------------
        //
        mCamera.setParameters(params);
        //--------------------设置预览的SurfaceTexture-----------------------
        try {
            // 设置预览的SurfaceTexture
            mCamera.setPreviewTexture(previewSurfaceTexture);
        } catch (final IOException e) {
            LogUtils.e(TAG, "IOException:", e);
            if (mCamera != null) {
                mCamera.release();
                mCamera = null;
            }
        } catch (final RuntimeException e) {
            LogUtils.e(TAG, "RuntimeException:", e);
            if (mCamera != null) {
                mCamera.release();
                mCamera = null;
            }
        }
        //--------------------开启预览-----------------------
        if (mCamera != null) {
            mCamera.startPreview();
        }
    }

    /**
     * stop camera preview
     */
    public void stopPreview() {
        LogUtils.e(TAG, "---stopPreview---");

        // 释放摄像头资源
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        // 重置预览摄像头id
        mCurrentPreviewCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    }
}
