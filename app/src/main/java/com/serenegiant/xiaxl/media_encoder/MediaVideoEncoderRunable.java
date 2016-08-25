package com.serenegiant.xiaxl.media_encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.opengl.EGLContext;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.xiaxl.LogUtils;
import com.serenegiant.xiaxl.MainGLSurfaceView;
import com.serenegiant.xiaxl.gl_recoder.RecoderGLRenderRunnable;
import com.serenegiant.xiaxl.media_muxer.SohuMediaMuxerManager;

import java.io.IOException;

public class MediaVideoEncoderRunable extends BaseMediaEncoderRunable {

    private static final String TAG = MediaVideoEncoderRunable.class.getSimpleName();

    private static final String MIME_TYPE = "video/avc";

    // FPS 帧率
    private static final int FRAME_RATE = 25;
    //
    private static final float BPP = 0.25f;

    private final int mWidth;
    private final int mHeight;
    private RecoderGLRenderRunnable mRenderRunnable;

    // 由MediaCodec创建的输入surface
    private Surface mSurface;

    /**
     * 构造方法,父类中，开启了该线程
     *
     * @param mediaMuxerManager
     * @param mediaEncoderListener
     * @param width
     * @param height
     */
    public MediaVideoEncoderRunable(final SohuMediaMuxerManager mediaMuxerManager, final MediaEncoderListener mediaEncoderListener, final int width, final int height) {
        super(mediaMuxerManager, mediaEncoderListener);
        LogUtils.i(TAG, "MediaVideoEncoderRunable: ");
        mWidth = width;
        mHeight = height;

        /**
         * 开启了一个看不到的绘制线程
         */
        mRenderRunnable = RecoderGLRenderRunnable.createHandler(TAG);
    }

    /**
     * 运行在GLThread
     *
     * @param mvp_matrix
     * @return
     */
    public boolean frameAvailableSoon(final float[] mvp_matrix) {
        LogUtils.d(TAG, "---frameAvailableSoon---");
        boolean result;
        if (result = super.frameAvailableSoon()) {
            mRenderRunnable.draw(mvp_matrix);
        }
        return result;
    }


    /**
     * 开始录制前的准备(目前由SohuMediaMuxerManager在主线程调用)
     *
     * @throws IOException
     */
    @Override
    public void prepare() throws IOException {
        LogUtils.d(TAG, "---prepare---");
        //
        mTrackIndex = -1;
        //
        mMuxerStarted = mIsEndOfStream = false;

        //-----------------MediaFormat-----------------------
        // mediaCodeC采用的是H.264编码
        final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        // 数据来源自surface
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        // 视频码率
        format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate());
        // fps
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        //设置关键帧的时间
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);

        //-----------------Encoder-----------------------
        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // get Surface for encoder input
        // this method only can call between #configure and #start
        mSurface = mMediaCodec.createInputSurface();
        //
        mMediaCodec.start();
        //
        LogUtils.i(TAG, "prepare finishing");
        if (mMediaEncoderListener != null) {
            try {
                mMediaEncoderListener.onPrepared(this);
            } catch (final Exception e) {
                Log.e(TAG, "prepare:", e);
            }
        }
    }

    /**
     * 运行在GLThread中
     *
     * @param eglContext 这个eglContext来自GLThread的eglContext
     * @param texId      纹理Id
     */
    public void setEglContext(final EGLContext eglContext, MainGLSurfaceView glSurfaceView, final int texId) {
        mRenderRunnable.setEglContext(eglContext, glSurfaceView, texId, mSurface);
    }

    @Override
    public void release() {
        LogUtils.i(TAG, "release:");
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mRenderRunnable != null) {
            mRenderRunnable.release();
            mRenderRunnable = null;
        }
        super.release();
    }

    /**
     * 码率
     *
     * @return
     */
    private int calcBitRate() {
        //final int bitrate = (int) (BPP * FRAME_RATE * mWidth * mHeight);
        final int bitrate = 800000;
        return bitrate;
    }

    @Override
    public void signalEndOfInputStream() {
        LogUtils.d(TAG, "sending EOS to encoder");
        // 停止录制
        mMediaCodec.signalEndOfInputStream();
        //
        mIsEndOfStream = true;
    }

}
