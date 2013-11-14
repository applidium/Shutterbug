package com.applidium.shutterbug.utils;

import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class BitmapFactoryScale {
    public static Bitmap decodeSampledBitmapFromStream(InputStream sizeStream, InputStream imageStream, DownloadRequest request) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(sizeStream, null, options);

            options.inSampleSize = request.getSampleSize(options);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeStream(imageStream, null, options);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }
}
