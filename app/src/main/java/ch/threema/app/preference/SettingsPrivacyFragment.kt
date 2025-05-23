/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.preference

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.TwoStatePreference
import androidx.work.WorkManager
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.BlockedIdentitiesActivity
import ch.threema.app.activities.ExcludedSyncIdentitiesActivity
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.exceptions.FileSystemNotPresentException
import ch.threema.app.listeners.SynchronizeContactsListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.routines.SynchronizeContactsRoutine
import ch.threema.app.services.SynchronizeContactsService
import ch.threema.app.utils.*
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.localcrypto.MasterKeyLockedException
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar

private val logger = LoggingUtil.getThreemaLogger("SettingsPrivacyFragment")

class SettingsPrivacyFragment :
    ThreemaPreferenceFragment(),
    GenericAlertDialog.DialogClickListener {
    init {
        logScreenVisibility(logger)
    }

    private val serviceManager by lazy { ThreemaApplication.requireServiceManager() }
    private val preferenceService by lazy { serviceManager.preferenceService }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }

    private lateinit var disableScreenshot: CheckBoxPreference
    private var disableScreenshotChecked = false

    private lateinit var fragmentView: View

    private lateinit var contactSyncPreference: TwoStatePreference

    private val synchronizeContactsService: SynchronizeContactsService? =
        getOrNull { requireSynchronizeContactsService() }

    private val synchronizeContactsListener: SynchronizeContactsListener =
        object : SynchronizeContactsListener {
            override fun onStarted(startedRoutine: SynchronizeContactsRoutine) {
                RuntimeUtil.runOnUiThread {
                    updateView()
                    GenericProgressDialog.newInstance(
                        R.string.wizard1_sync_contacts,
                        R.string.please_wait,
                    ).show(parentFragmentManager, DIALOG_TAG_SYNC_CONTACTS)
                }
            }

            override fun onFinished(finishedRoutine: SynchronizeContactsRoutine) {
                RuntimeUtil.runOnUiThread {
                    updateView()
                    if (this@SettingsPrivacyFragment.isAdded) {
                        DialogUtil.dismissDialog(
                            parentFragmentManager,
                            DIALOG_TAG_SYNC_CONTACTS,
                            true,
                        )
                    }
                }
            }

            override fun onError(finishedRoutine: SynchronizeContactsRoutine) {
                RuntimeUtil.runOnUiThread {
                    updateView()
                    if (this@SettingsPrivacyFragment.isAdded) {
                        DialogUtil.dismissDialog(
                            parentFragmentManager,
                            DIALOG_TAG_SYNC_CONTACTS,
                            true,
                        )
                    }
                }
            }
        }

    override fun initializePreferences() {
        super.initializePreferences()

        disableScreenshot = getPref(R.string.preferences__hide_screenshots)
        disableScreenshotChecked = this.disableScreenshot.isChecked

        if (ConfigUtils.getScreenshotsDisabled(preferenceService, serviceManager.lockAppService)) {
            disableScreenshot.isEnabled = false
            disableScreenshot.isSelectable = false
        }

        initContactSyncPref()

        initWorkRestrictedPrefs()

        initExcludedSyncIdentitiesPref()

        initBlockedContactsPref()

        initResetReceiptsPref()

        initDirectSharePref()

        updateView()

        if (multiDeviceManager.isMultiDeviceActive) {
            subscribeToMdRelevantSettings()
        }
    }

    override fun getPreferenceTitleResource(): Int = R.string.prefs_privacy

    override fun getPreferenceResource(): Int = R.xml.preference_privacy

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ListenerManager.synchronizeContactsListeners.add(synchronizeContactsListener)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fragmentView = view
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        ListenerManager.synchronizeContactsListeners.remove(synchronizeContactsListener)
        if (isAdded) {
            DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_VALIDATE, true)
            DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_SYNC_CONTACTS, true)
            DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_DISABLE_SYNC, true)
        }
        super.onDestroyView()
    }

    override fun onDetach() {
        super.onDetach()
        if (disableScreenshot.isChecked != disableScreenshotChecked) {
            ConfigUtils.recreateActivity(activity)
        }
    }

    private fun updateView() {
        if (!AppRestrictionUtil.hasBooleanRestriction(getString(R.string.restriction__contact_sync)) && !SynchronizeContactsUtil.isRestrictedProfile(
                activity,
            )
        ) {
            contactSyncPreference.apply {
                isEnabled = synchronizeContactsService?.isSynchronizationInProgress != true
            }
        }
    }

    private fun initContactSyncPref() {
        contactSyncPreference = getPref(resources.getString(R.string.preferences__sync_contacts))
        contactSyncPreference.summaryOn =
            getString(R.string.prefs_sum_sync_contacts_on, getString(R.string.app_name))
        contactSyncPreference.summaryOff =
            getString(R.string.prefs_sum_sync_contacts_off, getString(R.string.app_name))

        if (SynchronizeContactsUtil.isRestrictedProfile(activity)) {
            // restricted android profile (e.g. guest user)
            contactSyncPreference.isChecked = false
            contactSyncPreference.isEnabled = false
            contactSyncPreference.isSelectable = false
        } else {
            contactSyncPreference.onChange<Boolean> { enabled ->
                preferenceService.emailSyncHashCode = 0
                preferenceService.phoneNumberSyncHashCode = 0
                preferenceService.timeOfLastContactSync = 0L

                if (enabled) {
                    enableSync()
                } else {
                    disableSync()
                }
            }
        }
    }

    private fun initWorkRestrictedPrefs() {
        if (ConfigUtils.isWorkRestricted()) {
            var value =
                AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__block_unknown))
            if (value != null) {
                val blockUnknown: CheckBoxPreference = getPref(R.string.preferences__block_unknown)
                blockUnknown.isEnabled = false
                blockUnknown.isSelectable = false
            }
            value =
                AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_screenshots))
            if (value != null) {
                disableScreenshot.isEnabled = false
                disableScreenshot.isSelectable = false
            }
            value =
                AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__contact_sync))
            if (value != null) {
                contactSyncPreference.isEnabled = false
                contactSyncPreference.isSelectable = false
                contactSyncPreference.isChecked = value
            }
        }
    }

    private fun initExcludedSyncIdentitiesPref() {
        getPref<Preference>("pref_excluded_sync_identities").onClick {
            startActivity(Intent(activity, ExcludedSyncIdentitiesActivity::class.java))
        }
    }

    private fun initBlockedContactsPref() {
        getPref<Preference>("pref_blocked_contacts").onClick {
            startActivity(Intent(activity, BlockedIdentitiesActivity::class.java))
        }
    }

    private fun initResetReceiptsPref() {
        getPref<Preference>("pref_reset_receipts").onClick {
            val dialog = GenericAlertDialog.newInstance(
                R.string.prefs_title_reset_receipts,
                // TODO(ANDR-3686)
                getString(R.string.prefs_sum_reset_receipts) + "?",
                R.string.yes,
                R.string.no,
            )
            dialog.targetFragment = this@SettingsPrivacyFragment
            dialog.show(parentFragmentManager, DIALOG_TAG_RESET_RECEIPTS)
        }
    }

    private fun initDirectSharePref() {
        getPrefOrNull<TwoStatePreference>(R.string.preferences__direct_share)?.onChange<Boolean> { enabled ->
            if (enabled) {
                ThreemaApplication.scheduleShareTargetShortcutUpdate()
            } else {
                WorkManager.getInstance(requireContext())
                    .cancelUniqueWork(ThreemaApplication.WORKER_SHARE_TARGET_UPDATE)
                ShortcutUtil.deleteAllShareTargetShortcuts()
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val preferenceCategory = getPref<PreferenceCategory>("pref_key_other")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                preferenceCategory.removePreference(getPref(resources.getString(R.string.preferences__direct_share)))
            }
            preferenceCategory.removePreference(getPref(resources.getString(R.string.preferences__disable_smart_replies)))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray,
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_CONTACTS -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchContactsSync()
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                disableSync()
                ConfigUtils.showPermissionRationale(
                    context,
                    fragmentView,
                    R.string.permission_contacts_sync_required,
                    object : BaseCallback<Snackbar?>() {},
                )
            } else {
                disableSync()
            }
        }
    }

    private fun launchContactsSync() {
        // start a Sync
        synchronizeContactsService?.instantiateSynchronizationAndRun()
    }

    private fun enableSync() {
        getOrNull { requireSynchronizeContactsService() }?.apply {
            try {
                if (enableSync() && ConfigUtils.requestContactPermissions(
                        requireActivity(),
                        this@SettingsPrivacyFragment,
                        PERMISSION_REQUEST_CONTACTS,
                    )
                ) {
                    launchContactsSync()
                }
            } catch (e: MasterKeyLockedException) {
                logger.error("Exception", e)
            } catch (e: FileSystemNotPresentException) {
                logger.error("Exception", e)
            }
        }
    }

    private fun disableSync() {
        getOrNull { requireSynchronizeContactsService() }?.apply {
            GenericProgressDialog.newInstance(R.string.app_name, R.string.please_wait)
                .show(parentFragmentManager, DIALOG_TAG_DISABLE_SYNC)
            Thread({
                disableSync {
                    RuntimeUtil.runOnUiThread {
                        DialogUtil.dismissDialog(
                            parentFragmentManager,
                            DIALOG_TAG_DISABLE_SYNC,
                            true,
                        )
                        contactSyncPreference.isChecked = false
                    }
                }
            }, "DisableSync").start()
        }
    }

    private fun resetReceipts() {
        getOrNull { requireContactService() }?.apply {
            Thread({
                resetReceiptsSettings()
                RuntimeUtil.runOnUiThread {
                    Toast.makeText(
                        context,
                        R.string.reset_successful,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }, "ResetReceiptSettings").start()
        }
    }

    private fun subscribeToMdRelevantSettings() {
        getPref<CheckBoxPreference>(R.string.preferences__block_unknown).onChange<Boolean> { enabled ->
            // Note that it is necessary that the preference is already persisted before it is reflected.
            preferenceService.setBlockUnknown(enabled, TriggerSource.LOCAL)
        }

        getPref<CheckBoxPreference>(R.string.preferences__read_receipts).onChange<Boolean> { enabled ->
            // Note that it is necessary that the preference is already persisted before it is reflected.
            preferenceService.setReadReceipts(enabled, TriggerSource.LOCAL)
        }

        getPref<CheckBoxPreference>(R.string.preferences__typing_indicator).onChange<Boolean> { enabled ->
            // Note that it is necessary that the preference is already persisted before it is reflected.
            preferenceService.setTypingIndicator(enabled, TriggerSource.LOCAL)
        }
    }

    companion object {
        private const val DIALOG_TAG_VALIDATE = "vali"
        private const val DIALOG_TAG_SYNC_CONTACTS = "syncC"
        private const val DIALOG_TAG_DISABLE_SYNC = "dissync"
        private const val DIALOG_TAG_RESET_RECEIPTS = "rece"

        private const val PERMISSION_REQUEST_CONTACTS = 1
    }

    override fun onYes(tag: String?, data: Any?) {
        resetReceipts()
    }

    override fun onNo(tag: String?, data: Any?) {}
}
