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

import com.mthrok.tango.recorder.utility.ExceptionQueue;
import com.mthrok.tango.recorder.utility.FixedDelayExecutor;


public class TangoDataProcessor extends ExceptionQueue {
    private static final String TAG = TangoDataProcessor.class.getSimpleName();

    private TangoDataStore mTangoDataStore;

    private FixedDelayExecutor mMainProcess;

    private TangoPoseData mColorCameraTPointCloud = new TangoPoseData();
    private ReentrantLock mImageBufferLock = new ReentrantLock();
    private TangoPoseData mPose;
    private TangoPointCloudData mPointCloud;
    private DepthBuffer mDepthBuffer;
    private ByteBuffer mDepthImageBuffer;
    private ByteBuffer mColorImageBuffer;
    private int mImageWidth = -1;
    private int mImageHeight = -1;

    private Boolean mIsBufferReady = false;

    class DataProcessorJob implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "Running MainProcess");
            boolean isBufferUpdated = generateRGBDImages();
        }

        private boolean generateRGBDImages() {
            mPointCloud = mTangoDataStore.getPointCloud(Double.MAX_VALUE);
            if (mPointCloud == null || mPointCloud.numPoints == 0) {
                return false;
            }

            mPose = mTangoDataStore.getPoseData(mPointCloud.timestamp);
            if (mPose == null) {
                return false;
            }

            TangoImageBuffer colorImageBuffer = mTangoDataStore.getColorImage(mPointCloud.timestamp);
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
            } catch (Exception e) {
                Log.e(TAG, "Failed to fill image buffer.", e);
                storeException(e);
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
            Utility.convertDepthBufferToByteBuffer(depthBuffer, mDepthImageBuffer);
            Utility.convertTangoImageBufferToByteBuffer(colorBuffer, mColorImageBuffer);
        }
    }

    public TangoDataProcessor(TangoDataStore Store) {
        mTangoDataStore = Store;
        mMainProcess = new FixedDelayExecutor(new DataProcessorJob(), 30);
    }

    public void stop() {
        mMainProcess.stop();
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
        } catch(Exception e) {
            storeException(e);
        } finally {
            mImageBufferLock.unlock();
        }
    }
}

