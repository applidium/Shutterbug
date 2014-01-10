# Shutterbug - Remote image loader with caching for Android

`Shutterbug` is an Android library that lets you fetch remote images and cache them. It is particularly suited for displaying remote images in lists, grids, and maps as it includes convenience subclasses of `ImageView` (`FetchableImageView`) and `OverlayItem` (`FetchableOverlayItem`) that make implementation a one-liner.

A dual memory and disk cache was implemented. It makes use of two backports of Android classes: [LruCache][] for the memory part and [DiskLruCache][] for the disk part. `LruCache` was introduced by API Level 12, but we provide it here as a standalone class so you can use the library under lower level APIs. Both `LruCache` and `DiskLruCache` are licensed under the Apache Software License, 2.0.

`Shutterbug` was inspired by [SDWebImage][] which does the same thing on iOS. It uses the same structure and interface. People who are familiar with `SDWebImage` on iOS will feel at home with `Shutterbug` on Android.

[SDWebImage]: https://github.com/rs/SDWebImage
[LruCache]: http://developer.android.com/reference/android/util/LruCache.html
[DiskLruCache]: https://github.com/JakeWharton/DiskLruCache
[Android Support Library]: http://developer.android.com/tools/extras/support-library.html

## How to use

First, ensure that the following permissions were added to your AndroidManifest.xml file:

    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
    
Then, you just have to add the jar or the library project to your project.

### Using convenience subclasses (FetchableImageView and FetchableOverlayItem)

1. Instantiate the subclass (either in your code or in an xml file, for example by replacing `ImageView` by `com.applidium.shutterbug.FetchableImageView`).
2. Fetch the image (`setImage(String url)` or `setImage(String url, Drawable placeholderDrawable)` if you need to add a placeholder while waiting for the image to be fetched)
3. That's it!

We also provide you with a listener interface (`FetchableImageViewListener` and `FetchableOverlayItemListener`) which will help you refresh your UI if need.

### Using ShutterbugManager

If you need to do more advanced coding, you can use `ShutterbugManager`. It is a singleton class whose instance is accessed by the static method `ShutterbugManager.getSharedManager(context)`. Downloading and caching is done by calling `download(String url, ShutterbugManagerListener listener)` on this instance.
