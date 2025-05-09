/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

package ch.threema.storage;


import android.database.Cursor;

import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.storage.models.GroupMemberModel;
import ch.threema.storage.models.GroupModel;

public class DatabaseUtil {

    private DatabaseUtil() {
    }

    public static Long getDateTimeContentValue(Date date) {
        return date != null ? date.getTime() : null;
    }

    public static Date getDateFromValue(Long timestamp) {
        if (timestamp != null) {
            return new Date(timestamp);
        }
        return null;
    }

    public static String makePlaceholders(int len) {
        if (len < 1) {
            // It will lead to an invalid query anyway ..
            throw new RuntimeException("No placeholders");
        } else {
            StringBuilder sb = new StringBuilder(len * 2 - 1);
            sb.append("?");
            for (int i = 1; i < len; i++) {
                sb.append(",?");
            }
            return sb.toString();
        }
    }

    /**
     * only for a select count(*) result, the first column must be the count value
     *
     * @param c
     * @return
     */
    public static long count(Cursor c) {
        long count = 0;
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    count = c.getLong(0);
                }
            } finally {
                c.close();
            }
        }
        return count;
    }

    /**
     * Convert a array of Objects to a valid argument String array
     *
     * @param objectList
     * @return
     */
    public static <T> String[] convertArguments(List<T> objectList) {
        String[] arguments = new String[objectList.size()];
        for (int n = 0; n < objectList.size(); n++) {
            arguments[n] = String.valueOf(objectList.get(n));
        }
        return arguments;
    }

    /**
     * An SQL query that can be used to check whether an identity is part of a group. There is one
     * placeholder (?) that should be used for the identity that should be checked.
     */
    @NonNull
    public final static String IS_GROUP_MEMBER_QUERY = "SELECT EXISTS(" +
        "SELECT 1 FROM " + GroupModel.TABLE + " g INNER JOIN " + GroupMemberModel.TABLE + " m" +
        "  ON m." + GroupMemberModel.COLUMN_GROUP_ID + " = g." + GroupModel.COLUMN_ID + " " +
        "WHERE m." + GroupMemberModel.COLUMN_IDENTITY + " = ?" +
        ")";
}
