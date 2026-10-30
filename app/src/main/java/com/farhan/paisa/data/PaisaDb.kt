package com.farhan.paisa.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PaisaDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM txns ORDER BY date DESC, id DESC") fun txns(): Flow<List<Txn>>
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE") fun tags(): Flow<List<Tag>>
    @Query("SELECT * FROM txn_tags") fun txnTags(): Flow<List<TxnTag>>
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE") fun people(): Flow<List<Person>>
    @Query("SELECT * FROM split_shares") fun shares(): Flow<List<SplitShare>>
    @Query("SELECT * FROM detected ORDER BY date DESC, id DESC") fun detected(): Flow<List<Detected>>
    @Query("SELECT * FROM recurring ORDER BY day, id") fun recurring(): Flow<List<Recurring>>
    @Query("SELECT * FROM goals ORDER BY id") fun goals(): Flow<List<Goal>>
    @Query("SELECT * FROM goal_entries ORDER BY date DESC, id DESC") fun goalEntries(): Flow<List<GoalEntry>>

    @Query("SELECT * FROM detected") suspend fun allDetected(): List<Detected>
    @Query("SELECT * FROM recurring") suspend fun allRecurring(): List<Recurring>
    @Query("SELECT * FROM goals") suspend fun allGoals(): List<Goal>
    @Query("SELECT * FROM goal_entries") suspend fun allGoalEntries(): List<GoalEntry>
    @Query("SELECT * FROM accounts") suspend fun allAccounts(): List<Account>
    @Query("SELECT * FROM txns") suspend fun allTxns(): List<Txn>
    @Query("SELECT * FROM categories") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM tags") suspend fun allTags(): List<Tag>
    @Query("SELECT * FROM txn_tags") suspend fun allTxnTags(): List<TxnTag>
    @Query("SELECT * FROM people") suspend fun allPeople(): List<Person>
    @Query("SELECT * FROM split_shares") suspend fun allShares(): List<SplitShare>

    @Query("SELECT COUNT(*) FROM txns WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun txnCountFor(accountId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAccount(item: Account): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategory(item: Category): Long
    @Insert suspend fun insertTag(item: Tag): Long
    @Insert suspend fun insertPerson(item: Person): Long
    @Insert suspend fun insertTxn(item: Txn): Long

    @Insert suspend fun insertAccounts(items: List<Account>)
    @Insert suspend fun insertCategories(items: List<Category>)
    @Insert suspend fun insertTags(items: List<Tag>)
    @Insert suspend fun insertPeople(items: List<Person>)
    @Insert suspend fun insertTxns(items: List<Txn>)
    @Insert suspend fun insertTxnTags(items: List<TxnTag>)
    @Insert suspend fun insertShares(items: List<SplitShare>)
    @Insert suspend fun insertDetected(items: List<Detected>)
    @Insert suspend fun insertRecurring(items: List<Recurring>)
    @Insert suspend fun insertGoals(items: List<Goal>)
    @Insert suspend fun insertGoalEntries(items: List<GoalEntry>)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDetected(item: Detected): Long
    @Insert suspend fun insertRecurring(item: Recurring): Long
    @Insert suspend fun insertGoal(item: Goal): Long
    @Insert suspend fun insertGoalEntry(item: GoalEntry): Long

    @Update suspend fun updateDetected(item: Detected)
    @Update suspend fun updateRecurring(item: Recurring)
    @Update suspend fun updateGoal(item: Goal)
    @Delete suspend fun deleteRecurring(item: Recurring)
    @Delete suspend fun deleteGoal(item: Goal)

    @Update suspend fun updateAccount(item: Account)
    @Update suspend fun updateCategory(item: Category)
    @Update suspend fun updateTag(item: Tag)
    @Update suspend fun updatePerson(item: Person)
    @Update suspend fun updateTxn(item: Txn)

    @Delete suspend fun deleteAccount(item: Account)
    @Delete suspend fun deleteCategory(item: Category)
    @Delete suspend fun deleteTag(item: Tag)
    @Delete suspend fun deletePerson(item: Person)

    @Query("DELETE FROM txns WHERE id = :id") suspend fun deleteTxn(id: Long)
    @Query("DELETE FROM txn_tags WHERE txnId = :txnId") suspend fun clearTagsFor(txnId: Long)
    @Query("DELETE FROM split_shares WHERE txnId = :txnId") suspend fun clearSharesFor(txnId: Long)
    @Query("UPDATE txns SET categoryId = NULL WHERE categoryId = :categoryId") suspend fun detachCategory(categoryId: Long)
    @Query("UPDATE recurring SET categoryId = NULL WHERE categoryId = :categoryId") suspend fun detachBills(categoryId: Long)
    @Query("DELETE FROM txns WHERE type = 'SETTLEMENT' AND personId = :personId") suspend fun deleteSettlementsBy(personId: Long)

    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM txns") suspend fun clearTxns()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM detected") suspend fun clearDetected()
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM goals") suspend fun clearGoals()
}

@Database(
    entities = [
        Account::class, Txn::class, Category::class, Tag::class, TxnTag::class, Person::class, SplitShare::class,
        Detected::class, Recurring::class, Goal::class, GoalEntry::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PaisaDb : RoomDatabase() {
    abstract fun dao(): PaisaDao

    companion object {
        fun create(context: Context): PaisaDb =
            Room.databaseBuilder(context, PaisaDb::class.java, "paisa.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** Adds SMS detection, recurring bills and goals. Existing tables are left untouched. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `detected` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`dedupKey` TEXT NOT NULL, `type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `counterparty` TEXT NOT NULL, " +
                "`last4` TEXT NOT NULL, `toLast4` TEXT NOT NULL, `method` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, `txnId` INTEGER)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_detected_dedupKey` ON `detected` (`dedupKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_detected_status` ON `detected` (`status`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `amount` INTEGER NOT NULL, `day` INTEGER NOT NULL, `categoryId` INTEGER, " +
                "`accountId` INTEGER, `method` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `target` INTEGER NOT NULL, `targetDay` INTEGER, `accent` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`goalId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `date` INTEGER NOT NULL, " +
                "FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_entries_goalId` ON `goal_entries` (`goalId`)")
    }
}
