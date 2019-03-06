package org.tvheadend.tvhclient.ui.base

import android.content.Context
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import org.tvheadend.tvhclient.ui.base.callbacks.ToolbarInterface
import org.tvheadend.tvhclient.util.LocaleUtils

open class BaseActivity : AppCompatActivity(), ToolbarInterface {

    override fun attachBaseContext(context: Context) {
        super.attachBaseContext(LocaleUtils.onAttach(context))
    }

    override fun setTitle(title: String) {
        supportActionBar?.title = title
    }

    override fun setSubtitle(subtitle: String) {
        supportActionBar?.subtitle = subtitle
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
