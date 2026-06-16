package com.example.se114_callingsystem.features.chat.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {CachedMessage.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MessageDao messageDao();
}
