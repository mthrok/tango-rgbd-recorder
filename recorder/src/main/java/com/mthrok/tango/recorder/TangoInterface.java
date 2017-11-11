package com.mthrok.tango.recorder;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

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
import com.projecttango.tangosupport.ux.TangoUx;
import com.projecttango.tangosupport.ux.UxExceptionEvent;
import com.projecttango.tangosupport.ux.UxExceptionEventListener;

import java.util.ArrayList;

/**
 * Created by moto on 11/11/17.
 */

public class TangoInterface {
    private final String TAG = TangoInterface.class.getSimpleName();

    private final Tango mTango;
    private final TangoUx mTangoUx;
    private final TangoDataManager mTangoDataManager;
    private final TangoDataProcessor mTangoDataProcessor;
    private Integer mErrorStringId = null;

    private Boolean mIsStarted;

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
            mTangoUx.equals(event);
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
                    mTangoUx.start();
                    setupTango();
                    startTango();
                    initTangoSupport();
                    mTangoDataProcessor.start();
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
            mIsStarted = true;
        }

        private void initTangoSupport() {
            TangoSupport.initialize(mTango);
        }
    }

    class TangoUxExceptionEventListener implements UxExceptionEventListener {
        @Override
        public void onUxExceptionEvent(UxExceptionEvent event) {
            String status = (event.getStatus() == UxExceptionEvent.STATUS_DETECTED) ? "[DETECTED]" : "[RESOLVED]";
            int eventType = event.getType();
            if (eventType == UxExceptionEvent.TYPE_LYING_ON_SURFACE) {
                Log.i(TAG, status + " Device lying on surface");
            }
            if (eventType == UxExceptionEvent.TYPE_FEW_DEPTH_POINTS) {
                Log.i(TAG, status + " Too few depth points");
            }
            if (eventType == UxExceptionEvent.TYPE_FEW_FEATURES) {
                Log.i(TAG, status + " Too few features");
            }
            if (eventType == UxExceptionEvent.TYPE_MOTION_TRACK_INVALID) {
                Log.i(TAG, status + " Invalid poses in MotionTracking");
            }
            if (eventType == UxExceptionEvent.TYPE_MOVING_TOO_FAST) {
                Log.i(TAG, status + " Moving too fast");
            }
            if (eventType == UxExceptionEvent.TYPE_FISHEYE_CAMERA_OVER_EXPOSED) {
                Log.i(TAG, status + " Fisheye Camera Over Exposed");
            }
            if (eventType == UxExceptionEvent.TYPE_FISHEYE_CAMERA_UNDER_EXPOSED) {
                Log.i(TAG, status + " Fisheye Camera Under Exposed");
            }
        }
    }

    public TangoInterface(Context context) {
        mTangoUx = new TangoUx(context);
        mTangoUx.setUxExceptionEventListener(new TangoInterface.TangoUxExceptionEventListener());
        mTango = new Tango(context, new TangoInterface.OnTangoInitializedCallback(context));
        mTangoDataManager = new TangoDataManager();
        mTangoDataProcessor = new TangoDataProcessor(mTangoDataManager);
    }

    public void stop() {
        synchronized (TangoInterface.this) {
            mIsStarted = false;
            mTangoDataProcessor.stop();
            mTango.disconnect();
            mTangoUx.stop();
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
}

