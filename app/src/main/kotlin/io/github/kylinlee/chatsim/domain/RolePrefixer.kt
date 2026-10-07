package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel

object RolePrefixer {
    private val EMERGENCY_NUMBERS = setOf(
        "110", "112", "119", "120", "122", "911", "999", "000", "08", "118"
    )

    private const val MIN_PREFIXABLE_DIGITS = 7

    fun normalize(number: String): String {
        val trimmed = number.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digits" else digits
    }

    fun isPrefixable(number: String): Boolean {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains('*') || trimmed.contains('#')) return false

        val normalized = normalize(trimmed)
        val digits = normalized.removePrefix("+")
        if (digits.length < MIN_PREFIXABLE_DIGITS) return false
        if (normalized in EMERGENCY_NUMBERS) return false

        return true
    }

    fun matchesPrefix(number: String, prefix: String): Boolean {
        if (number.isBlank() || prefix.isBlank()) return false
        if (number.startsWith(prefix)) return true

        val normalizedNumber = normalize(number)
        val normalizedPrefix = normalize(prefix)
        return normalizedPrefix.isNotEmpty() && normalizedNumber.startsWith(normalizedPrefix)
    }

    fun stripPrefix(number: String, prefix: String): String {
        if (prefix.isBlank()) return number

        if (number.startsWith(prefix)) {
            return number.removePrefix(prefix)
        }

        val normalizedNumber = normalize(number)
        val normalizedPrefix = normalize(prefix)
        return if (normalizedPrefix.isNotEmpty() && normalizedNumber.startsWith(normalizedPrefix)) {
            normalizedNumber.removePrefix(normalizedPrefix)
        } else {
            number
        }
    }

    fun stripPrefix(number: String, role: Role, channel: RoleChannel): String =
        stripPrefix(number, role.prefixFor(channel).trim())

    fun applyPrefix(number: String, role: Role?, channel: RoleChannel): String {
        if (role == null || !role.enabled || !role.isVirtual) return number

        val prefix = role.prefixFor(channel).trim()
        if (prefix.isEmpty()) return number
        if (!isPrefixable(number)) return number
        if (number.startsWith(prefix)) return number

        return prefix + number
    }

    fun matchRole(number: String, roles: List<Role>, channel: RoleChannel): Role? {
        if (number.isBlank()) return null

        return roles
            .asSequence()
            .filter { it.enabled && it.isVirtual }
            .mapNotNull { role ->
                val prefix = role.prefixFor(channel).trim()
                if (prefix.isEmpty() || !matchesPrefix(number, prefix)) null else role to prefix
            }
            .maxByOrNull { (_, prefix) -> prefix.length }
            ?.first
    }

    fun stripPrefix(number: String, roles: List<Role>, channel: RoleChannel): String {
        val match = matchRole(number, roles, channel) ?: return number
        return stripPrefix(number, match.prefixFor(channel).trim())
    }

    fun isAlreadyPrefixed(number: String, roles: List<Role>, channel: RoleChannel): Boolean =
        matchRole(number, roles, channel) != null
}
