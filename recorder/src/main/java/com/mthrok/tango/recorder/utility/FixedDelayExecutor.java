package com.mthrok.tango.recorder.utility;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class FixedDelayExecutor {

    private final ScheduledExecutorService mScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> mJobHandle;

    public FixedDelayExecutor(Runnable job, long interval) {
        mJobHandle = mScheduler.scheduleAtFixedRate(job, 0, interval, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        mJobHandle.cancel(false);
    }

    public void join() {
        while (!mJobHandle.isDone()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
