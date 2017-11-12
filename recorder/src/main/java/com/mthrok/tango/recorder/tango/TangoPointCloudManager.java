package com.mthrok.tango.recorder.tango;

import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoPointCloudData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class TangoPointCloudManager {
    private static final String TAG = TangoPointCloudManager.class.getSimpleName();

    private final static int DEFAULT_BUFFER_SIZE = 7;

    private ByteBuffer mDirectBuffers[];
    private TangoPointCloudData mPointCloudBuffer[];

    public class PointCloudAndBuffer {
        public final TangoPointCloudData pointCloud;
        public final ByteBuffer buffer;

        public PointCloudAndBuffer(TangoPointCloudData pointCloud, ByteBuffer buffer) {
            this.pointCloud = pointCloud;
            this.buffer = buffer;
        }
    }

    public TangoPointCloudManager(int bufferSize) {
        mPointCloudBuffer = new TangoPointCloudData[bufferSize];
        mDirectBuffers = new ByteBuffer[bufferSize];
        for (int i = 0; i < bufferSize; ++i) {
            mDirectBuffers[i] = ByteBuffer.allocateDirect(0);
            mPointCloudBuffer[i] = new TangoPointCloudData();
        }
    }

    public TangoPointCloudManager() {
        this(DEFAULT_BUFFER_SIZE);
    }

    private int getIndexClosest(double timestamp, int defaultValue) {
        int index = defaultValue;
        double min_diff = Double.POSITIVE_INFINITY;
        for (int i = 0; i < mPointCloudBuffer.length; ++i) {
            if (mPointCloudBuffer[i] != null) {
                double diff = Math.abs(timestamp - mPointCloudBuffer[i].timestamp);
                if (diff < min_diff) {
                    min_diff = diff;
                    index = i;
                }
            }
        }
        return index;
    }

    /**
     * Fetch point cloud data closest to the given timestamp.
     * @param timestamp
     * @return The point cloud with the closest timestamp. If no color frame is available, null.
     */
    public TangoPointCloudData getPointCloud(double timestamp) {
        return getPointCloudAndBuffer(timestamp).pointCloud;
    }

    public PointCloudAndBuffer getPointCloudAndBuffer(double timestamp) {
        synchronized (this) {
            int index = getIndexClosest(timestamp, -1);
            if (index == -1) {
                return null;
            } else {
                PointCloudAndBuffer ret = new PointCloudAndBuffer(
                        mPointCloudBuffer[index],
                        mDirectBuffers[index]
                );
                mPointCloudBuffer[index] = new TangoPointCloudData();
                mDirectBuffers[index] = null;
                return ret;
            }
        }
    }

    public void updatePointCloud(TangoPointCloudData sourceBuffer) throws TangoInvalidException {
        if(sourceBuffer == null || Double.isNaN(sourceBuffer.timestamp) || sourceBuffer.timestamp < 0.0D || sourceBuffer.numPoints <= 0 || sourceBuffer.points == null) {
            throw new TangoInvalidException();
        } else {
            synchronized(this) {
                int index = getIndexClosest(0, 0);
                TangoPointCloudData targetBuffer = mPointCloudBuffer[index];

                int capacity = sourceBuffer.points.capacity();
                if(targetBuffer.points == null || targetBuffer.points.capacity() < capacity) {
                    mDirectBuffers[index] = ByteBuffer.allocateDirect(4 * capacity).order(ByteOrder.nativeOrder());
                    targetBuffer.points = mDirectBuffers[index].asFloatBuffer();
                }
                copyPointCloud(sourceBuffer, targetBuffer);
            }
        }
    }

    private void copyPointCloud(TangoPointCloudData src, TangoPointCloudData tgt) {
        tgt.timestamp = src.timestamp;
        tgt.numPoints = src.numPoints;
        tgt.pointCloudParcelFileDescriptorSize = src.pointCloudParcelFileDescriptorSize;
        tgt.pointCloudParcelFileDescriptorFlags = src.pointCloudParcelFileDescriptorFlags;
        tgt.pointCloudParcelFileDescriptorOffset = src.pointCloudParcelFileDescriptorOffset;
        tgt.pointCloudNativeFileDescriptor = src.pointCloudNativeFileDescriptor;

        src.points.rewind();
        tgt.points.rewind();
        tgt.points.put(src.points);
        src.points.rewind();
    }
}