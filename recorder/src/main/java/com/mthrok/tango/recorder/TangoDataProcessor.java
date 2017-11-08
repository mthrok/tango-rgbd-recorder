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
    private ByteBuffer mDepthImageBuffer;
    private ByteBuffer mColorImageBuffer;
    private int mImageWidth = 0;
    private int mImageHeight = 0;

    private Boolean mIsBufferReady = false;
    private Boolean mIsRecording = false;

    class MainProcess implements Runnable {
        @Override
        public void run() {
            while(mIsRunning) {
                runOnce();
            }
        }

        private void runOnce() {
            generateRGBDImages();

            if (mIsBufferReady && mIsRecording) {
                Log.d(TAG, "Recording...");
                mRecorder.saveDepthImage();
                mRecorder.saveColorImage();
                mRecorder.savePoseData();
            }
        }

        private void generateRGBDImages() {
            TangoPointCloudData pointCloud = mManager.getLatestPointCloud();
            if (pointCloud == null) {
                return;
            }

            if (mColorCameraTPointCloud.statusCode != TangoPoseData.POSE_VALID) {
                boolean success = initializeRelativePose(pointCloud.timestamp);
                if (!success) {
                    return;
                }
            }

            TangoImageBuffer colorImageBuffer = mManager.getColorImage(pointCloud.timestamp);
            if (colorImageBuffer == null) {
                return;
            }

            if (colorImageBuffer.width == 0 || colorImageBuffer.height == 0) {
                return;
            }

            mImageWidth = colorImageBuffer.width;
            mImageHeight = colorImageBuffer.height;

            if (pointCloud.numPoints == 0) {
                return;
            }

            DepthBuffer depthImageBuffer = TangoDepthInterpolation.upsampleImageNearestNeighbor(
                    pointCloud, mImageWidth, mImageHeight, mColorCameraTPointCloud);

            mImageBufferLock.lock();
            try {
                fillImageBuffers(colorImageBuffer, depthImageBuffer);
            } finally {
                mImageBufferLock.unlock();
            }

            mIsBufferReady = true;
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

    public void start() {
        mIsRunning = true;
        mMainThread = new Thread(new MainProcess());
        mMainThread.start();
    }

    public void stop() {
        Log.d(TAG, "Stopping main process.");
        mIsRunning = false;
        try {
            mMainThread.join();
            Log.d(TAG, "Stopped main process.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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

