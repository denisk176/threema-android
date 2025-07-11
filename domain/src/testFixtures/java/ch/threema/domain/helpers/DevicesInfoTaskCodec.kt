/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.domain.helpers

import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage

/**
 * This task codec answers each outgoing [OutboundD2mMessage.GetDevicesInfo] with a response given in [deviceInfoResponses] in the same order of the
 * list.
 * @throws NoSuchElementException if the number of [OutboundD2mMessage.GetDevicesInfo] messages that are sent exceeds the number of responses in
 * [deviceInfoResponses]
 */
open class DevicesInfoTaskCodec(
    private val deviceInfoResponses: MutableList<InboundD2mMessage.DevicesInfo>,
) : DropDeviceAckTaskCodec() {
    override suspend fun write(message: OutboundMessage) {
        if (message is OutboundD2mMessage.GetDevicesInfo) {
            inboundMessages.add(deviceInfoResponses.removeAt(0))
        } else {
            super.write(message)
        }
    }
}
