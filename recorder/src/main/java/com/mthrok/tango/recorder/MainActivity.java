package com.mthrok.tango.recorder;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.Button;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.google.tango.support.TangoSupport;
import com.projecttango.tangosupport.ux.TangoUx;
import com.projecttango.tangosupport.ux.UxExceptionEventListener;
import com.projecttango.tangosupport.ux.UxExceptionEvent;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private TangoInterface mTangoInterface;

    private ImageView mImageView;
    private Button mRecordButton;

    private PreviewUpdater mPreviewUpdater;

    private class TangoInterface {
        private final String TAG = TangoInterface.class.getSimpleName();

        private final Tango mTango;
        private final TangoUx mTangoUx;
        private final TangoDataManager mTangoDataManager;
        private final TangoDataProcessor mTangoDataProcessor;

        private Boolean mIsStarted;

        class TangoUpdateCallback extends Tango.TangoUpdateCallback {
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                mTangoDataManager.updatePoseData(pose);
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                // Log.d(TAG, "POINT CLOUD AVAILABLE.");
                mTangoDataManager.updatePointCloud(pointCloud);
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    Log.d(TAG, "COLOR CAMERA FRAME AVAILABLE.");
                } else {
                    Log.d(TAG, "FISH-EYE CAMERA FRAME AVAILABLE.");
                }
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                mTangoUx.equals(event);
                Log.d(TAG, "TANGO EVENT: " + event.toString());
            }
        }

        class ColorCameraListener implements Tango.OnFrameAvailableListener {
            @Override
            public void onFrameAvailable(TangoImageBuffer buffer, int cameraId) {
                // Log.d(TAG, "COLOR CAMERA FRAME AVAILABLE.");
                mTangoDataManager.updateColorCameraFrame(buffer);
            }
        }

        class OnTangoInitializedCallback implements Runnable {
            private final String TAG = TangoInterface.OnTangoInitializedCallback.class.getSimpleName();
            private final Context mContext;

            private OnTangoInitializedCallback(Context context) {
                mContext = context;
            }

            @Override
            public void run() {
                synchronized (MainActivity.TangoInterface.this) {
                    try {
                        mTangoUx.start();
                        setupTango();
                        startTango();
                        initTangoSupport();
                        mTangoDataProcessor.start();
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, mContext.getString(R.string.exception_out_of_date), e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, mContext.getString(R.string.exception_tango_error), e);
                        showToastAndFinishOnUiThread(R.string.exception_tango_error);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, mContext.getString(R.string.exception_tango_invalid), e);
                        showToastAndFinishOnUiThread(R.string.exception_tango_invalid);
                    }
                }
            }

            private void setupTango() {
                TangoConfig config = mTango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
                config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
                config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
                config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
                mTango.connect(config);
            }

            private void startTango() {
                ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<>();
                // Required to enable pose data callback
                framePairs.add(new TangoCoordinateFramePair(
                        TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                        TangoPoseData.COORDINATE_FRAME_DEVICE
                ));

                mTango.connectListener(
                        framePairs, new TangoUpdateCallback());
                mTango.experimentalConnectOnFrameListener(
                        TangoCameraIntrinsics.TANGO_CAMERA_COLOR, new ColorCameraListener());
                mIsStarted = true;
            }

            private void initTangoSupport() {
                TangoSupport.initialize(mTango);
            }

            private void showToastAndFinishOnUiThread(final int resId) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(
                                MainActivity.this, getString(resId), Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        }

        class TangoUxExceptionEventListener implements UxExceptionEventListener {
            @Override
            public void onUxExceptionEvent(UxExceptionEvent event) {
                String status = (event.getStatus() == UxExceptionEvent.STATUS_DETECTED) ? "[DETECTED]" : "[RESOLVED]";
                int eventType = event.getType();
                if (eventType == UxExceptionEvent.TYPE_LYING_ON_SURFACE) {
                    Log.i(TAG, status + " Device lying on surface");
                }
                if (eventType == UxExceptionEvent.TYPE_FEW_DEPTH_POINTS) {
                    Log.i(TAG, status + " Too few depth points");
                }
                if (eventType == UxExceptionEvent.TYPE_FEW_FEATURES) {
                    Log.i(TAG, status + " Too few features");
                }
                if (eventType == UxExceptionEvent.TYPE_MOTION_TRACK_INVALID) {
                    Log.i(TAG, status + " Invalid poses in MotionTracking");
                }
                if (eventType == UxExceptionEvent.TYPE_MOVING_TOO_FAST) {
                    Log.i(TAG, status + " Moving too fast");
                }
                if (eventType == UxExceptionEvent.TYPE_FISHEYE_CAMERA_OVER_EXPOSED) {
                    Log.i(TAG, status + " Fisheye Camera Over Exposed");
                }
                if (eventType == UxExceptionEvent.TYPE_FISHEYE_CAMERA_UNDER_EXPOSED) {
                    Log.i(TAG, status + " Fisheye Camera Under Exposed");
                }
            }
        }
        
        private TangoInterface(Context context) {
            mTangoUx = new TangoUx(context);
            mTangoUx.setUxExceptionEventListener(new TangoUxExceptionEventListener());
            mTango = new Tango(context, new OnTangoInitializedCallback(context));
            mTangoDataManager = new TangoDataManager();
            mTangoDataProcessor = new TangoDataProcessor(mTangoDataManager);
        }

        private void stop() {
            synchronized (MainActivity.TangoInterface.this) {
                mIsStarted = false;
                mTangoDataProcessor.stop();
                mTango.disconnect();
                mTangoUx.stop();
            }
        }

        // TODO: Think better approach
        public void toggleRecordingState() {
            mTangoDataProcessor.toggleRecordingState();
        }

        // TODO: Think better approach
        public Bitmap[] getBitmaps() {
            return mTangoDataProcessor.getBitmaps();
        }

    }

    class PreviewUpdater {
        private final ScheduledExecutorService mScheduler =
                Executors.newScheduledThreadPool(1);
        private ScheduledFuture<?> mJobHandle;
        class PreviewUpdateJob implements Runnable {
            @Override
            public void run() {
                Bitmap[] bitmaps = mTangoInterface.getBitmaps();
                if (bitmaps == null) {
                    return;
                }
                final Drawable[] layers = new Drawable[2];
                layers[0] = new BitmapDrawable(getResources(), bitmaps[0]);
                layers[1] = new BitmapDrawable(getResources(), bitmaps[1]);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mImageView.setImageDrawable(new LayerDrawable(layers));
                    }
                });
                Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                switch (display.getRotation()) {
                    case 0: // Portrait
                        mImageView.setRotation(90);
                        break;
                    case 1: // Landscape
                        mImageView.setRotation(0);
                        break;
                    case 2: // Upside down
                        mImageView.setRotation(0);
                        break;
                    case 3: // Landscape right
                        mImageView.setRotation(180);
                        break;
                }
            }
        }

        public void start() {
            mJobHandle =
                    mScheduler.scheduleAtFixedRate(
                            new PreviewUpdateJob(), 0, 100, TimeUnit.MILLISECONDS);
        }

        public void stop() {
            mJobHandle.cancel(false);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageView = (ImageView) findViewById(R.id.preview_image);
        mRecordButton = (Button) findViewById(R.id.record_button);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mTangoInterface = new TangoInterface(MainActivity.this);

        mRecordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mTangoInterface.toggleRecordingState();
            }
        });

        mPreviewUpdater = new PreviewUpdater();
        mPreviewUpdater.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPreviewUpdater.stop();
        mTangoInterface.stop();
    }
}
