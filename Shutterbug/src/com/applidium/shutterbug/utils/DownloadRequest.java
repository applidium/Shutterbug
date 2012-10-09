package com.applidium.shutterbug.utils;

import com.applidium.shutterbug.utils.ShutterbugManager.ShutterbugManagerListener;

public class DownloadRequest {
    private String                    mUrl;
    private ShutterbugManagerListener mListener;

    public DownloadRequest(String url, ShutterbugManagerListener listener) {
        mUrl = url;
        mListener = listener;
    }

    public String getUrl() {
        return mUrl;
    }

    public ShutterbugManagerListener getListener() {
        return mListener;
    }
}
