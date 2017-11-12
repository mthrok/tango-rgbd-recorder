package com.mthrok.tango.recorder.utility;

import java.util.LinkedList;
import java.util.List;


public class ExceptionQueue {
    private List<Exception> mExceptions = new LinkedList<>();

    protected synchronized void storeException(Exception exception) {
        mExceptions.add(exception);
    }

    public synchronized Exception[] flushExceptions() {
        Exception[] ret = mExceptions.toArray(new Exception[0]);
        mExceptions = new LinkedList<>();
        return ret;
    }
}
