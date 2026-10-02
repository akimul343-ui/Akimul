package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ProxyConfig
import com.example.data.model.SubscriptionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [SubscriptionSource::class, ProxyConfig::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun proxyConfigDao(): ProxyConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val DEFAULT_SOURCES = listOf(
            SubscriptionSource(
                name = "OpenRay V2Ray",
                url = "https://raw.githubusercontent.com/sakha1370/OpenRay/main/v2ray.txt"
            ),
            SubscriptionSource(
                name = "Proxy-Mining",
                url = "https://raw.githubusercontent.com/yitong2333/proxy-minging/main/v2ray.txt"
            ),
            SubscriptionSource(
                name = "AutoVPN V2Ray",
                url = "https://raw.githubusercontent.com/acymz/AutoVPN/main/v2ray.txt"
            ),
            SubscriptionSource(
                name = "CFG Dumper",
                url = "https://raw.githubusercontent.com/miladtahanian/V2RayCFGDumper/main/v2ray.txt"
            ),
            SubscriptionSource(
                name = "Epodonios Configs",
                url = "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/v2ray.txt"
            ),
            SubscriptionSource(
                name = "Pawdroid Free Servers",
                url = "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub"
            )
        )

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ray_collector.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch(Dispatchers.IO) {
                            getDatabase(context, scope).subscriptionDao().insertAll(DEFAULT_SOURCES)
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
