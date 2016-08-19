package com.serenegiant.xiaxl.media_encoder;


import android.media.MediaCodec;
import android.media.MediaFormat;

import com.serenegiant.xiaxl.LogUtils;
import com.serenegiant.xiaxl.media_muxer.SohuMediaMuxerManager;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 视频与音频录制的基类
 */
public abstract class BaseMediaEncoderRunable implements Runnable {

    private static final String TAG = BaseMediaEncoderRunable.class.getSimpleName();

    // 10[msec]
    protected static final int TIMEOUT_USEC = 10000;

    /**
     *
     */
    public interface MediaEncoderListener {
        void onPrepared(BaseMediaEncoderRunable encoder);

        void onStopped(BaseMediaEncoderRunable encoder);
    }

    // 同步锁
    protected final Object mSync = new Object();
    // Flag that indicate this encoder is capturing now.
    // 是否正在进行录制的状态记录
    protected volatile boolean mIsCapturing;
    // Flag that indicate the frame data will be available soon.
    // 可用数据帧数量
    private int mRequestDrainEncoderCount;
    // Flag to request stop capturing
    // 结束录制的标识
    protected volatile boolean mRequestStop;
    // Flag that indicate encoder received EOS(End Of Stream)
    // 结束录制标识
    protected boolean mIsEndOfStream;
    //Flag the indicate the muxer is running
    // muxer结束标识
    protected boolean mMuxerStarted;
    //Track Number
    protected int mTrackIndex;

    /**
     * -----------------------------
     */
    // MediaCodec instance for encoding
    protected MediaCodec mMediaCodec;
    // BufferInfo instance for dequeuing
    private MediaCodec.BufferInfo mBufferInfo;

    /**
     * ----------------------------
     */
    // MediaMuxerWarapper instance
    protected SohuMediaMuxerManager mSohuMediaMuxerManager;
    //
    protected final MediaEncoderListener mMediaEncoderListener;


    /**
     * 构造方法
     *
     * @param mediaMuxerManager
     * @param mediaEncoderListener
     */
    public BaseMediaEncoderRunable(final SohuMediaMuxerManager mediaMuxerManager, final MediaEncoderListener mediaEncoderListener) {
        LogUtils.d(TAG,"---BaseMediaEncoderRunable---");
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
        this.mSohuMediaMuxerManager.addEncoder(BaseMediaEncoderRunable.this);

        //
        LogUtils.d(TAG,"---BaseMediaEncoderRunable synchronized (mSync) before begin---");
        synchronized (mSync) {
            LogUtils.d(TAG,"---BaseMediaEncoderRunable synchronized (mSync) begin---");
            // create BufferInfo here for effectiveness(to reduce GC)
            mBufferInfo = new MediaCodec.BufferInfo();
            // wait for starting thread
            new Thread(this, getClass().getSimpleName()).start();
            try {
                mSync.wait();
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
        LogUtils.d(TAG,"---BaseMediaEncoderRunable synchronized (mSync) end---");
    }


    /**
     * the method to indicate frame data is soon available or already available
     *
     * @return return true if encoder is ready to encod.
     */
    public boolean frameAvailableSoon() {
        LogUtils.d(TAG, "---frameAvailableSoon---");
        LogUtils.d(TAG, "---mSync before begin---");
        synchronized (mSync) {
            LogUtils.d(TAG, "---mSync begin---");
            if (!mIsCapturing || mRequestStop) {
                LogUtils.d(TAG, "mIsCapturing: "+mIsCapturing);
                LogUtils.d(TAG, "mRequestStop: "+mRequestStop);
                LogUtils.d(TAG, "return false");
                return false;
            }
            mRequestDrainEncoderCount++;
            LogUtils.d(TAG, "mRequestDrainEncoderCount: "+mRequestDrainEncoderCount);
            mSync.notifyAll();
        }
        LogUtils.d(TAG, "---mSync end---");
        LogUtils.d(TAG, "return true");
        return true;
    }

    /**
     * encoding loop on private thread
     */
    @Override
    public void run() {
        LogUtils.d(TAG,"---run---");
        LogUtils.d(TAG,"---run synchronized (mSync) before begin---");
        // 线程开启
        synchronized (mSync) {
            LogUtils.d(TAG,"---run synchronized (mSync) begin---");
            //
            mRequestStop = false;
            mRequestDrainEncoderCount = 0;
            //
            mSync.notify();
        }
        LogUtils.d(TAG,"---run synchronized (mSync) end---");
        // 线程开启
        final boolean isRunning = true;
        boolean localRequestStop;
        boolean localRequestDrainEncoderFlag;
        while (isRunning) {
            //
            LogUtils.d(TAG,"---run2 synchronized (mSync) before begin---");
            synchronized (mSync) {
                LogUtils.d(TAG,"---run2 synchronized (mSync) begin---");
                localRequestStop = mRequestStop;
                localRequestDrainEncoderFlag = (mRequestDrainEncoderCount > 0);
                if (localRequestDrainEncoderFlag) {
                    mRequestDrainEncoderCount--;
                }
            }
            LogUtils.d(TAG,"---run2 synchronized (mSync) end---");
            // 停止编码时，调用
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
            // 需要编码
            if (localRequestDrainEncoderFlag) {
                drainEncoder();
            } else {
                // ------线程进入等待状态---------
                LogUtils.d(TAG,"---run3 synchronized (mSync) before begin---");
                synchronized (mSync) {
                    LogUtils.d(TAG,"---run3 synchronized (mSync) begin---");
                    try {
                        mSync.wait();
                    } catch (final InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
                LogUtils.d(TAG,"---run3 synchronized (mSync) end---");
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
    public abstract void prepare() throws IOException;

    /**
     * 目前主线程调用
     */
    public void startRecording() {
        LogUtils.d(TAG,"---startRecording synchronized (mSync) before begin---");
        synchronized (mSync) {
            LogUtils.d(TAG,"---startRecording synchronized (mSync) begin---");
            // 正在录制标识
            mIsCapturing = true;
            // 停止标识 置false
            mRequestStop = false;
            //
            mSync.notifyAll();
        }
        LogUtils.d(TAG,"---startRecording synchronized (mSync) end---");
    }


    /**
     * 停止录制(目前在主线程调用)
     */
    public void stopRecording() {
        LogUtils.d(TAG,"---stopRecording synchronized (mSync) before begin---");
        synchronized (mSync) {
            LogUtils.d(TAG,"---stopRecording synchronized (mSync) begin---");
            if (!mIsCapturing || mRequestStop) {
                return;
            }
            mRequestStop = true;
            mSync.notifyAll();
        }
        LogUtils.d(TAG,"---stopRecording synchronized (mSync) end---");
    }


    /**
     * Release all releated objects
     */
    public void release() {
        // 回调停止
        try {
            mMediaEncoderListener.onStopped(BaseMediaEncoderRunable.this);
        } catch (final Exception e) {
            e.printStackTrace();
        }
        // 设置标识 停止
        mIsCapturing = false;
        // ------释放mediacodec--------
        if (mMediaCodec != null) {
            try {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
        // ----------释放muxer-----------
        if (mMuxerStarted) {
            if (mSohuMediaMuxerManager != null) {
                try {
                    mSohuMediaMuxerManager.stop();
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        }
        // mBufferInfo置空
        mBufferInfo = null;
    }

    /**
     * 停止录制
     */
    public void signalEndOfInputStream() {
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
