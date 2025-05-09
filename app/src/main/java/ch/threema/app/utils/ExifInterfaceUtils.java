/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

/*
 * Based on AOSP code with modifications by Threema
 *
 * (C) Google Inc. All Rights Reserved
 * Licensed under the Apache License, version 2.0
 */

package ch.threema.app.utils;

import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import androidx.annotation.DoNotInline;
import androidx.annotation.RequiresApi;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class ExifInterfaceUtils {
    private static final String TAG = "ExifInterfaceUtils";

    private ExifInterfaceUtils() {
        // Prevent instantiation
    }

    /**
     * Copies all of the bytes from {@code in} to {@code out}. Neither stream is closed.
     * Returns the total number of bytes transferred.
     */
    static int copy(InputStream in, OutputStream out) throws IOException {
        int total = 0;
        byte[] buffer = new byte[8192];
        int c;
        while ((c = in.read(buffer)) != -1) {
            total += c;
            out.write(buffer, 0, c);
        }
        return total;
    }

    /**
     * Copies the given number of the bytes from {@code in} to {@code out}. Neither stream is
     * closed.
     */
    static void copy(InputStream in, OutputStream out, int numBytes) throws IOException {
        int remainder = numBytes;
        byte[] buffer = new byte[8192];
        while (remainder > 0) {
            int bytesToRead = Math.min(remainder, 8192);
            int bytesRead = in.read(buffer, 0, bytesToRead);
            if (bytesRead != bytesToRead) {
                throw new IOException("Failed to copy the given amount of bytes from the input"
                    + "stream to the output stream.");
            }
            remainder -= bytesRead;
            out.write(buffer, 0, bytesRead);
        }
    }

    /**
     * Convert given int[] to long[]. If long[] is given, just return it.
     * Return null for other types of input.
     */
    static long[] convertToLongArray(Object inputObj) {
        if (inputObj instanceof int[]) {
            int[] input = (int[]) inputObj;
            long[] result = new long[input.length];
            for (int i = 0; i < input.length; i++) {
                result[i] = input[i];
            }
            return result;
        } else if (inputObj instanceof long[]) {
            return (long[]) inputObj;
        }
        return null;
    }

    static boolean startsWith(byte[] cur, byte[] val) {
        if (cur == null || val == null) {
            return false;
        }
        if (cur.length < val.length) {
            return false;
        }
        for (int i = 0; i < val.length; i++) {
            if (cur[i] != val[i]) {
                return false;
            }
        }
        return true;
    }

    static String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    static long parseSubSeconds(String subSec) {
        try {
            final int len = Math.min(subSec.length(), 3);
            long sub = Long.parseLong(subSec.substring(0, len));
            for (int i = len; i < 3; i++) {
                sub *= 10;
            }
            return sub;
        } catch (NumberFormatException e) {
            // Ignored
        }
        return 0L;
    }


    /**
     * Closes 'closeable', ignoring any checked exceptions. Does nothing if 'closeable' is null.
     */
    static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Closes a file descriptor that has been duplicated.
     */
    static void closeFileDescriptor(FileDescriptor fd) {
        // Os.dup and Os.close was introduced in API 21 so this method shouldn't be called
        // in API < 21.
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                Api21Impl.close(fd);
                // Catching ErrnoException will raise error in API < 21
            } catch (Exception ex) {
                Log.e(TAG, "Error closing fd.");
            }
        } else {
            Log.e(TAG, "closeFileDescriptor is called in API < 21, which must be wrong.");
        }
    }

    @RequiresApi(21)
    static class Api21Impl {
        private Api21Impl() {
        }

        @DoNotInline
        static FileDescriptor dup(FileDescriptor fileDescriptor) throws ErrnoException {
            return Os.dup(fileDescriptor);
        }

        @DoNotInline
        static long lseek(FileDescriptor fd, long offset, int whence) throws ErrnoException {
            return Os.lseek(fd, offset, whence);
        }

        @DoNotInline
        static void close(FileDescriptor fd) throws ErrnoException {
            Os.close(fd);
        }
    }

    @RequiresApi(23)
    static class Api23Impl {
        private Api23Impl() {
        }

        @DoNotInline
        static void setDataSource(MediaMetadataRetriever retriever, MediaDataSource dataSource) {
            retriever.setDataSource(dataSource);
        }
    }
}
