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

/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.vending.licensing;

import android.text.TextUtils;

import java.util.regex.Pattern;

/**
 * ResponseData from licensing server.
 */
public class ResponseData {

    public int responseCode;
    public int nonce;
    public String packageName;
    public String versionCode;
    public String userId;
    public long timestamp;
    /**
     * Response-specific data.
     */
    public String extra;

    public String responseData;
    public String signature;

    /**
     * Parses response string into ResponseData.
     *
     * @param responseData response data string
     * @return ResponseData object
     * @throws IllegalArgumentException upon parsing error
     */
    public static ResponseData parse(String responseData, String signature) {
        // Must parse out main response data and response-specific data.
        int index = responseData.indexOf(':');
        String mainData, extraData;
        if (-1 == index) {
            mainData = responseData;
            extraData = "";
        } else {
            mainData = responseData.substring(0, index);
            extraData = index >= responseData.length() ? "" : responseData.substring(index + 1);
        }

        String[] fields = TextUtils.split(mainData, Pattern.quote("|"));
        if (fields.length < 6) {
            throw new IllegalArgumentException("Wrong number of fields.");
        }

        ResponseData data = new ResponseData();
        data.extra = extraData;
        data.responseCode = Integer.parseInt(fields[0]);
        data.nonce = Integer.parseInt(fields[1]);
        data.packageName = fields[2];
        data.versionCode = fields[3];
        // Application-specific user identifier.
        data.userId = fields[4];
        data.timestamp = Long.parseLong(fields[5]);

        data.responseData = responseData;
        data.signature = signature;

        return data;
    }

    @Override
    public String toString() {
        return TextUtils.join("|", new Object[]{
            responseCode, nonce, packageName, versionCode,
            userId, timestamp
        });
    }
}
