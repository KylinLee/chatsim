package io.github.kylinlee.chatsim.data.local

import io.github.kylinlee.chatsim.domain.model.Role

interface RoleStore {
    fun loadRoles(): List<Role>

    fun saveRoles(roles: List<Role>)

    fun loadActiveId(): Long?

    fun saveActiveId(id: Long?)
}
