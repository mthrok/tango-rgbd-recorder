package com.mthrok.tango.recorder;

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

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by moto on 11/7/17.
 */

class TangoDataProcessor {
    private static final String TAG = TangoDataProcessor.class.getSimpleName();

    private TangoDataManager mManager;
    private TangoDataRecorder mRecorder;

    private Thread mMainThread;
    private Boolean mIsRunning = false;

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

    class MainProcess implements Runnable {
        @Override
        public void run() {
            mIsRunning = true;
            while(mIsRunning) {
                runOnce();
            }
        }

        private void runOnce() {
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
            mPointCloud = mManager.getLatestPointCloud();
            if (mPointCloud == null) {
                return false;
            }

            mPose = mManager.getPoseData(mPointCloud.timestamp);

            if (mColorCameraTPointCloud.statusCode != TangoPoseData.POSE_VALID) {
                boolean success = initializeRelativePose(mPointCloud.timestamp);
                if (!success) {
                    return false;
                }
            }

            TangoImageBuffer colorImageBuffer = mManager.getColorImage(mPointCloud.timestamp);
            if (colorImageBuffer == null) {
                return false;
            }

            if (colorImageBuffer.width == 0 || colorImageBuffer.height == 0) {
                return false;
            }

            mImageWidth = colorImageBuffer.width;
            mImageHeight = colorImageBuffer.height;

            if (mPointCloud.numPoints == 0) {
                return false;
            }

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
                Log.d(TAG, "Allocating Depth Buffer. " + width + " x " + height + " (" +capacity + " bytes)");
                mDepthImageBuffer = ByteBuffer.allocateDirect(capacity);
            }
            if (mColorImageBuffer == null || mColorImageBuffer.capacity() < capacity) {
                Log.d(TAG, "Allocating Color Buffer. " + width + " x  " + height + " (" +capacity + " bytes)");
                mColorImageBuffer = ByteBuffer.allocateDirect(capacity);
            }
            Utility.convertDepthBufferToByteBuffer(depthBuffer, mDepthImageBuffer);
            Utility.convertTangoImageBufferToByteBuffer(colorBuffer, mColorImageBuffer);
        }
    }

    public TangoDataProcessor(TangoDataManager manager) {
        mManager = manager;
        mIsRunning = false;
        mIsRecording = false;
        mRecorder =  new TangoDataRecorder();
    }

    private void startMainProcess() {
        mMainThread = new Thread(new MainProcess());
        mMainThread.start();
    }

    private void stopMainProcess() {
        Log.d(TAG, "Stopping main process.");
        mIsRunning = false;
        try {
            mMainThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "Stopped main process.");
    }

    public void start() {
        startMainProcess();
    }

    public void stop() {
        stopMainProcess();
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

    public Bitmap[] getBitmaps() {
        if (!mIsBufferReady) {
            return null;
        }

        mImageBufferLock.lock();
        try {
            Bitmap[] ret = {
                Utility.createBitmapFromByteBuffer(mColorImageBuffer, mImageWidth, mImageHeight),
                Utility.createBitmapFromByteBuffer(mDepthImageBuffer, mImageWidth, mImageHeight)
            };
            return ret;
        } finally {
            mImageBufferLock.unlock();
        }
    }
}

