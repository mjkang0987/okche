package app.okche.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 기기 안 저장소. 엔티티를 바꾸면 version을 올리고 마이그레이션을 더한다(스키마 JSON은 app/schemas/).
 * 마이그레이션이 없으면 열 때 멈춘다 — 지우고 새로 만드는 설정(fallbackToDestructiveMigration)은 쓰지 않는다.
 */
@Database(
    entities = [
        Pet::class, RestPeriod::class, Record::class, Medication::class, MedSchedule::class, InsulinPlan::class,
        Food::class, FoodChange::class, Snack::class, MealPlan::class, Photo::class, PhotoPet::class, Setting::class,
    ],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class OkcheDatabase : RoomDatabase() {
    abstract fun dao(): OkcheDao

    companion object {
        @Volatile private var instance: OkcheDatabase? = null

        fun get(context: Context): OkcheDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OkcheDatabase::class.java, "okche.db").build()
                .also { instance = it }
        }
    }
}
