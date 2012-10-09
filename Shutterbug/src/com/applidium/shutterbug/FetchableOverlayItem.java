package com.applidium.shutterbug;

import android.R;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import com.applidium.shutterbug.utils.ShutterbugManager;
import com.applidium.shutterbug.utils.ShutterbugManager.ShutterbugManagerListener;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.OverlayItem;

public class FetchableOverlayItem extends OverlayItem implements ShutterbugManagerListener {
    public interface FetchableOverlayItemListener {
        // You may want to invalidate the MapView instance here
        void onImageFetched(Bitmap bitmap, String url);

        void onImageFailure(String url);
    }

    private FetchableOverlayItemListener mListener;
    private Context                      mContext;

    public FetchableOverlayItem(Context context, GeoPoint point, String title, String snippet) {
        super(point, title, snippet);
        mContext = context;
    }

    public void setImage(String url) {
        setImage(url, new ColorDrawable(mContext.getResources().getColor(R.color.transparent)));
    }

    public void setImage(String url, int placeholderDrawableId) {
        setImage(url, mContext.getResources().getDrawable(placeholderDrawableId));
    }

    public void setImage(String url, Drawable placeholderDrawable) {
        final ShutterbugManager manager = ShutterbugManager.getSharedImageManager(mContext);
        manager.cancel(this);
        setDrawable(placeholderDrawable);
        if (url != null) {
            manager.download(url, this);
        }
    }

    public FetchableOverlayItemListener getListener() {
        return mListener;
    }

    public void setListener(FetchableOverlayItemListener listener) {
        mListener = listener;
    }

    @Override
    public void onImageSuccess(ShutterbugManager imageManager, Bitmap bitmap, String url) {
        setDrawable(new BitmapDrawable(mContext.getResources(), bitmap));
        if (mListener != null) {
            mListener.onImageFetched(bitmap, url);
        }
    }

    @Override
    public void onImageFailure(ShutterbugManager imageManager, String url) {
        if (mListener != null) {
            mListener.onImageFailure(url);
        }
    }

    private void setDrawable(Drawable drawable) {
        drawable.setBounds(drawable.getIntrinsicWidth() / -4, drawable.getIntrinsicHeight() / -4, drawable.getIntrinsicWidth() / 4,
                drawable.getIntrinsicHeight() / 4);
        setMarker(drawable);
    }
}
