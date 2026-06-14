package com.example.se114_callingsystem.core.di;

import android.content.Context;
import androidx.room.Room;
import com.example.se114_callingsystem.features.chat.data.AppDatabase;
import com.example.se114_callingsystem.features.chat.data.MessageDao;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {

    @Provides
    @Singleton
    public static AppDatabase provideAppDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(
                context.getApplicationContext(),
                AppDatabase.class,
                "calling_system_db"
        ).fallbackToDestructiveMigration().build();
    }

    @Provides
    @Singleton
    public static MessageDao provideMessageDao(AppDatabase database) {
        return database.messageDao();
    }
}
