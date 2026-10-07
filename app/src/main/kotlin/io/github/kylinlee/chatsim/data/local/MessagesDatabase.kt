package io.github.kylinlee.chatsim.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.kylinlee.chatsim.data.local.Converters
import io.github.kylinlee.chatsim.data.local.dao.AttachmentsDao
import io.github.kylinlee.chatsim.data.local.dao.ConversationsDao
import io.github.kylinlee.chatsim.data.local.dao.MessageAttachmentsDao
import io.github.kylinlee.chatsim.data.local.dao.MessageTagsDao
import io.github.kylinlee.chatsim.data.local.dao.MessagesDao
import io.github.kylinlee.chatsim.data.local.dao.RuleHitsDao
import io.github.kylinlee.chatsim.data.local.dao.ScheduledTasksDao
import io.github.kylinlee.chatsim.data.local.dao.UserRulesDao
import io.github.kylinlee.chatsim.data.local.dao.UserTagsDao
import io.github.kylinlee.chatsim.data.model.*
import io.github.kylinlee.chatsim.domain.rule.user.BuiltinTags

@Database(
    entities = [
        Conversation::class,
        Attachment::class,
        MessageAttachment::class,
        Message::class,
        RecycleBinMessage::class,
        UserRuleEntity::class,
        UserRuleTagEntity::class,
        UserTagEntity::class,
        MessageTagEntity::class,
        RuleHitEntity::class,
        ScheduledTaskEntity::class,
    ],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class MessagesDatabase : RoomDatabase() {

    abstract fun ConversationsDao(): ConversationsDao

    abstract fun AttachmentsDao(): AttachmentsDao

    abstract fun MessageAttachmentsDao(): MessageAttachmentsDao

    abstract fun MessagesDao(): MessagesDao

    abstract fun UserRulesDao(): UserRulesDao

    abstract fun UserTagsDao(): UserTagsDao

    abstract fun MessageTagsDao(): MessageTagsDao

    abstract fun RuleHitsDao(): RuleHitsDao

    abstract fun ScheduledTasksDao(): ScheduledTasksDao

    companion object {
        private var db: MessagesDatabase? = null

        private fun seedBuiltinTags(db: SupportSQLiteDatabase) {
            BuiltinTags.all.forEach { tag ->
                db.execSQL(
                    "INSERT OR IGNORE INTO `user_tags` (`name`, `is_builtin`, `created_at`) VALUES (?, 1, ?)",
                    arrayOf(tag, System.currentTimeMillis()),
                )
            }
        }

        fun getInstance(context: Context): MessagesDatabase {
            if (db == null) {
                synchronized(MessagesDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, MessagesDatabase::class.java, "conversations.db")
                            .addCallback(object : RoomDatabase.Callback() {
                                override fun onCreate(db: SupportSQLiteDatabase) {
                                    seedBuiltinTags(db)
                                }
                            })
                            .build()
                    }
                }
            }
            return db!!
        }
    }
}
