package com.silentstudy.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.silentstudy.data.model.Answer
import kotlinx.coroutines.flow.Flow

@Dao
interface AnswerDao {
    @Query("SELECT * FROM answers ORDER BY id DESC")
    fun getAllAnswers(): Flow<List<Answer>>

    @Query("SELECT * FROM answers WHERE id = :id LIMIT 1")
    suspend fun getAnswerById(id: Long): Answer?

    @Query("SELECT * FROM answers WHERE subject = :subject ORDER BY id DESC")
    fun getAnswersBySubject(subject: String): Flow<List<Answer>>

    @Query("SELECT * FROM answers WHERE starred = 1 ORDER BY id DESC")
    fun getStarredAnswers(): Flow<List<Answer>>

    @Query("SELECT * FROM answers WHERE questionText LIKE '%' || :query || '%' OR shortAnswer LIKE '%' || :query || '%' ORDER BY id DESC")
    fun searchAnswers(query: String): Flow<List<Answer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswer(answer: Answer): Long

    @Update
    suspend fun updateAnswer(answer: Answer)

    @Delete
    suspend fun deleteAnswer(answer: Answer)

    @Query("UPDATE answers SET starred = NOT starred WHERE id = :id")
    suspend fun toggleStar(id: Long)

    @Query("UPDATE answers SET lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun updateLastPlayed(id: Long, timestamp: Long)
}
