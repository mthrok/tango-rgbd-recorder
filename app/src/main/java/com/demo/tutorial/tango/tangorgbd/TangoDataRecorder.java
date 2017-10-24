package com.demo.tutorial.tango.tangorgbd;

import android.util.Log;

/**
 * Created by moto on 10/23/17.
 */

public class TangoDataRecorder {
    private static final String TAG = TangoDataRecorder.class.getSimpleName();

    String mPrefix;

    public TangoDataRecorder() {
        Long ts = System.currentTimeMillis() / 1000;
        mPrefix = ts.toString();
        Log.d(TAG, "Initializing " + mPrefix);
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
