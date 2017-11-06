package com.mthrok.tango.recorder;

import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.google.tango.support.TangoPointCloudManager;

/**
 * Created by moto on 10/18/17.
 */


public class TangoDataManager {
    private static final String TAG = TangoDataManager.class.getSimpleName();

    private TangoPointCloudManager mPointCloudManager;
    private TangoImageBufferManager mColorImageBuffer;
    private TangoPoseDataManager mPoseDataManager;

    public TangoDataManager() {
        mColorImageBuffer = new TangoImageBufferManager();
        mPointCloudManager = new TangoPointCloudManager();
        mPoseDataManager = new TangoPoseDataManager();
    };

    public void updatePointCloud(TangoPointCloudData pointCloud) throws TangoInvalidException {
        mPointCloudManager.updatePointCloud(pointCloud);
    }

    public void updateColorCameraFrame(TangoImageBuffer buffer) throws TangoInvalidException {
        mColorImageBuffer.updateImageBuffer(buffer);
    }

    public void updatePoseData(TangoPoseData poseData) throws TangoInvalidException {
        mPoseDataManager.updatePoseData(poseData);
    }

    public TangoPointCloudData getLatestPointCloud() {
        return mPointCloudManager.getLatestPointCloud();
    }

    public TangoImageBuffer getColorImage(double timestamp) {
        return mColorImageBuffer.getImageBuffer(timestamp);
    }

    public TangoPoseData getPoseData(double timestamp) {
        return mPoseDataManager.getPoseData(timestamp);
    }
}
