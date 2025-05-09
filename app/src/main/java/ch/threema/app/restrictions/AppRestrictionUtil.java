/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.app.restrictions;

import static ch.threema.app.utils.AutoDeleteUtil.validateKeepMessageDays;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

public class AppRestrictionUtil {

    private static final Logger logger = LoggingUtil.getThreemaLogger("AppRestrictionUtil");

    public static boolean isAddContactDisabled(Context context) {
        return getBoolRestriction(context, R.string.restriction__disable_add_contact);
    }

    public static boolean isCreateGroupDisabled(Context context) {
        return getBoolRestriction(context, R.string.restriction__disable_create_group);
    }

    public static boolean isBackupsDisabled(Context context) {
        return getBoolRestriction(context, R.string.restriction__disable_backups);
    }

    public static boolean isDataBackupsDisabled(Context context) {
        return getBoolRestriction(context, R.string.restriction__disable_data_backups);
    }

    public static boolean isIdBackupsDisabled(Context context) {
        return getBoolRestriction(context, R.string.restriction__disable_id_export);
    }

    public static boolean isExportDisabled(Context context) {
        return getBoolRestriction(context, R.string.restriction__disable_export);
    }

    public static boolean isSaveToGalleryDisabled(Context context) {
        return getBoolRestriction(context, R.string.restriction__disable_save_to_gallery);
    }

    public static boolean isMessagePreviewDisabled(Context context) {
        return getBoolRestriction(context, R.string.restriction__disable_message_preview);
    }

    public static boolean isCallsDisabled() {
        return getBoolRestriction(ThreemaApplication.getAppContext(), R.string.restriction__disable_calls);
    }

    public static boolean isReadonlyProfile(Context context) {
        return getBoolRestriction(context, R.string.restriction__readonly_profile);
    }

    public static boolean isWebDisabled(Context context) {
        return getBoolRestriction(context, R.string.restriction__disable_web);
    }

    @Nullable
    public static Boolean isMultiDeviceDisabled(@NonNull Context context) {
        return getBooleanRestriction(context.getString(R.string.restriction__disable_multidevice));
    }

    public static boolean isShareMediaDisabled(Context context) {
        return getBoolRestriction(context, R.string.restriction__disable_share_media);
    }

    public static boolean isVideoCallsDisabled() {
        return getBoolRestriction(ThreemaApplication.getAppContext(), R.string.restriction__disable_video_calls);
    }

    public static boolean isWorkDirectoryDisabled() {
        return getBoolRestriction(ThreemaApplication.getAppContext(), R.string.restriction__disable_work_directory);
    }

    public static boolean isGroupCallsDisabled() {
        return getBoolRestriction(ThreemaApplication.getAppContext(), R.string.restriction__disable_group_calls);
    }

    /**
     * Check if a valid Threema Safe password pattern has been set by means of app restrictions
     *
     * @param context Context
     * @return true if a password pattern has been set and its syntax is valid, false otherwise
     */
    public static boolean isSafePasswordPatternSet(Context context) {
        if (ConfigUtils.isWorkRestricted() && !TestUtil.isEmptyOrNull(getSafePasswordPattern(context))) {
            // check validity of regex pattern
            try {
                Pattern.compile(getSafePasswordPattern(context));
                return true;
            } catch (PatternSyntaxException ignored) {
            }
        }
        return false;
    }

    public static String getSafePasswordPattern(Context context) {
        return getStringRestriction(context.getString(R.string.restriction__safe_password_pattern));
    }

    public static String getSafePasswordMessage(Context context) {
        String message = getStringRestriction(context.getString(R.string.restriction__safe_password_message));
        if (TestUtil.isEmptyOrNull(message)) {
            return context.getString(R.string.password_does_not_comply);
        }
        return message;
    }

    /**
     * Return a list of allowed signaling hosts for Threema Web (or null if no restrictions apply).
     */
    @Nullable
    public static List<String> getWebHosts(@NonNull Context context) {
        final String stringRestriction = getStringRestriction(context.getString(R.string.restriction__web_hosts));
        if (stringRestriction != null && !stringRestriction.isEmpty()) {
            List<String> hosts = new ArrayList<>();
            for (String host : stringRestriction.split(",")) {
                final String trimmed = host.trim();
                if (!trimmed.isEmpty()) {
                    hosts.add(trimmed);
                }
            }
            return hosts.isEmpty() ? null : hosts;
        }
        return null;
    }

    /**
     * Return true in one of the following three cases:
     * <p>
     * - Threema Web signaling host whitelist is empty
     * - Hostname is contained in whitelist
     * - A suffix pattern in the whitelist matches the hostname
     */
    public static boolean isWebHostAllowed(@NonNull Context context, @NonNull String hostname) {
        final List<String> whitelist = getWebHosts(context);
        if (whitelist == null) {
            logger.debug("No Threema Web signaling server whitelist set by MDM");
            return true;
        }
        logger.info("Validating Threema Web signaling server against whitelist");
        logger.debug("Whitelist: {}", whitelist);
        for (String pattern : whitelist) {
            if (pattern.equals(hostname)) {
                logger.debug("Host {} matched pattern {}", hostname, pattern);
                return true;
            }
            if (pattern.startsWith("*") && hostname.endsWith(pattern.substring(1))) {
                logger.debug("Host {} matched pattern {}", hostname, pattern);
                return true;
            }
        }
        logger.warn("Threema Web signaling server \"{}\" blocked by MDM", hostname);
        return false;
    }

    /**
     * Get MDM configuration for how long messages are to be kept before they are deleted
     *
     * @param context The context
     * @return Number of days or 0 if messages should be kept forever. null if the restriction is not set.
     */
    public static Integer getKeepMessagesDays(Context context) {
        Integer days = getIntRestriction(context.getString(R.string.restriction__keep_messages_days));
        if (days != null) {
            return validateKeepMessageDays(days);
        }
        return days;
    }

    /**
     * get boolean value for restriction
     *
     * @param restriction The resource id of the restriction name
     * @return true if restriction value is set to true, false otherwise or if restriction does not exist or app is not restricted
     */
    public static boolean getBoolRestriction(@NonNull Context context, @StringRes int restriction) {
        if (ConfigUtils.isWorkRestricted()) {
            Boolean restrictionValue = getBooleanRestriction(context.getString(restriction));
            return (restrictionValue != null && restrictionValue);
        }
        return false;
    }

    /**
     * Get a string restriction
     *
     * @param string Restriction key
     * @return Restriction value or null if restriction is empty or has not been set
     */
    @Nullable
    public static String getStringRestriction(String string) {
        Bundle appRestrictions = AppRestrictionService.getInstance()
            .getAppRestrictions();

        if (appRestrictions != null && appRestrictions.containsKey(string)) {
            String preset = appRestrictions.getString(string);

            if (!TestUtil.isEmptyOrNull(preset)) {
                return preset;
            }
        }
        return null;
    }

    @Nullable
    public static Boolean getBooleanRestriction(String string) {
        Bundle appRestrictions = AppRestrictionService.getInstance()
            .getAppRestrictions();

        if (appRestrictions != null && appRestrictions.containsKey(string)) {
            return appRestrictions.getBoolean(string);
        }
        return null;
    }

    public static boolean hasBooleanRestriction(String string) {
        return getBooleanRestriction(string) != null;
    }

    /**
     * Get an integer restriction
     *
     * @param string Restriction key
     * @return Restriction value or null if restriction is empty or has not been set or 0 if there
     * is no int mapping for the given key.
     */
    public static Integer getIntRestriction(String string) {
        Bundle appRestrictions = AppRestrictionService.getInstance()
            .getAppRestrictions();

        if (appRestrictions != null && appRestrictions.containsKey(string)) {
            return appRestrictions.getInt(string, 0);
        }
        return null;
    }
}
