package com.mthrok.tango.recorder;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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

    class PreviewUpdater {
        private final ScheduledExecutorService mScheduler =
                Executors.newScheduledThreadPool(1);
        private ScheduledFuture<?> mJobHandle;
        class PreviewUpdateJob implements Runnable {

            private void checkError() {
                Integer resId = mTangoInterface.checkError();
                if (resId != null) {
                    showToastAndFinishOnUiThread(resId);
                }

            }

            private void showToastAndFinishOnUiThread(final int resId) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, getString(resId), Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }

            private void updatePreview() {
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

            @Override
            public void run() {
                checkError();

                if (mTangoInterface.isRunning()) {
                    updatePreview();
                }
            }
        }

        public void start() {
            mJobHandle = mScheduler.scheduleAtFixedRate(
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
