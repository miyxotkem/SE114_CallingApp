package com.example.se114_callingsystem.features.chat.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(CachedMessage message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateAll(List<CachedMessage> messages);

    @Query("SELECT * FROM cached_messages WHERE receiverId = :groupId ORDER BY timestamp ASC")
    LiveData<List<CachedMessage>> getMessagesForGroup(String groupId);

    @Query("SELECT * FROM cached_messages WHERE receiverId = :groupId AND (content LIKE :query OR fileUrl LIKE :query) ORDER BY timestamp DESC")
    List<CachedMessage> searchMessages(String groupId, String query);

    @Query("DELETE FROM cached_messages WHERE receiverId = :groupId")
    void clearMessagesForGroup(String groupId);
}
