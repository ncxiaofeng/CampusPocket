package com.ecut.campuspocket.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        AuthCredentialEntity::class,
        CookieEntity::class,
        ElectricityRecordEntity::class,
        CourseEntity::class,
        GradeEntity::class,
        ExamEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun authDao(): AuthDao
    abstract fun cookieDao(): CookieDao
    abstract fun electricityDao(): ElectricityDao
    abstract fun courseDao(): CourseDao
    abstract fun gradeDao(): GradeDao
    abstract fun examDao(): ExamDao

    companion object {
        private const val DB_NAME = "campus_pocket.db"

        /**
         * v2 → v3：新增考试安排表。显式迁移，避免升级时清空已保存的登录态。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exams` (" +
                        "`key` TEXT NOT NULL, `courseName` TEXT NOT NULL, " +
                        "`courseCode` TEXT NOT NULL, `examTime` TEXT NOT NULL, " +
                        "`examDate` TEXT NOT NULL, `startClock` TEXT NOT NULL, " +
                        "`endClock` TEXT NOT NULL, `examName` TEXT NOT NULL, " +
                        "`examType` TEXT NOT NULL, `location` TEXT NOT NULL, " +
                        "`campus` TEXT NOT NULL, `seatNo` TEXT NOT NULL, " +
                        "`ticketNo` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                        "`term` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`key`))"
                )
            }
        }

        /**
         * v3 → v4：课表增加精确节次与校区。旧数据按 2 节一个时间块回填。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE course_blocks ADD COLUMN startPeriod INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE course_blocks ADD COLUMN endPeriod INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE course_blocks ADD COLUMN campus TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "UPDATE course_blocks SET " +
                        "startPeriod = startSlot * 2 + 1, " +
                        "endPeriod = startSlot * 2 + slotSpan * 2 " +
                        "WHERE startPeriod = 0"
                )
            }
        }

        fun create(context: Context): CampusDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyManager(context).getOrCreatePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                CampusDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
