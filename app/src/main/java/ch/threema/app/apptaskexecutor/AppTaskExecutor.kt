/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.apptaskexecutor

import ch.threema.app.apptaskexecutor.tasks.AppTask
import ch.threema.app.apptaskexecutor.tasks.PersistableAppTask
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.startup.models.AppSystem
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.DispatcherProvider
import ch.threema.common.awaitNonNull
import kotlin.jvm.Throws
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

private val logger = LoggingUtil.getThreemaLogger("AppTaskExecutor")

/**
 * This is a task executor that is bound to the application. There are no restrictions on network connectivity. Note that scheduled tasks may run in
 * parallel.
 *
 * As soon as the database is ready, persistent tasks are loaded and run.
 */
// TODO(ANDR-4220): Refactor appTaskPersistenceProvider such that it can be injected by Koin
class AppTaskExecutor(
    private val appStartupMonitor: AppStartupMonitor,
    private val dispatcherProvider: DispatcherProvider,
    private val serviceManagerProvider: ServiceManagerProvider,
    private val appTaskPersistenceProvider: suspend (ServiceManager) -> AppTaskPersistence = { serviceManager ->
        databaseAppTaskPersistenceProvider(
            serviceManager,
            appStartupMonitor,
        )
    },
) {
    private val mutex = Mutex()

    private val taskQueue = Channel<Pair<AppTask, CompletableDeferred<Unit>>>(capacity = Channel.UNLIMITED)

    private val taskPersistenceStateFlow = MutableStateFlow<AppTaskPersistence?>(null)

    /**
     * Start the task executor. This method never returns as long as the task executor is running tasks or waiting for new tasks to be scheduled.
     *
     * @throws IllegalStateException if the tasks executor has already been started
     */
    @Throws(IllegalStateException::class)
    suspend fun start() {
        if (!mutex.tryLock()) {
            throw IllegalStateException("Tasks are already being run")
        }
        try {
            coroutineScope {
                launch {
                    runQueuedTasks()
                }

                launch {
                    collectAndMapServiceManager()
                }

                initPersistedTasks()
            }
        } finally {
            logger.error("Running app tasks has been stopped.")
            mutex.unlock()
        }
    }

    /**
     * Persist and schedule the given [appTask]. Suspends until the task can be persisted in case there is no access to the task persistor.
     */
    suspend fun persistAndScheduleTask(appTask: PersistableAppTask): Deferred<Unit> {
        taskPersistenceStateFlow.awaitNonNull().persistTask(appTask)
        return schedule(appTask)
    }

    /**
     * Schedule the given [appTask]. Note that the task will *not* be persisted, even if it is a persistable task.
     */
    fun scheduleTask(appTask: AppTask): Deferred<Unit> {
        if (appTask is PersistableAppTask) {
            logger.warn("Not persisting persistable task {}", appTask)
        }
        return schedule(appTask)
    }

    private fun schedule(appTask: AppTask): CompletableDeferred<Unit> {
        val completableDeferred = CompletableDeferred<Unit>()
        val sendResult = taskQueue.trySend(appTask to completableDeferred)
        when {
            sendResult.isSuccess -> return completableDeferred
            sendResult.isClosed -> error("Could not schedule task $appTask because channel is closed")
            else -> error("Scheduling the task was not successful but the channel wasn't closed.")
        }
    }

    /**
     * Collects and maps the service manager to app task persistence.
     */
    private suspend fun collectAndMapServiceManager() {
        serviceManagerProvider.serviceManagerFlow.collect { serviceManager ->
            taskPersistenceStateFlow.value = serviceManager?.let { appTaskPersistenceProvider(it) }
        }
    }

    /**
     * Awaits the task persistor and loads and schedules all persisted tasks.
     */
    private suspend fun initPersistedTasks() {
        logger.info("Loading persisted app tasks")
        taskPersistenceStateFlow
            .awaitNonNull()
            .loadAllPersistedTasks()
            .forEach(this::schedule)
    }

    /**
     * Runs all tasks that are currently queued and those that will be queued in the future. This method runs forever, except the coroutine has been
     * cancelled.
     */
    private suspend fun runQueuedTasks() {
        coroutineScope {
            while (isActive) {
                val (appTask, completableDeferred) = taskQueue.receive()
                launch {
                    logger.info("Running app task {}", appTask)
                    var taskWasCancelled = false
                    try {
                        withContext(dispatcherProvider.worker) {
                            appTask.run()
                        }
                        completableDeferred.complete(Unit)
                    } catch (exception: Exception) {
                        logger.error("Task {} couldn't be run successfully", appTask, exception)
                        // Note that a task that has been cancelled should not be removed from storage
                        taskWasCancelled = exception is CancellationException
                        completableDeferred.completeExceptionally(exception)
                    } finally {
                        if (appTask is PersistableAppTask && !taskWasCancelled) {
                            taskPersistenceStateFlow.awaitNonNull().removePersistedTask(appTask)
                        }
                        logger.info("Task {} complete", appTask)
                    }
                }
            }
        }
    }

    companion object {
        private val databaseAppTaskPersistenceProvider: suspend (ServiceManager, AppStartupMonitor) -> AppTaskPersistence =
            { serviceManager, appStartupMonitor ->
                appStartupMonitor.awaitSystem(AppSystem.DATABASE_UPDATES)
                DatabaseAppTaskPersistence(serviceManager.databaseService.appTaskPersistenceFactory)
            }
    }
}
