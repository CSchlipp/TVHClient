package org.tvheadend.tvhclient.domain.repository.data_source

import android.os.AsyncTask
import androidx.lifecycle.LiveData
import org.tvheadend.tvhclient.data.db.AppRoomDatabase
import org.tvheadend.tvhclient.domain.entity.ChannelTag
import timber.log.Timber
import java.util.*
import java.util.concurrent.ExecutionException

class ChannelTagData(private val db: AppRoomDatabase) : DataSourceInterface<ChannelTag> {

    val liveDataSelectedItemIds: LiveData<List<Int>>
        get() = db.channelTagDao.loadAllSelectedItemIds()

    val itemCount: Int
        get() {
            try {
                return ChannelTagCountTask(db).execute().get()
            } catch (e: InterruptedException) {
                Timber.d("Loading channel tag count task got interrupted", e)
            } catch (e: ExecutionException) {
                Timber.d("Loading channel tag count task aborted", e)
            }

            return 0
        }

    override fun addItem(item: ChannelTag) {
        AsyncTask.execute { db.channelTagDao.insert(item) }
    }

    fun addItems(items: List<ChannelTag>) {
        AsyncTask.execute { db.channelTagDao.insert(ArrayList(items)) }
    }

    override fun updateItem(item: ChannelTag) {
        AsyncTask.execute { db.channelTagDao.update(item) }
    }

    override fun removeItem(item: ChannelTag) {
        AsyncTask.execute { db.channelTagDao.delete(item) }
    }

    fun updateSelectedChannelTags(ids: Set<Int>) {
        AsyncTask.execute {
            val channelTags = db.channelTagDao.loadAllChannelTagsSync()
            for (channelTag in channelTags) {
                channelTag.isSelected = false
                if (ids.contains(channelTag.tagId)) {
                    channelTag.isSelected = true
                }
            }
            db.channelTagDao.update(channelTags)
        }
    }


    override fun getLiveDataItemCount(): LiveData<Int>? {
        return null
    }

    override fun getLiveDataItems(): LiveData<List<ChannelTag>>? {
        return db.channelTagDao.loadAllChannelTags()
    }

    override fun getLiveDataItemById(id: Any): LiveData<ChannelTag>? {
        return null
    }

    override fun getItemById(id: Any): ChannelTag? {
        try {
            return ChannelTagByIdTask(db, id as Int).execute().get()
        } catch (e: InterruptedException) {
            Timber.d("Loading channel tag by id task got interrupted", e)
        } catch (e: ExecutionException) {
            Timber.d("Loading channel tag by id task aborted", e)
        }

        return null
    }

    override fun getItems(): List<ChannelTag> {
        var channelTags: List<ChannelTag> = ArrayList()
        try {
            channelTags = ChannelTagListTask(db).execute().get()
        } catch (e: InterruptedException) {
            Timber.d("Loading all channel tags task got interrupted", e)
        } catch (e: ExecutionException) {
            Timber.d("Loading all channel tags task aborted", e)
        }

        return channelTags
    }

    private class ChannelTagListTask internal constructor(private val db: AppRoomDatabase) : AsyncTask<Void, Void, List<ChannelTag>>() {

        override fun doInBackground(vararg voids: Void): List<ChannelTag> {
            return db.channelTagDao.loadAllChannelTagsSync()
        }
    }

    private class ChannelTagCountTask internal constructor(private val db: AppRoomDatabase) : AsyncTask<Void, Void, Int>() {

        override fun doInBackground(vararg voids: Void): Int? {
            return db.channelTagDao.itemCountSync
        }
    }

    private class ChannelTagByIdTask internal constructor(private val db: AppRoomDatabase, private val id: Int) : AsyncTask<Void, Void, ChannelTag>() {

        override fun doInBackground(vararg voids: Void): ChannelTag {
            return db.channelTagDao.loadChannelTagByIdSync(id)
        }
    }
}
