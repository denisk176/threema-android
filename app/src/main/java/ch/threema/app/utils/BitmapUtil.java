/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.NetworkOnMainThreadException;
import android.provider.MediaStore;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.View;

import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.webclient.utils.ThumbnailUtils;
import ch.threema.base.utils.LoggingUtil;

public class BitmapUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("BitmapUtil");

    private static final int DEFAULT_JPG_QUALITY = 80;
    private static final int DEFAULT_PNG_QUALITY = 100; // PNG is lossless anyway

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({FLIP_NONE, FLIP_HORIZONTAL, FLIP_VERTICAL})
    public @interface FlipType {
    }

    public static final int FLIP_NONE = 0;
    public static final int FLIP_HORIZONTAL = 1;
    public static final int FLIP_VERTICAL = 1 << 1;

    /**
     * Get the inSampleSize that produces an image that has its width *smaller* or equal to maxWidth
     *
     * @param imageStream Input stream of image data
     * @param maxWidth    maxWidth that results from applying the inSampleSize
     * @return calculated inSampleSize as a power of two
     */
    private static int getInSampleSizeByWidth(@NonNull InputStream imageStream, int maxWidth) {
        // check dimensions of input bitmap
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(imageStream, null, o);

        // no scaling necessary if width of bitmap is smaller or equal to maxWidth
        if (o.outWidth > maxWidth) {
            float scalingFactor = (float) o.outWidth / (float) maxWidth;

            // round to the next higher power of two
            return MathUtils.getNextHigherPowerOfTwo((int) Math.ceil(scalingFactor));
        }
        return 1;
    }

    /**
     * Calculate the largest inSampleSize value that is a power of 2 and keeps both
     * height and width *larger* than the requested height and width.
     *
     * @param width     width of source bitmap
     * @param height    height of source bitmap
     * @param reqWidth  requested width
     * @param reqHeight requested height
     * @return SampleResult
     */
    static SampleResult getSampleSize(final int width, final int height, int reqWidth, int reqHeight) {
        SampleResult result = new SampleResult();
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > reqHeight
                && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        result.newWidth = reqWidth;
        result.newHeight = reqHeight;
        result.inSampleSize = inSampleSize;

        return result;
    }

    static public byte[] bitmapToPngByteArray(@NonNull Bitmap bitmap) {
        return bitmapToByteArray(bitmap, Bitmap.CompressFormat.PNG, DEFAULT_PNG_QUALITY);
    }

    static public byte[] bitmapToJpegByteArray(@NonNull Bitmap bitmap) {
        return bitmapToByteArray(bitmap, Bitmap.CompressFormat.JPEG, DEFAULT_JPG_QUALITY);
    }

    /**
     * Get a compressed byte array representation of the supplied bitmap
     *
     * @return Byte array of bitmap
     */
    public static byte[] bitmapToByteArray(@NonNull Bitmap bitmap, @NonNull Bitmap.CompressFormat format, int quality) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(format, quality, stream);
        return stream.toByteArray();
    }

    public static BitmapFactory.Options getImageDimensions(InputStream inputStream) throws OutOfMemoryError {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);

        return options;
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, float rotation) {
        return rotateBitmap(bitmap, rotation, FLIP_NONE);
    }

    /**
     * Rotate and flip a bitmap according to parameters
     * The bitmap will be filtered
     *
     * @param bitmap   Bitmap to rotate and/or flip
     * @param rotation Desired rotation in degrees
     * @param flip     How to flip the bitmap. Choice of horizontal (along the x axis) or vertical (along the y axis)
     * @return Processed bitmap or original bitmap in case of failure
     */
    public static @NonNull Bitmap rotateBitmap(@NonNull Bitmap bitmap, float rotation, int flip) {
        if (rotation != 0 || flip != FLIP_NONE) {
            final int width = bitmap.getWidth();
            final int height = bitmap.getHeight();

            final Matrix matrix = new Matrix();

            if (flip != FLIP_NONE) {
                if ((flip & FLIP_HORIZONTAL) == FLIP_HORIZONTAL) {
                    matrix.postScale(-1, 1, width / 2f, height / 2f);
                }
                if ((flip & FLIP_VERTICAL) == FLIP_VERTICAL) {
                    matrix.postScale(1, -1, width / 2f, height / 2f);
                }
            }
            if (rotation != 0) {
                matrix.postRotate(rotation);
            }
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        }
        return bitmap;
    }

    @Nullable
    public static byte[] getJpegByteArray(Bitmap bitmap, float rotation, int flip) {
        if (bitmap != null) {
            if (rotation != 0f || flip != FLIP_NONE) {
                bitmap = rotateBitmap(bitmap, rotation, flip);
            }

            byte[] bitmapArray = bitmapToJpegByteArray(bitmap);
            recycle(bitmap);

            return bitmapArray;
        }
        return null;
    }

    @Nullable
    public static byte[] getPngByteArray(Bitmap bitmap, float rotation, int flip) {
        if (bitmap != null) {
            if (rotation != 0f || flip != FLIP_NONE) {
                bitmap = rotateBitmap(bitmap, rotation, flip);
            }

            byte[] bitmapArray = bitmapToPngByteArray(bitmap);
            recycle(bitmap);

            return bitmapArray;
        }
        return null;
    }

    /**
     * Get a scaled bitmap from a JPG image file pointed at by imageUri keeping its aspect ratio
     * The image is scaled so that it fits into a bounding box of maxSize x maxSize unless the scaleToWidth parameter is set.
     * If scaleToWidth is set, the image will be scaled so that its width does not exceed maxSize while the height may be larger
     *
     * @param imageUri             Uri pointing to source image
     * @param maxSize              max size of the image
     * @param replaceTransparency  if set to true, transparency in the image will be replaced with Color.WHITE
     * @param scaleToWidth         if set, the image will be scaled so its width does not exceed maxSize while the height may be larger
     * @param applyExifOrientation whether exif rotation or flip settings should be applied, if present
     * @return resulting bitmap or null in case of failure
     */
    static public @Nullable
    Bitmap safeGetBitmapFromUri(Context context, Uri imageUri, int maxSize, boolean replaceTransparency, boolean scaleToWidth, boolean applyExifOrientation) {
        logger.debug("safeGetBitmapFromUri");
        InputStream measure = null, data = null;
        BitmapFactory.Options options;
        int sourceImageWidth, sourceImageHeight;

        final Uri fixedImageUri = FileUtil.getFixedContentUri(context, imageUri);
        if (fixedImageUri == null) {
            return null;
        }

        ThumbnailUtils.Size targetSize;
        try {
            try {
                try {
                    measure = context.getContentResolver().openInputStream(fixedImageUri);
                    data = context.getContentResolver().openInputStream(fixedImageUri);
                } catch (FileNotFoundException | SecurityException | IllegalStateException e) {
                    logger.error("Exception", e);
                    return null;
                }

                // get dimensions of file
                try {
                    options = getImageDimensions(measure);
                } catch (OutOfMemoryError e) {
                    logger.error("Exception", e);
                    return null;
                }
            } finally {
                if (measure != null) {
                    try {
                        measure.close();
                    } catch (IOException e) {
                        //
                    }
                }
            }
            sourceImageWidth = options.outWidth;
            sourceImageHeight = options.outHeight;

            if (scaleToWidth) {
                ThumbnailUtils.Size size = getSizeFromTargetWidth(sourceImageWidth, sourceImageHeight, maxSize);
                maxSize = Math.max(size.height, size.width);
            }

            targetSize = getTargetImageSize(sourceImageWidth, sourceImageHeight, maxSize);
            SampleResult sampleSize = BitmapUtil.getSampleSize(sourceImageWidth, sourceImageHeight, targetSize.width, targetSize.height);

            options.inSampleSize = sampleSize.inSampleSize;
            options.inDensity = sourceImageWidth;
            options.inTargetDensity = targetSize.width * sampleSize.inSampleSize;
            options.inJustDecodeBounds = false;

            if (data != null) {
                try {
                    Bitmap result = BitmapFactory.decodeStream(new BufferedInputStream(data), null, options);
                    if (replaceTransparency && result != null && result.hasAlpha()) {
                        logger.debug("Image has alpha channel, replace transparency with white");
                        result = replaceTransparency(result, Color.WHITE);
                    }

                    if (applyExifOrientation) {
                        BitmapUtil.ExifOrientation exifOrientation = BitmapUtil.getExifOrientation(context, imageUri);
                        if (result != null && (exifOrientation.getRotation() != 0f || exifOrientation.getFlip() != BitmapUtil.FLIP_NONE)) {
                            return BitmapUtil.rotateBitmap(result, exifOrientation.getRotation(), exifOrientation.getFlip());
                        }
                    }

                    return result;
                } catch (StackOverflowError e) {
                    logger.error("Exception", e);
                    return null;
                }
            }
        } finally {
            if (data != null) {
                try {
                    data.close();
                } catch (IOException e) {
                    //
                }
            }
        }
        return null;
    }

    private static ThumbnailUtils.Size getTargetImageSize(int sourceImageWidth, int sourceImageHeight, int maxSize) {
        float aspectWidth, aspectHeight;

        if (sourceImageWidth > maxSize || sourceImageHeight > maxSize) {
            if (sourceImageWidth == 0 || sourceImageHeight == 0) {
                aspectWidth = maxSize;
                aspectHeight = maxSize;
            } else if (sourceImageWidth >= sourceImageHeight) {
                aspectWidth = maxSize;
                aspectHeight = (aspectWidth / sourceImageWidth) * sourceImageHeight;
            } else {
                aspectHeight = maxSize;
                aspectWidth = (aspectHeight / sourceImageHeight) * sourceImageWidth;
            }
        } else {
            aspectHeight = sourceImageHeight;
            aspectWidth = sourceImageWidth;
        }

        return new ThumbnailUtils.Size(Math.round(aspectWidth), Math.round(aspectHeight));
    }

    /**
     * Replace transparency in bitmap with new color.
     * After the replacement, the old bitmap will be recycled and cannot be reused.
     *
     * @param in    The bitmap to be processed
     * @param color A color, e.g. Color.WHITE
     * @return A new bitmap with transparency replaced by the specified color
     */
    public static Bitmap replaceTransparency(@NonNull Bitmap in, @ColorInt int color) {
        if (in.getConfig() != null) {
            final Bitmap out = Bitmap.createBitmap(in.getWidth(), in.getHeight(), in.getConfig());
            out.setDensity(in.getDensity());
            out.eraseColor(color);
            Canvas canvas = new Canvas(out);  // create a canvas to draw on the new image
            canvas.drawBitmap(in, 0f, 0f, null); // draw old image on the background
            return out;
        }
        return in;
    }

    /**
     * Resize a bitmap provided as a byte array so that the width of the resulting image is less or equal to maxWidth.
     * For the sake of memory efficiency, we use subsampling which means the scaling is approximate and may only be a power of two.
     *
     * @param imageData stream of original image data. Must be resettable, i.e. imageData.markSupported() must return true.
     * @param maxWidth maximum width of the image after scaling is applied
     * @return compressed byte array of scaled bitmap in either PNG or JPG format - depending on source bitmap format - or null in case of an error
     */
    @Nullable
    static public byte[] resizeImageToMaxWidth(InputStream imageData, int maxWidth) {
        try {
            boolean isJpeg = ExifInterface.isJpegFormat(imageData);
            imageData.reset();

            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = getInSampleSizeByWidth(imageData, maxWidth);
            imageData.reset();
            o2.inScaled = true;
            o2.inPreferredConfig = isJpeg ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;

            Bitmap newPhoto = BitmapFactory.decodeStream(imageData, null, o2);
            try {
                if (newPhoto != null) {
                    if (isJpeg) {
                        return bitmapToJpegByteArray(newPhoto);
                    } else {
                        return bitmapToPngByteArray(newPhoto);
                    }
                }
            } finally {
                recycle(newPhoto);
            }
        } catch (Exception x) {
            logger.error("Exception", x);
        }
        return null;
    }

    /**
     * Resizes a given bitmap so that the width of the resulting image is less or equal to maxWidth while keeping the aspect ratio of the image
     * Returns a new scaled bitmap or the original bitmap if it already fits into the bounding box
     *
     * @param bitmap   Bitmap to resize
     * @param maxWidth Width of bounding box
     * @return scaled and filtered bitmap, or existing bitmap
     */
    public static Bitmap resizeBitmapExactlyToMaxWidth(@NonNull Bitmap bitmap, int maxWidth) {
        if (bitmap.getWidth() > maxWidth) {
            ThumbnailUtils.Size targetSize = getSizeFromTargetWidth(bitmap.getWidth(), bitmap.getHeight(), maxWidth);
            return Bitmap.createScaledBitmap(bitmap, targetSize.width, targetSize.height, true);
        }
        return bitmap;
    }

    public static Bitmap resizeBitmap(Bitmap sourceBitmap, int width, int height) {
        SampleResult sampleSize = BitmapUtil.getSampleSize(sourceBitmap.getWidth(), sourceBitmap.getHeight(), width, height);

        Bitmap res = null;

        if (sampleSize.inSampleSize > 0) {
            try {
                return Bitmap.createScaledBitmap(sourceBitmap, sampleSize.newWidth, sampleSize.newHeight, true);
            } catch (Exception x) {
                logger.error("Exception", x);
            }
        }
        return res;
    }

    public static class ExifOrientation {
        // flip first
        int flip;
        float rotation;

        public ExifOrientation(int flip, float rotation) {
            this.flip = flip;
            this.rotation = rotation;
        }

        public @BitmapUtil.FlipType int getFlip() {
            return flip;
        }

        public float getRotation() {
            return rotation;
        }
    }

    /**
     * Get the rotation of an image by looking at its Exif data
     * This should be called from a worker thread as it performs I/O operations
     *
     * @param context Context
     * @param uri     Uri of the image to be checked for rotation
     * @return rotation in degrees
     */
    @SuppressLint("InlinedApi")
    @WorkerThread
    public static ExifOrientation getExifOrientation(@NonNull Context context, @Nullable Uri uri) {
        ExifOrientation retVal = new ExifOrientation(FLIP_NONE, 0);

        if (uri != null) {
            int orientation = ExifInterface.ORIENTATION_UNDEFINED;

            try {
                try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                    if (inputStream != null) {
                        ExifInterface exif = new ExifInterface(inputStream);
                        orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                    }
                } catch (IOException | NegativeArraySizeException e) {
                    logger.error("Error checking exif", e);
                } catch (SecurityException e) {
                    logger.error("Error checking exif: Permission denied", e);
                } catch (NetworkOnMainThreadException e) {
                    logger.error("Error checking exif: Cannot get it from network", e);
                }
            } catch (IllegalStateException e) {
                logger.error("Error opening input stream", e);
            }

            if (orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                return (exifOrientationToDegrees(orientation));
            } else {
                String[] projection = {MediaStore.Images.Media.ORIENTATION};
                try (Cursor c = context.getContentResolver().query(uri, projection, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        orientation = c.getInt(0);
                        // sanity check for returned value
                        if (orientation >= 0 && orientation < 360) {
                            retVal.rotation = (float) orientation;
                        } else {
                            retVal.rotation = ExifInterface.ORIENTATION_UNDEFINED;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("No orientation column");
                }
            }
        }
        return retVal;
    }

    private static ExifOrientation exifOrientationToDegrees(int exifOrientation) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return new ExifOrientation(FLIP_NONE, 90F);
            case ExifInterface.ORIENTATION_TRANSVERSE: // flip horizontally, rotate 90
                return new ExifOrientation(FLIP_HORIZONTAL, 90F);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return new ExifOrientation(FLIP_NONE, 180F);
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                return new ExifOrientation(FLIP_VERTICAL, 0F);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return new ExifOrientation(FLIP_NONE, 270F);
            case ExifInterface.ORIENTATION_TRANSPOSE: // flip horizontally, rotate 270
                return new ExifOrientation(FLIP_HORIZONTAL, 270F);
            case ExifInterface.ORIENTATION_NORMAL:
                return new ExifOrientation(FLIP_NONE, 0F);
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: // flip horizontally
                return new ExifOrientation(FLIP_HORIZONTAL, 0F);
        }
        return new ExifOrientation(FLIP_NONE, 0F);
    }

    public static void recycle(Bitmap bitmapToRecycle) {
        if (bitmapToRecycle != null && !bitmapToRecycle.isRecycled()) {
            bitmapToRecycle.recycle();
        }
    }

    public static Bitmap tintImage(Bitmap bitmap, @ColorInt int color) {
        if (bitmap != null) {
            Paint paint = new Paint();
            paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            Bitmap bitmapResult = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmapResult);
            canvas.drawBitmap(bitmap, 0, 0, paint);
            return bitmapResult;
        }
        return bitmap;
    }

    public static Bitmap addOverlay(@NonNull Bitmap background, @NonNull Bitmap foreground, int offset) {
        try {
            int bgWidth = background.getWidth();
            int bgHeight = background.getHeight();
            Bitmap result = Bitmap.createBitmap(bgWidth, bgHeight, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(result);
            int x = (bgWidth - foreground.getWidth()) / 2;
            int y = (bgHeight - foreground.getHeight()) / 2;

            cv.drawBitmap(background, 0, 0, null);
            cv.drawBitmap(BitmapUtil.tintImage(foreground, Color.GRAY), x + offset, y + offset, null);
            cv.drawBitmap(BitmapUtil.tintImage(foreground, Color.WHITE), x, y, null);
            cv.save();
            cv.restore();
            return result;
        } catch (Exception e) {
            return background;
        }
    }

    @Nullable
    public static Bitmap getBitmapFromVectorDrawable(Drawable icon, Integer tintColor) {
        Bitmap bitmap = null;
        if (icon instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) icon).getBitmap();
        } else {
            // e.g. VectorDrawable or AdaptiveIconDrawable
            bitmap = Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);
            icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            if (tintColor != null) {
                icon.setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN));
            }
            icon.draw(canvas);
        }
        return bitmap;
    }

    /**
     * Get size where the width is always smaller or equal to maxWidth,
     * resizing height if necessary to preserve aspect ratio.
     *
     * @param inWidth  original width
     * @param inHeight original height
     * @param maxWidth target width max
     * @return target size where width is exactly maxWidth or smaller if inWidth is already smaller
     */
    private static ThumbnailUtils.Size getSizeFromTargetWidth(final int inWidth, final int inHeight, final int maxWidth) {
        if (inWidth > maxWidth) {
            float aspectRatio = (float) inWidth / (float) maxWidth;

            return new ThumbnailUtils.Size(maxWidth, Math.round((float) inHeight / aspectRatio));
        }
        return new ThumbnailUtils.Size(inWidth, inHeight);
    }

    /**
     * Check a bitmap for the presence of transparency
     * For the sake of speed we only check the topmost left pixel...
     *
     * @return true if the topmost left pixel is transparent, false otherwise
     */
    public static boolean hasTransparency(@NonNull Bitmap bitmap) {
        if (bitmap.hasAlpha()) {
            if (bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                int pixel = bitmap.getPixel(0, 0);
                return (pixel & 0xff000000) == 0x0;
            }
        }
        return false;
    }

    /**
     * Create bitmap from a view
     *
     * @param view source view
     * @return generated bitmap object
     */
    public static Bitmap getBitmapFromView(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable bgDrawable = view.getBackground();
        if (bgDrawable != null)
            bgDrawable.draw(canvas);
        else
            canvas.drawColor(Color.WHITE);
        view.draw(canvas);
        return bitmap;
    }

    /**
     * Blur the Bitmap.
     * NOTE: The input Bitmap will be altered.
     *
     * @param bitmap The bitmap to blur
     * @return The blurred bitmap. This is the same object which has been passed as input
     */
    @WorkerThread
    public static Bitmap blurBitmap(@Nullable Bitmap bitmap, Context context) {
        if (bitmap != null && (bitmap.getConfig() == Bitmap.Config.RGB_565 || bitmap.getConfig() == Bitmap.Config.ARGB_8888)) {
            final RenderScript rs = RenderScript.create(context);
            final Allocation input = Allocation.createFromBitmap(rs, bitmap);
            final Allocation output = Allocation.createTyped(rs, input.getType());
            final ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            blurScript.setRadius(12f);
            blurScript.setInput(input);
            blurScript.forEach(output);
            output.copyTo(bitmap);
            blurScript.destroy();
            input.destroy();
            output.destroy();
            rs.destroy();
        }
        return bitmap;
    }
}
