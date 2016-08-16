package com.serenegiant.audiovideosample;

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

import com.serenegiant.encoder.MediaAudioEncoder;
import com.serenegiant.encoder.MediaEncoder;
import com.serenegiant.encoder.MediaMuxerWrapper;
import com.serenegiant.encoder.MediaVideoEncoder;

import java.io.IOException;

public class CameraFragment extends Fragment {
    private static final boolean DEBUG = false;
    private static final String TAG = "CameraFragment";

    /**
     * for camera preview display
     */
    private CameraGLView mCameraView;
    /**
     * button for start/stop recording
     */
    private Button mRecordButton;
    /**
     * muxer for audio/video recording
     */
    private MediaMuxerWrapper mMuxer;

    public CameraFragment() {
        // need default constructor
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mCameraView = (CameraGLView) rootView.findViewById(R.id.cameraView);
        mCameraView.setVideoSize(1280, 720);


        mRecordButton = (Button) rootView.findViewById(R.id.record_button);
        mRecordButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMuxer == null) {
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
        mCameraView.onResume();
    }

    @Override
    public void onPause() {
        stopRecording();
        mCameraView.onPause();
        super.onPause();
    }

    /**
     * 开始录制，应该放在异步线程中进行
     * start resorcing
     * This is a sample project and call this on UI thread to avoid being complicated
     * but basically this should be called on private thread because prepareing
     * of encoder is heavy work
     */
    private void startRecording() {
        if (DEBUG) Log.v(TAG, "startRecording:");
        try {
            mRecordButton.setText(R.string.toggleRecordingOff);    // turn red
            mMuxer = new MediaMuxerWrapper(".mp4");    // if you record audio only, ".m4a" is also OK.
            if (true) {
                // for video capturing
                //new MediaVideoEncoder(mMuxer, mMediaEncoderListener, mCameraView.getVideoWidth(), mCameraView.getVideoHeight());
                new MediaVideoEncoder(mMuxer, mMediaEncoderListener, 480, 480);
            }
            if (true) {
                // for audio capturing
                new MediaAudioEncoder(mMuxer, mMediaEncoderListener);
            }
            mMuxer.prepare();
            mMuxer.startRecording();
        } catch (IOException e) {
            e.printStackTrace();
            mRecordButton.setText(R.string.toggleRecordingOn);
        }
    }

    /**
     * request stop recording
     * 开始录制
     */
    private void stopRecording() {
        mRecordButton.setText(R.string.toggleRecordingOn);
        if (mMuxer != null) {
            mMuxer.stopRecording();
            mMuxer = null;
        }
    }

    /**
     * callback methods from encoder
     */
    private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
        @Override
        public void onPrepared(final MediaEncoder encoder) {
            // 开始录制视频
            if (encoder instanceof MediaVideoEncoder) {
                mCameraView.setVideoEncoder((MediaVideoEncoder) encoder);
            }
        }

        @Override
        public void onStopped(final MediaEncoder encoder) {
            // 结束录制视频
            if (encoder instanceof MediaVideoEncoder) {
                mCameraView.setVideoEncoder(null);
            }
        }
    };
}
