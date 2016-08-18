package com.serenegiant.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.opengl.EGLContext;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.LogUtils;
import com.serenegiant.glutils.RenderRunnable;

import java.io.IOException;

public class MediaVideoEncoderRunable extends MediaEncoderRunable {

    private static final String TAG = MediaVideoEncoderRunable.class.getSimpleName();

    private static final String MIME_TYPE = "video/avc";

    // FPS 帧率
    private static final int FRAME_RATE = 25;
    //
    private static final float BPP = 0.25f;

    private final int mWidth;
    private final int mHeight;
    private RenderRunnable mRenderRunnable;

    // 由MediaCodec创建的输入surface
    private Surface mSurface;

    public MediaVideoEncoderRunable(final SohuMediaMuxerManager muxer, final MediaEncoderListener listener, final int width, final int height) {
        super(muxer, listener);
        LogUtils.i(TAG, "MediaVideoEncoderRunable: ");
        mWidth = width;
        mHeight = height;
        mRenderRunnable = RenderRunnable.createHandler(TAG);
    }

    /**
     * 运行在GLThread
     *
     * @param tex_matrix
     * @param mvp_matrix
     * @return
     */
    public boolean frameAvailableSoon(final float[] tex_matrix, final float[] mvp_matrix) {
        LogUtils.d(TAG, "---frameAvailableSoon---");
        boolean result;
        if (result = super.frameAvailableSoon()) {
            mRenderRunnable.draw(tex_matrix, mvp_matrix);
        }
        return result;
    }


    /**
     * 目前在主线程被调用,开始录制前的准备
     *
     * @throws IOException
     */
    @Override
    protected void prepare() throws IOException {
        LogUtils.d(TAG, "---prepare---");
        mTrackIndex = -1;
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
    public void setEglContext(final EGLContext eglContext, final int texId) {
        mRenderRunnable.setEglContext(eglContext, texId, mSurface);
    }

    @Override
    protected void release() {
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
        final int bitrate = (int) (BPP * FRAME_RATE * mWidth * mHeight);
        //final int bitrate = 800000;
        return bitrate;
    }

    @Override
    protected void signalEndOfInputStream() {
        LogUtils.d(TAG, "sending EOS to encoder");
        // 停止录制
        mMediaCodec.signalEndOfInputStream();
        //
        mIsEndOfStream = true;
    }

}
