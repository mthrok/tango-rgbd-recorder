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
import com.google.atap.tangoservice.experimental.TangoImageBuffer;
import com.google.atap.tangoservice.TangoPointCloudData;

import com.mthrok.tango.recorder.utility.ExceptionQueue;


public class TangoDataRecorder extends ExceptionQueue {
    private static final String TAG = TangoDataRecorder.class.getSimpleName();

    private final String mAppName;
    private final TangoDataStore mTangoDataStore;

    private Thread mRecordingProcess;

    private Boolean mIsRecording = false;

    FileOutputStream mPoseStream;
    FileOutputStream mColorImageStream;
    FileOutputStream mPointCloudStream;

    class Recorder implements Runnable {
        private final String TAG = Recorder.class.getSimpleName();

        @Override
        public void run() {
            mIsRecording = true;
            initFileStreams();
            try {
                while (mIsRecording) {
                    recordData();
                }
            } catch (Exception e) {
                Log.d(TAG, e.getMessage(), e);
                storeException(e);
            } finally {
                closeFileStreams();
            }
        }

        private synchronized void initFileStreams() {
            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
            String timestamp = df.format(new Date(System.currentTimeMillis()));

            String prefix = mAppName + "/" + timestamp;

            File baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), prefix);
            Log.d(TAG, "Initializing output directory: " + baseDir.getPath());

            baseDir.mkdirs();


            File poseFile = new File(baseDir, "pose.bin");
            File colorFile = new File(baseDir, "color.bin");
            File pointCloudFile = new File(baseDir, "point_cloud.bin");
            Log.d(TAG, "Initializing FileOutputStreams");
            try {
                mPoseStream = new FileOutputStream(poseFile, true);
                mColorImageStream = new FileOutputStream(colorFile, true);
                mPointCloudStream = new FileOutputStream(pointCloudFile, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private synchronized void closeFileStream(FileOutputStream stream, String message) throws IOException {
            if (stream != null) {
                Log.d(TAG, message);
                stream.close();
            }
        }

        public void closeFileStreams() {
            FileOutputStream[] streams = {mPoseStream, mColorImageStream, mPointCloudStream};
            String[] messages = {
                    "Closing Pose FileOutputStream",
                    "Closing Color FileOutputStream",
                    "Closing PointCloud FileOutputStream",
            };
            for (int i = 0; i < streams.length; ++i) {
                try {
                    closeFileStream(streams[i], messages[i]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void recordData() {
            TangoPointCloudManager.PointCloudAndBuffer pointCloudAndBuffer = mTangoDataStore.getPointCloudAndBuffer(Double.MAX_VALUE);
            TangoPointCloudData pointCloud = pointCloudAndBuffer.pointCloud;
            if (pointCloud == null || pointCloud.points == null) {
                return;
            }
            TangoPoseData pose = mTangoDataStore.getPoseData(pointCloud.timestamp);
            TangoImageBuffer colorImage = mTangoDataStore.getColorImage(pointCloud.timestamp);
            savePointCloud(pointCloudAndBuffer);
            saveColorImage(colorImage.data);
            savePoseData(pose);
        }

        private synchronized void saveBuffer(ByteBuffer buffer, FileOutputStream stream, String message) {
            Log.d(TAG, message);
            buffer.position(0);
            try {
                stream.getChannel().write(buffer);
                Log.d(TAG, "Wrote " + buffer.position() + " bytes.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /*
        public void saveDepthImage(DepthBuffer imageBuffer) {
            ByteBuffer buffer = ByteBuffer.allocate(imageBuffer.depths.capacity() * 4);
            imageBuffer.depths.position(0);
            for(int i = 0; i < imageBuffer.depths.capacity(); ++ i) {
                buffer.putFloat(imageBuffer.depths.get());
            }
            saveBuffer(buffer, mDepthStream, "Saving Depth image");
        }
        */

        public void saveColorImage(ByteBuffer imageBuffer) {
            saveBuffer(imageBuffer, mColorImageStream, "Saving Color image");
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

        public void savePointCloud(TangoPointCloudManager.PointCloudAndBuffer pointCloudAndBuffer) {
            // 1 double 5 int X float -> 1 * 8 + 5 * 4 = 28
            ByteBuffer buffer = ByteBuffer.allocate(28);

            buffer.putDouble(pointCloudAndBuffer.pointCloud.timestamp);
            buffer.putInt(pointCloudAndBuffer.pointCloud.numPoints);
            buffer.putInt(pointCloudAndBuffer.pointCloud.pointCloudParcelFileDescriptorSize);
            buffer.putInt(pointCloudAndBuffer.pointCloud.pointCloudParcelFileDescriptorFlags);
            buffer.putInt(pointCloudAndBuffer.pointCloud.pointCloudParcelFileDescriptorOffset);
            buffer.putInt(pointCloudAndBuffer.pointCloud.pointCloudNativeFileDescriptor);

            saveBuffer(buffer, mPointCloudStream, "Saving Point Cloud data header");
            saveBuffer(pointCloudAndBuffer.buffer, mPointCloudStream, "Saving Point Cloud data");
        }

    };

    public TangoDataRecorder(TangoDataStore store, String appName) {
        mTangoDataStore = store;
        mAppName = appName;
    }

    public void start() {
        mRecordingProcess = new Thread(new Recorder());
        mRecordingProcess.start();
    }

    public void stop() {
        mIsRecording = false;
        try {
            mRecordingProcess.join();
        } catch (InterruptedException e) {
            Log.d(TAG, e.getLocalizedMessage(), e);
        } finally {
            mRecordingProcess = null;
        }
    }
}
