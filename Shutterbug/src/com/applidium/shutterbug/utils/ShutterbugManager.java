package com.applidium.shutterbug.utils;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import android.R;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.widget.ImageView;

import com.applidium.shutterbug.cache.DiskLruCache.Snapshot;
import com.applidium.shutterbug.cache.ImageCache;
import com.applidium.shutterbug.cache.ImageCache.ImageCacheListener;
import com.applidium.shutterbug.downloader.ShutterbugDownloader;
import com.applidium.shutterbug.downloader.ShutterbugDownloader.ShutterbugDownloaderListener;
import com.applidium.shutterbug.utils.BitmapFactoryScale.InputStreamGenerator;

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
        download(url, listener, -1, -1);
    }

    public void download(String url, ShutterbugManagerListener listener, int desiredHeight, int desiredWidth) {
        if (url == null || listener == null || mFailedUrls.contains(url)) {
            return;
        }

        mCacheListeners.add(listener);
        mCacheUrls.add(url);
        ImageCache.getSharedImageCache(mContext).queryCache(getCacheKey(url), this, new DownloadRequest(url, listener, desiredHeight, desiredWidth));
    }

    public void download(String url, final ImageView imageView) {
        download(url, imageView, -1, -1);
    }

    public void download(String url, final ImageView imageView, int desiredHeight, int desiredWidth) {
        imageView.setImageDrawable(new ColorDrawable(mContext.getResources().getColor(R.color.transparent)));
        cancel(imageView);
        download(url, new ImageManagerListener(imageView), desiredHeight, desiredWidth);
    }

    public static String getCacheKey(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(url.getBytes("UTF-8"), 0, url.length());
            return String.format("%x", new BigInteger(md.digest()));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
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
    public void onImageDownloadSuccess(final ShutterbugDownloader downloader, final InputStream inputStream, final DownloadRequest downloadRequest) {
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
                bitmap = BitmapFactoryScale.decodeSampledBitmapFromStream(new InputStreamGenerator() {
                    @Override
                    public InputStream getStream() {
                        return sharedImageCache.queryDiskCache(cacheKey).getInputStream(0);
                    }
                }, mDownloadRequest);
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

    public void cancel(ImageView imageView) {
        Queue<ShutterbugManagerListener> queue = new LinkedList<ShutterbugManagerListener>();
        for (ShutterbugManagerListener listener : mCacheListeners) {
            if (listener instanceof ImageManagerListener && ((ImageManagerListener) listener).mImageView.equals(imageView)) {
                queue.add(listener);
            }
        }
        for (ShutterbugManagerListener listener : mDownloadImageListeners) {
            if (listener instanceof ImageManagerListener && ((ImageManagerListener) listener).mImageView.equals(imageView)) {
                queue.add(listener);
            }
        }
        for (ShutterbugManagerListener listener : queue) {
            cancel(listener);
        }
    }

    private static class ImageManagerListener implements ShutterbugManagerListener {
        private ImageView mImageView;

        public ImageManagerListener(ImageView imageView) {
            mImageView = imageView;
        }

        @Override
        public void onImageSuccess(ShutterbugManager imageManager, Bitmap bitmap, String url) {
            mImageView.setImageBitmap(bitmap);
        }

        @Override
        public void onImageFailure(ShutterbugManager imageManager, String url) {

        }
    }
}
