/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.protocol.csp.messages.protobuf.ProtobufDataInterface
import ch.threema.protobuf.csp.e2e.EditMessage
import com.google.protobuf.InvalidProtocolBufferException
import java.util.Objects

class EditMessageData(
    val messageId: Long,
    val text: String,
) : ProtobufDataInterface<EditMessage> {
    companion object {
        @JvmStatic
        fun fromProtobuf(rawProtobufMessage: ByteArray): EditMessageData {
            try {
                val protobufMessage = EditMessage.parseFrom(rawProtobufMessage)
                return EditMessageData(
                    protobufMessage.messageId,
                    protobufMessage.text,
                )
            } catch (e: InvalidProtocolBufferException) {
                throw BadMessageException("Invalid EditMessage protobuf data", e)
            } catch (e: IllegalArgumentException) {
                throw BadMessageException("Could not create EditMessage data", e)
            }
        }
    }

    override fun toProtobufMessage(): EditMessage {
        return EditMessage.newBuilder()
            .setMessageId(messageId)
            .setText(text)
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EditMessageData) return false

        if (messageId != other.messageId) return false
        if (text != other.text) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(messageId, text)
    }
}
