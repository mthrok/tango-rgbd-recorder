package com.demo.tutorial.tango.tangorgbd;

import android.util.Log;
import android.content.Context;

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
    String mPrefix;

    FileOutputStream mDepthStream;
    FileOutputStream mColorStream;
    FileOutputStream mPoseStream;

    public TangoDataRecorder(Context context) {
        mContext = context;

        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmssZ");
        mPrefix = df.format(new Date(System.currentTimeMillis()));
        Log.d(TAG, "Initializing " + mPrefix);

        initFileStreams();
    }

    private void initFileStreams() {
        File baseDir = new File(mContext.getFilesDir(), mPrefix);
        baseDir.mkdirs();

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
    }

    public void closeFileStreams() {
        try {
            mPoseStream.close();
            mColorStream.close();
            mDepthStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
