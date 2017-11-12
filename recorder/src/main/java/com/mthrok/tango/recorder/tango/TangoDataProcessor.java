package com.mthrok.tango.recorder.tango;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.google.tango.depthinterpolation.TangoDepthInterpolation;
import com.google.tango.depthinterpolation.TangoDepthInterpolation.DepthBuffer;
import com.google.tango.support.TangoSupport;
import com.mthrok.tango.recorder.utility.FixedDelayExecutor;


public class TangoDataProcessor {
    private static final String TAG = TangoDataProcessor.class.getSimpleName();

    private TangoDataStore mStore;
    private TangoDataRecorder mRecorder;

    private FixedDelayExecutor mMainProcess;

    private TangoPoseData mColorCameraTPointCloud = new TangoPoseData();
    private ReentrantLock mImageBufferLock = new ReentrantLock();
    private TangoPoseData mPose;
    private TangoPointCloudData mPointCloud;
    private DepthBuffer mDepthBuffer;
    private ByteBuffer mDepthImageBuffer;
    private ByteBuffer mColorImageBuffer;
    private int mImageWidth = 0;
    private int mImageHeight = 0;

    private Boolean mIsBufferReady = false;
    private Boolean mIsRecording = false;

    class DataProcessorJob implements Runnable {
            @Override
            public void run() {
                Log.d(TAG, "Running MainProcess");
                boolean isBufferUpdated = generateRGBDImages();

                if (isBufferUpdated && mIsRecording && mRecorder.isOutputStreamReady()) {
                    mImageBufferLock.lock();
                    try {
                        mRecorder.saveDepthImage(mDepthBuffer);
                        mRecorder.saveColorImage(mColorImageBuffer);
                        mRecorder.savePoseData(mPose);
                    } finally {
                        mImageBufferLock.unlock();
                    }
                }
            }

            private boolean generateRGBDImages() {
                mPointCloud = mStore.getLatestPointCloud();
                if (mPointCloud == null || mPointCloud.numPoints == 0) {
                    return false;
                }

                mPose = mStore.getPoseData(mPointCloud.timestamp);
                if (mPose == null) {
                    return false;
                }

                TangoImageBuffer colorImageBuffer = mStore.getColorImage(mPointCloud.timestamp);
                if (colorImageBuffer == null || colorImageBuffer.width * colorImageBuffer.height == 0) {
                    return false;
                }

                if (mColorCameraTPointCloud.statusCode != TangoPoseData.POSE_VALID) {
                    boolean success = initializeRelativePose(mPointCloud.timestamp);
                    if (!success) {
                        return false;
                    }
                }

                mImageWidth = colorImageBuffer.width;
                mImageHeight = colorImageBuffer.height;

                mDepthBuffer = TangoDepthInterpolation.upsampleImageNearestNeighbor(
                        mPointCloud, mImageWidth, mImageHeight, mColorCameraTPointCloud);

                mImageBufferLock.lock();
                try {
                    fillImageBuffers(colorImageBuffer, mDepthBuffer);
                } finally {
                    mImageBufferLock.unlock();
                }
                mIsBufferReady = true;
                return true;
            }

            private Boolean initializeRelativePose(double timestamp) {
                Boolean success = false;
                try {
                    mColorCameraTPointCloud = TangoSupport.calculateRelativePose(
                            timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                            timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH
                    );
                    success = true;
                } catch (TangoInvalidException e) {
                    // TangoSupport is not initialized
                    Log.d(TAG, "Error calculating relative pose.");
                } catch (TangoErrorException e) {
                    // Device might not be in a good position
                    Log.d(TAG, "Failed to calculate relative pose.");
                }
                return success;
            }

            private void fillImageBuffers(TangoImageBuffer colorBuffer, DepthBuffer depthBuffer) {
                int width = colorBuffer.width;
                int height = colorBuffer.height;
                int capacity = 4 * width * height;
                if (mDepthImageBuffer == null || mDepthImageBuffer.capacity() < capacity) {
                    Log.d(TAG, "Allocating Depth Buffer. " + width + " x " + height + " (" + capacity + " bytes)");
                    mDepthImageBuffer = ByteBuffer.allocateDirect(capacity);
                }
                if (mColorImageBuffer == null || mColorImageBuffer.capacity() < capacity) {
                    Log.d(TAG, "Allocating Color Buffer. " + width + " x  " + height + " (" + capacity + " bytes)");
                    mColorImageBuffer = ByteBuffer.allocateDirect(capacity);
                }
                com.mthrok.tango.recorder.tango.Utility.convertDepthBufferToByteBuffer(depthBuffer, mDepthImageBuffer);
                com.mthrok.tango.recorder.tango.Utility.convertTangoImageBufferToByteBuffer(colorBuffer, mColorImageBuffer);
            }
        }


    public TangoDataProcessor(TangoDataStore Store) {
        mStore = Store;
        mIsRecording = false;
        mRecorder =  new TangoDataRecorder();
        mMainProcess = new FixedDelayExecutor(new DataProcessorJob(), 30);
    }

    public void stop() {
        mMainProcess.stop();
        mRecorder.closeFileStreams();
    }

    public void toggleRecordingState() {
        mIsRecording = !mIsRecording;

        if (mIsRecording) {
            mRecorder.initFileStreams();
        } else {
            mRecorder.closeFileStreams();
        }
    }

    public boolean isBufferReady() {
        return mIsBufferReady;
    }

    // precondition: mIsBufferReady == true
    public int[] getImageSize() {
        int [] ret = {mImageWidth, mImageHeight};
        return ret;
    }

    // precondition: mIsBufferReady == true
    public void getBitmaps(Bitmap[] bitmaps) {
        mImageBufferLock.lock();
        try {
            mColorImageBuffer.rewind();
            mDepthImageBuffer.rewind();
            bitmaps[0].copyPixelsFromBuffer(mColorImageBuffer);
            bitmaps[1].copyPixelsFromBuffer(mDepthImageBuffer);
        } finally {
            mImageBufferLock.unlock();
        }
    }
}

