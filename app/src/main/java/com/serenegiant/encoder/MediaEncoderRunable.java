package com.serenegiant.encoder;


import android.media.MediaCodec;
import android.media.MediaFormat;

import com.serenegiant.LogUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class MediaEncoderRunable implements Runnable {

    private static final String TAG = MediaEncoderRunable.class.getSimpleName();

    protected static final int TIMEOUT_USEC = 10000;    // 10[msec]

    public interface MediaEncoderListener {
        public void onPrepared(MediaEncoderRunable encoder);

        public void onStopped(MediaEncoderRunable encoder);
    }

    protected final Object mSync = new Object();
    /**
     * Flag that indicate this encoder is capturing now.
     */
    protected volatile boolean mIsCapturing;
    /**
     * Flag that indicate the frame data will be available soon.
     */
    private int mRequestDrain;
    /**
     * Flag to request stop capturing
     */
    protected volatile boolean mRequestStop;
    /**
     * Flag that indicate encoder received EOS(End Of Stream)
     */
    protected boolean mIsEndOfStream;
    /**
     * Flag the indicate the muxer is running
     */
    protected boolean mMuxerStarted;
    /**
     * Track Number
     */
    protected int mTrackIndex;
    /**
     * MediaCodec instance for encoding
     */
    protected MediaCodec mMediaCodec;                // API >= 16(Android4.1.2)
    /**
     * Weak refarence of MediaMuxerWarapper instance
     */
    protected SohuMediaMuxerManager mSohuMediaMuxerManager;
    /**
     * BufferInfo instance for dequeuing
     */
    private MediaCodec.BufferInfo mBufferInfo;


    /**
     *
     */
    protected final MediaEncoderListener mMediaEncoderListener;


    /**
     * 构造方法
     *
     * @param mediaMuxerManager
     * @param mediaEncoderListener
     */
    public MediaEncoderRunable(final SohuMediaMuxerManager mediaMuxerManager, final MediaEncoderListener mediaEncoderListener) {
        if (mediaEncoderListener == null) {
            throw new NullPointerException("MediaEncoderListener is null");
        }
        if (mediaMuxerManager == null) {
            throw new NullPointerException("MediaMuxerWrapper is null");
        }
        //
        this.mSohuMediaMuxerManager = mediaMuxerManager;
        this.mMediaEncoderListener = mediaEncoderListener;
        //
        //
        this.mSohuMediaMuxerManager.addEncoder(this);

        //
        synchronized (mSync) {
            // create BufferInfo here for effectiveness(to reduce GC)
            mBufferInfo = new MediaCodec.BufferInfo();
            // wait for starting thread
            new Thread(this, getClass().getSimpleName()).start();
            try {
                mSync.wait();
            } catch (final InterruptedException e) {
            }
        }
    }


    /**
     * the method to indicate frame data is soon available or already available
     *
     * @return return true if encoder is ready to encod.
     */
    public boolean frameAvailableSoon() {
        LogUtils.d(TAG, "---frameAvailableSoon---");
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return false;
            }
            mRequestDrain++;
            mSync.notifyAll();
        }
        return true;
    }

    /**
     * encoding loop on private thread
     */
    @Override
    public void run() {
        //
        synchronized (mSync) {
            mRequestStop = false;
            mRequestDrain = 0;
            mSync.notify();
        }
        final boolean isRunning = true;
        boolean localRequestStop;
        boolean localRequestDrain;
        while (isRunning) {
            synchronized (mSync) {
                localRequestStop = mRequestStop;
                localRequestDrain = (mRequestDrain > 0);
                if (localRequestDrain) {
                    mRequestDrain--;
                }
            }
            if (localRequestStop) {
                drainEncoder();
                // request stop recording
                signalEndOfInputStream();
                // process output data again for EOS signale
                drainEncoder();
                // release all related objects
                release();
                break;
            }
            if (localRequestDrain) {
                drainEncoder();
            } else {
                synchronized (mSync) {
                    try {
                        mSync.wait();
                    } catch (final InterruptedException e) {
                        break;
                    }
                }
            }
        } // end of while

        synchronized (mSync) {
            mRequestStop = true;
            mIsCapturing = false;
        }
    }


    /**
     * 目前在主线程被调用
     *
     * @throws IOException
     */
    abstract void prepare() throws IOException;

    /**
     * 目前主线程调用
     */
    void startRecording() {

        synchronized (mSync) {
            mIsCapturing = true;
            mRequestStop = false;
            mSync.notifyAll();
        }
    }


    /**
     * 停止录制(目前在主线程调用)
     */
    void stopRecording() {
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return;
            }
            mRequestStop = true;
            mSync.notifyAll();
        }
    }


    /**
     * Release all releated objects
     */
    protected void release() {

        try {
            mMediaEncoderListener.onStopped(this);
        } catch (final Exception e) {
            e.printStackTrace();
        }
        mIsCapturing = false;
        if (mMediaCodec != null) {
            try {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
        if (mMuxerStarted) {
            final SohuMediaMuxerManager muxer = mSohuMediaMuxerManager;
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        }
        mBufferInfo = null;
    }

    protected void signalEndOfInputStream() {

        encode(null, 0, getPTSUs());
    }

    /**
     * Method to set byte array to the MediaCodec encoder
     *
     * @param buffer
     * @param length             　length of byte array, zero means EOS.
     * @param presentationTimeUs
     */
    protected void encode(final ByteBuffer buffer, final int length, final long presentationTimeUs) {
        //
        if (!mIsCapturing) {
            return;
        }
        //
        final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        //
        while (mIsCapturing) {
            final int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                //
                if (buffer != null) {
                    inputBuffer.put(buffer);
                }
                if (length <= 0) {
                    mIsEndOfStream = true;
                    //
                    mMediaCodec.queueInputBuffer(
                            //
                            inputBufferIndex, 0, 0,
                            //
                            presentationTimeUs,
                            //
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    break;
                } else {
                    mMediaCodec.queueInputBuffer(
                            //
                            inputBufferIndex, 0, length,
                            presentationTimeUs, 0);
                }
                break;
            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            }
        }
    }


    /**
     * mEncoder从缓冲区取数据，然后交给mMuxer编码
     */
    protected void drainEncoder() {
        if (mMediaCodec == null) {
            return;
        }

        //
        int count = 0;

        if (mSohuMediaMuxerManager == null) {
            return;
        }

        //拿到输出缓冲区,用于取到编码后的数据
        ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();

        LOOP:
        while (mIsCapturing) {
            //拿到输出缓冲区的索引
            int encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!mIsEndOfStream) {
                    if (++count > 5) {
                        // out of while
                        break LOOP;
                    }
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // this shoud not come when encoding
                //拿到输出缓冲区,用于取到编码后的数据
                encoderOutputBuffers = mMediaCodec.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                // get output format from codec and pass them to muxer
                final MediaFormat format = mMediaCodec.getOutputFormat();
                //
                mTrackIndex = mSohuMediaMuxerManager.addTrack(format);
                //
                mMuxerStarted = true;
                //
                if (!mSohuMediaMuxerManager.start()) {
                    // we should wait until muxer is ready
                    synchronized (mSohuMediaMuxerManager) {
                        while (!mSohuMediaMuxerManager.isStarted())
                            try {
                                mSohuMediaMuxerManager.wait(100);
                            } catch (final InterruptedException e) {
                                break LOOP;
                            }
                    }
                }
            } else if (encoderStatus < 0) {
                // unexpected status

            } else {
                //获取解码后的数据
                final ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    // this never should come...may be a MediaCodec internal error
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
                //
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    mBufferInfo.size = 0;
                }
                //
                if (mBufferInfo.size != 0) {
                    // encoded data is ready, clear waiting counter
                    count = 0;
                    if (!mMuxerStarted) {
                        // muxer is not ready...this will prrograming failure.
                        throw new RuntimeException("drain:muxer hasn't started");
                    }
                    // write encoded data to muxer(need to adjust presentationTimeUs.
                    mBufferInfo.presentationTimeUs = getPTSUs();
                    // 编码
                    mSohuMediaMuxerManager.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    prevOutputPTSUs = mBufferInfo.presentationTimeUs;
                }
                // return buffer to encoder
                mMediaCodec.releaseOutputBuffer(encoderStatus, false);
                //
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // when EOS come.
                    mIsCapturing = false;
                    break;      // out of while
                }
            }
        }
    }

    /**
     * previous presentationTimeUs for writing
     */
    private long prevOutputPTSUs = 0;

    /**
     * get next encoding presentationTimeUs
     *
     * @return
     */
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }

}
