/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.domain.onprem

import ch.threema.base.ThreemaException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnPremConfigVerifierTest {

    @Test
    fun `verify valid config`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.PUBLIC_KEY))

        val obj = verifier.verify(OnPremConfigTestData.goodOppf)

        assertEquals("1.0", obj.getString("version"))
    }

    @Test
    fun `invalid signature`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.PUBLIC_KEY))

        assertFailsWith<ThreemaException> {
            verifier.verify(OnPremConfigTestData.badOppf)
        }
    }

    @Test
    fun `wrong public key`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.WRONG_PUBLIC_KEY))

        assertFailsWith<ThreemaException> {
            verifier.verify(OnPremConfigTestData.goodOppf)
        }
    }

    @Test
    fun `empty input is invalid`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.PUBLIC_KEY))

        assertFailsWith<ThreemaException> {
            verifier.verify("")
        }
    }
}
