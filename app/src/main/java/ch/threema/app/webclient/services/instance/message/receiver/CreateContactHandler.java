/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.asynctasks.AddContactRestrictionPolicy;
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask;
import ch.threema.app.asynctasks.ContactResult;
import ch.threema.app.asynctasks.ContactAvailable;
import ch.threema.app.asynctasks.ContactCreated;
import ch.threema.app.asynctasks.PolicyViolation;
import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UserService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Contact;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.storage.models.ContactModel;

@WorkerThread
public class CreateContactHandler extends MessageReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("CreateContactHandler");

    private final MessageDispatcher dispatcher;
    private final ContactService contactService;
    private final UserService userService;
    private final APIConnector apiConnector;
    private final ContactModelRepository contactModelRepository;

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        Protocol.ERROR_INVALID_IDENTITY,
        Protocol.ERROR_DISABLED_BY_POLICY,
        Protocol.ERROR_INTERNAL,
    })
    private @interface ErrorCode {
    }

    @AnyThread
    public CreateContactHandler(
        MessageDispatcher dispatcher,
        ContactService contactService,
        @NonNull UserService userService,
        @NonNull APIConnector apiConnector,
        @NonNull ContactModelRepository contactModelRepository
    ) {
        super(Protocol.SUB_TYPE_CONTACT);
        this.dispatcher = dispatcher;
        this.contactService = contactService;
        this.userService = userService;
        this.apiConnector = apiConnector;
        this.contactModelRepository = contactModelRepository;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received add contact create");
        final Map<String, Value> args = this.getArguments(message, false, new String[]{
            Protocol.ARGUMENT_TEMPORARY_ID,
        });
        final Map<String, Value> data = this.getData(message, false, new String[]{
            Protocol.ARGUMENT_IDENTITY,
        });

        // Get args and data
        final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID)
            .asStringValue().toString();
        final String threemaId = data.get(Protocol.ARGUMENT_IDENTITY)
            .asStringValue().toString()
            .toUpperCase().trim();

        // Validate identity
        if (threemaId.length() != ProtocolDefines.IDENTITY_LEN) {
            this.failed(threemaId, temporaryId, Protocol.ERROR_INVALID_IDENTITY);
            return;
        }

        // If contact already exists, simply return it
        ContactModel contactModel = this.contactService.getByIdentity(threemaId);
        if (contactModel != null) {
            this.success(threemaId, temporaryId, contactModel);
            return;
        }

        // Otherwise try to create the contact
        try {
            contactModel = createContact(threemaId);
            this.success(threemaId, temporaryId, contactModel);
        } catch (InvalidEntryException e) {
            this.failed(threemaId, temporaryId, Protocol.ERROR_INVALID_IDENTITY);
        } catch (EntryAlreadyExistsException e) { // should not happen
            this.failed(threemaId, temporaryId, Protocol.ERROR_INTERNAL);
        } catch (PolicyViolationException e) {
            this.failed(threemaId, temporaryId, Protocol.ERROR_DISABLED_BY_POLICY);
        }
    }

    private void success(String threemaId, String temporaryId, ContactModel contact) {
        logger.debug("Respond add contact success");
        try {
            this.send(this.dispatcher,
                new MsgpackObjectBuilder()
                    .put(Protocol.SUB_TYPE_RECEIVER, Contact.convert(contact)),
                new MsgpackObjectBuilder()
                    .put(Protocol.ARGUMENT_SUCCESS, true)
                    .put(Protocol.ARGUMENT_IDENTITY, threemaId)
                    .put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
            );
        } catch (ConversionException e) {
            logger.error("Exception", e);
        }
    }

    private void failed(String threemaId, String temporaryId, @ErrorCode String errorCode) {
        logger.warn("Respond add contact failed ({})", errorCode);
        this.send(this.dispatcher,
            (MsgpackObjectBuilder) null,
            new MsgpackObjectBuilder()
                .put(Protocol.ARGUMENT_SUCCESS, false)
                .put(Protocol.ARGUMENT_ERROR, errorCode)
                .put(Protocol.ARGUMENT_IDENTITY, threemaId)
                .put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
        );
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return false;
    }

    @NonNull
    @WorkerThread
    private ContactModel createContact(@NonNull String identity)
        throws InvalidEntryException, EntryAlreadyExistsException, PolicyViolationException {
        ContactResult result = new BasicAddOrUpdateContactBackgroundTask(
            identity,
            ContactModel.AcquaintanceLevel.DIRECT,
            userService.getIdentity(),
            apiConnector,
            contactModelRepository,
            AddContactRestrictionPolicy.CHECK,
            ThreemaApplication.getAppContext(),
            null
        ).runSynchronously();

        if (result instanceof ContactCreated) {
            ContactModel contactModel = contactService.getByIdentity(identity);
            if (contactModel == null) {
                throw new IllegalStateException("Contact model is null after adding it");
            }
            return contactModel;
        } else if (result instanceof ContactAvailable) {
            throw new EntryAlreadyExistsException(R.string.identity_already_exists);
        } else if (result instanceof PolicyViolation) {
            throw new PolicyViolationException();
        } else {
            throw new InvalidEntryException(R.string.invalid_threema_id);
        }
    }
}
