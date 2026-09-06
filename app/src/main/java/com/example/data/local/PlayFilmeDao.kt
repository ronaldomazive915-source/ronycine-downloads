package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayFilmeDao {

    // --- Media Catalog ---
    @Query("SELECT * FROM media_catalog ORDER BY addedAt DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_catalog")
    suspend fun getAllMediaSync(): List<MediaEntity>

    @Query("SELECT * FROM media_catalog WHERE mediaType = :type ORDER BY addedAt DESC")
    fun getMediaByType(type: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_catalog WHERE mediaType = :type ORDER BY addedAt DESC")
    suspend fun getMediaByTypeSync(type: String): List<MediaEntity>

    @Query("SELECT * FROM media_catalog WHERE tmdbId = :tmdbId AND mediaType = :type LIMIT 1")
    suspend fun getMediaByTmdbIdAndType(tmdbId: Int, type: String): MediaEntity?

    @Query("SELECT * FROM media_catalog WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getMediaByTmdbId(tmdbId: Int): MediaEntity?

    @Query("SELECT * FROM media_catalog WHERE tmdbId = :tmdbId LIMIT 1")
    fun observeMediaByTmdbId(tmdbId: Int): Flow<MediaEntity?>

    @Query("SELECT COUNT(*) FROM media_catalog")
    suspend fun getMediaCount(): Int

    @Query("SELECT COUNT(*) FROM media_catalog WHERE mediaType = 'movie'")
    suspend fun getMovieCount(): Int

    @Query("SELECT COUNT(*) FROM media_catalog WHERE mediaType = 'tv'")
    suspend fun getSeriesCount(): Int

    @Query("SELECT COUNT(*) FROM media_catalog WHERE mediaType = 'movie'")
    fun observeMovieCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_catalog WHERE mediaType = 'tv'")
    fun observeSeriesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM episodes")
    fun observeEpisodeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM episodes")
    suspend fun getEpisodeCount(): Int

    @Query("SELECT COUNT(*) FROM my_list")
    fun observeMyListCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM watch_history")
    fun observeWatchHistoryCount(): Flow<Int>

    @Query("SELECT * FROM media_catalog ORDER BY addedAt DESC LIMIT 1")
    fun observeLatestAddedMedia(): Flow<MediaEntity?>

    @Query("SELECT * FROM media_catalog WHERE mediaType = 'movie' ORDER BY addedAt DESC LIMIT 1")
    fun observeLatestMovie(): Flow<MediaEntity?>

    @Query("SELECT * FROM media_catalog WHERE mediaType = 'tv' ORDER BY addedAt DESC LIMIT 1")
    fun observeLatestSeries(): Flow<MediaEntity?>

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT 1")
    fun observeLatestWatchHistory(): Flow<WatchHistoryEntity?>

    @Query("SELECT * FROM media_catalog ORDER BY rating DESC LIMIT 10")
    fun observePopularMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_catalog WHERE title LIKE '%' || :query || '%' OR overview LIKE '%' || :query || '%'")
    fun searchLocalMedia(query: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_catalog WHERE isHeroFeatured = 1")
    fun getFeaturedHeroMedia(): Flow<List<MediaEntity>>

    // --- Featured Media Management ---
    @Query("SELECT * FROM featured_media ORDER BY displayOrder ASC, createdAt DESC")
    fun getAllFeaturedMedia(): Flow<List<FeaturedMediaEntity>>

    @Query("SELECT * FROM featured_media ORDER BY displayOrder ASC, createdAt DESC")
    suspend fun getAllFeaturedMediaSync(): List<FeaturedMediaEntity>

    @Query("SELECT * FROM featured_media WHERE isActive = 1 ORDER BY displayOrder ASC, createdAt DESC")
    fun getActiveFeaturedMedia(): Flow<List<FeaturedMediaEntity>>

    @Query("SELECT * FROM featured_media WHERE mediaTmdbId = :tmdbId LIMIT 1")
    suspend fun getFeaturedByTmdbId(tmdbId: Int): FeaturedMediaEntity?

    @Query("SELECT * FROM featured_media WHERE id = :id LIMIT 1")
    suspend fun getFeaturedById(id: Int): FeaturedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeaturedMedia(item: FeaturedMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeaturedMediaList(items: List<FeaturedMediaEntity>)

    @Update
    suspend fun updateFeaturedMedia(item: FeaturedMediaEntity)

    @Query("DELETE FROM featured_media WHERE id = :id")
    suspend fun deleteFeaturedMediaById(id: Int)

    @Query("DELETE FROM featured_media WHERE mediaTmdbId = :tmdbId")
    suspend fun deleteFeaturedMediaByTmdbId(tmdbId: Int)

    @Query("UPDATE featured_media SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFeaturedActiveStatus(id: Int, isActive: Boolean, updatedAt: Long = System.currentTimeMillis())


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaList(list: List<MediaEntity>)

    @Query("SELECT * FROM media_catalog WHERE mediaType = :type AND tmdbId != :excludeTmdbId ORDER BY rating DESC LIMIT 8")
    fun getSimilarMedia(type: String, excludeTmdbId: Int): Flow<List<MediaEntity>>

    @Query("DELETE FROM media_catalog WHERE tmdbId = :tmdbId")
    suspend fun deleteMediaByTmdbId(tmdbId: Int)

    @Query("DELETE FROM episodes WHERE mediaTmdbId = :tmdbId")
    suspend fun deleteEpisodesByMediaId(tmdbId: Int)

    @Query("DELETE FROM my_list WHERE tmdbId = :tmdbId")
    suspend fun deleteFromMyList(tmdbId: Int)

    @Query("DELETE FROM watch_history WHERE tmdbId = :tmdbId")
    suspend fun deleteFromWatchHistory(tmdbId: Int)

    // --- Episodes ---
    @Query("SELECT * FROM episodes WHERE mediaTmdbId = :tmdbId AND seasonNumber = :seasonNumber ORDER BY episodeNumber ASC")
    fun getEpisodesForSeason(tmdbId: Int, seasonNumber: Int): Flow<List<EpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE mediaTmdbId = :tmdbId AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber")
    suspend fun deleteSingleEpisode(tmdbId: Int, seasonNumber: Int, episodeNumber: Int)

    @Query("SELECT * FROM episodes WHERE mediaTmdbId = :tmdbId ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun getAllEpisodesForMedia(tmdbId: Int): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE mediaTmdbId = :tmdbId ORDER BY seasonNumber ASC, episodeNumber ASC")
    suspend fun getAllEpisodesForMediaSync(tmdbId: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes")
    suspend fun getAllEpisodesSync(): List<EpisodeEntity>

    // --- Live TV Channels ---
    @Query("SELECT * FROM live_channels ORDER BY name ASC")
    fun getAllLiveChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM live_channels ORDER BY name ASC")
    suspend fun getAllChannelsSync(): List<ChannelEntity>

    @Query("SELECT * FROM live_channels WHERE id = :id LIMIT 1")
    suspend fun getLiveChannelById(id: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM live_channels WHERE id = :id")
    suspend fun deleteChannelById(id: String)

    @Query("DELETE FROM live_channels")
    suspend fun clearLiveChannels()

    // --- My List ---
    @Query("SELECT m.* FROM media_catalog m INNER JOIN my_list l ON m.tmdbId = l.tmdbId ORDER BY l.addedAt DESC")
    fun getMyList(): Flow<List<MediaEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM my_list WHERE tmdbId = :tmdbId)")
    fun isMediaInMyList(tmdbId: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToMyList(item: MyListEntity)

    @Query("DELETE FROM my_list WHERE tmdbId = :tmdbId")
    suspend fun removeFromMyList(tmdbId: Int)

    // --- Watch History & Continue Watching ---
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE progressPercent > 0 AND progressPercent < 98 ORDER BY watchedAt DESC")
    fun getContinueWatching(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWatchProgress(history: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE tmdbId = :tmdbId ORDER BY watchedAt DESC LIMIT 1")
    fun getWatchHistoryForMedia(tmdbId: Int): Flow<WatchHistoryEntity?>

    @Query("SELECT * FROM watch_history WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getWatchHistoryItemByTmdbId(tmdbId: Int): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE tmdbId = :tmdbId AND mediaType = :mediaType AND (:seasonNumber IS NULL OR seasonNumber = :seasonNumber) AND (:episodeNumber IS NULL OR episodeNumber = :episodeNumber) LIMIT 1")
    suspend fun getWatchHistoryItemByKey(tmdbId: Int, mediaType: String, seasonNumber: Int?, episodeNumber: Int?): WatchHistoryEntity?

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteWatchHistoryById(id: Int)

    @Query("DELETE FROM watch_history WHERE tmdbId = :tmdbId")
    suspend fun deleteWatchHistoryByTmdbId(tmdbId: Int)

    @Query("DELETE FROM watch_history")
    suspend fun clearWatchHistory()

    // --- App Settings ---
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: AppSettingsEntity)

    // --- Real-time Notifications ---
    @Query("SELECT * FROM notifications WHERE isActive = 1 ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isActive = 1 AND isRead = 0 ORDER BY timestamp DESC")
    fun getUnreadNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isActive = 1 AND isRead = 0")
    fun observeUnreadNotificationsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getTotalNotificationsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllNotificationsAsRead()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotificationById(id: String)

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()

    // --- Audit Logs ---
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAllAuditLogs()

    // --- Backup History ---
    @Query("SELECT * FROM backup_history ORDER BY timestamp DESC")
    fun getAllBackupHistory(): Flow<List<BackupHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupHistory(item: BackupHistoryEntity)

    // --- TMDB Auto Sync History ---
    @Query("SELECT * FROM tmdb_auto_sync_history ORDER BY timestamp DESC")
    fun getAllTmdbAutoSyncHistory(): Flow<List<TmdbAutoSyncHistoryEntity>>

    @Query("SELECT * FROM tmdb_auto_sync_history ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestTmdbAutoSync(): Flow<TmdbAutoSyncHistoryEntity?>

    @Query("SELECT * FROM tmdb_auto_sync_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestTmdbAutoSyncSync(): TmdbAutoSyncHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTmdbAutoSyncHistory(item: TmdbAutoSyncHistoryEntity)

    @Query("DELETE FROM tmdb_auto_sync_history")
    suspend fun clearTmdbAutoSyncHistory()

    // --- Offline Downloads ---
    @Query("SELECT * FROM download_items ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_items ORDER BY createdAt DESC")
    suspend fun getAllDownloadsSync(): List<DownloadEntity>

    @Query("SELECT * FROM download_items WHERE status = 'COMPLETED' ORDER BY completedAt DESC, createdAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_items WHERE status IN ('PENDING', 'PREPARING', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt DESC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_items WHERE id = :id LIMIT 1")
    fun observeDownloadById(id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM download_items WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query("SELECT * FROM download_items WHERE tmdbId = :tmdbId")
    fun observeDownloadsForMedia(tmdbId: Int): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_items WHERE tmdbId = :tmdbId")
    suspend fun getDownloadsForMediaSync(tmdbId: Int): List<DownloadEntity>

    @Query("SELECT * FROM download_items WHERE tmdbId = :tmdbId AND (seasonNumber = :seasonNumber OR (seasonNumber IS NULL AND :seasonNumber IS NULL)) AND (episodeNumber = :episodeNumber OR (episodeNumber IS NULL AND :episodeNumber IS NULL)) LIMIT 1")
    fun observeDownloadForEpisode(tmdbId: Int, seasonNumber: Int?, episodeNumber: Int?): Flow<DownloadEntity?>

    @Query("SELECT * FROM download_items WHERE tmdbId = :tmdbId AND (seasonNumber = :seasonNumber OR (seasonNumber IS NULL AND :seasonNumber IS NULL)) AND (episodeNumber = :episodeNumber OR (episodeNumber IS NULL AND :episodeNumber IS NULL)) LIMIT 1")
    suspend fun getDownloadForEpisodeSync(tmdbId: Int, seasonNumber: Int?, episodeNumber: Int?): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE download_items SET status = :status, progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, downloadSpeed = :downloadSpeed, localFilePath = COALESCE(:localFilePath, localFilePath), completedAt = CASE WHEN :status = 'COMPLETED' THEN :completedAt ELSE completedAt END, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateDownloadProgress(
        id: String,
        status: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        downloadSpeed: String?,
        localFilePath: String? = null,
        completedAt: Long? = null,
        errorMessage: String? = null
    )

    @Query("DELETE FROM download_items WHERE id = :id")
    suspend fun deleteDownloadById(id: String)

    @Query("DELETE FROM download_items")
    suspend fun deleteAllDownloads()
}
