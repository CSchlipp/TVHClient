package org.tvheadend.tvhclient;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.billingclient.api.Purchase;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.facebook.stetho.Stetho;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;
import com.google.android.gms.cast.framework.media.NotificationOptions;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import org.tvheadend.tvhclient.ui.base.logging.CrashlyticsTree;
import org.tvheadend.tvhclient.ui.base.logging.DebugTree;
import org.tvheadend.tvhclient.ui.base.logging.FileLoggingTree;
import org.tvheadend.tvhclient.ui.features.purchase.BillingHandler;
import org.tvheadend.tvhclient.ui.features.purchase.BillingManager;
import org.tvheadend.tvhclient.ui.features.purchase.BillingUpdatesListener;
import org.tvheadend.tvhclient.ui.features.streaming.external.ExpandedControlsActivity;
import org.tvheadend.tvhclient.injection.MainApplicationComponent;
import org.tvheadend.tvhclient.injection.modules.MainApplicationModule;
import org.tvheadend.tvhclient.injection.modules.RepositoryModule;
import org.tvheadend.tvhclient.injection.modules.SharedPreferencesModule;
import org.tvheadend.tvhclient.utils.MigrateUtils;

import java.util.List;

import javax.inject.Inject;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;
import io.fabric.sdk.android.Fabric;
import timber.log.Timber;

import static org.tvheadend.tvhclient.utils.Constants.UNLOCKER;

// TODO use data binding
// TODO use more livedata for connection and server status in a centralized viewmodel
// TODO move diffutils to background thread
// TODO improve epg loading speed by loading programs as livedata
// TODO Use paged loading

// TODO add discord link
// TODO add timeframe menu to additional features page
// TODO hide features menu when unlocker gets available later
// TODO show channel text if no icon is available
// TODO check where injected app context can be used
// TODO restructure package into base, core, ui, utils
// TODO reduce flicker when loading webview in dark mode

public class MainApplication extends Application implements OptionsProvider, LifecycleObserver, BillingUpdatesListener {

    private BillingHandler billingHandler;
    private static MainApplication instance;
    private static boolean activityVisible;
    private RefWatcher refWatcher;
    @Inject
    protected SharedPreferences sharedPreferences;

    private static MainApplicationComponent component;
    private boolean isUnlocked;
    private BillingManager billingManager;

    public static synchronized MainApplication getInstance() {
        return instance;
    }

    public static MainApplicationComponent getComponent() {
        return component;
    }

    public static RefWatcher getRefWatcher(Context context) {
        MainApplication application = (MainApplication) context.getApplicationContext();
        return application.refWatcher;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        instance = this;
        // Create the component upon start of the app. This component
        // is used by all other classes to inject certain fields
        component = buildComponent();
        // Inject the shared preferences
        component.inject(this);

        billingHandler = new BillingHandler();
        billingHandler.addListener(this);

        // This process is dedicated to LeakCanary for heap analysis.
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        refWatcher = LeakCanary.install(this);

        // Enable stetho to enable accessing the database
        // and other resources via the chrome browser
        if (BuildConfig.DEBUG) {
            Stetho.initialize(Stetho.newInitializerBuilder(this)
                    .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
                    .enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))
                    .build());
        }

        initCrashlytics();
        initTimber();
        initBilling();

        Timber.d("Application build time is " + BuildConfig.BUILD_TIME + ", git commit hash is " + BuildConfig.GIT_SHA);

        // Migrates existing connections from the old database to the new room database.
        // Migrates existing preferences or remove old ones before starting the actual application
        new MigrateUtils().doMigrate();
    }

    private MainApplicationComponent buildComponent() {
        return DaggerMainApplicationComponent.builder()
                .mainApplicationModule(new MainApplicationModule(this))
                .sharedPreferencesModule(new SharedPreferencesModule())
                .repositoryModule(new RepositoryModule(this))
                .build();
    }

    private void initBilling() {
        billingManager = new BillingManager(this.getApplicationContext(), billingHandler);
        billingManager.queryPurchases();
    }

    private void initTimber() {
        if (BuildConfig.DEBUG || BuildConfig.DEBUG_LOG) {
            Timber.plant(new DebugTree());
        }

        if (sharedPreferences.getBoolean("debug_mode_enabled",
                getResources().getBoolean(R.bool.pref_default_debug_mode_enabled))) {
            Timber.plant(new FileLoggingTree(getApplicationContext()));
        }
        if (sharedPreferences.getBoolean("crash_reports_enabled",
                getResources().getBoolean(R.bool.pref_default_crash_reports_enabled))) {
            Timber.plant(new CrashlyticsTree());
        }
    }

    private void initCrashlytics() {
        if (sharedPreferences.getBoolean("crash_reports_enabled",
                getResources().getBoolean(R.bool.pref_default_crash_reports_enabled))) {
            // Set up Crashlytics, disabled for debug builds
            Crashlytics crashlyticsKit = new Crashlytics.Builder()
                    .core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build())
                    .build();

            // Initialize Fabric with the debug-disabled crashlytics.
            Fabric.with(this, crashlyticsKit, new Crashlytics());
        }
    }

    /**
     * Checks if the user has purchased the unlocker from the play store.
     * If yes the application is unlocked then all extra features are accessible.
     *
     * @return True if the application is unlocked otherwise false
     */
    public boolean isUnlocked() {
        return isUnlocked;
    }

    @Override
    public void onTerminate() {
        billingHandler.removeListener(this);
        super.onTerminate();
    }

    @Override
    public CastOptions getCastOptions(Context context) {
        NotificationOptions notificationOptions = new NotificationOptions.Builder()
                .setTargetActivityClassName(ExpandedControlsActivity.class.getName())
                .build();
        CastMediaOptions mediaOptions = new CastMediaOptions.Builder()
                .setNotificationOptions(notificationOptions)
                .setExpandedControllerActivityClassName(ExpandedControlsActivity.class.getName())
                .build();

        return new CastOptions.Builder()
                .setReceiverApplicationId(BuildConfig.CAST_ID)
                .setCastMediaOptions(mediaOptions)
                .build();
    }

    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }

    public BillingManager getBillingManager() {
        return billingManager;
    }

    public BillingHandler getBillingHandler() {
        return billingHandler;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onAppBackgrounded() {
        Timber.d("App is now in the background");
        activityVisible = false;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onAppForegrounded() {
        Timber.d("App is now in the foreground");
        activityVisible = true;
    }

    public static boolean isActivityVisible() {
        return activityVisible;
    }

    @Override
    public void onBillingClientSetupFinished() {
        Timber.d("Billing client setup has finished");
        billingManager.queryPurchases();
    }

    @Override
    public void onConsumeFinished(String token, int result) {

    }

    @Override
    public void onPurchaseSuccessful(List<Purchase> purchases) {
        Timber.d("Purchase was successful");
        for (Purchase purchase : purchases) {
            if (purchase.getSku().equals(UNLOCKER)) {
                Timber.d("Received purchase item " + UNLOCKER);
                isUnlocked = true;
            }
        }
    }

    @Override
    public void onPurchaseCancelled() {
        Timber.d("Purchase was successful");
    }

    @Override
    public void onPurchaseError(int errorCode) {
        Timber.d("Purchase was not successful");
    }
}
