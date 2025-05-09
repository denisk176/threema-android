/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

public interface DeadlineListService {
    long DEADLINE_INDEFINITE = -1;
    long DEADLINE_INDEFINITE_EXCEPT_MENTIONS = -2;

    void add(String uid, long timeout);

    void init();

    boolean has(String uid);

    void remove(String uid);

    /**
     * Return the deadline timestamp for this uid.
     * If no entry is found, 0 is returned.
     * For indefinite settings, DeadlineListService.DEADLINE_INDEFINITE is returned.
     */
    long getDeadline(String uid);

    int getSize();

    void clear();
}
