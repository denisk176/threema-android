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

package ch.threema.app.workers

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.asynctasks.AddOrUpdateWorkContactBackgroundTask
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.routines.UpdateAppLogoRoutine
import ch.threema.app.routines.UpdateWorkInfoRoutine
import ch.threema.app.services.ContactService
import ch.threema.app.services.FileService
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.license.UserCredentials
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.stores.IdentityStore
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.RuntimeUtil
import ch.threema.app.utils.WorkManagerUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.work.WorkData
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

private val logger = LoggingUtil.getThreemaLogger("WorkSyncWorker")

class WorkSyncWorker(private val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {
    private val serviceManager: ServiceManager? = ThreemaApplication.getServiceManager()
    private val contactService: ContactService? = serviceManager?.contactService
    private val preferenceService: PreferenceService? = serviceManager?.preferenceService
    private val fileService: FileService? = serviceManager?.fileService
    private val licenseService: LicenseService<*>? = serviceManager?.licenseService
    private val apiConnector: APIConnector? = serviceManager?.apiConnector
    private val notificationService: NotificationService? = serviceManager?.notificationService
    private val userService: UserService? = serviceManager?.userService
    private val multiDeviceManager: MultiDeviceManager? = serviceManager?.multiDeviceManager
    private val taskCreator: TaskCreator? = serviceManager?.taskCreator
    private val lifetimeService: LifetimeService? = serviceManager?.lifetimeService
    private val identityStore: IdentityStore? = serviceManager?.identityStore
    private val contactModelRepository: ContactModelRepository? =
        serviceManager?.modelRepositories?.contacts

    companion object {
        private const val EXTRA_FORCE_UPDATE = "FORCE_UPDATE"

        fun schedulePeriodicWorkSync(context: Context, preferenceService: PreferenceService) {
            if (!ConfigUtils.isWorkBuild()) {
                logger.debug("Do not start work sync worker in non-work build")
                return
            }

            val schedulePeriodMs =
                WorkManagerUtil.normalizeSchedulePeriod(preferenceService.workSyncCheckInterval)
            logger.info("Scheduling periodic work sync. Schedule period: {} ms", schedulePeriodMs)

            try {
                val workManager = WorkManager.getInstance(context)
                val policy = if (WorkManagerUtil.shouldScheduleNewWorkManagerInstance(
                        workManager,
                        ThreemaApplication.WORKER_PERIODIC_WORK_SYNC,
                        schedulePeriodMs,
                    )
                ) {
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                } else {
                    ExistingPeriodicWorkPolicy.KEEP
                }
                logger.info(
                    "{}: {} existing periodic work",
                    ThreemaApplication.WORKER_PERIODIC_WORK_SYNC,
                    policy,
                )
                val workRequest = buildPeriodicWorkRequest(schedulePeriodMs)
                workManager.enqueueUniquePeriodicWork(
                    ThreemaApplication.WORKER_PERIODIC_WORK_SYNC,
                    policy,
                    workRequest,
                )
            } catch (e: IllegalStateException) {
                logger.error("Unable to schedule periodic work sync work", e)
            }
        }

        suspend fun cancelPeriodicWorkSyncAwait(context: Context) {
            WorkManagerUtil.cancelUniqueWorkAwait(
                context,
                ThreemaApplication.WORKER_PERIODIC_WORK_SYNC,
            )
        }

        private fun buildOneTimeWorkRequest(
            forceUpdate: Boolean,
            tag: String?,
        ): OneTimeWorkRequest {
            val data = Data.Builder()
                .putBoolean(EXTRA_FORCE_UPDATE, forceUpdate)
                .build()

            val builder = OneTimeWorkRequestBuilder<WorkSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .apply { setInputData(data) }

            tag?.let {
                builder.addTag(tag)
            }

            return builder.build()
        }

        private fun buildPeriodicWorkRequest(schedulePeriodMs: Long): PeriodicWorkRequest {
            val data = Data.Builder()
                .putBoolean(EXTRA_FORCE_UPDATE, false)
                .build()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return PeriodicWorkRequestBuilder<WorkSyncWorker>(
                schedulePeriodMs,
                TimeUnit.MILLISECONDS,
            )
                .setConstraints(constraints)
                .addTag(schedulePeriodMs.toString())
                .apply { setInputData(data) }
                .build()
        }

        /**
         * Start a one time work sync request. Existing work will be [ExistingWorkPolicy.REPLACE]d.
         *
         * If it is required to run a callback when the request was successful or failed, use
         * `performOneTimeWorkSync(Activity, Runnable, Runnable)`.
         */
        fun performOneTimeWorkSync(
            context: Context,
            forceUpdate: Boolean,
            tag: String?,
        ) {
            val workRequest = buildOneTimeWorkRequest(forceUpdate, tag)
            WorkManager.getInstance(context).enqueueUniqueWork(
                ThreemaApplication.WORKER_WORK_SYNC,
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
        }

        /**
         * Start a one time work sync request.
         *
         * @param onSuccess is run when the work sync request was successful
         * @param onFail    is run when the work sync request was unsuccessful
         */
        fun performOneTimeWorkSync(
            activity: AppCompatActivity,
            onSuccess: Runnable,
            onFail: Runnable,
        ) {
            val workerTag = "OneTimeWorkSyncWorker"
            val workRequest = buildOneTimeWorkRequest(
                forceUpdate = true,
                tag = workerTag,
            )
            val workManager = WorkManager.getInstance(ThreemaApplication.getAppContext())
            workManager.getWorkInfoByIdLiveData(workRequest.id)
                .observe(activity) { workInfo: WorkInfo? ->
                    if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                        onSuccess.run()
                    } else if (workInfo?.state == WorkInfo.State.FAILED) {
                        onFail.run()
                    } else if (workInfo == null) {
                        // Just log this. It is likely that a non-null work info will be observed
                        // later and the sync completes successfully.
                        logger.info("Work info is null")
                    }
                }
            workManager.enqueueUniqueWork(
                ThreemaApplication.WORKER_WORK_SYNC,
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
        }
    }

    override fun doWork(): Result {
        val forceUpdate: Boolean = inputData.getBoolean(EXTRA_FORCE_UPDATE, false)
        val newWorkContacts: MutableList<ContactModel> = mutableListOf()

        logger.info("Refreshing work data. Force = {}", forceUpdate)

        if (licenseService == null ||
            notificationService == null ||
            contactService == null ||
            apiConnector == null ||
            preferenceService == null ||
            userService == null ||
            multiDeviceManager == null ||
            taskCreator == null ||
            lifetimeService == null ||
            contactModelRepository == null
        ) {
            logger.info("Services not available")
            return Result.failure()
        }

        if (!ConfigUtils.isWorkBuild()) {
            logger.info("Not allowed to run in a non work environment")
            return Result.failure()
        }

        val credentials =
            licenseService.loadCredentials() as? UserCredentials ?: return Result.failure()

        val workData: WorkData?
        try {
            // TODO(ANDR-3172): Get all contacts via contact model repository
            val allContacts: List<ch.threema.storage.models.ContactModel> = contactService.all
            val identities = arrayOfNulls<String>(allContacts.size)
            for (n in allContacts.indices) {
                identities[n] = allContacts[n].identity
            }
            workData = apiConnector.fetchWorkData(
                /* username = */
                credentials.username,
                /* password = */
                credentials.password,
                /* identities = */
                identities,
            )
        } catch (e: Exception) {
            logger.error("Failed to fetch2 work data from API", e)
            notificationService.cancelWorkSyncProgress()
            return Result.failure()
        }

        if (workData.responseCode > 0) {
            logger.error(
                "Failed to fetch2 work data. Server response code = {}",
                workData.responseCode,
            )
            if (workData.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                RuntimeUtil.runOnUiThread {
                    Toast.makeText(context, R.string.fetch2_failure, Toast.LENGTH_LONG).show()
                }
            }
            notificationService.cancelWorkSyncProgress()
            return Result.failure()
        }

        val existingWorkIdentities = contactService.allWork.map { it.identity }.toSet()
        val fetchedWorkIdentities = workData.workContacts.map { it.threemaId }.toSet()

        // Create or update work contacts
        val refreshedWorkIdentities = workData.workContacts
            .mapNotNull { workContact ->
                AddOrUpdateWorkContactBackgroundTask(
                    workContact = workContact,
                    myIdentity = userService.identity,
                    contactModelRepository = contactModelRepository,
                ).runSynchronously()
            }.map { it.identity }

        val newWorkIdentities = refreshedWorkIdentities - existingWorkIdentities
        newWorkContacts.addAll(
            newWorkIdentities.mapNotNull(contactModelRepository::getByIdentity),
        )

        // Downgrade work contacts
        val downgradedIdentities = existingWorkIdentities - fetchedWorkIdentities
        downgradedIdentities.mapNotNull { contactModelRepository.getByIdentity(it) }.forEach {
            // The contact is no longer a work contact, so set work verification level to none
            it.setWorkVerificationLevelFromLocal(WorkVerificationLevel.NONE)

            // Additionally, the contact may not be server verified anymore (except it has been
            // fully verified before)
            if (it.data.value?.verificationLevel == VerificationLevel.SERVER_VERIFIED) {
                it.setVerificationLevelFromLocal(VerificationLevel.UNVERIFIED)
            }
        }

        // update app-logos
        // start a new thread to lazy download the app icons
        logger.trace("Updating app logos in new thread")
        Thread(
            UpdateAppLogoRoutine(
                /* fileService = */
                this.fileService,
                /* preferenceService = */
                this.preferenceService,
                /* lightUrl = */
                workData.logoLight,
                /* darkUrl = */
                workData.logoDark,
                /* forceUpdate = */
                forceUpdate,
            ),
            "UpdateAppIcon",
        ).start()
        preferenceService.customSupportUrl = workData.supportUrl
        // Save the Mini-MDM Parameters to a local file
        AppRestrictionService.getInstance()
            .storeWorkMDMSettings(workData.mdm)

        // update work info
        UpdateWorkInfoRoutine(
            /* context = */
            context,
            /* apiConnector = */
            apiConnector,
            /* identityStore = */
            identityStore,
            /* deviceService = */
            null,
            /* licenseService = */
            licenseService,
        ).run()
        preferenceService.workDirectoryEnabled = workData.directory.enabled
        preferenceService.workDirectoryCategories = workData.directory.categories
        preferenceService.workOrganization = workData.organization
        logger.trace("CheckInterval = " + workData.checkInterval)
        if (workData.checkInterval > 0) {
            // schedule next interval
            preferenceService.workSyncCheckInterval = workData.checkInterval
        }

        notificationService.cancelWorkSyncProgress()

        logger.info("Refreshing work data successfully finished")

        if (newWorkContacts.isEmpty()) {
            return Result.success()
        }
        val wasSuccessful = ContactUpdateWorker.fetchAndUpdateContactModels(
            contactModels = newWorkContacts,
            apiConnector = apiConnector,
            preferenceService = preferenceService,
        )
        return if (wasSuccessful) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
        val notification =
            NotificationCompat.Builder(context, NotificationChannels.NOTIFICATION_CHANNEL_WORK_SYNC)
                .setSound(null)
                .setSmallIcon(R.drawable.ic_sync_notification)
                .setContentTitle(context.getString(R.string.wizard1_sync_work))
                .setProgress(0, 0, true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build()

        return Futures.immediateFuture(
            ForegroundInfo(
                ThreemaApplication.WORK_SYNC_NOTIFICATION_ID,
                notification,
            ),
        )
    }
}
