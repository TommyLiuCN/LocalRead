package com.example.localread

import android.app.Application
import androidx.room.Room
import com.example.localread.data.db.AppDatabase

class LocalReadApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "localread.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
}
