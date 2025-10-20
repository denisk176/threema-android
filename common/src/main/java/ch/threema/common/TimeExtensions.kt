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

package ch.threema.common

import java.time.Instant
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun now() = Date()

operator fun Date.minus(other: Date): Duration = (time - other.time).milliseconds

operator fun Date.plus(duration: Duration) = Date(time + duration.inWholeMilliseconds)

operator fun Date.minus(duration: Duration) = Date(time - duration.inWholeMilliseconds)

operator fun Instant.minus(other: Instant): Duration = (toEpochMilli() - other.toEpochMilli()).milliseconds

operator fun Instant.plus(duration: Duration): Instant = Instant.ofEpochMilli(toEpochMilli() + duration.inWholeMilliseconds)

operator fun Instant.minus(duration: Duration): Instant = Instant.ofEpochMilli(toEpochMilli() - duration.inWholeMilliseconds)

fun Instant.toDate() = Date.from(this)

/**
 *  If the duration exceeds one hour, a string in the form of `h:mm:ss` will be returned. If not, it will just return `mm:ss`.
 */
fun Duration.toHMMSS(): String {
    val hours: Long = this.inWholeHours
    val minutes: Long = (this.inWholeMinutes % 60)
    val seconds: Long = (this.inWholeSeconds % 60)
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        else -> "%02d:%02d".format(minutes, seconds)
    }
}
