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

package ch.threema.app.webclient.services.instance.message.receiver;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.Map;

import ch.threema.app.services.ContactService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;

@WorkerThread
public class IsTypingHandler extends MessageReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("IsTypingHandler");

    @NonNull
    private final ContactService contactService;

    @AnyThread
    public IsTypingHandler(@NonNull ContactService userService) {
        super(Protocol.SUB_TYPE_TYPING);
        this.contactService = userService;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received typing update");

        // Get args
        final Map<String, Value> args = this.getArguments(message, false, new String[]{
            Protocol.ARGUMENT_RECEIVER_ID,
        });
        final String identity = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();

        // Get data
        final Map<String, Value> data = this.getData(message, false, new String[]{
            Protocol.ARGUMENT_IS_TYPING,
        });
        boolean isTyping = data.get(Protocol.ARGUMENT_IS_TYPING).asBooleanValue().getBoolean();

        this.contactService.sendTypingIndicator(identity, isTyping);
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return true;
    }
}
