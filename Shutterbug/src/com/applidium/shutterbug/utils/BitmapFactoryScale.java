package com.applidium.shutterbug.utils;

import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class BitmapFactoryScale {
    public interface InputStreamGenerator {
        public InputStream getStream();
    }

    public static Bitmap decodeSampledBitmapFromStream(InputStreamGenerator generator, DownloadRequest request) {
        if (generator == null || request == null) {
            return null;
        }
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(generator.getStream(), null, options);

            options.inSampleSize = request.getSampleSize(options);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeStream(generator.getStream(), null, options);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }
}
