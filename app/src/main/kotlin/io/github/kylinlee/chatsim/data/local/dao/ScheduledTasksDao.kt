package io.github.kylinlee.chatsim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.kylinlee.chatsim.data.model.ScheduledTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTasksDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(task: ScheduledTaskEntity): Long

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    fun getById(id: Long): ScheduledTaskEntity?

    @Query("SELECT * FROM scheduled_tasks")
    fun getAll(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE state = 'PENDING' ORDER BY trigger_at")
    fun getPending(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE state = 'PENDING' ORDER BY trigger_at")
    fun observePending(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE state = 'PENDING' AND conversation_id = :conversationId ORDER BY trigger_at")
    fun getPendingByConversation(conversationId: Long): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE state = 'PENDING' AND conversation_id = :conversationId ORDER BY trigger_at")
    fun observePendingByConversation(conversationId: Long): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE state = 'RUNNING' AND updated_at < :updatedBefore")
    fun getStaleRunning(updatedBefore: Long): List<ScheduledTaskEntity>

    /** 原子认领：仅 PENDING 可转 RUNNING，返回影响行数，0 表示已被取消或已执行。 */
    @Query(
        "UPDATE scheduled_tasks SET state = 'RUNNING', attempts = attempts + 1, updated_at = :now " +
            "WHERE id = :id AND state = 'PENDING'"
    )
    fun claim(id: Long, now: Long): Int

    /** 原子取消：仅 PENDING 可取消，返回影响行数。 */
    @Query("UPDATE scheduled_tasks SET state = 'CANCELLED', updated_at = :now WHERE id = :id AND state = 'PENDING'")
    fun cancelPending(id: Long, now: Long): Int

    @Query("UPDATE scheduled_tasks SET state = 'DONE', updated_at = :now, last_error = NULL WHERE id = :id AND state = 'RUNNING'")
    fun markDone(id: Long, now: Long): Int

    @Query(
        "UPDATE scheduled_tasks SET state = 'FAILED', updated_at = :now, last_error = :error " +
            "WHERE id = :id AND state IN ('PENDING', 'RUNNING')"
    )
    fun markFailed(id: Long, now: Long, error: String?): Int

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM scheduled_tasks WHERE conversation_id = :conversationId")
    fun deleteByConversation(conversationId: Long)
}
