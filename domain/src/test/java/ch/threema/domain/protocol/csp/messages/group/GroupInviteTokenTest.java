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

package ch.threema.domain.protocol.csp.messages.group;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GroupInviteTokenTest {


    @Test
    void testValidToken() throws GroupInviteToken.InvalidGroupInviteTokenException {
        byte[] byteValue = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        GroupInviteToken token = new GroupInviteToken(byteValue);
        Assertions.assertArrayEquals(byteValue, token.get());
    }

    @Test
    void testInvalidToken() {
        Assertions.assertThrows(
            GroupInviteToken.InvalidGroupInviteTokenException.class,
            () -> new GroupInviteToken(new byte[]{0, 1, 2, 3})
        );
    }

    @Test
    void testFromHexString() throws GroupInviteToken.InvalidGroupInviteTokenException {
        byte[] byteValue = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        GroupInviteToken token = GroupInviteToken.fromHexString("000102030405060708090a0b0c0d0e0f");
        Assertions.assertArrayEquals(byteValue, token.get());
    }

    @Test
    void testEquals() throws GroupInviteToken.InvalidGroupInviteTokenException {
        byte[] byteValue = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        GroupInviteToken token1 = new GroupInviteToken(byteValue);
        GroupInviteToken token2 = new GroupInviteToken(byteValue);
        Assertions.assertEquals(token1, token2);
    }

    @Test
    void testNotEquals() throws GroupInviteToken.InvalidGroupInviteTokenException {
        byte[] byteValue1 = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        byte[] byteValue2 = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        GroupInviteToken token1 = new GroupInviteToken(byteValue1);
        GroupInviteToken token2 = new GroupInviteToken(byteValue2);
        Assertions.assertNotEquals(token1, token2);
    }

    @Test
    void testToString() throws GroupInviteToken.InvalidGroupInviteTokenException {
        byte[] byteValue = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};
        GroupInviteToken token = new GroupInviteToken(byteValue);
        Assertions.assertEquals("000102030405060708090a0b0c0d0e0f", token.toString());
    }
}
