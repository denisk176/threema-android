/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.webclient.services.instance.message.updater;

import org.msgpack.core.MessagePackException;
import org.slf4j.Logger;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Utils;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageUpdater;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.GroupIdentity;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

/**
 * Notify Threema Web about changes to avatars.
 */
@WorkerThread
public class AvatarUpdateHandler extends MessageUpdater {
    private static final Logger logger = LoggingUtil.getThreemaLogger("AvatarUpdateHandler");

    // Handler
    private final @NonNull HandlerExecutor handler;

    // Listeners
    private final @NonNull ContactListener contactListener;
    private final @NonNull GroupListener groupListener;

    // Dispatchers
    private final @NonNull MessageDispatcher updateDispatcher;

    // Services
    private final @NonNull ContactService contactService;
    private final @NonNull GroupService groupService;

    @AnyThread
    public AvatarUpdateHandler(
        @NonNull HandlerExecutor handler,
        @NonNull MessageDispatcher updateDispatcher,
        @NonNull ContactService contactService,
        @NonNull GroupService groupService
    ) {
        super(Protocol.SUB_TYPE_AVATAR);
        this.handler = handler;

        this.groupService = groupService;

        // Dispatchers
        this.updateDispatcher = updateDispatcher;

        // Services
        this.contactService = contactService;

        // Create receiver listeners
        this.contactListener = new ContactListener();
        this.groupListener = new GroupListener();
    }

    @Override
    public void register() {
        logger.debug("register()");
        ListenerManager.contactListeners.add(this.contactListener);
        ListenerManager.groupListeners.add(this.groupListener);
    }

    /**
     * This method can be safely called multiple times without any negative side effects
     */
    @Override
    public void unregister() {
        logger.debug("unregister()");
        ListenerManager.contactListeners.remove(this.contactListener);
        ListenerManager.groupListeners.remove(this.groupListener);
    }

    /**
     * Update a contact avatar.
     */
    private void update(@NonNull final ContactModel contact) {
        this.update(new Utils.ModelWrapper(contact));
    }

    /**
     * Update a group avatar.
     */
    private void update(GroupModel group) {
        this.update(new Utils.ModelWrapper(group));
    }

    private void update(final Utils.ModelWrapper model) {
        try {
            // Get avatar
            byte[] data = model.getAvatar(true, Protocol.SIZE_AVATAR_HIRES_MAX_PX);

            // Convert message and prepare arguments
            MsgpackObjectBuilder args = new MsgpackObjectBuilder();
            args.put(Protocol.ARGUMENT_RECEIVER_TYPE, model.getType());
            args.put(Protocol.ARGUMENT_RECEIVER_ID, model.getId());

            // Send message
            logger.debug("Sending {} avatar update", model.getType());
            send(updateDispatcher, data, args);
        } catch (ConversionException | MessagePackException e) {
            logger.error("Exception", e);
        }
    }

    /**
     * Listen for contact changes.
     */
    @AnyThread
    private class ContactListener implements ch.threema.app.listeners.ContactListener {
        @Override
        public void onAvatarChanged(final @NonNull String identity) {
            logger.debug("Contact Listener: onAvatarChanged");
            ContactModel contactModel = contactService.getByIdentity(identity);
            if (contactModel == null) {
                logger.error("Got an avatar update for an unknown contact");
            } else {
                handler.post(() -> AvatarUpdateHandler.this.update(contactModel));
            }
        }
    }

    @AnyThread
    private class GroupListener implements ch.threema.app.listeners.GroupListener {
        @Override
        public void onUpdatePhoto(@NonNull GroupIdentity groupIdentity) {
            logger.debug("Group Listener: onUpdatePhoto");
            GroupModel groupModel = groupService.getByGroupIdentity(groupIdentity);
            if (groupModel == null) {
                logger.error("Group model is null");
                return;
            }

            handler.post(() -> AvatarUpdateHandler.this.update(groupModel));
        }
    }
}
