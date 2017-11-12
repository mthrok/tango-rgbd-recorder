package com.mthrok.tango.recorder.tango;

import java.util.ArrayList;

import android.content.Context;
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
import com.mthrok.tango.recorder.R;


public class TangoInterface {
    private final String TAG = TangoInterface.class.getSimpleName();

    private final Tango mTango;
    private final TangoDataStore mTangoDataStore;
    private Integer mErrorStringId = null;

    class TangoUpdateCallback extends Tango.TangoUpdateCallback {
        @Override
        public void onPoseAvailable(TangoPoseData pose) {
            mTangoDataStore.updatePoseData(pose);
        }

        @Override
        public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
            // Log.d(TAG, "POINT CLOUD AVAILABLE.");
            mTangoDataStore.updatePointCloud(pointCloud);
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
            mTangoDataStore.updateColorCameraFrame(buffer);
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
            framePairs.add(
                new TangoCoordinateFramePair(
                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                    TangoPoseData.COORDINATE_FRAME_DEVICE
                )
            );

            mTango.connectListener(
                    framePairs,
                    new TangoInterface.TangoUpdateCallback()
            );
            mTango.experimentalConnectOnFrameListener(
                    TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                    new TangoInterface.ColorCameraListener()
            );
        }

        private void initTangoSupport() {
            TangoSupport.initialize(mTango);
        }
    }

    public TangoInterface(Context context, TangoDataStore store) {
        mTango = new Tango(context, new TangoInterface.OnTangoInitializedCallback(context));
        mTangoDataStore = store;
    }

    public void stop() {
        synchronized (TangoInterface.this) {
            mTango.disconnect();
        }
    }

    public Integer checkError() {
        return mErrorStringId;
    }
}