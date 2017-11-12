package com.mthrok.tango.recorder.tango;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import android.util.Log;
import android.os.Environment;

import com.google.atap.tangoservice.TangoPoseData;
import com.google.tango.depthinterpolation.TangoDepthInterpolation.DepthBuffer;


public class TangoDataRecorder {
    private static final String TAG = TangoDataRecorder.class.getSimpleName();

    private static final String APP_NAME = "TangoRecorder";
    private Boolean mIsOutputStreamReady = false;
    String mTimestamp;

    FileOutputStream mDepthStream;
    FileOutputStream mColorStream;
    FileOutputStream mPoseStream;

    public TangoDataRecorder() {
    }

    private boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public void initFileStreams() {
        if (!isExternalStorageWritable()) {
            return;
        }

        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        mTimestamp = df.format(new Date(System.currentTimeMillis()));

        String prefix = APP_NAME + "/" + mTimestamp;

        File baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), prefix);
        Log.d(TAG, "Initializing output directory: " + baseDir.getPath());

        baseDir.mkdirs();


        File poseFile = new File(baseDir, "pose.bin");
        File colorFile = new File(baseDir, "color.bin");
        File depthFile = new File(baseDir, "depth.bin");
        Log.d(TAG, "Initializing FileOutputStreams");
        synchronized (this) {
            try {
                mPoseStream = new FileOutputStream(poseFile, true);
                mColorStream = new FileOutputStream(colorFile, true);
                mDepthStream = new FileOutputStream(depthFile, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mIsOutputStreamReady = true;
        }
    }

    public void stop() {
        closeFileStreams();
    }

    public void closeFileStreams() {
        synchronized (this) {
            mIsOutputStreamReady = false;
            if (mPoseStream != null) {
                Log.d(TAG, "Closing Pose FileOutputStream");
                try {
                    mPoseStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mPoseStream = null;
                }
            }

            if (mColorStream != null) {
                Log.d(TAG, "Closing Color FileOutputStream");
                try {
                    mColorStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mColorStream = null;
                }
            }

            if (mDepthStream != null) {
                Log.d(TAG, "Closing Depth FileOutputStream");
                try {
                    mDepthStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mDepthStream = null;
                }
            }
        }
    }

    public boolean isOutputStreamReady() {
        return mIsOutputStreamReady;
    }

    private void saveBuffer(ByteBuffer buffer, FileOutputStream stream) {
        buffer.position(0);
        try {
            stream.getChannel().write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveDepthImage(DepthBuffer imageBuffer) {
        Log.d(TAG, "Saving Depth image");
        ByteBuffer buffer = ByteBuffer.allocate(imageBuffer.depths.capacity() * 4);
        imageBuffer.depths.position(0);
        for(int i = 0; i < imageBuffer.depths.capacity(); ++ i) {
            buffer.putFloat(imageBuffer.depths.get());
        }
        synchronized (this) {
            saveBuffer(buffer, mDepthStream);
        }
    }

    public void saveColorImage(ByteBuffer imageBuffer) {
        Log.d(TAG, "Saving Color image");
        synchronized (this) {
            saveBuffer(imageBuffer, mColorStream);
        }
    }

    public void savePoseData(TangoPoseData pose) {
        Log.d(TAG, "Saving Pose data");
        // 8 double 4 int 1 float -> 8 * 8 + 4 * 4 + 1 * 4 = 84 byte
        ByteBuffer buffer = ByteBuffer.allocate(84);
        buffer.putDouble(pose.timestamp);
        for (int i = 0; i < 4; i ++) {
            buffer.putDouble(pose.rotation[i]);
        }
        for (int i = 0; i < 3; i ++) {
            buffer.putDouble(pose.translation[i]);
        }
        buffer.putInt(pose.statusCode);
        buffer.putInt(pose.baseFrame);
        buffer.putInt(pose.targetFrame);
        buffer.putInt(pose.confidence);
        buffer.putFloat(pose.accuracy);

        synchronized (this) {
            saveBuffer(buffer, mPoseStream);
        }
    }
}
