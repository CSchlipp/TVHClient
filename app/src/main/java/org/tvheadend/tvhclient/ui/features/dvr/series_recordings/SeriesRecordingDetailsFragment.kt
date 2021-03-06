package org.tvheadend.tvhclient.ui.features.dvr.series_recordings

import android.os.Bundle
import android.view.*
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.details_fragment_header.*
import kotlinx.android.synthetic.main.series_recording_details_fragment.*
import org.tvheadend.data.entity.SeriesRecording
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.databinding.SeriesRecordingDetailsFragmentBinding
import org.tvheadend.tvhclient.ui.base.BaseFragment
import org.tvheadend.tvhclient.ui.common.*
import org.tvheadend.tvhclient.ui.common.interfaces.RecordingRemovedInterface
import org.tvheadend.tvhclient.util.extensions.gone
import org.tvheadend.tvhclient.util.extensions.visible

class SeriesRecordingDetailsFragment : BaseFragment(), RecordingRemovedInterface {

    private lateinit var seriesRecordingViewModel: SeriesRecordingViewModel
    private var recording: SeriesRecording? = null
    private lateinit var itemBinding: SeriesRecordingDetailsFragmentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        itemBinding = DataBindingUtil.inflate(inflater, R.layout.series_recording_details_fragment, container, false)
        return itemBinding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        seriesRecordingViewModel = ViewModelProviders.of(activity!!).get(SeriesRecordingViewModel::class.java)

        if (!isDualPane) {
            toolbarInterface.setTitle(getString(R.string.details))
            toolbarInterface.setSubtitle("")
        }

        arguments?.let {
            seriesRecordingViewModel.currentId.value = it.getString("id", "")
        }

        seriesRecordingViewModel.recordingLiveData.observe(viewLifecycleOwner, Observer {
            recording = it
            showRecordingDetails()
        })
    }

    private fun showRecordingDetails() {
        if (recording != null) {
            itemBinding.recording = recording
            itemBinding.htspVersion = htspVersion
            itemBinding.isDualPane = isDualPane
            // The toolbar is hidden as a default to prevent pressing any icons if no recording
            // has been loaded yet. The toolbar is shown here because a recording was loaded
            nested_toolbar.visible()
            activity?.invalidateOptionsMenu()
        } else {
            scrollview.gone()
            status.text = getString(R.string.error_loading_recording_details)
            status.visible()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val recording = this.recording ?: return
        preparePopupOrToolbarSearchMenu(menu, recording.title, isConnectionToServerAvailable)

        nested_toolbar.menu.findItem(R.id.menu_edit_recording)?.isVisible = true
        nested_toolbar.menu.findItem(R.id.menu_remove_recording)?.isVisible = true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.external_search_options_menu, menu)
        nested_toolbar.inflateMenu(R.menu.recording_details_toolbar_menu)
        nested_toolbar.setOnMenuItemClickListener { this.onOptionsItemSelected(it) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val ctx = context ?: return super.onOptionsItemSelected(item)
        val recording = this.recording ?: return super.onOptionsItemSelected(item)

        return when (item.itemId) {
            R.id.menu_edit_recording -> return editSelectedSeriesRecording(ctx, recording.id)
            R.id.menu_remove_recording -> showConfirmationToRemoveSelectedSeriesRecording(ctx, recording, this)

            R.id.menu_search_imdb -> return searchTitleOnImdbWebsite(ctx, recording.title)
            R.id.menu_search_fileaffinity -> return searchTitleOnFileAffinityWebsite(ctx, recording.title)
            R.id.menu_search_youtube -> return searchTitleOnYoutube(ctx, recording.title)
            R.id.menu_search_google -> return searchTitleOnGoogle(ctx, recording.title)
            R.id.menu_search_epg -> return searchTitleInTheLocalDatabase(activity!!, baseViewModel, recording.title)
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRecordingRemoved() {
        if (!isDualPane) {
            activity?.onBackPressed()
        } else {
            val detailsFragment = activity?.supportFragmentManager?.findFragmentById(R.id.details)
            if (detailsFragment != null) {
                activity?.supportFragmentManager?.beginTransaction()?.also {
                    it.remove(detailsFragment)
                    it.commit()
                }
            }
        }
    }

    companion object {

        fun newInstance(id: String): SeriesRecordingDetailsFragment {
            val f = SeriesRecordingDetailsFragment()
            val args = Bundle()
            args.putString("id", id)
            f.arguments = args
            return f
        }
    }
}
