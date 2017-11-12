package com.mthrok.tango.recorder;

import java.nio.ByteBuffer;
import android.graphics.Bitmap;


public class Utility {
    private static final String TAG = Utility.class.getSimpleName();

    public static Bitmap createBitmapFromByteBuffer(ByteBuffer buffer, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }
}