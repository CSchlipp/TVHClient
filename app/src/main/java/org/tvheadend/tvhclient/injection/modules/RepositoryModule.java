package org.tvheadend.tvhclient.injection.modules;

import android.content.Context;
import android.support.annotation.NonNull;

import org.tvheadend.tvhclient.data.db.AppRoomDatabase;
import org.tvheadend.tvhclient.data.repository.AppRepository;
import org.tvheadend.tvhclient.data.repository.ChannelData;
import org.tvheadend.tvhclient.data.repository.ChannelTagData;
import org.tvheadend.tvhclient.data.repository.ConnectionData;
import org.tvheadend.tvhclient.data.repository.ProgramData;
import org.tvheadend.tvhclient.data.repository.RecordingData;
import org.tvheadend.tvhclient.data.repository.SeriesRecordingData;
import org.tvheadend.tvhclient.data.repository.ServerProfileData;
import org.tvheadend.tvhclient.data.repository.ServerStatusData;
import org.tvheadend.tvhclient.data.repository.TimerRecordingData;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class RepositoryModule {

    private final AppRoomDatabase appRoomDatabase;

    public RepositoryModule(Context context) {
        appRoomDatabase = AppRoomDatabase.getInstance(context);
    }

    @Singleton
    @NonNull
    @Provides
    AppRoomDatabase providesAppRoomDatabase() {
        return appRoomDatabase;
    }

    @Singleton
    @NonNull
    @Provides
    AppRepository providesAppRepository(AppRoomDatabase db, Context context) {
        return new AppRepository(
                new ChannelData(db, context),
                new ProgramData(db),
                new RecordingData(db),
                new SeriesRecordingData(db),
                new TimerRecordingData(db),
                new ConnectionData(db),
                new ChannelTagData(db),
                new ServerStatusData(db),
                new ServerProfileData(db));
    }
}