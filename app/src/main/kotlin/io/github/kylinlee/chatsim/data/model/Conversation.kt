package io.github.kylinlee.chatsim.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A conversation is uniquely identified by the pair (role, peer number), where the role is one of
 * my own numbers (a SIM card or a virtual number) and the peer number has my virtual prefixes
 * stripped.
 */
@Entity(
    tableName = "conversations",
    indices = [(Index(value = ["role_id", "peer_number"], unique = true))]
)
data class Conversation(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "conversation_id") var conversationId: Long = 0,
    @ColumnInfo(name = "role_id") var roleId: Long,
    @ColumnInfo(name = "peer_number") var peerNumber: String,
    @ColumnInfo(name = "snippet") var snippet: String,
    @ColumnInfo(name = "date") var date: Int,
    @ColumnInfo(name = "read") var read: Boolean,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "photo_uri") var photoUri: String,
    @ColumnInfo(name = "is_group_conversation") var isGroupConversation: Boolean,
    @ColumnInfo(name = "is_scheduled") var isScheduled: Boolean = false,
    @ColumnInfo(name = "uses_custom_title") var usesCustomTitle: Boolean = false
) {

    companion object {
        fun areItemsTheSame(old: Conversation, new: Conversation): Boolean {
            return old.conversationId == new.conversationId
        }

        fun areContentsTheSame(old: Conversation, new: Conversation): Boolean {
            return old.roleId == new.roleId &&
                old.peerNumber == new.peerNumber &&
                old.snippet == new.snippet &&
                old.date == new.date &&
                old.read == new.read &&
                old.title == new.title &&
                old.photoUri == new.photoUri &&
                old.isGroupConversation == new.isGroupConversation
        }
    }
}
