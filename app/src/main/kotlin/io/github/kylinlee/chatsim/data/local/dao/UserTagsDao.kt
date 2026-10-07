package io.github.kylinlee.chatsim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.kylinlee.chatsim.data.model.UserTagEntity

@Dao
interface UserTagsDao {
    @Query("SELECT * FROM user_tags ORDER BY is_builtin DESC, name")
    fun getAll(): List<UserTagEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM user_tags WHERE name = :name)")
    fun exists(name: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(tag: UserTagEntity)

    @Query("DELETE FROM user_tags WHERE name = :name AND is_builtin = 0")
    fun deleteCustom(name: String): Int
}
