package com.applidium.shutterbug.utils;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

import com.applidium.shutterbug.cache.DiskLruCache.Snapshot;
import com.applidium.shutterbug.cache.ImageCache;
import com.applidium.shutterbug.cache.ImageCache.ImageCacheListener;
import com.applidium.shutterbug.downloader.ShutterbugDownloader;
import com.applidium.shutterbug.downloader.ShutterbugDownloader.ShutterbugDownloaderListener;

public class ShutterbugManager implements ImageCacheListener, ShutterbugDownloaderListener {
    public interface ShutterbugManagerListener {
        void onImageSuccess(ShutterbugManager imageManager, Bitmap bitmap, String url);

        void onImageFailure(ShutterbugManager imageManager, String url);
    }

    private static ShutterbugManager          sImageManager;

    private Context                           mContext;
    private List<String>                      mFailedUrls             = new ArrayList<String>();
    private List<ShutterbugManagerListener>   mCacheListeners         = new ArrayList<ShutterbugManagerListener>();
    private List<String>                      mCacheUrls              = new ArrayList<String>();
    private Map<String, ShutterbugDownloader> mDownloadersMap         = new HashMap<String, ShutterbugDownloader>();
    private List<DownloadRequest>             mDownloadRequests       = new ArrayList<DownloadRequest>();
    private List<ShutterbugManagerListener>   mDownloadImageListeners = new ArrayList<ShutterbugManagerListener>();
    private List<ShutterbugDownloader>        mDownloaders            = new ArrayList<ShutterbugDownloader>();

    final static private int                  LISTENER_NOT_FOUND      = -1;

    public ShutterbugManager(Context context) {
        mContext = context;
    }

    public static ShutterbugManager getSharedImageManager(Context context) {
        if (sImageManager == null) {
            sImageManager = new ShutterbugManager(context);
        }
        return sImageManager;
    }

    public void download(String url, ShutterbugManagerListener listener) {
        if (url == null || listener == null || mFailedUrls.contains(url)) {
            return;
        }

        mCacheListeners.add(listener);
        mCacheUrls.add(url);
        ImageCache.getSharedImageCache(mContext).queryCache(getCacheKey(url), this, new DownloadRequest(url, listener));
    }

    private String getCacheKey(String url) {
        try {
            return URLEncoder.encode(url, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    private int getListenerIndex(ShutterbugManagerListener listener, String url) {
        for (int index = 0; index < mCacheListeners.size(); index++) {
            if (mCacheListeners.get(index) == listener && mCacheUrls.get(index).equals(url)) {
                return index;
            }
        }
        return LISTENER_NOT_FOUND;
    }

    @Override
    public void onImageFound(ImageCache imageCache, Bitmap bitmap, String key, DownloadRequest downloadRequest) {
        final String url = downloadRequest.getUrl();
        final ShutterbugManagerListener listener = downloadRequest.getListener();

        int idx = getListenerIndex(listener, url);
        if (idx == LISTENER_NOT_FOUND) {
            // Request has since been canceled
            return;
        }

        listener.onImageSuccess(this, bitmap, url);
        mCacheListeners.remove(idx);
        mCacheUrls.remove(idx);
    }

    @Override
    public void onImageNotFound(ImageCache imageCache, String key, DownloadRequest downloadRequest) {
        final String url = downloadRequest.getUrl();
        final ShutterbugManagerListener listener = downloadRequest.getListener();

        int idx = getListenerIndex(listener, url);
        if (idx == LISTENER_NOT_FOUND) {
            // Request has since been canceled
            return;
        }
        mCacheListeners.remove(idx);
        mCacheUrls.remove(idx);

        // Share the same downloader for identical URLs so we don't download the
        // same URL several times
        ShutterbugDownloader downloader = mDownloadersMap.get(url);
        if (downloader == null) {
            downloader = new ShutterbugDownloader(url, this, downloadRequest);
            downloader.start();
            mDownloadersMap.put(url, downloader);
        }
        mDownloadRequests.add(downloadRequest);
        mDownloadImageListeners.add(listener);
        mDownloaders.add(downloader);
    }

    @Override
    public void onImageDownloadSuccess(final ShutterbugDownloader downloader, final InputStream inputStream,
            final DownloadRequest downloadRequest) {
        new InputStreamHandlingTask(downloader, downloadRequest).execute(inputStream);
    }

    @Override
    public void onImageDownloadFailure(ShutterbugDownloader downloader, DownloadRequest downloadRequest) {
        for (int idx = mDownloaders.size() - 1; idx >= 0; idx--) {
            final int uidx = idx;
            ShutterbugDownloader aDownloader = mDownloaders.get(uidx);
            if (aDownloader == downloader) {
                ShutterbugManagerListener listener = mDownloadImageListeners.get(uidx);
                listener.onImageFailure(this, downloadRequest.getUrl());
                mDownloaders.remove(uidx);
                mDownloadImageListeners.remove(uidx);
            }
        }
        mDownloadersMap.remove(downloadRequest.getUrl());

    }

    private class InputStreamHandlingTask extends AsyncTask<InputStream, Void, Bitmap> {
        ShutterbugDownloader mDownloader;
        DownloadRequest      mDownloadRequest;

        InputStreamHandlingTask(ShutterbugDownloader downloader, DownloadRequest downloadRequest) {
            mDownloader = downloader;
            mDownloadRequest = downloadRequest;
        }

        @Override
        protected Bitmap doInBackground(InputStream... params) {
            final ImageCache sharedImageCache = ImageCache.getSharedImageCache(mContext);
            final String cacheKey = getCacheKey(mDownloadRequest.getUrl());
            // Store the image in the cache
            Snapshot cachedSnapshot = sharedImageCache.storeToDisk(params[0], cacheKey);
            Bitmap bitmap = null;
            if (cachedSnapshot != null) {
                try {
                    bitmap = BitmapFactory.decodeStream(cachedSnapshot.getInputStream(0));
                } catch (OutOfMemoryError e) {
                    e.printStackTrace();
                }
                if (bitmap != null) {
                    sharedImageCache.storeToMemory(bitmap, cacheKey);
                }
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            // Notify all the downloadListener with this downloader
            for (int idx = mDownloaders.size() - 1; idx >= 0; idx--) {
                final int uidx = idx;
                ShutterbugDownloader aDownloader = mDownloaders.get(uidx);
                if (aDownloader == mDownloader) {
                    ShutterbugManagerListener listener = mDownloadImageListeners.get(uidx);
                    if (bitmap != null) {
                        listener.onImageSuccess(ShutterbugManager.this, bitmap, mDownloadRequest.getUrl());
                    } else {
                        listener.onImageFailure(ShutterbugManager.this, mDownloadRequest.getUrl());
                    }
                    mDownloaders.remove(uidx);
                    mDownloadImageListeners.remove(uidx);
                }
            }
            if (bitmap != null) {
            } else { // TODO add retry option
                mFailedUrls.add(mDownloadRequest.getUrl());
            }
            mDownloadersMap.remove(mDownloadRequest.getUrl());
        }

    }

    public void cancel(ShutterbugManagerListener listener) {
        int idx;
        while ((idx = mCacheListeners.indexOf(listener)) != -1) {
            mCacheListeners.remove(idx);
            mCacheUrls.remove(idx);
        }

        while ((idx = mDownloadImageListeners.indexOf(listener)) != -1) {
            ShutterbugDownloader downloader = mDownloaders.get(idx);

            mDownloadRequests.remove(idx);
            mDownloadImageListeners.remove(idx);
            mDownloaders.remove(idx);

            if (!mDownloaders.contains(downloader)) {
                // No more listeners are waiting for this download, cancel it
                downloader.cancel();
                mDownloadersMap.remove(downloader.getUrl());
            }
        }

    }
}
