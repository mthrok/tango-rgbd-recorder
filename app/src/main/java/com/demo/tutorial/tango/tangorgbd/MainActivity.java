package com.demo.tutorial.tango.tangorgbd;

import android.content.Context;
import android.graphics.Point;
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
import com.google.tango.depthinterpolation.TangoDepthInterpolation;
import com.google.tango.support.TangoSupport;

import java.util.ArrayList;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private TangoWrapper mTangoWrapper;
    private TangoDataManager mTangoDataManager;

    private DataProcessor mDataProcessor;
    private Thread mMainThread;

    private ImageView mImageView;
    private Button mRecordButton;

    private class TangoWrapper {
        private final String TAG = TangoWrapper.class.getSimpleName();

        private final Tango mTango;
        private final TangoDataManager mTangoDataManager;
        private Boolean mIsStarted;

        class TangoUpdateCallback extends Tango.TangoUpdateCallback {
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // Log.d(TAG, "POSE" + pose.toString());
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
                Log.d(TAG, "TANGO EVENT." + event.toString());
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
            private final String TAG = TangoWrapper.OnTangoInitializedCallback.class.getSimpleName();
            private final Context mContext;

            private OnTangoInitializedCallback(Context context) {
                mContext = context;
            }

            @Override
            public void run() {
                synchronized (MainActivity.this) {
                    try {
                        setupTango();
                        startTango();
                        initTangoSupport();
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

        private TangoWrapper(Context context, TangoDataManager tangoDataProcessor) {
            mTango = new Tango(context, new OnTangoInitializedCallback(context));
            mTangoDataManager = tangoDataProcessor;
        }

        private void stop() {
            mTango.disconnect();
            mIsStarted = false;
        }
    }

    class DataProcessor {
        private TangoDataManager mProcessor;

        private TangoPoseData mColorCameraTPointCloud;
        private ByteBuffer mDepthImageTmpBuffer;
        private ByteBuffer mColorImageTmpBuffer;

        private Thread mMainThread;
        private Boolean mRunning = false;
        private Boolean mIsRecording = false;

        private TangoDataRecorder mRecorder;

        class MainProcess implements Runnable {
            private TangoDepthInterpolation.DepthBuffer generateDepthImage(TangoPointCloudData pointCloud, int width, int height) {
                if (mColorCameraTPointCloud == null || mColorCameraTPointCloud.statusCode != TangoPoseData.POSE_VALID) {
                    mColorCameraTPointCloud = TangoSupport.calculateRelativePose(
                            pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                            pointCloud.timestamp, TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH
                    );
                }
                return TangoDepthInterpolation.upsampleImageNearestNeighbor(
                        pointCloud, width, height, mColorCameraTPointCloud);
            }

            private void fillDepthImageBuffer(TangoDepthInterpolation.DepthBuffer depthBuffer, int width, int height) {
                int capacity = 4 * width * height;
                if (mDepthImageTmpBuffer == null || mDepthImageTmpBuffer.capacity() < capacity) {
                    Log.d(TAG, "Allocating " + capacity + " bytes");
                    mDepthImageTmpBuffer = ByteBuffer.allocateDirect(capacity);
                }
                Utility.convertDepthBufferToByteBuffer(depthBuffer, mDepthImageTmpBuffer);
            }

            private void fillColorImageBuffer(TangoImageBuffer imageBuffer, int width, int height) {
                int capacity = 4 * width * height;
                if (mColorImageTmpBuffer == null || mColorImageTmpBuffer.capacity() < capacity) {
                    Log.d(TAG, "Allocating " + capacity + " bytes");
                    mColorImageTmpBuffer = ByteBuffer.allocateDirect(capacity);
                }
                Utility.convertTangoImageBufferToByteBuffer(imageBuffer, mColorImageTmpBuffer);
            }

            private void runOnce() {
                TangoPointCloudData pointCloud = mProcessor.getLatestPointCloud();

                if (pointCloud == null) {
                    return;
                }

                TangoPoseData poseData = mProcessor.getPoseData(pointCloud.timestamp);
                Log.d(TAG, poseData.toString());

                TangoImageBuffer colorImageBuffer = mProcessor.getColorImage(pointCloud.timestamp);

                if (colorImageBuffer == null) {
                    return;
                }

                if (pointCloud.numPoints > 0 && colorImageBuffer.width > 0 && colorImageBuffer.height > 0) {
                    TangoDepthInterpolation.DepthBuffer depthImageBuffer = generateDepthImage(
                            pointCloud, colorImageBuffer.width, colorImageBuffer.height);

                    fillDepthImageBuffer(
                            depthImageBuffer, colorImageBuffer.width, colorImageBuffer.height);
                    fillColorImageBuffer(
                            colorImageBuffer, colorImageBuffer.width, colorImageBuffer.height);


                    if (mIsRecording) {
                        Log.d(TAG, "Recording...");
                        mRecorder.saveDepthImage();
                        mRecorder.saveColorImage();
                        mRecorder.savePoseData();
                    }

                    final Bitmap depthBitmap = Utility.createBitmapFromByteBuffer(
                            mDepthImageTmpBuffer, colorImageBuffer.width, colorImageBuffer.height);
                    final Bitmap colorBitmap = Utility.createBitmapFromByteBuffer(
                            mColorImageTmpBuffer, colorImageBuffer.width, colorImageBuffer.height);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updatePreview(colorBitmap, depthBitmap);
                        }
                    });
                }
            }

            @Override
            public void run() {
                while(mRunning) {
                    runOnce();
                }
            }
        }

        public DataProcessor(TangoDataManager processor) {
            mProcessor = processor;
            mRunning = false;
            mIsRecording = false;
        }

        public void start() {
            mRunning = true;
            mMainThread = new Thread(new MainProcess());
            mMainThread.start();
        }

        public void stop() {
            Log.d(TAG, "Stopping main process.");
            mRunning = false;
            try {
                mMainThread.join();
                Log.d(TAG, "Stopped main process.");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void toggleRecordingState() {
            mIsRecording = !mIsRecording;

            if (mIsRecording) {
                mRecorder = new TangoDataRecorder();
            }
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

        mTangoDataManager = new TangoDataManager();
        mTangoWrapper = new TangoWrapper(MainActivity.this, mTangoDataManager);
        mDataProcessor = new DataProcessor(mTangoDataManager);
        mDataProcessor.start();

        mRecordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mDataProcessor.toggleRecordingState();
            }
        });
    }

    private void updatePreview(Bitmap colorBitmap, Bitmap depthBitmap) {
        Drawable[] layers = new Drawable[2];
        layers[0] = new BitmapDrawable(getResources(), colorBitmap);
        layers[1] = new BitmapDrawable(getResources(), depthBitmap);
        mImageView.setImageDrawable(new LayerDrawable(layers));

        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        switch(display.getRotation()) {
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

    @Override
    protected void onStop() {
        super.onStop();
        synchronized (MainActivity.this) {
            try {
                mDataProcessor.stop();
                mTangoWrapper.stop();
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }

    }
}
