package com.demo.tutorial.tango.tangorgbd;

import android.util.Log;

import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.Math;

/**
 * Created by moto on 10/19/17.
 */

public class TangoImageBufferManager {
    private static final String TAG = TangoImageBufferManager.class.getSimpleName();

    private final static int DEFAULT_BUFFER_SIZE = 7;
    private TangoImageBuffer[] mImageBuffers;

    public TangoImageBufferManager(int bufferSize) {
        mImageBuffers = new TangoImageBuffer[bufferSize];
        for (int i = 0; i < bufferSize; ++i) {
            mImageBuffers[i] = new TangoImageBuffer();
        }
    }

    public TangoImageBufferManager() {
        this(DEFAULT_BUFFER_SIZE);
    }

    public TangoImageBuffer getLatestImageBuffer() {
        return getImageBuffer(Double.MAX_VALUE);
    }

    private int getIndexClosest(double timestamp, int defaultValue) {
        int index = defaultValue;
        double min_diff = Double.POSITIVE_INFINITY;
        for (int i = 0; i < mImageBuffers.length; ++i) {
            if (mImageBuffers[i] != null) {
                double diff = Math.abs(timestamp - mImageBuffers[i].timestamp);
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
     * @return The color frame with the closest timestamp. If no color frame is available, null.
     */
    public TangoImageBuffer getImageBuffer(double timestamp) {
        synchronized (this) {
            int index = getIndexClosest(timestamp, -1);
            if (index == -1) {
                return null;
            } else {
                TangoImageBuffer ret = mImageBuffers[index];
                mImageBuffers[index] = new TangoImageBuffer();
                return ret;
            }
        }
    }

    public void updateImageBuffer(TangoImageBuffer sourceBuffer) throws TangoInvalidException {
        if(sourceBuffer == null || Double.isNaN(sourceBuffer.timestamp) || sourceBuffer.timestamp < 0.0D || sourceBuffer.width <= 0 || sourceBuffer.height <= 0 || sourceBuffer.data == null) {
            throw new TangoInvalidException();
        } else {
            synchronized(this) {
                int index = getIndexClosest(0, 0);
                TangoImageBuffer targetBuffer = mImageBuffers[index];

                int capacity = sourceBuffer.data.capacity();
                if(targetBuffer.data == null || targetBuffer.data.capacity() < capacity) {
                    targetBuffer.data = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
                }
                copyImageBuffer(sourceBuffer, targetBuffer);
            }
        }
    }

    private void copyImageBuffer(TangoImageBuffer src, TangoImageBuffer tgt) {
        tgt.width = src.width;
        tgt.height = src.height;
        tgt.stride = src.stride;
        tgt.format = src.format;
        tgt.timestamp = src.timestamp;
        tgt.frameNumber = src.frameNumber;
        tgt.exposureDurationNs = src.exposureDurationNs;

        src.data.rewind();
        tgt.data.rewind();
        tgt.data.put(src.data);
        src.data.rewind();
    }
}
