package com.serenegiant.xiaxl.media_encoder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;

import com.serenegiant.xiaxl.LogUtils;
import com.serenegiant.xiaxl.media_muxer.SohuMediaMuxerManager;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaAudioEncoderRunable extends BaseMediaEncoderRunable {

    private static final String TAG = MediaAudioEncoderRunable.class.getSimpleName();
    //
    private static final String MIME_TYPE = "audio/mp4a-latm";
    // 44.1[KHz] is only setting guaranteed to be available on all devices.
    private static final int SAMPLE_RATE = 44100;
    //
    private static final int BIT_RATE = 64000;
    // AAC, bytes/frame/channel
    public static final int SAMPLES_PER_FRAME = 1024;
    // AAC, frame/buffer/sec
    public static final int FRAMES_PER_BUFFER = 25;

    //
    private AudioThread mAudioThread = null;

    /**
     * 构造方法，父类中开启了该线程
     *
     * @param mediaMuxerManager
     * @param mediaEncoderListener
     */
    public MediaAudioEncoderRunable(final SohuMediaMuxerManager mediaMuxerManager, final MediaEncoderListener mediaEncoderListener) {
        super(mediaMuxerManager, mediaEncoderListener);
    }

    /**
     * 录制前的准备
     *
     * @throws IOException
     */
    @Override
    public void prepare() throws IOException {

        mTrackIndex = -1;
        mMuxerStarted = mIsEndOfStream = false;

        // mediaFormat配置
        final MediaFormat audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        //
        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();

        if (mMediaEncoderListener != null) {
            try {
                mMediaEncoderListener.onPrepared(this);
            } catch (final Exception e) {
                LogUtils.e(TAG, "prepare:", e);
            }
        }
    }

    @Override
    public void startRecording() {
        super.startRecording();
        // create and execute audio capturing thread using internal mic
        if (mAudioThread == null) {
            mAudioThread = new AudioThread();
            mAudioThread.start();
        }
    }

    @Override
    public void release() {
        mAudioThread = null;
        super.release();
    }

    private static final int[] AUDIO_SOURCES = new int[]{
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
    };

    /**
     * Thread to capture audio data from internal mic as uncompressed 16bit PCM data
     * and write them to the MediaCodec encoder
     */
    private class AudioThread extends Thread {
        @Override
        public void run() {
            //
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            //
            try {
                final int min_buffer_size = AudioRecord.getMinBufferSize(
                        //
                        SAMPLE_RATE,
                        // 但声道
                        AudioFormat.CHANNEL_IN_MONO,
                        //
                        AudioFormat.ENCODING_PCM_16BIT);
                //
                int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
                if (buffer_size < min_buffer_size) {
                    buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
                }
                //
                AudioRecord audioRecord = null;
                for (final int source : AUDIO_SOURCES) {
                    try {
                        audioRecord = new AudioRecord(
                                source,
                                //
                                SAMPLE_RATE,
                                // 单声道
                                AudioFormat.CHANNEL_IN_MONO,
                                //
                                AudioFormat.ENCODING_PCM_16BIT,
                                //
                                buffer_size);
                        //
                        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                            audioRecord = null;
                        }
                    } catch (final Exception e) {
                        audioRecord = null;
                    }
                    if (audioRecord != null) {
                        break;
                    }
                }
                if (audioRecord != null) {
                    try {
                        if (mIsCapturing) {

                            final ByteBuffer buf = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME);
                            int readBytes;
                            audioRecord.startRecording();
                            try {
                                for (; mIsCapturing && !mRequestStop && !mIsEndOfStream; ) {
                                    // read audio data from internal mic
                                    buf.clear();
                                    readBytes = audioRecord.read(buf, SAMPLES_PER_FRAME);
                                    if (readBytes > 0) {
                                        // set audio data to encoder
                                        buf.position(readBytes);
                                        buf.flip();
                                        encode(buf, readBytes, getPTSUs());
                                        frameAvailableSoon();
                                    }
                                }
                                frameAvailableSoon();
                            } finally {
                                audioRecord.stop();
                            }
                        }
                    } finally {
                        audioRecord.release();
                    }
                } else {
                    LogUtils.e(TAG, "failed to initialize AudioRecord");
                }
            } catch (final Exception e) {
                LogUtils.e(TAG, "AudioThread#run", e);
            }

        }
    }

}
