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

package ch.threema.app.services;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.storage.models.AbstractMessageModel;

/**
 * Handling methods for messages
 */
public interface MessageSendingService {
    interface MessageSendingServiceState {
        void processingFailed(AbstractMessageModel messageModel, MessageReceiver<AbstractMessageModel> receiver);

        void exception(Exception x, int tries);
    }

    interface MessageSendingProcess {
        MessageReceiver<AbstractMessageModel> getReceiver();

        AbstractMessageModel getMessageModel();

        boolean send() throws Exception;
    }

    void addToQueue(MessageSendingProcess process);

    void abort(String messageUid);
}
