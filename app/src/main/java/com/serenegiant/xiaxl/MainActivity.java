package com.serenegiant.xiaxl;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.serenegiant.xiaxl.media_encoder.MediaAudioEncoderRunable;
import com.serenegiant.xiaxl.media_encoder.BaseMediaEncoderRunable;
import com.serenegiant.xiaxl.media_encoder.MediaVideoEncoderRunable;
import com.serenegiant.xiaxl.media_muxer.SohuMediaMuxerManager;

import java.io.IOException;

public class MainActivity extends Activity {


    // 显示Camera的GlsurfaceView
    private MainGLSurfaceView mGLCameraView;
    // 录制按钮
    private Button mRecordButton;

    // muxer for audio/video recording
    private SohuMediaMuxerManager mMediaMuxerManager;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mGLCameraView = (MainGLSurfaceView) findViewById(R.id.cameraView);


        mRecordButton = (Button) findViewById(R.id.record_button);
        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMediaMuxerManager == null) {
                    startRecording();
                } else {
                    stopRecording();
                }
            }
        });

    }


    @Override
    public void onResume() {
        super.onResume();
        mGLCameraView.onResume();
    }

    @Override
    public void onPause() {
        stopRecording();
        mGLCameraView.onPause();
        super.onPause();
    }

    /**
     * 开始录制，这里放在了主线程运行(实际应该放在异步线程中运行)
     */
    private void startRecording() {

        try {
            mRecordButton.setText(R.string.toggleRecordingOff);

            // if you record audio only, ".m4a" is also OK.
            mMediaMuxerManager = new SohuMediaMuxerManager(".mp4");
            //开始视频录制
            new MediaVideoEncoderRunable(mMediaMuxerManager, mMediaEncoderListener, 480, 480);
            // 开启音频录制
            new MediaAudioEncoderRunable(mMediaMuxerManager, mMediaEncoderListener);
            // 视频，音频 录制初始化
            mMediaMuxerManager.prepare();
            // 视频，音频 开始录制
            mMediaMuxerManager.startRecording();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * request stop recording
     * 开始录制
     */
    private void stopRecording() {
        mRecordButton.setText(R.string.toggleRecordingOn);
        if (mMediaMuxerManager != null) {
            mMediaMuxerManager.stopRecording();
            mMediaMuxerManager = null;
        }
    }

    /**
     * 视频、音频 开始与结束录制的回调
     */
    private final BaseMediaEncoderRunable.MediaEncoderListener mMediaEncoderListener = new BaseMediaEncoderRunable.MediaEncoderListener() {
        /**
         * 目前由MediaVideoEncoderRunable在主线程调用
         * @param encoder
         */
        @Override
        public void onPrepared(final BaseMediaEncoderRunable encoder) {
            // 开始录制视频
            if (encoder instanceof MediaVideoEncoderRunable) {
                mGLCameraView.setVideoEncoder((MediaVideoEncoderRunable) encoder);
            }
        }

        /**
         * 目前在异步线程中调用
         * @param encoder
         */
        @Override
        public void onStopped(final BaseMediaEncoderRunable encoder) {
            // 结束录制视频
            if (encoder instanceof MediaVideoEncoderRunable) {
                mGLCameraView.setVideoEncoder(null);
            }
        }
    };


}
