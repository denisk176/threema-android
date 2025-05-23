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

package ch.threema.app;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.app.ForegroundServiceStartNotAllowedException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteException;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StrictMode;
import android.provider.ContactsContract;
import android.text.format.DateUtils;
import android.widget.Toast;

import com.datatheorem.android.trustkit.TrustKit;
import com.datatheorem.android.trustkit.reporting.BackgroundReporter;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.grouplinks.IncomingGroupJoinRequestListener;
import ch.threema.app.listeners.BallotVoteListener;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.DistributionListListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.listeners.MessageListener;
import ch.threema.app.listeners.ServerMessageListener;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.managers.CoreServiceManagerImpl;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.push.PushService;
import ch.threema.app.receivers.ConnectivityChangeReceiver;
import ch.threema.app.receivers.PinningFailureReportBroadcastReceiver;
import ch.threema.app.receivers.ShortcutAddedReceiver;
import ch.threema.app.restrictions.ApplyAppRestrictionsWorker;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.restrictions.AppRestrictionService;
import ch.threema.app.services.AvatarCacheService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.services.ThreemaPushService;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.services.UpdateSystemServiceImpl;
import ch.threema.app.services.UserService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.app.tasks.MessageQueueMigrationTask;
import ch.threema.app.utils.ApplicationExitInfoUtil;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ConnectionIndicatorUtil;
import ch.threema.app.utils.ConversationNotificationUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.LinuxSecureRandom;
import ch.threema.app.utils.LoggingUEH;
import ch.threema.app.utils.PushUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.StateBitmapUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.WidgetUtil;
import ch.threema.app.utils.WorkManagerUtil;
import ch.threema.app.voip.Config;
import ch.threema.app.voip.listeners.VoipCallEventListener;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.app.webclient.listeners.WebClientServiceListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.services.SessionAndroidService;
import ch.threema.app.webclient.services.SessionWakeUpServiceImpl;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.app.workers.AutoDeleteWorker;
import ch.threema.app.workers.ContactUpdateWorker;
import ch.threema.app.workers.ShareTargetUpdateWorker;
import ch.threema.app.workers.WorkSyncWorker;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceScope;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.data.models.GroupIdentity;
import ch.threema.data.repositories.ModelRepositories;
import ch.threema.domain.models.AppVersion;
import ch.threema.domain.protocol.connection.ConnectionState;
import ch.threema.domain.protocol.connection.ServerConnection;
import ch.threema.domain.stores.DHSessionStoreInterface;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.libthreema.LibthreemaKt;
import ch.threema.libthreema.LogLevel;
import ch.threema.localcrypto.MasterKey;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.logging.LibthreemaLogger;
import ch.threema.logging.backend.DebugLogFileBackend;
import ch.threema.storage.DatabaseNonceStore;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.SQLDHSessionStore;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ServerMessageModel;
import ch.threema.storage.models.WebClientSessionModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.GroupBallotModel;
import ch.threema.storage.models.ballot.IdentityBallotModel;
import ch.threema.storage.models.ballot.LinkBallotModel;
import ch.threema.storage.models.data.status.GroupStatusDataModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;

import static android.app.NotificationManager.ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED;
import static android.app.NotificationManager.EXTRA_BLOCKED_STATE;
import static android.app.NotificationManager.EXTRA_NOTIFICATION_CHANNEL_GROUP_ID;

public class ThreemaApplication extends Application implements DefaultLifecycleObserver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ThreemaApplication");

    public static final String INTENT_DATA_CONTACT = "identity";
    public static final String INTENT_DATA_CONTACT_READONLY = "readonly";
    public static final String INTENT_DATA_TEXT = "text";
    public static final String INTENT_DATA_ID_BACKUP = "idbackup";
    public static final String INTENT_DATA_ID_BACKUP_PW = "idbackuppw";
    public static final String INTENT_DATA_PASSPHRASE_CHECK = "check";
    public static final String INTENT_DATA_IS_FORWARD = "is_forward";
    public static final String INTENT_DATA_TIMESTAMP = "timestamp";
    public static final String INTENT_DATA_EDITFOCUS = "editfocus";
    public static final String INTENT_DATA_GROUP_DATABASE_ID = "group";
    public static final String INTENT_DATA_GROUP_API = "group_api";
    public static final String INTENT_DATA_GROUP_LINK = "group_link";
    public static final String INTENT_DATA_DISTRIBUTION_LIST_ID = "distribution_list";
    public static final String INTENT_DATA_ARCHIVE_FILTER = "archiveFilter";
    public static final String INTENT_DATA_QRCODE = "qrcodestring";
    public static final String INTENT_DATA_QRCODE_TYPE_OK = "qrcodetypeok";
    public static final String INTENT_DATA_MESSAGE_ID = "messageid";
    public static final String INTENT_DATA_INCOMING_GROUP_REQUEST = "groupRequest";
    public static final String INTENT_DATA_GROUP_REQUEST_NOTIFICATION_ID = "groupRequestNotificationId";
    public static final String EXTRA_VOICE_REPLY = "voicereply";
    public static final String EXTRA_OUTPUT_FILE = "output";
    public static final String EXTRA_ORIENTATION = "rotate";
    public static final String EXTRA_FLIP = "flip";
    public static final String INTENT_DATA_CHECK_ONLY = "check";
    public static final String INTENT_DATA_ANIM_CENTER = "itemPos";
    public static final String INTENT_DATA_PICK_FROM_CAMERA = "useCam";
    public static final String INTENT_PUSH_REGISTRATION_COMPLETE = "registrationComplete";
    public static final String INTENT_DATA_PIN = "ppin";
    public static final String INTENT_DATA_HIDE_RECENTS = "hiderec";
    public static final String INTENT_ACTION_FORWARD = "ch.threema.app.intent.FORWARD";
    public static final String INTENT_ACTION_SHORTCUT_ADDED = BuildConfig.APPLICATION_ID + ".intent.SHORTCUT_ADDED";

    public static final String CONFIRM_TAG_CLOSE_BALLOT = "cb";

    // Notification IDs
    public static final int PASSPHRASE_SERVICE_NOTIFICATION_ID = 587;
    public static final int NEW_MESSAGE_NOTIFICATION_ID = 723;
    public static final int MASTER_KEY_LOCKED_NOTIFICATION_ID = 724;
    public static final int NEW_MESSAGE_LOCKED_NOTIFICATION_ID = 725;
    public static final int NEW_MESSAGE_PIN_LOCKED_NOTIFICATION_ID = 726;
    public static final int SAFE_FAILED_NOTIFICATION_ID = 727;
    public static final int SERVER_MESSAGE_NOTIFICATION_ID = 730;
    public static final int UNSENT_MESSAGE_NOTIFICATION_ID = 732;
    public static final int WORK_SYNC_NOTIFICATION_ID = 735;
    public static final int NEW_SYNCED_CONTACTS_NOTIFICATION_ID = 736;
    public static final int WEB_RESUME_FAILED_NOTIFICATION_ID = 737;
    public static final int VOICE_MSG_PLAYER_NOTIFICATION_ID = 749;
    public static final int INCOMING_CALL_NOTIFICATION_ID = 800;
    public static final int INCOMING_GROUP_CALL_NOTIFICATION_ID = 803;

    private static final String THREEMA_APPLICATION_LISTENER_TAG = "al";
    public static final String AES_KEY_FILE = "key.dat";
    public static final String ECHO_USER_IDENTITY = "ECHOECHO";
    public static final String PHONE_LINKED_PLACEHOLDER = "***";
    public static final String EMAIL_LINKED_PLACEHOLDER = "***@***";

    public static final String ACTIVITY_CONNECTION_TAG = "threemaApplication";
    private static final long ACTIVITY_CONNECTION_LIFETIME = 60000;

    public static final int MAX_BLOB_SIZE_MB = 100;
    public static final int MAX_BLOB_SIZE = MAX_BLOB_SIZE_MB * 1024 * 1024;
    public static final int MIN_PIN_LENGTH = 4;
    public static final int MAX_PIN_LENGTH = 8;
    public static final int MIN_GROUP_MEMBERS_COUNT = 1;
    public static final int MIN_PW_LENGTH_BACKUP = 8;
    public static final int MAX_PW_LENGTH_BACKUP = 256;
    public static final int MIN_PW_LENGTH_ID_EXPORT_LEGACY = 4; // extremely ancient versions of the app on some platform accepted four-letter passwords when generating ID exports

    @Deprecated // Use WORKER_CONTACT_UPDATE_PERIODIC_NAME instead.
    public static final String WORKER_IDENTITY_STATES_PERIODIC_NAME = "IdentityStates";
    public static final String WORKER_CONTACT_UPDATE_PERIODIC_NAME = "PeriodicContactUpdate";
    public static final String WORKER_SHARE_TARGET_UPDATE = "ShareTargetUpdate";
    public static final String WORKER_WORK_SYNC = "WorkSync";
    public static final String WORKER_PERIODIC_WORK_SYNC = "PeriodicWorkSync";
    public static final String WORKER_THREEMA_SAFE_UPLOAD = "SafeUpload";
    public static final String WORKER_PERIODIC_THREEMA_SAFE_UPLOAD = "PeriodicSafeUpload";
    public static final String WORKER_CONNECTIVITY_CHANGE = "ConnectivityChange";
    public static final String WORKER_AUTO_DELETE = "AutoDelete";
    public static final String WORKER_AUTOSTART = "Autostart";

    public static final Lock onAndroidContactChangeLock = new ReentrantLock();

    private static final String EXIT_REASON_LOGGING_TIMESTAMP = "exit_reason_timestamp";

    private static Context context;

    private static volatile ServiceManager serviceManager;
    private static volatile AppVersion appVersion;
    private static volatile MasterKey masterKey;

    private static Date lastLoggedIn;
    private static boolean isDeviceIdle;
    public static boolean isResumed = false;

    private static HashMap<String, String> messageDrafts = new HashMap<>();
    private static HashMap<String, String> quoteDrafts = new HashMap<>();

    public static final ExecutorService sendMessageExecutorService = Executors.newFixedThreadPool(4);
    public static final ExecutorService sendMessageSingleThreadExecutorService = Executors.newSingleThreadExecutor();
    public static final ExecutorService voiceMessageThumbnailExecutorService = Executors.newFixedThreadPool(4);

    private static boolean checkAppReplacingState(Context context) {
        // workaround https://code.google.com/p/android/issues/detail?id=56296
        if (context.getResources() == null) {
            logger.debug("App is currently installing. Killing it.");
            android.os.Process.killProcess(android.os.Process.myPid());

            return false;
        }

        return true;
    }

    private void logStackTrace(StackTraceElement[] stackTraceElements) {
        for (int i = 1; i < stackTraceElements.length; i++) {
            logger.info("\tat " + stackTraceElements[i]);
        }
    }

    private static void showNotesGroupNotice(GroupModel groupModel, @GroupService.GroupState int oldState, @GroupService.GroupState int newState) {
        if (oldState != newState) {
            try {
                GroupService groupService = serviceManager.getGroupService();
                MessageService messageService = serviceManager.getMessageService();
                GroupStatusDataModel.GroupStatusType type = null;

                if (newState == GroupService.NOTES) {
                    type = GroupStatusDataModel.GroupStatusType.IS_NOTES_GROUP;
                } else if (newState == GroupService.PEOPLE && oldState != GroupService.UNDEFINED) {
                    type = GroupStatusDataModel.GroupStatusType.IS_PEOPLE_GROUP;
                }

                if (type != null) {
                    messageService.createGroupStatus(
                        groupService.createReceiver(groupModel),
                        type,
                        null,
                        null,
                        null
                    );
                }
            } catch (ThreemaException e) {
                logger.error("Exception", e);
            }
        }
    }

    @Override
    public void onCreate() {
        long startupTime = System.currentTimeMillis();

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyListener(Executors.newSingleThreadExecutor(), v -> {
                    logger.info("STRICTMODE VMPolicy: " + v.getCause());
                    logStackTrace(v.getStackTrace());
                })
                .build());
/*
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
				.detectAll()   // or .detectAll() for all detectable problems
//				.penaltyFlashScreen()
				.penaltyListener(Executors.newSingleThreadExecutor(), v -> {
					logger.info("STRICTMODE ThreadPolicy: " + v.getCause());
					logStackTrace(v.getStackTrace());
				})
				.build());
*/
        }

        super.onCreate();

        applyDynamicColorsIfEnabled();

        // always log database migration
        setupLogging(null);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        context = getApplicationContext();

        LibthreemaKt.init(LogLevel.TRACE, new LibthreemaLogger());

        if (!checkAppReplacingState(context)) {
            return;
        }

        // Initialize TrustKit for CA pinning
        TrustKit.initializeWithNetworkSecurityConfiguration(this);

        // Set unhandled exception logger
        Thread.setDefaultUncaughtExceptionHandler(new LoggingUEH());

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        // Instantiate our own SecureRandom implementation to make sure this gets used everywhere
        new LinuxSecureRandom();

        // Prepare app version object
        appVersion = new AppVersion(
            ConfigUtils.getAppVersion(),
            "A",
            Locale.getDefault().getLanguage(),
            Locale.getDefault().getCountry(),
            Build.MODEL,
            Build.VERSION.RELEASE
        );

        // Create master key
        File filesDir = getAppContext().getFilesDir();
        if (filesDir != null) {
            filesDir.mkdirs();

            if (filesDir.exists() && filesDir.isDirectory()) {
                File masterKeyFile = new File(filesDir, AES_KEY_FILE);

                try {
                    boolean reset = !masterKeyFile.exists();

                    if (reset) {
                        /*
                         *
                         * IMPORTANT
                         *
                         * If the MasterKey file does not exists, remove every file that is encrypted with this
                         * non-existing MasterKey file
                         *
                         * 1. Database
                         * 2. Settings
                         * 3. Message Queue
                         *
                         * TODO(ANDR-XXXX): move this into a separate method/file
                         *
                         */
                        //remove database, its encrypted with the wrong master key

                        logger.info("master key is missing or does not match. rename database files.");

                        File databaseFile = getAppContext().getDatabasePath(DatabaseServiceNew.DEFAULT_DATABASE_NAME_V4);
                        if (databaseFile.exists()) {
                            File databaseBackup = new File(databaseFile.getPath() + ".backup");
                            if (!databaseFile.renameTo(databaseBackup)) {
                                FileUtil.deleteFileOrWarn(databaseFile, "threema4 database", logger);
                            }
                        }

                        databaseFile = getAppContext().getDatabasePath(DatabaseNonceStore.DATABASE_NAME_V4);
                        if (databaseFile.exists()) {
                            FileUtil.deleteFileOrWarn(databaseFile, "nonce4 database", logger);
                        }

                        databaseFile = getAppContext().getDatabasePath(SQLDHSessionStore.DATABASE_NAME);
                        if (databaseFile.exists()) {
                            FileUtil.deleteFileOrWarn(databaseFile, "sql dh session database", logger);
                        }

                        //remove all settings!
                        logger.info("initialize: remove preferences");
                        PreferenceStore preferenceStore = new PreferenceStore(getAppContext(), masterKey);
                        preferenceStore.clear();
                    } else {
                        logger.info("OK, masterKeyFile exists");
                    }

                    masterKey = new MasterKey(masterKeyFile, null, true);

                    if (!masterKey.isLocked()) {
                        reset();
                    } else {
                        setupDayNightMode();
                    }
                } catch (IOException e) {
                    logger.error("IOException", e);
                }

                // Register "Connectivity Action" broadcast receiver.
                // This is called when a change in network connectivity has occurred.
                // Note: This is deprecated on API 28+!
                getAppContext().registerReceiver(
                    new ConnectivityChangeReceiver(),
                    new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Register "Device Idle Mode Changed" broadcast receiver.
                    // This is called when the state of isDeviceIdleMode() changes. This broadcast
                    // is only sent to registered receivers.
                    getAppContext().registerReceiver(new BroadcastReceiver() {
                        @TargetApi(Build.VERSION_CODES.M)
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            final PowerManager powerManager = (PowerManager) context
                                .getApplicationContext()
                                .getSystemService(Context.POWER_SERVICE);
                            if (powerManager != null && powerManager.isDeviceIdleMode()) {
                                logger.info("*** Device going to deep sleep");

                                isDeviceIdle = true;

                                try {
                                    // Pause connection
                                    serviceManager.getLifetimeService().pause();
                                } catch (Exception e) {
                                    logger.error("Exception while pausing connection", e);
                                }

                                if (BackupService.isRunning()) {
                                    context.stopService(new Intent(context, BackupService.class));
                                }
                            } else {
                                logger.info("*** Device waking up");
                                if (serviceManager != null) {
                                    new Thread(() -> {
                                        try {
                                            serviceManager.getLifetimeService().unpause();
                                        } catch (Exception e) {
                                            logger.error("Exception while unpausing connection", e);
                                        }
                                    }, "device_wakup").start();
                                    isDeviceIdle = false;
                                } else {
                                    logger.info("Service manager unavailable");
                                    if (masterKey != null && !masterKey.isLocked()) {
                                        reset();
                                    }
                                }
                            }
                        }
                    }, new IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Register "Notification Channel Group Block State Changed" broadcast receiver.
                    // This is called when a NotificationChannelGroup is blocked or unblocked.
                    // This broadcast is only sent to the app that owns the channel group that has changed.
                    getAppContext().registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                boolean blockedState = intent.getBooleanExtra(EXTRA_BLOCKED_STATE, false);
                                String groupName = intent.getStringExtra(EXTRA_NOTIFICATION_CHANNEL_GROUP_ID);
                                logger.info(
                                    "*** Channel group {} blocked: {}",
                                    groupName != null ? groupName : "<not specified>",
                                    blockedState
                                );
                            } catch (Exception e) {
                                logger.error("Could not get data from intent", e);
                            }
                        }
                    }, new IntentFilter(ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED));
                }

                // Add a local broadcast receiver to receive PinningFailureReports
                PinningFailureReportBroadcastReceiver receiver = new PinningFailureReportBroadcastReceiver();
                LocalBroadcastManager.getInstance(context).registerReceiver(receiver, new IntentFilter(BackgroundReporter.REPORT_VALIDATION_EVENT));

                // Register a broadcast receiver for changes in app restrictions
                if (ConfigUtils.isWorkRestricted()) {
                    getAppContext().registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            logger.info("Restrictions have changed. Updating restrictions");

                            AppRestrictionService.getInstance().reload();
                        }
                    }, new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED));
                }

                // register a receiver for shortcuts that have been added to the launcher
                ContextCompat.registerReceiver(this, new ShortcutAddedReceiver(), new IntentFilter(INTENT_ACTION_SHORTCUT_ADDED), ContextCompat.RECEIVER_NOT_EXPORTED);

                // Start the Threema Push Service (if enabled in config)
                ThreemaPushService.tryStart(logger, getAppContext());
            }
        }

        logger.info("Startup time {}s", (System.currentTimeMillis() - startupTime) / DateUtils.SECOND_IN_MILLIS);
    }

    private void applyDynamicColorsIfEnabled() {
        if (DynamicColors.isDynamicColorAvailable()) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if (sharedPreferences != null && sharedPreferences.getBoolean("pref_dynamic_color", false)) {
                DynamicColorsOptions dynamicColorsOptions = new DynamicColorsOptions.Builder().setPrecondition((activity, theme) -> {
                    SharedPreferences sharedPreferences1 = PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext());
                    return sharedPreferences1 != null && sharedPreferences1.getBoolean("pref_dynamic_color", false);
                }).build();

                DynamicColors.applyToActivitiesIfAvailable(this, dynamicColorsOptions);
            }
        }
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        logger.info("*** Lifecycle: App now visible");
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        logger.info("*** Lifecycle: App now stopped");
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        logger.info("*** Lifecycle: App now created");
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        logger.info("*** Lifecycle: App now resumed");
        isResumed = true;

        if (serviceManager != null) {
            serviceManager.getLifetimeService().acquireConnection(ACTIVITY_CONNECTION_TAG);
            logger.info("Connection now acquired");

            // Check app restrictions when the app resumes
            if (ConfigUtils.isWorkBuild()) {
                // TODO(ANDR-3790): This method does not need to be run on the UI thread
                AppRestrictionService.getInstance().reload();
            }
        } else {
            logger.info("Service manager is null");
        }
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        logger.info("*** Lifecycle: App now paused");
        isResumed = false;

        if (serviceManager != null) {
            serviceManager.getLifetimeService().releaseConnectionLinger(ACTIVITY_CONNECTION_TAG, ACTIVITY_CONNECTION_LIFETIME);
        }
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        logger.info("*** Lifecycle: App now destroyed");
    }

    @Override
    public void onLowMemory() {
        logger.info("*** App is low on memory");

        super.onLowMemory();
        try {
            if (serviceManager != null) {
                serviceManager.getAvatarCacheService().clear();
            }
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    @SuppressLint("SwitchIntDef")
    @Override
    public void onTrimMemory(int level) {
        logger.info("onTrimMemory (level={})", level);

        super.onTrimMemory(level);

        /* save our master key now if necessary, as we may get killed and if the user was still in the
         * initial setup procedure, this can lead to trouble as the database may already be there
         * but we may no longer be able to access it due to missing master key
         */
        try {
            if (getMasterKey() != null && !getMasterKey().isProtected()) {
                if (serviceManager != null && serviceManager.getNotificationPreferenceService().getWizardRunning()) {
                    getMasterKey().setPassphrase(null);
                }
            }
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    @Nullable
    public static ServiceManager getServiceManager() {
        return serviceManager;
    }

    @NonNull
    public static ServiceManager requireServiceManager() throws NullPointerException {
        return Objects.requireNonNull(serviceManager);
    }

    public static MasterKey getMasterKey() {
        return masterKey;
    }

    public static void putMessageDraft(String chatId, CharSequence value, @Nullable AbstractMessageModel quotedMessageModel) {
        if (value == null || value.toString().trim().length() < 1) {
            messageDrafts.remove(chatId);
            quoteDrafts.remove(chatId);
        } else {
            messageDrafts.put(chatId, value.toString());
            if (quotedMessageModel != null) {
                quoteDrafts.put(chatId, quotedMessageModel.getApiMessageId());
            } else {
                quoteDrafts.remove(chatId);
            }
        }
        try {
            PreferenceService preferenceService = requireServiceManager().getPreferenceService();
            preferenceService.setMessageDrafts(messageDrafts);
            preferenceService.setQuoteDrafts(quoteDrafts);
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    public static String getMessageDraft(String chatId) {
        if (messageDrafts.containsKey(chatId)) {
            return messageDrafts.get(chatId);
        }
        return null;
    }

    public static String getQuoteDraft(String chatId) {
        if (quoteDrafts.containsKey(chatId)) {
            return quoteDrafts.get(chatId);
        }
        return null;
    }

    private static void retrieveMessageDraftsFromStorage() {
        try {
            messageDrafts = getServiceManager().getPreferenceService().getMessageDrafts();
            quoteDrafts = getServiceManager().getPreferenceService().getQuoteDrafts();
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    @SuppressLint("ApplySharedPref")
    private static void resetPreferences(SharedPreferences prefs) {
        // Fix master key preference state if necessary (could be wrong if user kills app
        // while disabling master key passphrase).
        if (masterKey.isProtected() && prefs != null && !prefs.getBoolean(getAppContext().getString(R.string.preferences__masterkey_switch), false)) {
            logger.debug("Master key is protected, but switch preference is disabled - fixing");
            prefs.edit().putBoolean(getAppContext().getString(R.string.preferences__masterkey_switch), true).commit();
        }

        // If device is in AEC exclusion list and the user did not choose a preference yet,
        // update the shared preference.
        if (prefs != null && prefs.getString(getAppContext().getString(R.string.preferences__voip_echocancel), "none").equals("none")) {
            // Determine whether device is excluded from hardware AEC
            final String modelInfo = Build.MANUFACTURER + ";" + Build.MODEL;
            boolean exclude = !Config.allowHardwareAec();

            // Set default preference
            final SharedPreferences.Editor editor = prefs.edit();
            if (exclude) {
                logger.debug("Device {} is on AEC exclusion list, switching to software echo cancellation", modelInfo);
                editor.putString(getAppContext().getString(R.string.preferences__voip_echocancel), "sw");
            } else {
                logger.debug("Device {} is not on AEC exclusion list", modelInfo);
                editor.putString(getAppContext().getString(R.string.preferences__voip_echocancel), "hw");
            }
            editor.commit();
        }

        try {
            PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_chat, true);
            PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_privacy, true);
            PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_appearance, true);
            PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_notifications, true);
            PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_media, true);
            PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_calls, true);
            PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_advanced_options, true);
        } catch (Exception e) {
            logger.error("Exception", e);
        }

        setupDayNightMode();
    }

    /**
     * Setup day / night theme for application depending on preferences
     */
    private static void setupDayNightMode() {
        AppCompatDelegate.setDefaultNightMode(ConfigUtils.getAppThemePrefs());
    }

    private static void setupLogging(PreferenceStore preferenceStore) {
        // check if a THREEMA_MESSAGE_LOG exist on the
        final File forceMessageLog = new File(Environment.getExternalStorageDirectory() + "/ENABLE_THREEMA_MESSAGE_LOG");
        final File forceDebugLog = new File(Environment.getExternalStorageDirectory() + "/ENABLE_THREEMA_DEBUG_LOG");

        // enable message logging if necessary
        if (preferenceStore == null || preferenceStore.getBoolean(getAppContext().getString(R.string.preferences__message_log_switch))
            || forceMessageLog.exists() || forceDebugLog.exists()) {
            DebugLogFileBackend.setEnabled(true);
        } else {
            DebugLogFileBackend.setEnabled(false);
        }

        // temporary - testing native crash in CompletableFuture while loading emojis
        if (preferenceStore != null) {
            final File forceAndroidEmojis = new File(Environment.getExternalStorageDirectory() + "/FORCE_SYSTEM_EMOJIS");
            if (forceAndroidEmojis.exists()) {
                preferenceStore.save(getAppContext().getString(R.string.preferences__emoji_style), "1");
            }
        }
    }

    public static synchronized void reset() {

        //set default preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getAppContext());
        resetPreferences(sharedPreferences);

        // init state bitmap cache singleton
        StateBitmapUtil.init(getAppContext());

        // init connection state colors
        ConnectionIndicatorUtil.init(getAppContext());

        try {
            // Load preference store
            PreferenceStore preferenceStore = new PreferenceStore(getAppContext(), masterKey);

            // Set logging to "always on"
            setupLogging(null);

            // Make database key from master key
            String databaseKey = "x\"" + Utils.byteArrayToHexString(masterKey.getKey()) + "\"";

            UpdateSystemService updateSystemService = new UpdateSystemServiceImpl();

            // Instantiate database service
            System.loadLibrary("sqlcipher");
            DatabaseServiceNew databaseServiceNew = new DatabaseServiceNew(getAppContext(), databaseKey, updateSystemService);
            databaseServiceNew.executeNull();

            // We create the DH session store here and execute a null operation on it to prevent
            // the app from being launched when the database is downgraded.
            DHSessionStoreInterface dhSessionStore = new SQLDHSessionStore(context, masterKey.getKey(), updateSystemService);
            try {
                dhSessionStore.executeNull();
            } catch (Exception e) {
                logger.error("Could not execute a statement on the database", e);
                // The database file seems to be corrupt, therefore we delete the file
                File databaseFile = getAppContext().getDatabasePath(SQLDHSessionStore.DATABASE_NAME);
                if (databaseFile.exists()) {
                    FileUtil.deleteFileOrWarn(databaseFile, "sql dh session database", logger);
                }
                dhSessionStore = new SQLDHSessionStore(context, masterKey.getKey(), updateSystemService);
            }

            IdentityStore identityStore = new IdentityStore(preferenceStore);

            // Instantiate core service manager. Note that the task manager should only be used to
            // schedule tasks once the service manager is set.
            final CoreServiceManagerImpl coreServiceManager = new CoreServiceManagerImpl(
                appVersion,
                databaseServiceNew,
                preferenceStore,
                identityStore,
                () -> {
                    DatabaseNonceStore databaseNonceStore = new DatabaseNonceStore(getAppContext(), identityStore);
                    databaseNonceStore.executeNull();
                    logger.info("Nonce count (csp): {}", databaseNonceStore.getCount(NonceScope.CSP));
                    logger.info("Nonce count (d2d): {}", databaseNonceStore.getCount(NonceScope.D2D));
                    return databaseNonceStore;
                }
            );

            // Instantiate model repositories
            final ModelRepositories modelRepositories = new ModelRepositories(coreServiceManager);

            logger.info("*** App launched");
            logVersion();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ActivityManager activityManager = (ActivityManager) getAppContext().getSystemService(Context.ACTIVITY_SERVICE);
                try {
                    List<ApplicationExitInfo> applicationExitInfos = activityManager.getHistoricalProcessExitReasons(null, 0, 0);

                    if (applicationExitInfos.size() > 0) {
                        for (ApplicationExitInfo exitInfo : applicationExitInfos) {
                            long timestampOfLastLog = 0L;
                            if (sharedPreferences != null) {
                                timestampOfLastLog = sharedPreferences.getLong(EXIT_REASON_LOGGING_TIMESTAMP, timestampOfLastLog);
                            }

                            if (exitInfo.getTimestamp() > timestampOfLastLog) {
                                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
                                logger.info(
                                    "*** App last exited at {} with reason: {}, description: {}, status: {}",
                                    simpleDateFormat.format(exitInfo.getTimestamp()),
                                    ApplicationExitInfoUtil.getReasonText(exitInfo),
                                    exitInfo.getDescription(),
                                    ApplicationExitInfoUtil.getStatusText(exitInfo)
                                );
                                if (exitInfo.getReason() == ApplicationExitInfo.REASON_ANR) {
                                    try {
                                        InputStream traceInputStream = exitInfo.getTraceInputStream();
                                        if (traceInputStream != null) {
                                            BufferedReader r = new BufferedReader(new InputStreamReader(traceInputStream));
                                            StringBuilder s = new StringBuilder();
                                            for (String line; (line = r.readLine()) != null; ) {
                                                s.append(line).append('\n');
                                            }
                                            logger.info(s.toString());
                                        }
                                    } catch (IOException e) {
                                        logger.error("Error getting ANR trace", e);
                                    }
                                }
                            }
                        }

                        if (sharedPreferences != null) {
                            sharedPreferences.edit().putLong(EXIT_REASON_LOGGING_TIMESTAMP, System.currentTimeMillis()).apply();
                        }
                    }
                } catch (IllegalArgumentException e) {
                    logger.error("Unable to get reason of last exit", e);
                }
            }

            // Set up logging
            setupLogging(preferenceStore);

            try {
                // Instantiate service manager
                serviceManager = new ServiceManager(
                    modelRepositories,
                    dhSessionStore,
                    masterKey,
                    coreServiceManager,
                    updateSystemService
                );
            } catch (ThreemaException e) {
                logger.error("Could not instantiate service manager", e);
                return;
            }

            serviceManager.getTaskManager().schedule(new MessageQueueMigrationTask(
                    context,
                    identityStore.getIdentity(),
                    serviceManager.getMessageService(),
                    serviceManager.getGroupService(),
                    serviceManager.getDatabaseServiceNew().getMessageModelFactory(),
                    serviceManager.getDatabaseServiceNew().getGroupMessageModelFactory()
                )
            );

            ServerConnection connection = serviceManager.getConnection();

            // Whenever the connection is established, check whether the
            // push token needs to be updated.
            connection.addConnectionStateListener((newConnectionState) -> {
                if (newConnectionState == ConnectionState.LOGGEDIN) {
                    final Context appContext = getAppContext();
                    if (PushService.servicesInstalled(appContext)) {
                        if (PushUtil.isPushEnabled(appContext)) {
                            if (PushUtil.pushTokenNeedsRefresh(appContext)) {
                                PushUtil.enqueuePushTokenUpdate(appContext, false, false);
                            } else {
                                logger.debug("Push token is still fresh. No update needed");
                            }
                        }
                    }
                }
            });

            // get application restrictions
            if (ConfigUtils.isWorkBuild()) {
                // TODO(ANDR-3790): This method does not need to be run on the UI thread
                AppRestrictionService.getInstance()
                    .reload();
            }

            connection.addConnectionStateListener(connectionState -> {
                logger.info("ServerConnection state changed: {}", connectionState);

                if (connectionState == ConnectionState.LOGGEDIN) {
                    lastLoggedIn = new Date();
                }
            });

            /* cancel any "new message" notification */
            NotificationManagerCompat.from(getAppContext()).cancel(NEW_MESSAGE_LOCKED_NOTIFICATION_ID);

			/* trigger a connection now, just to be sure we're up-to-date and any broken connection
			   (e.g. from before a reboot) is preempted.
			 */
            serviceManager.getLifetimeService().acquireConnection("resetConnection");
            serviceManager.getLifetimeService().releaseConnectionLinger("resetConnection", ACTIVITY_CONNECTION_LIFETIME);
            configureListeners();

            // Mark all file messages with state 'uploading' as failed. This is because the file
            // upload is not continued after app restarts. When the state has been changed to
            // failed, a resend button is displayed on the message. We only need to do this in the
            // uploading state as in sending state a persistent task is already scheduled and the
            // message will be sent when a connection is available.
            databaseServiceNew
                .getMessageModelFactory()
                .markUnscheduledFileMessagesAsFailed();

            databaseServiceNew
                .getGroupMessageModelFactory()
                .markUnscheduledFileMessagesAsFailed();

            databaseServiceNew
                .getDistributionListMessageModelFactory()
                .markUnscheduledFileMessagesAsFailed();

            retrieveMessageDraftsFromStorage();

            // process webclient wakeups
            SessionWakeUpServiceImpl.getInstance().processPendingWakeupsAsync();

            // start threema safe scheduler
            serviceManager.getThreemaSafeService().schedulePeriodicUpload();

            PreferenceService preferenceService = serviceManager.getPreferenceService();

            new Thread(() -> {
                // schedule work synchronization
                WorkSyncWorker.Companion.schedulePeriodicWorkSync(getAppContext(), preferenceService);
                // schedule identity states / feature masks etc.
                ContactUpdateWorker.schedulePeriodicSync(getAppContext(), preferenceService);
                // schedule shortcut update
                if (preferenceStore.getBoolean(getAppContext().getString(R.string.preferences__direct_share))) {
                    scheduleShareTargetShortcutUpdate();
                }
                // schedule auto delete
                AutoDeleteWorker.Companion.scheduleAutoDelete(getAppContext());
            }, "scheduleSync").start();
        } catch (MasterKeyLockedException | SQLiteException e) {
            logger.error("Exception opening database", e);
        } catch (ThreemaException e) {
            // no identity
            logger.info("No valid identity.");
        }
    }

    public static void logVersion() {
        String commitHash = BuildConfig.DEBUG
            ? ", Commit: " + BuildConfig.GIT_HASH
            : "";
        logger.info(
            "*** App Version. Device/Android Version/Flavor: {} Version: {} Build: {}{}",
            ConfigUtils.getDeviceInfo(false),
            BuildConfig.VERSION_NAME,
            ConfigUtils.getBuildNumber(getAppContext()),
            commitHash
        );
    }

    @WorkerThread
    public static boolean scheduleShareTargetShortcutUpdate() {
        logger.info("Scheduling share target shortcut update work");

        long schedulePeriod = DateUtils.MINUTE_IN_MILLIS * 15;

        try {
            WorkManager workManager = WorkManager.getInstance(context);

            if (WorkManagerUtil.shouldScheduleNewWorkManagerInstance(
                workManager,
                WORKER_SHARE_TARGET_UPDATE,
                schedulePeriod
            )) {
                logger.debug("Create new worker");

                // schedule the start of the service according to schedule period
                Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

                PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(ShareTargetUpdateWorker.class, schedulePeriod, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .addTag(String.valueOf(schedulePeriod))
                    .setInitialDelay(3, TimeUnit.MINUTES)
                    .build();

                workManager.enqueueUniquePeriodicWork(WORKER_SHARE_TARGET_UPDATE, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, workRequest);
            } else {
                logger.debug("Reusing existing worker");
            }
        } catch (IllegalStateException e) {
            logger.error("Unable to schedule share target update work", e);
            return false;
        }

        return true;
    }

    private static void showConversationNotification(AbstractMessageModel newMessage, boolean updateExisting) {
        try {
            if (!newMessage.isOutbox()
                && !newMessage.isStatusMessage()
                && !newMessage.isRead()) {

                NotificationService notificationService = serviceManager.getNotificationService();
                ContactService contactService = serviceManager.getContactService();
                GroupService groupService = serviceManager.getGroupService();
                ConversationCategoryService conversationCategoryService = serviceManager.getConversationCategoryService();

                if (TestUtil.required(notificationService, contactService, groupService)) {
                    if (newMessage.getType() != MessageType.GROUP_CALL_STATUS) {
                        notificationService.showConversationNotification(ConversationNotificationUtil.convert(
                                getAppContext(),
                                newMessage,
                                contactService,
                                groupService,
                                conversationCategoryService),
                            updateExisting);
                    }

                    // update widget on incoming message
                    WidgetUtil.updateWidgets(serviceManager.getContext());
                }
            }
        } catch (ThreemaException e) {
            logger.error("Exception", e);
        }
    }

    private static void configureListeners() {
        ListenerManager.groupListeners.add(new GroupListener() {
            @Override
            public void onCreate(@NonNull GroupIdentity groupIdentity) {
                try {
                    GroupModel groupModel = getGroupModel(groupIdentity);
                    if (groupModel == null) {
                        return;
                    }
                    serviceManager.getConversationService().refresh(groupModel);
                    serviceManager.getMessageService().createGroupStatus(
                        serviceManager.getGroupService().createReceiver(groupModel),
                        GroupStatusDataModel.GroupStatusType.CREATED,
                        null,
                        null,
                        null
                    );
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            }

            @Override
            public void onRename(@NonNull GroupIdentity groupIdentity) {
                new Thread(() -> {
                    try {
                        GroupModel groupModel = getGroupModel(groupIdentity);
                        if (groupModel == null) {
                            return;
                        }
                        GroupMessageReceiver messageReceiver = serviceManager.getGroupService().createReceiver(groupModel);
                        serviceManager.getConversationService().refresh(groupModel);
                        String groupName = groupModel.getName();
                        if (groupName == null) {
                            groupName = "";
                        }
                        serviceManager.getMessageService().createGroupStatus(
                            messageReceiver,
                            GroupStatusDataModel.GroupStatusType.RENAMED,
                            null,
                            null,
                            groupName
                        );
                        ShortcutUtil.updatePinnedShortcut(messageReceiver);
                    } catch (ThreemaException e) {
                        logger.error("Exception", e);
                    }
                }).start();
            }

            @Override
            public void onUpdatePhoto(@NonNull GroupIdentity groupIdentity) {
                new Thread(() -> {
                    try {
                        GroupModel groupModel = getGroupModel(groupIdentity);
                        if (groupModel == null) {
                            return;
                        }
                        GroupMessageReceiver messageReceiver = serviceManager.getGroupService().createReceiver(groupModel);
                        serviceManager.getConversationService().refresh(groupModel);
                        serviceManager.getMessageService().createGroupStatus(
                            messageReceiver,
                            GroupStatusDataModel.GroupStatusType.PROFILE_PICTURE_UPDATED,
                            null,
                            null,
                            null
                        );
                        ShortcutUtil.updatePinnedShortcut(messageReceiver);
                    } catch (ThreemaException e) {
                        logger.error("Exception", e);
                    }
                }).start();
            }

            @Override
            public void onNewMember(@NonNull GroupIdentity groupIdentity, String identityNew) {
                GroupModel groupModel = getGroupModel(groupIdentity);
                if (groupModel == null) {
                    return;
                }
                try {
                    final GroupMessageReceiver receiver = serviceManager.getGroupService()
                        .createReceiver(groupModel);
                    final String myIdentity = serviceManager.getUserService().getIdentity();

                    if (!TestUtil.isEmptyOrNull(myIdentity)) {
                        serviceManager.getMessageService().createGroupStatus(
                            receiver,
                            GroupStatusDataModel.GroupStatusType.MEMBER_ADDED,
                            identityNew,
                            null,
                            null
                        );
                    }
                } catch (ThreemaException x) {
                    logger.error("Could not create group state after new member was added", x);
                }

                //reset avatar to recreate it!
                try {
                    serviceManager.getAvatarCacheService().reset(groupModel);
                } catch (FileSystemNotPresentException e) {
                    logger.error("Could not reset avatar cache", e);
                }
            }

            @Override
            public void onMemberLeave(@NonNull GroupIdentity groupIdentity, @NonNull String identityLeft) {
                GroupModel groupModel = getGroupModel(groupIdentity);
                if (groupModel == null) {
                    return;
                }
                try {
                    final GroupMessageReceiver receiver = serviceManager.getGroupService()
                        .createReceiver(groupModel);

                    serviceManager.getMessageService().createGroupStatus(
                        receiver,
                        GroupStatusDataModel.GroupStatusType.MEMBER_LEFT,
                        identityLeft,
                        null,
                        null
                    );

                    BallotService ballotService = serviceManager.getBallotService();
                    ballotService.removeVotes(receiver, identityLeft);
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            }

            @Override
            public void onMemberKicked(@NonNull GroupIdentity groupIdentity, String identityKicked) {
                final String myIdentity = serviceManager.getUserService().getIdentity();

                GroupModel groupModel = getGroupModel(groupIdentity);
                if (groupModel == null) {
                    return;
                }

                if (myIdentity != null && myIdentity.equals(identityKicked)) {
                    // my own member status has changed
                    try {
                        serviceManager.getNotificationService().cancelGroupCallNotification(groupModel.getId());
                        serviceManager.getConversationService().refresh(groupModel);
                    } catch (Exception e) {
                        logger.error("Exception", e);
                    }
                }
                try {
                    final GroupMessageReceiver receiver = serviceManager.getGroupService().createReceiver(groupModel);

                    serviceManager.getMessageService().createGroupStatus(
                        receiver,
                        GroupStatusDataModel.GroupStatusType.MEMBER_KICKED,
		                    identityKicked,
                        null,
                        null
                    );

                    BallotService ballotService = serviceManager.getBallotService();
                    ballotService.removeVotes(receiver, identityKicked);
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            }

            @Override
            public void onUpdate(@NonNull GroupIdentity groupIdentity) {
                try {
                    GroupModel groupModel = getGroupModel(groupIdentity);
                    if (groupModel == null) {
                        return;
                    }
                    serviceManager.getConversationService().refresh(groupModel);
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            }

            @Override
            public void onLeave(@NonNull GroupIdentity groupIdentity) {
                new Thread(() -> {
                    try {
                        GroupModel groupModel = getGroupModel(groupIdentity);
                        if (groupModel == null) {
                            return;
                        }
                        serviceManager.getConversationService().refresh(groupModel);
                    } catch (ThreemaException e) {
                        logger.error("Exception", e);
                    }
                }).start();
            }

            @Override
            public void onGroupStateChanged(@NonNull GroupIdentity groupIdentity, @GroupService.GroupState int oldState, @GroupService.GroupState int newState) {
                logger.debug("onGroupStateChanged: {} -> {}", oldState, newState);
                GroupModel groupModel = getGroupModel(groupIdentity);
                if (groupModel == null) {
                    return;
                }

                showNotesGroupNotice(groupModel, oldState, newState);
            }

            @Nullable
            private GroupModel getGroupModel(@NonNull GroupIdentity groupIdentity) {
                try {
                    GroupService groupService = serviceManager.getGroupService();
                    groupService.removeFromCache(groupIdentity);
                    GroupModel groupModel = groupService.getByGroupIdentity(groupIdentity);
                    if (groupModel == null) {
                        logger.error("Group model is null");
                    }
                    return groupModel;
                } catch (MasterKeyLockedException | FileSystemNotPresentException e) {
                    logger.error("Could not get group service", e);
                    return null;
                }
            }
        }, THREEMA_APPLICATION_LISTENER_TAG);

        ListenerManager.distributionListListeners.add(new DistributionListListener() {
            @Override
            public void onCreate(DistributionListModel distributionListModel) {
                try {
                    serviceManager.getConversationService().refresh(distributionListModel);
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            }

            @Override
            public void onModify(DistributionListModel distributionListModel) {
                new Thread(() -> {
                    try {
                        serviceManager.getConversationService().refresh(distributionListModel);
                        ShortcutUtil.updatePinnedShortcut(serviceManager.getDistributionListService().createReceiver(distributionListModel));
                    } catch (ThreemaException e) {
                        logger.error("Exception", e);
                    }
                }).start();
            }


            @Override
            public void onRemove(DistributionListModel distributionListModel) {
                new Thread(() -> {
                    try {
                        serviceManager.getConversationService().empty(distributionListModel);
                    } catch (ThreemaException e) {
                        logger.error("Exception", e);
                    }
                }).start();
            }
        }, THREEMA_APPLICATION_LISTENER_TAG);

        ListenerManager.messageListeners.add(new MessageListener() {
            @Override
            public void onNew(AbstractMessageModel newMessage) {
                logger.debug("MessageListener.onNewMessage");
                ConversationService conversationService;
                try {
                    conversationService = serviceManager.getConversationService();
                } catch (ThreemaException e) {
                    logger.error("Could not get conversation service", e);
                    return;
                }
                if (!newMessage.isStatusMessage()) {
                    ConversationModel conversationModel = conversationService.refresh(newMessage);
                    if (conversationModel != null) {
                        // Show notification only if there is a conversation
                        showConversationNotification(newMessage, false);
                    }
                } else if (newMessage.getType() == MessageType.GROUP_CALL_STATUS) {
                    conversationService.refresh(newMessage);
                }
            }

            @Override
            public void onModified(List<AbstractMessageModel> modifiedMessageModels) {
                logger.debug("MessageListener.onModified");
                for (final AbstractMessageModel modifiedMessageModel : modifiedMessageModels) {
                    if (modifiedMessageModel.isStatusMessage()) {
                        continue;
                    }
                    try {
                        ConversationService conversationService =
                            serviceManager.getConversationService();
                        ConversationModel conversationModel =
                            conversationService.refresh(modifiedMessageModel);
                        if (conversationModel != null &&
                            modifiedMessageModel.getType() == MessageType.IMAGE) {
                            // Only show a notification if there is a conversation
                            showConversationNotification(modifiedMessageModel, true);
                        }
                    } catch (ThreemaException e) {
                        logger.error("Exception", e);
                    }
                }
            }

            @Override
            public void onRemoved(AbstractMessageModel removedMessageModel) {
                logger.debug("MessageListener.onRemoved");
                if (!removedMessageModel.isStatusMessage()) {
                    try {
                        serviceManager.getConversationService().refreshWithDeletedMessage(removedMessageModel);
                    } catch (ThreemaException e) {
                        logger.error("Exception", e);
                    }
                }
            }

            @Override
            public void onRemoved(List<AbstractMessageModel> removedMessageModels) {
                logger.debug("MessageListener.onRemoved multi");
                for (final AbstractMessageModel removedMessageModel : removedMessageModels) {
                    if (!removedMessageModel.isStatusMessage()) {
                        try {
                            serviceManager.getConversationService().refreshWithDeletedMessage(removedMessageModel);
                        } catch (ThreemaException e) {
                            logger.error("Exception", e);
                        }
                    }
                }
            }

            @Override
            public void onProgressChanged(AbstractMessageModel messageModel, int newProgress) {
                // Ignore
            }

            @Override
            public void onResendDismissed(@NonNull AbstractMessageModel messageModel) {
                // Ignore
            }
        }, THREEMA_APPLICATION_LISTENER_TAG);

        ListenerManager.editMessageListener.add(message -> showConversationNotification(message, true));
        ListenerManager.messageDeletedForAllListener.add(message -> showConversationNotification(message, true));

        ListenerManager.groupJoinResponseListener.add((outgoingGroupJoinRequestModel, status) ->
            serviceManager.getNotificationService()
                .showGroupJoinResponseNotification(
                    outgoingGroupJoinRequestModel, status, serviceManager.getDatabaseServiceNew()
                ));


        ListenerManager.incomingGroupJoinRequestListener.add(new IncomingGroupJoinRequestListener() {
            @Override
            public void onReceived(IncomingGroupJoinRequestModel incomingGroupJoinRequestModel, GroupModel groupModel) {
                NotificationService notificationService = serviceManager.getNotificationService();
                notificationService.showGroupJoinRequestNotification(incomingGroupJoinRequestModel, groupModel);
            }

            @Override
            public void onRespond() {
                // don't bother here
            }
        });

        ListenerManager.serverMessageListeners.add(new ServerMessageListener() {
            @Override
            public void onAlert(ServerMessageModel serverMessage) {
                NotificationService notificationService = serviceManager.getNotificationService();
                notificationService.showServerMessage(serverMessage);
            }

            @Override
            public void onError(ServerMessageModel serverMessage) {
                NotificationService notificationService = serviceManager.getNotificationService();
                notificationService.showServerMessage(serverMessage);
            }
        }, THREEMA_APPLICATION_LISTENER_TAG);

        ListenerManager.contactListeners.add(new ContactListener() {
            @Override
            public void onModified(final @NonNull String identity) {
                final ContactModel modifiedContactModel = serviceManager.getDatabaseServiceNew().getContactModelFactory().getByIdentity(identity);
                if (modifiedContactModel == null) {
                    return;
                }
                new Thread(() -> {
                    try {
                        final ConversationService conversationService = serviceManager.getConversationService();
                        final ContactService contactService = serviceManager.getContactService();

                        // Refresh conversation cache
                        conversationService.updateContactConversation(modifiedContactModel);
                        conversationService.refresh(modifiedContactModel);

                        ShortcutUtil.updatePinnedShortcut(contactService.createReceiver(modifiedContactModel));
                    } catch (ThreemaException e) {
                        logger.error("Exception", e);
                    }
                }).start();
            }

            @Override
            public void onAvatarChanged(final @NonNull String identity) {
                new Thread(() -> {
                    try {
                        ContactService contactService = serviceManager.getContactService();
                        ContactModel contactModel = contactService.getByIdentity(identity);
                        if (contactModel != null) {
                            ShortcutUtil.updatePinnedShortcut(contactService.createReceiver(contactModel));
                        }
                    } catch (ThreemaException e) {
                        logger.error("Exception", e);
                    }
                }).start();
            }
        }, THREEMA_APPLICATION_LISTENER_TAG);

        ListenerManager.contactSettingsListeners.add(new ContactSettingsListener() {
            @Override
            public void onSortingChanged() {
                //do nothing!
            }

            @Override
            public void onNameFormatChanged() {
                //do nothing
            }

            @Override
            public void onAvatarSettingChanged() {
                //reset the avatar cache!
                if (serviceManager != null) {
                    try {
                        AvatarCacheService s = serviceManager.getAvatarCacheService();
                        s.clear();
                    } catch (FileSystemNotPresentException e) {
                        logger.error("Exception", e);
                    }
                }
            }

            @Override
            public void onInactiveContactsSettingChanged() {

            }

            @Override
            public void onNotificationSettingChanged(String uid) {

            }
        }, THREEMA_APPLICATION_LISTENER_TAG);

        ListenerManager.conversationListeners.add(new ConversationListener() {
            @Override
            public void onNew(ConversationModel conversationModel) {
            }

            @Override
            public void onModified(ConversationModel modifiedConversationModel, Integer oldPosition) {
            }

            @Override
            public void onRemoved(ConversationModel conversationModel) {
                //remove notification!
                NotificationService notificationService = serviceManager.getNotificationService();
                notificationService.cancel(conversationModel);
            }

            @Override
            public void onModifiedAll() {
            }
        }, THREEMA_APPLICATION_LISTENER_TAG);

        ListenerManager.ballotVoteListeners.add(new BallotVoteListener() {
            @Override
            public void onSelfVote(BallotModel ballotModel) {
            }

            @Override
            public void onVoteChanged(BallotModel ballotModel, String votingIdentity, boolean isFirstVote) {
                //add group state

                //DISABLED
                ServiceManager s = ThreemaApplication.getServiceManager();
                if (s != null) {
                    try {
                        BallotService ballotService = s.getBallotService();
                        ContactService contactService = s.getContactService();
                        GroupService groupService = s.getGroupService();
                        MessageService messageService = s.getMessageService();
                        UserService userService = s.getUserService();

                        if (TestUtil.required(ballotModel, contactService, groupService, messageService, userService)) {
                            LinkBallotModel linkBallotModel = ballotService.getLinkedBallotModel(ballotModel);
                            if (linkBallotModel != null) {
                                GroupStatusDataModel.GroupStatusType type = null;
                                MessageReceiver<? extends AbstractMessageModel> receiver = null;
                                if (linkBallotModel instanceof GroupBallotModel) {
                                    GroupModel groupModel = groupService.getById(((GroupBallotModel) linkBallotModel).getGroupId());

                                    // its a group ballot,write status
                                    receiver = groupService.createReceiver(groupModel);
                                    // reset archived status
                                    groupService.setIsArchived(
                                        groupModel.getCreatorIdentity(),
                                        groupModel.getApiGroupId(),
                                        false,
                                        TriggerSource.LOCAL
                                    );

                                } else if (linkBallotModel instanceof IdentityBallotModel) {
                                    String identity = ((IdentityBallotModel) linkBallotModel).getIdentity();

                                    // not implemented
                                    receiver = contactService.createReceiver(contactService.getByIdentity(identity));
                                    // reset archived status
                                    contactService.setIsArchived(identity, false, TriggerSource.LOCAL);
                                }

                                if (ballotModel.getType() == BallotModel.Type.RESULT_ON_CLOSE) {
                                    // Only show status message for first vote from a voter on private voting
                                    if (isFirstVote) {
                                        // On private voting, only show default update msg!
                                        type = GroupStatusDataModel.GroupStatusType.RECEIVED_VOTE;
                                    }
                                } else if (receiver != null) {
                                    if (isFirstVote) {
                                        type = GroupStatusDataModel.GroupStatusType.FIRST_VOTE;
                                    } else {
                                        type = GroupStatusDataModel.GroupStatusType.MODIFIED_VOTE;
                                    }
                                }

                                if (
                                    linkBallotModel instanceof GroupBallotModel
                                        && (type == GroupStatusDataModel.GroupStatusType.FIRST_VOTE
                                        || type == GroupStatusDataModel.GroupStatusType.MODIFIED_VOTE)
                                        && !BallotUtil.isMine(ballotModel, userService)
                                ) {
                                    // Only show votes (and vote changes) to the creator of the ballot in a group
                                    return;
                                }

                                if (type != null && receiver instanceof GroupMessageReceiver) {
                                    messageService.createGroupStatus(
                                        (GroupMessageReceiver) receiver,
                                        type,
                                        votingIdentity,
                                        ballotModel.getName(),
                                        null
                                    );
                                }

                                // now check if every participant has voted
                                if (isFirstVote
                                    && ballotService.getPendingParticipants(ballotModel.getId()).isEmpty()
                                    && receiver instanceof GroupMessageReceiver
                                ) {
                                    messageService.createGroupStatus(
                                        (GroupMessageReceiver) receiver,
                                        GroupStatusDataModel.GroupStatusType.VOTES_COMPLETE,
                                        null,
                                        ballotModel.getName(),
                                        null
                                    );
                                }
                            }
                        }
                    } catch (ThreemaException x) {
                        logger.error("Exception", x);
                    }
                }
            }

            @Override
            public void onVoteRemoved(BallotModel ballotModel, String votingIdentity) {
                //ignore
            }

            @Override
            public boolean handle(BallotModel ballotModel) {
                //handle all
                return true;
            }
        }, THREEMA_APPLICATION_LISTENER_TAG);

        final ContentObserver contentObserverChangeContactNames = new ContentObserver(null) {
            private boolean isRunning = false;

            @Override
            public boolean deliverSelfNotifications() {
                return super.deliverSelfNotifications();
            }

            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);

                if (!selfChange && serviceManager != null && !isRunning) {
                    this.isRunning = true;
                    onAndroidContactChangeLock.lock();

                    boolean cont;
                    //check if a sync is in progress.. wait!
                    try {
                        SynchronizeContactsService synchronizeContactService = serviceManager.getSynchronizeContactsService();
                        cont = !synchronizeContactService.isSynchronizationInProgress();
                    } catch (MasterKeyLockedException | FileSystemNotPresentException e) {
                        logger.error("Exception", e);
                        //do nothing
                        cont = false;
                    }

                    if (cont) {
                        PreferenceService preferencesService = serviceManager.getPreferenceService();
                        if (preferencesService.isSyncContacts()) {
                            try {
                                ContactService c = serviceManager.getContactService();
                                //update contact names if changed!
                                c.updateAllContactNamesFromAndroidContacts();
                            } catch (MasterKeyLockedException | FileSystemNotPresentException e) {
                                logger.error("Exception", e);
                            }
                        }
                    }
                    this.isRunning = false;
                    onAndroidContactChangeLock.unlock();
                }
            }
        };

        ListenerManager.synchronizeContactsListeners.add(new SynchronizeContactsListener() {
            @Override
            public void onStarted(SynchronizeContactsRoutine startedRoutine) {
                //disable contact observer
                serviceManager.getContext().getContentResolver().unregisterContentObserver(contentObserverChangeContactNames);
            }

            @Override
            public void onFinished(SynchronizeContactsRoutine finishedRoutine) {
                //enable contact observer
                serviceManager.getContext().getContentResolver().registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    false,
                    contentObserverChangeContactNames);
            }

            @Override
            public void onError(SynchronizeContactsRoutine finishedRoutine) {
                //enable contact observer
                serviceManager.getContext().getContentResolver().registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    false,
                    contentObserverChangeContactNames);
            }
        }, THREEMA_APPLICATION_LISTENER_TAG);

        ListenerManager.contactTypingListeners.add((fromContact, isTyping) -> {
            //update the conversations
            try {
                serviceManager.getConversationService()
                    .setIsTyping(fromContact, isTyping);
            } catch (ThreemaException e) {
                logger.error("Exception", e);
            }
        });

        ListenerManager.newSyncedContactListener.add(contactModels -> {
            NotificationService notificationService = serviceManager.getNotificationService();
            notificationService.showNewSyncedContactsNotification(contactModels);
        });

        WebClientListenerManager.serviceListener.add(new WebClientServiceListener() {
            @Override
            public void onEnabled() {
                SessionWakeUpServiceImpl.getInstance()
                    .processPendingWakeupsAsync();
            }

            @Override
            public void onStarted(
                @NonNull final WebClientSessionModel model,
                @NonNull final byte[] permanentKey,
                @NonNull final String browser
            ) {
                logger.info("WebClientListenerManager: onStarted", true);

                RuntimeUtil.runOnUiThread(() -> {
                    String toastText = getAppContext().getString(R.string.webclient_new_connection_toast);
                    if (model.getLabel() != null) {
                        toastText += " (" + model.getLabel() + ")";
                    }
                    Toast.makeText(getAppContext(), toastText, Toast.LENGTH_LONG).show();

                    final Intent intent = new Intent(context, SessionAndroidService.class);

                    if (SessionAndroidService.isRunning()) {
                        intent.setAction(SessionAndroidService.ACTION_UPDATE);
                        logger.info("sending ACTION_UPDATE to SessionAndroidService");
                        context.startService(intent);
                    } else {
                        logger.info("SessionAndroidService not running...starting");
                        intent.setAction(SessionAndroidService.ACTION_START);
                        logger.info("sending ACTION_START to SessionAndroidService");
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            // Starting on version S, foreground services cannot be started from the background.
                            // When battery optimizations are disabled (recommended for Threema Web), then no
                            // exception is thrown. Otherwise we just log it.
                            try {
                                ContextCompat.startForegroundService(context, intent);
                            } catch (ForegroundServiceStartNotAllowedException exception) {
                                logger.error("Couldn't start foreground service", exception);
                            }
                        } else {
                            ContextCompat.startForegroundService(context, intent);
                        }
                    }
                });
            }

            @Override
            public void onStateChanged(
                @NonNull final WebClientSessionModel model,
                @NonNull final WebClientSessionState oldState,
                @NonNull final WebClientSessionState newState
            ) {
                logger.info("WebClientListenerManager: onStateChanged");

                if (newState == WebClientSessionState.DISCONNECTED) {
                    RuntimeUtil.runOnUiThread(() -> {
                        logger.info("updating SessionAndroidService");
                        if (SessionAndroidService.isRunning()) {
                            final Intent intent = new Intent(context, SessionAndroidService.class);
                            intent.setAction(SessionAndroidService.ACTION_UPDATE);
                            logger.info("sending ACTION_UPDATE to SessionAndroidService");
                            context.startService(intent);
                        } else {
                            logger.info("SessionAndroidService not running...not updating");
                        }
                    });
                }
            }

            @Override
            public void onStopped(@NonNull final WebClientSessionModel model, @NonNull final DisconnectContext reason) {
                logger.info("WebClientListenerManager: onStopped");

                RuntimeUtil.runOnUiThread(() -> {
                    if (SessionAndroidService.isRunning()) {
                        final Intent intent = new Intent(context, SessionAndroidService.class);
                        intent.setAction(SessionAndroidService.ACTION_STOP);
                        logger.info("sending ACTION_STOP to SessionAndroidService");
                        context.startService(intent);
                    } else {
                        logger.info("SessionAndroidService not running...not stopping");
                    }
                });
            }
        });

        //called if a fcm message with a newer session received
        WebClientListenerManager.wakeUpListener.add(() -> RuntimeUtil.runOnUiThread(
            () -> Toast.makeText(getAppContext(), R.string.webclient_protocol_version_to_old, Toast.LENGTH_LONG).show()
        ));

        VoipListenerManager.callEventListener.add(new VoipCallEventListener() {
            private final Logger logger = LoggingUtil.getThreemaLogger("VoipCallEventListener");

            @Override
            public void onRinging(String peerIdentity) {
                this.logger.debug("onRinging {}", peerIdentity);
            }

            @Override
            public void onStarted(String peerIdentity, boolean outgoing) {
                final String direction = outgoing ? "to" : "from";
                this.logger.info("Call {} {} started", direction, peerIdentity);
            }

            @Override
            public void onFinished(long callId, @NonNull String peerIdentity, boolean outgoing, int duration) {
                final String direction = outgoing ? "to" : "from";
                this.logger.info("Call {} {} finished", direction, peerIdentity);
                this.saveStatus(peerIdentity,
                    outgoing,
                    VoipStatusDataModel.createFinished(callId, duration),
                    true);
            }

            @Override
            public void onRejected(long callId, String peerIdentity, boolean outgoing, byte reason) {
                final String direction = outgoing ? "to" : "from";
                this.logger.info("Call {} {} rejected (reason {})", direction, peerIdentity, reason);
                this.saveStatus(peerIdentity,
                    // on rejected incoming, the outgoing was rejected!
                    !outgoing,
                    VoipStatusDataModel.createRejected(callId, reason),
                    true);
            }

            @Override
            public void onMissed(long callId, String peerIdentity, boolean accepted, @Nullable Date date) {
                this.logger.info("Call from {} missed", peerIdentity);
                this.saveStatus(peerIdentity,
                    false,
                    VoipStatusDataModel.createMissed(callId, date),
                    accepted);
            }

            @Override
            public void onAborted(long callId, String peerIdentity) {
                this.logger.info("Call to {} aborted", peerIdentity);
                this.saveStatus(peerIdentity,
                    true,
                    VoipStatusDataModel.createAborted(callId),
                    true);
            }

            private void saveStatus(
                @NonNull String identity,
                boolean isOutbox,
                @NonNull VoipStatusDataModel status,
                boolean isRead
            ) {
                try {
                    // Services
                    if (serviceManager == null) {
                        this.logger.error("Could not save voip status, servicemanager is null");
                        return;
                    }
                    final IdentityStore identityStore = serviceManager.getIdentityStore();
                    final ContactService contactService = serviceManager.getContactService();
                    final MessageService messageService = serviceManager.getMessageService();

                    // If an incoming status message is not targeted at our own identity, something's wrong
                    final String appIdentity = identityStore.getIdentity();
                    if (TestUtil.compare(identity, appIdentity) && !isOutbox) {
                        this.logger.error("Could not save voip status (identity={}, appIdentity={}, outbox={})", identity, appIdentity, isOutbox);
                        return;
                    }

                    // Create status message
                    final ContactModel contactModel = contactService.getByIdentity(identity);
                    final ContactMessageReceiver receiver = contactService.createReceiver(contactModel);
                    messageService.createVoipStatus(status, receiver, isOutbox, isRead);
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            }
        }, THREEMA_APPLICATION_LISTENER_TAG);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(serviceManager.getContext(), android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            serviceManager.getContext().getContentResolver()
                .registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                    false,
                    contentObserverChangeContactNames);
        }
    }

    public static boolean activityResumed(Activity currentActivity) {
        logger.debug("*** App ActivityResumed");
        if (serviceManager != null) {
            serviceManager.getActivityService().resume(currentActivity);
            return true;
        }
        return false;
    }

    public static void activityPaused(Activity pausedActivity) {
        logger.debug("*** App ActivityPaused");
        if (serviceManager != null) {
            serviceManager.getActivityService().pause(pausedActivity);
        }
    }

    public static void activityDestroyed(Activity destroyedActivity) {
        logger.debug("*** App ActivityDestroyed");
        if (serviceManager != null) {
            serviceManager.getActivityService().destroy(destroyedActivity);
        }
    }

    public static boolean activityUserInteract(Activity interactedActivity) {
        if (serviceManager != null) {
            serviceManager.getActivityService().userInteract(interactedActivity);
        }
        return true;
    }

    public static Date getLastLoggedIn() {
        return lastLoggedIn;
    }

    public static boolean isIsDeviceIdle() {
        return isDeviceIdle;
    }

    public static AppVersion getAppVersion() {
        return appVersion;
    }

    public static Context getAppContext() {
        return ThreemaApplication.context;
    }
}
