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

import com.mthrok.tango.recorder.tango.TangoDataProcessor;
import com.mthrok.tango.recorder.tango.TangoDataStore;
import com.mthrok.tango.recorder.tango.TangoInterface;
import com.mthrok.tango.recorder.utility.ExceptionQueue;
import com.mthrok.tango.recorder.utility.FixedDelayExecutor;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private TangoInterface mTangoInterface;
    private TangoDataStore mTangoDataStore;
    private TangoDataProcessor mTangoDataProcessor;

    private ImageView mImageView;
    private Button mRecordButton;

    private FixedDelayExecutor mPreviewUpdater;

    class PreviewUpdateJob extends ExceptionQueue implements Runnable {
        private final String TAG = PreviewUpdateJob.class.getSimpleName();

        Bitmap[] mImageBitmaps = null;

        private void checkErrors() {
            Exception[] exceptions = flushExceptions();
            if (exceptions.length > 0) {
                showToastAndExit(
                    getString(R.string.preview_updater_error)
                );
            }

            exceptions = mTangoInterface.flushExceptions();
            if (exceptions.length > 0) {
                showToastAndExit(
                    getString(R.string.tango_interface_error)
                );
            }

            exceptions = mTangoDataProcessor.flushExceptions();
            if (exceptions.length > 0) {
                showToastAndExit(
                    getString(R.string.tango_data_processor_error)
                );
            }
        }

        private void showToastAndExit(final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        }

        private void updatePreview() {
            mTangoDataProcessor.getBitmaps(mImageBitmaps);
            final Drawable[] layers = new Drawable[2];
            layers[0] = new BitmapDrawable(getResources(), mImageBitmaps[0]);
            layers[1] = new BitmapDrawable(getResources(), mImageBitmaps[1]);
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

        private void allocateImageBitmap() {
            int[] size = mTangoDataProcessor.getImageSize();
            Bitmap[] bitmaps = {
                Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888),
                Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888)
            };
            mImageBitmaps = bitmaps;
        }

        @Override
        public void run() {
            checkErrors();
            Log.d(TAG, "Running PreviewUpdate");
            if (mTangoDataProcessor.isBufferReady()) {
                if (mImageBitmaps == null) {
                    allocateImageBitmap();
                }
                updatePreview();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageView = findViewById(R.id.preview_image);
        mRecordButton = findViewById(R.id.record_button);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mTangoDataStore = new TangoDataStore();
        mTangoInterface = new TangoInterface(this, mTangoDataStore);
        mTangoDataProcessor = new TangoDataProcessor(mTangoDataStore, getString(R.string.app_name));
        mPreviewUpdater = new FixedDelayExecutor(new PreviewUpdateJob(), 500);

        mRecordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mTangoDataProcessor.toggleRecordingState();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPreviewUpdater.stop();
        mTangoDataProcessor.stop();
        mTangoInterface.stop();
    }
}
