package com.mthrok.tango.recorder;

import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.google.tango.depthinterpolation.TangoDepthInterpolation;

import java.nio.ByteBuffer;

/**
 * Created by moto on 10/17/17.
 */

public class Utility {
    private static final String TAG = Utility.class.getSimpleName();

    private static final int[] DEPTH_COLORS = createColorPalette();
    private static final int DEPTH_COLOR_TOO_FAR = Color.WHITE;
    private static final int DEPTH_COLOR_TOO_CLOSE = Color.HSVToColor(new float[]{300, 1, 1}); // Purple

    private static int[] createColorPalette() {
        int palette_size = 360;
        float hue_begin = 0;
        float hue_end = 240;

        int[] palette = new int[palette_size];
        float[] hsv = new float[3];
        hsv[1] = hsv[2] = 1;
        for (int i = 0; i < palette_size; i++) {
            hsv[0] = hue_begin - (hue_begin - hue_end) * i / palette_size ;
            palette[i] = Color.HSVToColor(hsv);
        }
        return palette;
    }

    public static void convertYUVNV21ToRGB(ByteBuffer yuv, ByteBuffer tgt, int width, int height) {
        final int frameSize = width * height;

        tgt.rewind();
        for (int i = 0, ci = 0; i < height; ++i, ci += 1) {
            for (int j = 0, cj = 0; j < width; ++j, cj += 1) {
                int y = (0xff & ((int) yuv.get(ci * width + cj)));
                int v = (0xff & ((int) yuv.get(frameSize + (ci >> 1) * width + (cj & ~1) + 0)));
                int u = (0xff & ((int) yuv.get(frameSize + (ci >> 1) * width + (cj & ~1) + 1)));
                y = y < 16 ? 16 : y;

                int r = (int) (1.164f * (y - 16) + 1.596f * (v - 128));
                int g = (int) (1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                int b = (int) (1.164f * (y - 16) + 2.018f * (u - 128));

                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);

                tgt.put((byte)r);
                tgt.put((byte)g);
                tgt.put((byte)b);
                tgt.put((byte)255);
            }
        }
    }

    public static Bitmap createBitmapFromByteBuffer(ByteBuffer buffer, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        buffer.position(0);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    public static void convertTangoImageBufferToByteBuffer(TangoImageBuffer src, ByteBuffer tgt) {
        if (src.format != TangoImageBuffer.YCRCB_420_SP) {
            Log.e(TAG, "Unsupported TangoImageBuffer format: " + src.format);
        } else {
            convertYUVNV21ToRGB(src.data, tgt, src.width, src.height);
        }
    }

    public static void convertDepthBufferToByteBuffer(TangoDepthInterpolation.DepthBuffer src, ByteBuffer tgt) {
        float MAX_Z = 10;

        int width = src.width;
        int height = src.height;
        int nPixel = width * height;
        float z;
        int i_color, color;
        byte alpha;

        tgt.position(0);
        for (int i = 0; i < nPixel; ++i) {
            z = src.depths.get(i);
            i_color = (int) (z / MAX_Z * DEPTH_COLORS.length);
            if (i_color >= DEPTH_COLORS.length) {
                color = DEPTH_COLOR_TOO_FAR;
                alpha = (byte)32;
            } else if (i_color <= 0) {
                color = DEPTH_COLOR_TOO_CLOSE;
                alpha = (byte)1;
            } else {
                color = DEPTH_COLORS[i_color];
                alpha = (byte)32;
            }
            tgt.put((byte)Color.red(color));
            tgt.put((byte)Color.green(color));
            tgt.put((byte)Color.blue(color));
            tgt.put(alpha);
        }
    }

    public static void moveTangoImageBuffer(TangoImageBuffer src, TangoImageBuffer tgt) {
        tgt.width = src.width;
        tgt.height = src.height;
        tgt.stride = src.stride;
        tgt.timestamp = src.timestamp;
        tgt.frameNumber = src.frameNumber;
        tgt.exposureDurationNs = src.exposureDurationNs;
        tgt.format = src.format;
        ByteBuffer tmp = tgt.data;
        tgt.data = src.data;
        src.data = tmp;
    };
}
