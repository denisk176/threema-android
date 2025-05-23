/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.workers;

import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import ch.threema.app.BuildConfig;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.base.utils.LoggingUtil;

public class ShareTargetUpdateWorker extends Worker {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ShareTargetUpdateWorker");

    public ShareTargetUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        logger.info("Updating share target shortcuts");

        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            UserService userService = serviceManager.getUserService();
            if (userService != null && userService.hasIdentity()) {
                if (!BackupService.isRunning()) {
                    ShortcutUtil.publishRecentChatsAsShareTargets();
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            UsageStatsManager usageStatsManager = (UsageStatsManager) getApplicationContext().getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager != null) {
                logger.info("Is inactive = {}; Standby bucket = {} (should be <= 10)", usageStatsManager.isAppInactive(BuildConfig.APPLICATION_ID), usageStatsManager.getAppStandbyBucket());
            }
        }

        return Result.success();
    }
}
