package com.silentstudy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.silentstudy.data.model.Answer
import com.silentstudy.data.model.AnswerBlock
import com.silentstudy.data.model.ApiUsageLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Answer::class, ApiUsageLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun answerDao(): AnswerDao
    abstract fun usageDao(): UsageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "silentstudy_database"
                )
                    .addCallback(DatabasePreloadCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabasePreloadCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Seed initial study answers
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = database.answerDao()
                        dao.insertAnswer(
                            Answer(
                                subject = "Mathematics",
                                questionText = "Solve 2x^2 + 5x - 3 = 0 using the quadratic formula.",
                                imagePaths = emptyList(),
                                shortAnswer = "The solutions are x = 1/2 and x = -3.",
                                simpleExplanation = "We apply the quadratic formula with coefficients 2, 5, and -3 to find the points where the curve hits zero.",
                                blocks = listOf(
                                    AnswerBlock("step", "Step 1: Coefficients are a = 2, b = 5, c = -3.", "Step 1: Coefficients are a equals 2, b equals 5, and c equals negative 3.", 1),
                                    AnswerBlock("math", "Discriminant: 5^2 - 4(2)(-3) = 49", "The discriminant evaluates to 5 squared minus 4 times 2 times negative 3, which equals 49."),
                                    AnswerBlock("step", "Step 2: Roots x = (-5 ± 7) / 4 -> x = 1/2, x = -3.", "Step 2: Taking negative 5 plus or minus 7 over 4 yields 1 half and negative 3.", 2)
                                ),
                                starred = true
                            )
                        )
                        dao.insertAnswer(
                            Answer(
                                subject = "Physics",
                                questionText = "A 1200 kg car travelling at 20 m/s brakes to a stop over 40 m. Find the average braking force.",
                                imagePaths = emptyList(),
                                shortAnswer = "The average braking force is 6,000 Newtons.",
                                simpleExplanation = "Frictional work absorbs the car's initial kinetic energy over the stopping distance.",
                                blocks = listOf(
                                    AnswerBlock("step", "Step 1: Initial Kinetic Energy E_k = 0.5 * 1200 * 20^2 = 240,000 J", "Step 1: Initial kinetic energy is one half times 1200 times 20 squared, giving 240,000 Joules.", 1),
                                    AnswerBlock("step", "Step 2: Braking Force F = E_k / distance = 240,000 / 40 = 6,000 N", "Step 2: Dividing 240,000 Joules by 40 metres gives 6,000 Newtons.", 2)
                                ),
                                starred = false
                            )
                        )
                    }
                }
            }
        }
    }
}
