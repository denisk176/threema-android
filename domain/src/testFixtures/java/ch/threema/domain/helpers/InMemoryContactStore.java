/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

package ch.threema.domain.helpers;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.BasicContact;
import ch.threema.domain.stores.ContactStore;

/**
 * An in-memory contact store, used for testing.
 */
public class InMemoryContactStore implements ContactStore {
    private final Map<String, Contact> contacts = new HashMap<>();
    private final Map<String, BasicContact> contactsCache = new HashMap<>();

    @Override
    public Contact getContactForIdentity(@NonNull String identity) {
        return this.contacts.get(identity);
    }

    @Override
    public void addContact(@NonNull Contact contact) {
        this.contacts.put(contact.getIdentity(), contact);
    }

    @Override
    public void addCachedContact(@NonNull BasicContact contact) {
        this.contactsCache.put(contact.getIdentity(), contact);
    }

    @Nullable
    @Override
    public BasicContact getCachedContact(@NonNull String identity) {
        return this.contactsCache.get(identity);
    }

    @Nullable
    @Override
    public Contact getContactForIdentityIncludingCache(@NonNull String identity) {
        Contact cached = contactsCache.get(identity);
        if (cached != null) {
            return cached;
        }

        return getContactForIdentity(identity);
    }

    @Override
    public boolean isSpecialContact(@NonNull String identity) {
        return false;
    }
}
