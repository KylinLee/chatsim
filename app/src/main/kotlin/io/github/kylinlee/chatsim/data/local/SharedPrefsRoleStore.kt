package io.github.kylinlee.chatsim.data.local

import android.content.Context
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPrefsRoleStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : RoleStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun loadRoles(): List<Role> {
        val raw = prefs.getString(KEY_ROLES, null)
        if (raw != null) {
            return runCatching { json.decodeFromString<List<Role>>(raw) }.getOrDefault(emptyList())
        }

        return migrateLegacySmallNumbers()
    }

    override fun saveRoles(roles: List<Role>) {
        prefs.edit().putString(KEY_ROLES, json.encodeToString(roles)).apply()
    }

    override fun loadActiveId(): Long? {
        if (!prefs.contains(KEY_ACTIVE_ROLE_ID)) return null
        val id = prefs.getLong(KEY_ACTIVE_ROLE_ID, NO_ACTIVE_ID)
        return if (id == NO_ACTIVE_ID) null else id
    }

    override fun saveActiveId(id: Long?) {
        prefs.edit().putLong(KEY_ACTIVE_ROLE_ID, id ?: NO_ACTIVE_ID).apply()
    }

    /** The previous "small numbers" become virtual roles. */
    private fun migrateLegacySmallNumbers(): List<Role> {
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val raw = legacyPrefs.getString(LEGACY_KEY_SMALL_NUMBERS, null) ?: return emptyList()
        val legacyRoles = runCatching { json.decodeFromString<List<Role>>(raw) }
            .getOrDefault(emptyList())
            .map { it.copy(kind = RoleKind.VIRTUAL) }
        if (legacyRoles.isEmpty()) return emptyList()

        saveRoles(legacyRoles)
        val legacyActiveId = legacyPrefs.getLong(LEGACY_KEY_ACTIVE_SMALL_NUMBER_ID, NO_ACTIVE_ID)
        if (legacyActiveId != NO_ACTIVE_ID) {
            saveActiveId(legacyActiveId)
        }
        return legacyRoles
    }

    companion object {
        private const val PREFS_NAME = "roles"
        private const val KEY_ROLES = "roles"
        private const val KEY_ACTIVE_ROLE_ID = "active_role_id"
        private const val NO_ACTIVE_ID = -1L

        private const val LEGACY_PREFS_NAME = "small_numbers"
        private const val LEGACY_KEY_SMALL_NUMBERS = "small_numbers"
        private const val LEGACY_KEY_ACTIVE_SMALL_NUMBER_ID = "active_small_number_id"
    }
}
