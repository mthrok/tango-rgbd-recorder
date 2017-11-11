package com.mthrok.tango.recorder;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.google.tango.support.TangoSupport;

import java.util.ArrayList;

/**
 * Created by moto on 11/11/17.
 */

public class TangoInterface {
    private final String TAG = TangoInterface.class.getSimpleName();

    private final Tango mTango;
    private final TangoDataManager mTangoDataManager;
    private final TangoDataProcessor mTangoDataProcessor;
    private Integer mErrorStringId = null;

    private Boolean mIsRunning;

    class TangoUpdateCallback extends Tango.TangoUpdateCallback {
        @Override
        public void onPoseAvailable(TangoPoseData pose) {
            mTangoDataManager.updatePoseData(pose);
        }

        @Override
        public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
            // Log.d(TAG, "POINT CLOUD AVAILABLE.");
            mTangoDataManager.updatePointCloud(pointCloud);
        }

        @Override
        public void onFrameAvailable(int cameraId) {
            if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                Log.d(TAG, "COLOR CAMERA FRAME AVAILABLE.");
            } else {
                Log.d(TAG, "FISH-EYE CAMERA FRAME AVAILABLE.");
            }
        }

        @Override
        public void onTangoEvent(TangoEvent event) {
            Log.d(TAG, "TANGO EVENT: " + event.toString());
        }
    }

    class ColorCameraListener implements Tango.OnFrameAvailableListener {
        @Override
        public void onFrameAvailable(TangoImageBuffer buffer, int cameraId) {
            // Log.d(TAG, "COLOR CAMERA FRAME AVAILABLE.");
            mTangoDataManager.updateColorCameraFrame(buffer);
        }
    }

    class OnTangoInitializedCallback implements Runnable {
        private final String TAG = TangoInterface.OnTangoInitializedCallback.class.getSimpleName();
        private final Context mContext;

        private OnTangoInitializedCallback(Context context) {
            mContext = context;
        }

        @Override
        public void run() {
            synchronized (TangoInterface.this) {
                try {
                    setupTango();
                    startTango();
                    initTangoSupport();
                    mTangoDataProcessor.start();
                    mIsRunning = true;
                } catch (TangoOutOfDateException e) {
                    Log.e(TAG, mContext.getString(R.string.exception_out_of_date), e);
                    mErrorStringId = R.string.exception_out_of_date;
                } catch (TangoErrorException e) {
                    Log.e(TAG, mContext.getString(R.string.exception_tango_error), e);
                    mErrorStringId = R.string.exception_tango_error;
                } catch (TangoInvalidException e) {
                    Log.e(TAG, mContext.getString(R.string.exception_tango_invalid), e);
                    mErrorStringId = R.string.exception_tango_invalid;
                }
            }
        }

        private void setupTango() {
            TangoConfig config = mTango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
            config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
            config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
            config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
            mTango.connect(config);
        }

        private void startTango() {
            ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<>();
            // Required to enable pose data callback
            framePairs.add(new TangoCoordinateFramePair(
                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                    TangoPoseData.COORDINATE_FRAME_DEVICE
            ));

            mTango.connectListener(
                    framePairs, new TangoInterface.TangoUpdateCallback());
            mTango.experimentalConnectOnFrameListener(
                    TangoCameraIntrinsics.TANGO_CAMERA_COLOR, new TangoInterface.ColorCameraListener());
        }

        private void initTangoSupport() {
            TangoSupport.initialize(mTango);
        }
    }

    public TangoInterface(Context context) {
        mTango = new Tango(context, new TangoInterface.OnTangoInitializedCallback(context));
        mTangoDataManager = new TangoDataManager();
        mTangoDataProcessor = new TangoDataProcessor(mTangoDataManager);
    }

    public void stop() {
        synchronized (TangoInterface.this) {
            mIsRunning = false;
            mTangoDataProcessor.stop();
            mTango.disconnect();
        }
    }

    // TODO: Think better approach
    public void toggleRecordingState() {
        mTangoDataProcessor.toggleRecordingState();
    }

    // TODO: Think better approach
    public Bitmap[] getBitmaps() {
        return mTangoDataProcessor.getBitmaps();
    }

    public int checkError() {
        return mErrorStringId;
    }

    public boolean isRunning() {
        return mIsRunning;
    }
}