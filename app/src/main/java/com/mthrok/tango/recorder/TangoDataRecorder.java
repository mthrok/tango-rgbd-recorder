package com.mthrok.tango.recorder;

import android.util.Log;
import android.content.Context;

import com.google.tango.depthinterpolation.TangoDepthInterpolation;

import android.os.Environment;

import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by moto on 10/23/17.
 */

public class TangoDataRecorder {
    private static final String TAG = TangoDataRecorder.class.getSimpleName();

    Context mContext;
    static final String appname="TangoRGBD";
    String mTimestamp;

    FileOutputStream mDepthStream;
    FileOutputStream mColorStream;
    FileOutputStream mPoseStream;

    public TangoDataRecorder(Context context) {
        mContext = context;

        if (isExternalStorageWritable()) {
            initFileStreams();
        }
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

        String prefix = appname + "/" + mTimestamp;

        Log.d(TAG, "Initializing: " + prefix);
        File baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), prefix);
        Log.d(TAG, "Initializing directory: " + baseDir.getPath());

        baseDir.mkdirs();

        /*
        File poseFile = new File(baseDir, "pose.bin");
        File colorFile = new File(baseDir, "color.bin");
        File depthFile = new File(baseDir, "depth.bin");
        try {
            mPoseStream = mContext.openFileOutput(poseFile.getPath(), Context.MODE_APPEND);
            mColorStream = mContext.openFileOutput(colorFile.getPath(), Context.MODE_APPEND);
            mDepthStream = mContext.openFileOutput(depthFile.getPath(), Context.MODE_APPEND);
        } catch (Exception e) {
            e.printStackTrace();
        }
        */
    }

    public void closeFileStreams() {
        /*
        try {
            mPoseStream.close();
            mColorStream.close();
            mDepthStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        */
    }

    public void saveDepthImage() {
        Log.d(TAG, "Saving Depth image");

    }

    public void saveColorImage() {
        Log.d(TAG, "Saving Color image");
    }

    public void savePoseData() {
        Log.d(TAG, "Saving Pose data");
    }
}
