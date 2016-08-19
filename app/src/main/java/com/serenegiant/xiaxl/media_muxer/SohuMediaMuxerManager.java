package com.serenegiant.xiaxl.media_muxer;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.text.TextUtils;

import com.serenegiant.xiaxl.media_encoder.BaseMediaEncoderRunable;
import com.serenegiant.xiaxl.media_encoder.MediaAudioEncoderRunable;
import com.serenegiant.xiaxl.media_encoder.MediaVideoEncoderRunable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Locale;

public class SohuMediaMuxerManager {

    private static final String TAG = SohuMediaMuxerManager.class.getSimpleName();

    private static final String DIR_NAME = "GL_AUDIO_VIDEO_RECODE";

    private static final SimpleDateFormat mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);

    // 输出文件路径
    private String mOutputPath;
    private final MediaMuxer mMediaMuxer;
    private int mEncoderCount, mStatredCount;
    private boolean mIsStarted;
    private BaseMediaEncoderRunable mVideoEncoder, mAudioEncoder;

    /**
     * Constructor
     *
     * @param ext extension of output file
     * @throws IOException
     */
    public SohuMediaMuxerManager(String ext) throws IOException {
        if (TextUtils.isEmpty(ext)) {
            ext = ".mp4";
        }
        try {
            // 输出文件路径
            mOutputPath = getCaptureFile(ext).toString();
            //
        } catch (final NullPointerException e) {
            throw new RuntimeException("This app has no permission of writing external storage");
        }
        // 编码器
        mMediaMuxer = new MediaMuxer(mOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        //
        mEncoderCount = mStatredCount = 0;
        //
        mIsStarted = false;
    }

    /**
     * 目前在主线程被调用
     *
     * @throws IOException
     */
    public void prepare() throws IOException {
        if (mVideoEncoder != null)
            mVideoEncoder.prepare();
        if (mAudioEncoder != null)
            mAudioEncoder.prepare();
    }

    /**
     * 目前主线程调用
     */
    public void startRecording() {
        if (mVideoEncoder != null)
            mVideoEncoder.startRecording();
        if (mAudioEncoder != null)
            mAudioEncoder.startRecording();
    }

    public void stopRecording() {
        if (mVideoEncoder != null)
            mVideoEncoder.stopRecording();
        mVideoEncoder = null;
        if (mAudioEncoder != null)
            mAudioEncoder.stopRecording();
        mAudioEncoder = null;
    }

    public synchronized boolean isStarted() {
        return mIsStarted;
    }


    /**
     * assign encoder to this calss. this is called from encoder.
     *
     * @param encoder instance of MediaVideoEncoderRunable or MediaAudioEncoderRunable
     */
    public void addEncoder(final BaseMediaEncoderRunable encoder) {
        if (encoder instanceof MediaVideoEncoderRunable) {
            if (mVideoEncoder != null) {
                throw new IllegalArgumentException("Video encoder already added.");
            }
            mVideoEncoder = encoder;
        } else if (encoder instanceof MediaAudioEncoderRunable) {
            if (mAudioEncoder != null) {
                throw new IllegalArgumentException("Video encoder already added.");
            }
            mAudioEncoder = encoder;
        } else {
            throw new IllegalArgumentException("unsupported encoder");
        }
        mEncoderCount = (mVideoEncoder != null ? 1 : 0) + (mAudioEncoder != null ? 1 : 0);
    }

    /**
     * request start recording from encoder
     *
     * @return true when muxer is ready to write
     */
    /*package*/
    public synchronized boolean start() {

        mStatredCount++;
        if ((mEncoderCount > 0) && (mStatredCount == mEncoderCount)) {
            mMediaMuxer.start();
            mIsStarted = true;
            notifyAll();

        }
        return mIsStarted;
    }

    /**
     * request stop recording from encoder when encoder received EOS
     */
    /*package*/
    public synchronized void stop() {

        mStatredCount--;
        if ((mEncoderCount > 0) && (mStatredCount <= 0)) {
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mIsStarted = false;
        }
    }

    /**
     * assign encoder to muxer
     *
     * @param format
     * @return minus value indicate error
     */
    public synchronized int addTrack(final MediaFormat format) {
        if (mIsStarted) {
            throw new IllegalStateException("muxer already started");
        }
        final int trackIx = mMediaMuxer.addTrack(format);

        return trackIx;
    }

    /**
     * write encoded data to muxer
     * 写入数据
     *
     * @param trackIndex
     * @param byteBuf
     * @param bufferInfo
     */
    public synchronized void writeSampleData(final int trackIndex, final ByteBuffer byteBuf, final MediaCodec.BufferInfo bufferInfo) {
        if (mStatredCount > 0) {
            mMediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo);
        }
    }


    /**
     * generate output file
     * 获取输出文件路径
     *
     * @param ext .mp4(.m4a for audio) or .png
     * @return return null when this app has no writing permission to external storage.
     */
    public static final File getCaptureFile(final String ext) {
        // 文件路径/Sdcard/GL_AUDIO_VIDEO_RECODE
        final File dir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
        dir.mkdirs();
        if (dir.canWrite()) {
            return new File(dir, getDateTimeString() + ext);
        }
        return null;
    }

    /**
     * 获取当前时间的格式化形式
     * get current date and time as String
     *
     * @return
     */
    private static final String getDateTimeString() {
        final GregorianCalendar now = new GregorianCalendar();
        return mDateTimeFormat.format(now.getTime());
    }

}
