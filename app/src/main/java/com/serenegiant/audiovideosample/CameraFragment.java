package com.serenegiant.audiovideosample;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

import com.serenegiant.encoder.MediaAudioEncoderRunable;
import com.serenegiant.encoder.MediaEncoderRunable;
import com.serenegiant.encoder.SohuMediaMuxerManager;
import com.serenegiant.encoder.MediaVideoEncoderRunable;

import java.io.IOException;

public class CameraFragment extends Fragment {

    private static final String TAG = CameraFragment.class.getSimpleName();

    // 显示Camera的GlsurfaceView
    private GLCameraView mGLCameraView;
    // 录制按钮
    private Button mRecordButton;

    // muxer for audio/video recording
    private SohuMediaMuxerManager mMediaMuxerManager;

    /**
     * 构造方法
     */
    public CameraFragment() {
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mGLCameraView = (GLCameraView) rootView.findViewById(R.id.cameraView);


        mRecordButton = (Button) rootView.findViewById(R.id.record_button);
        mRecordButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMediaMuxerManager == null) {
                    startRecording();
                } else {
                    stopRecording();
                }
            }
        });
        return rootView;
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
    private final MediaEncoderRunable.MediaEncoderListener mMediaEncoderListener = new MediaEncoderRunable.MediaEncoderListener() {
        @Override
        public void onPrepared(final MediaEncoderRunable encoder) {
            // 开始录制视频
            if (encoder instanceof MediaVideoEncoderRunable) {
                mGLCameraView.setVideoEncoder((MediaVideoEncoderRunable) encoder);
            }
        }

        @Override
        public void onStopped(final MediaEncoderRunable encoder) {
            // 结束录制视频
            if (encoder instanceof MediaVideoEncoderRunable) {
                mGLCameraView.setVideoEncoder(null);
            }
        }
    };
}
