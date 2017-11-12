package com.mthrok.tango.recorder.tango;

import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoInvalidException;


public class TangoPoseDataManager {
    private static final String TAG = TangoImageBufferManager.class.getSimpleName();

    private final static int DEFAULT_BUFFER_SIZE = 7;

    private int mBufferSize;
    private TangoPoseData[] mPoseDataBuffer;

    public TangoPoseDataManager(int bufferSize) {
        mBufferSize = bufferSize;
        allocateBuffer();
    }

    public TangoPoseDataManager() {
        this(DEFAULT_BUFFER_SIZE);
    }

    public TangoPoseData getLatestPoseData() {
        return getPoseData(Double.MAX_VALUE);
    }

    private void allocateBuffer() {
        mPoseDataBuffer = new TangoPoseData[mBufferSize];
        for (int i = 0; i < mBufferSize; ++i) {
            mPoseDataBuffer[i] = new TangoPoseData();
        }
    }

    // Factor out base class
    private int getIndexClosest(double timestamp, int defaultValue) {
        int index = defaultValue;
        double min_diff = Double.POSITIVE_INFINITY;
        for (int i = 0; i < mPoseDataBuffer.length; ++i) {
            if (mPoseDataBuffer[i] != null) {
                double diff = Math.abs(timestamp - mPoseDataBuffer[i].timestamp);
                if (diff < min_diff) {
                    min_diff = diff;
                    index = i;
                }
            }
        }
        return index;
    }

    /**
     * Fetch color frame closest to the given timestamp.
     * @param timestamp
     * @return The pose data with the closest timestamp. If no color frame is available, null.
     */
    public TangoPoseData getPoseData(double timestamp) {
        synchronized (this) {
            int index = getIndexClosest(timestamp, -1);
            if (index == -1) {
                return null;
            } else {
                TangoPoseData ret = mPoseDataBuffer[index];
                mPoseDataBuffer[index] = new TangoPoseData();
                return ret;
            }
        }
    }

    public void updatePoseData(TangoPoseData sourcePoseData) throws TangoInvalidException {
        if(sourcePoseData == null || Double.isNaN(sourcePoseData.timestamp) || sourcePoseData.timestamp < 0.0D ) {
            throw new TangoInvalidException();
        } else {
            synchronized(this) {
                int index = getIndexClosest(0, 0);
                TangoPoseData targetPoseData = mPoseDataBuffer[index];
                copyPoseData(sourcePoseData, targetPoseData);
            }
        }
    }

    private void copyPoseData(TangoPoseData src, TangoPoseData tgt) {
        tgt.timestamp = src.timestamp;
        for (int i = 0; i < 3; ++i) {
            tgt.rotation[i] = src.rotation[i];
            tgt.translation[i] = src.translation[i];
        }
        tgt.statusCode = src.statusCode;
        tgt.baseFrame = src.baseFrame;
        tgt.targetFrame = src.targetFrame;
        tgt.confidence = src.confidence;
        tgt.accuracy = src.accuracy;
    }
}
