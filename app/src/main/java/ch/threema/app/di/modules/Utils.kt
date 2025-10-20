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

package ch.threema.app.di.modules

import androidx.preference.PreferenceManager
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.Toaster
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Provides access to utility classes.
 * Note that some of these may be functionally singletons, but that should be treated as an implementation detail only, i.e., they should
 * not hold global state.
 */
val utilsModule = module {
    factory { DispatcherProvider.default }
    factoryOf(::Toaster)
    factory { PreferenceManager.getDefaultSharedPreferences(get()) }
}
