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

package ch.threema.app.testutils;

import org.junit.Assert;

import androidx.annotation.NonNull;

/**
 * Better assertions.
 */
public class ThreemaAssert {
    private final static String START = "=== Start ===\n";
    private final static String END = "\n=== End ===";

    public static void assertContains(@NonNull String haystack, @NonNull CharSequence needle) {
        if (!haystack.contains(needle)) {
            Assert.fail("Substring '" + needle + "' not found in the following string:\n" + START + haystack + END);
        }
    }
}
