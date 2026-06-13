package com.example.data

import android.content.Context
import androidx.room.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

// --- Models & Entities ---

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val selectedChallenges: String, // Comma separated IDs (e.g. "anxiety,work")
    val decisionStyle: String?,
    val dailyReflection: String? = null,
    val dailyReflectionDate: String? = null, // yyyy-MM-dd or Date String
    val streak: Int = 0,
    val lastActiveDate: String? = null,
    val googleEmail: String? = null,
    val googleName: String? = null
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String, // UUID or timestamp
    val date: String,          // ISO timestamp
    val title: String,
    val messageCount: Int,
    val messagesJson: String,  // Serialized list of ChatMessage
    val type: String           // "chat", "session", "decider"
)

data class ChatMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

// --- DAO ---

@Dao
interface AppDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfileSync(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM chat_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun clearAllSessions()

    @Query("DELETE FROM user_profile")
    suspend fun clearUserProfile()
}

// --- Database ---

@Database(entities = [UserProfileEntity::class, ChatSessionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "espelho_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Serializer Helper ---

object JsonHelpers {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val messagesListType = Types.newParameterizedType(List::class.java, ChatMessage::class.java)
    private val messagesAdapter = moshi.adapter<List<ChatMessage>>(messagesListType)

    fun serializeMessages(messages: List<ChatMessage>): String {
        return messagesAdapter.toJson(messages)
    }

    fun deserializeMessages(json: String): List<ChatMessage> {
        return try {
            messagesAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// --- Repository ---

class AppRepository(private val dao: AppDao) {
    val userProfile: Flow<UserProfileEntity?> = dao.getUserProfile()
    val allSessions: Flow<List<ChatSessionEntity>> = dao.getAllSessions()

    suspend fun insertUserProfile(profile: UserProfileEntity) {
        dao.insertUserProfile(profile)
    }

    suspend fun getUserProfileSync(): UserProfileEntity? {
        return dao.getUserProfileSync()
    }

    suspend fun insertSession(session: ChatSessionEntity) {
        dao.insertSession(session)
    }

    suspend fun getSessionById(sessionId: String): ChatSessionEntity? {
        return dao.getSessionById(sessionId)
    }

    suspend fun deleteSessionById(sessionId: String) {
        dao.deleteSessionById(sessionId)
    }

    suspend fun clearAllData() {
        dao.clearAllSessions()
        dao.clearUserProfile()
    }
}
