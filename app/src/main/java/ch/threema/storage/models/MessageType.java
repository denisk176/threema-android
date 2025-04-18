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

package ch.threema.storage.models;

// Do not change the order of the values, since this would break the persisted type in the database
public enum MessageType {
    TEXT,
    @Deprecated IMAGE,
    @Deprecated VIDEO,
    @Deprecated VOICEMESSAGE,
    LOCATION,
    @Deprecated CONTACT,
    STATUS,
    BALLOT,
    FILE,
    VOIP_STATUS,
    DATE_SEPARATOR,
    GROUP_CALL_STATUS,
    FORWARD_SECURITY_STATUS,
    GROUP_STATUS,
}


