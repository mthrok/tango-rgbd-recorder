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

    private final String mAppName;
    private Boolean mIsOutputStreamReady = false;

    FileOutputStream mPoseStream;
    FileOutputStream mColorStream;
    FileOutputStream mDepthStream;

    public TangoDataRecorder(String appName) {
        mAppName = appName;
    }

    private boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public synchronized void initFileStreams() {
        if (!isExternalStorageWritable()) {
            return;
        }

        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = df.format(new Date(System.currentTimeMillis()));

        String prefix = mAppName + "/" + timestamp;

        File baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), prefix);
        Log.d(TAG, "Initializing output directory: " + baseDir.getPath());

        baseDir.mkdirs();


        File poseFile = new File(baseDir, "pose.bin");
        File colorFile = new File(baseDir, "color.bin");
        File depthFile = new File(baseDir, "depth.bin");
        Log.d(TAG, "Initializing FileOutputStreams");
        try {
            mPoseStream = new FileOutputStream(poseFile, true);
            mColorStream = new FileOutputStream(colorFile, true);
            mDepthStream = new FileOutputStream(depthFile, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mIsOutputStreamReady = true;
    }

    public void stop() {
        closeFileStreams();
    }

    private synchronized void closeFileStream(FileOutputStream stream, String message) throws IOException {
        if (stream != null) {
            Log.d(TAG, message);
            stream.close();
        }
    }

    public void closeFileStreams() {
        mIsOutputStreamReady = false;
        FileOutputStream[] streams = {mPoseStream, mColorStream, mDepthStream};
        String[] messages = {
                "Closing Pose FileOutputStream",
                "Closing Color FileOutputStream",
                "Closing Depth FileOutputStream",
        };
        for (int i = 0; i < 3; ++i) {
            try {
                closeFileStream(streams[i], messages[i]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isOutputStreamReady() {
        return mIsOutputStreamReady;
    }

    private synchronized void saveBuffer(ByteBuffer buffer, FileOutputStream stream, String message) {
        if (mIsOutputStreamReady) {
            Log.d(TAG, message);
            buffer.position(0);
            try {
                stream.getChannel().write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveDepthImage(DepthBuffer imageBuffer) {
        ByteBuffer buffer = ByteBuffer.allocate(imageBuffer.depths.capacity() * 4);
        imageBuffer.depths.position(0);
        for(int i = 0; i < imageBuffer.depths.capacity(); ++ i) {
            buffer.putFloat(imageBuffer.depths.get());
        }
        saveBuffer(buffer, mDepthStream, "Saving Depth image");
    }

    public void saveColorImage(ByteBuffer imageBuffer) {
        saveBuffer(imageBuffer, mColorStream, "Saving Color image");
    }

    public void savePoseData(TangoPoseData pose) {
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

        saveBuffer(buffer, mPoseStream, "Saving Pose data");
    }
}
