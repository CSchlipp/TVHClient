package org.tvheadend.tvhclient.ui.features.search

import android.app.SearchManager
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import androidx.fragment.app.Fragment
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.ui.base.BaseActivity
import org.tvheadend.tvhclient.ui.base.callbacks.NetworkStatusListener
import org.tvheadend.tvhclient.ui.base.utils.SnackbarUtils
import org.tvheadend.tvhclient.ui.features.programs.ProgramListFragment
import org.tvheadend.tvhclient.util.MiscUtils
import org.tvheadend.tvhclient.util.network.NetworkStatusReceiver
import timber.log.Timber

class SearchActivity : BaseActivity(), StartSearchInterface, NetworkStatusListener {

    private var networkStatusReceiver: NetworkStatusReceiver? = null
    private var isNetworkAvailable: Boolean = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(MiscUtils.getThemeId(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.misc_content_activity)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        networkStatusReceiver = NetworkStatusReceiver(this)

        handleIntent(intent)

        if (savedInstanceState == null) {
            // If a search was performed from a fragment the activity would call
            // the onSearchRequested(...) method of that fragment which will start
            // the SearchActivity (if implemented). Depending on the given search type
            // the corresponding fragment will be shown which will present the results.
            val type = intent.getStringExtra("type")
            var fragment: Fragment? = null
            when (type) {
                "program_guide" -> fragment = ProgramListFragment()
            }

            if (fragment != null) {
                fragment.arguments = intent.extras
                supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.main, fragment)
                        .commit()
            }
        }
    }

    override fun startSearch() {
        val fragment = supportFragmentManager.findFragmentById(R.id.main)
        if (fragment is SearchRequestInterface && fragment.isVisible) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            fragment.onSearchRequested(query)
        }
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            Timber.d("Saving suggestion $query so it can be shown in the recent search history")
            val suggestions = SearchRecentSuggestions(this, SuggestionProvider.AUTHORITY, SuggestionProvider.MODE)
            suggestions.saveRecentQuery(query, null)
        }
    }

    public override fun onStart() {
        super.onStart()
        registerReceiver(networkStatusReceiver, IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"))
    }

    public override fun onStop() {
        super.onStop()
        unregisterReceiver(networkStatusReceiver)
    }

    override fun onNetworkStatusChanged(isNetworkAvailable: Boolean) {
        onNetworkAvailabilityChanged(isNetworkAvailable)
        if (!isNetworkAvailable) {
            SnackbarUtils.sendSnackbarMessage(this, "No network available")
        }
    }

    /**
     * Executes certain actions when the connectivity has changed.
     * A new connection to the server is created if the connectivity changed from
     * unavailable to available. Otherwise the server will be pinged to check if the connection
     * is still active. Additionally the connectivity status is propagated to all fragments that
     * that are currently shown so they can update certain UI elements that depend on the
     * connectivity status like menus.
     *
     * @param isAvailable True if networking is available, otherwise false
     */
    private fun onNetworkAvailabilityChanged(isAvailable: Boolean) {
        Timber.d("Network availability changed, network is available $isAvailable")
        isNetworkAvailable = isAvailable

        var fragment = supportFragmentManager.findFragmentById(R.id.main)
        if (fragment is NetworkStatusListener) {
            (fragment as NetworkStatusListener).onNetworkStatusChanged(isAvailable)
        }

        fragment = supportFragmentManager.findFragmentById(R.id.details)
        if (fragment is NetworkStatusListener) {
            (fragment as NetworkStatusListener).onNetworkStatusChanged(isAvailable)
        }
        Timber.d("Network availability changed, invalidating menu")
        invalidateOptionsMenu()
    }

    override fun isNetworkAvailable(): Boolean {
        return isNetworkAvailable
    }

}
