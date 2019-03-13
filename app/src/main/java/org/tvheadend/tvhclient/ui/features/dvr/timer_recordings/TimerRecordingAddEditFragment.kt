package org.tvheadend.tvhclient.ui.features.dvr.timer_recordings

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProviders
import butterknife.*
import com.afollestad.materialdialogs.MaterialDialog
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.data.service.HtspService
import org.tvheadend.tvhclient.domain.entity.Channel
import org.tvheadend.tvhclient.domain.entity.ServerProfile
import org.tvheadend.tvhclient.ui.base.BaseFragment
import org.tvheadend.tvhclient.ui.base.callbacks.BackPressedInterface
import org.tvheadend.tvhclient.ui.base.utils.SnackbarUtils
import org.tvheadend.tvhclient.ui.features.dvr.DatePickerFragment
import org.tvheadend.tvhclient.ui.features.dvr.RecordingConfigSelectedListener
import org.tvheadend.tvhclient.ui.features.dvr.RecordingUtils
import org.tvheadend.tvhclient.ui.features.dvr.TimePickerFragment
import org.tvheadend.tvhclient.util.MiscUtils

class TimerRecordingAddEditFragment : BaseFragment(), BackPressedInterface, RecordingConfigSelectedListener, DatePickerFragment.Listener, TimePickerFragment.Listener {

    @BindView(R.id.is_enabled)
    lateinit var isEnabledCheckbox: CheckBox
    @BindView(R.id.priority)
    lateinit var priorityTextView: TextView
    @BindView(R.id.days_of_week)
    lateinit var daysOfWeekTextView: TextView
    @BindView(R.id.start_time)
    lateinit var startTimeTextView: TextView
    @BindView(R.id.stop_time)
    lateinit var stopTimeTextView: TextView
    @BindView(R.id.directory)
    lateinit var directoryEditText: EditText
    @BindView(R.id.directory_label)
    lateinit var directoryLabelTextView: TextView
    @BindView(R.id.title)
    lateinit var titleEditText: EditText
    @BindView(R.id.name)
    lateinit var nameEditText: EditText
    @BindView(R.id.channel)
    lateinit var channelNameTextView: TextView
    @BindView(R.id.dvr_config)
    lateinit var recordingProfileNameTextView: TextView
    @BindView(R.id.dvr_config_label)
    lateinit var recordingProfileLabelTextView: TextView

    lateinit var unbinder: Unbinder

    private lateinit var recordingProfilesList: Array<String>
    private var channelList: List<Channel>? = null
    private var profile: ServerProfile? = null

    lateinit var viewModel: TimerRecordingViewModel

    /**
     * Returns an intent with the recording data
     */
    private val intentData: Intent
        get() {
            val intent = Intent(activity, HtspService::class.java)
            intent.putExtra("directory", viewModel.recording.directory)
            intent.putExtra("title", viewModel.recording.title)
            intent.putExtra("name", viewModel.recording.name)
            intent.putExtra("start", viewModel.recording.start)
            intent.putExtra("stop", viewModel.recording.stop)
            intent.putExtra("daysOfWeek", viewModel.recording.daysOfWeek)
            intent.putExtra("priority", viewModel.recording.priority)
            intent.putExtra("enabled", if (viewModel.recording.isEnabled) 1 else 0)

            if (viewModel.recording.channelId > 0) {
                intent.putExtra("channelId", viewModel.recording.channelId)
            }
            // Add the recording profile if available and enabled
            if (MiscUtils.isServerProfileEnabled(profile, serverStatus) && recordingProfileNameTextView.text.isNotEmpty()) {
                // Use the selected profile. If no change was done in the
                // selection then the default one from the connection setting will be used
                intent.putExtra("configName", recordingProfileNameTextView.text.toString())
            }
            return intent
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.timer_recording_add_edit_fragment, container, false)
        unbinder = ButterKnife.bind(this, view)
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unbinder.unbind()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProviders.of(activity).get(TimerRecordingViewModel::class.java)

        recordingProfilesList = appRepository.serverProfileData.recordingProfileNames
        profile = appRepository.serverProfileData.getItemById(serverStatus.recordingServerProfileId)
        viewModel.recordingProfileNameId = RecordingUtils.getSelectedProfileId(profile, recordingProfilesList)
        channelList = appRepository.channelData.getItems()

        if (savedInstanceState == null) {
            viewModel.loadRecordingByIdSync(arguments?.getString("id", "") ?: "")
        }

        setHasOptionsMenu(true)
        updateUI()

        toolbarInterface.setTitle(if (!TextUtils.isEmpty(viewModel.recording.id))
            getString(R.string.edit_recording)
        else
            getString(R.string.add_recording))
    }

    private fun updateUI() {

        isEnabledCheckbox.visibility = if (htspVersion >= 19) View.VISIBLE else View.GONE
        isEnabledCheckbox.isChecked = viewModel.recording.isEnabled
        titleEditText.setText(viewModel.recording.title)
        nameEditText.setText(viewModel.recording.name)

        directoryLabelTextView.visibility = if (htspVersion >= 19) View.VISIBLE else View.GONE
        directoryEditText.visibility = if (htspVersion >= 19) View.VISIBLE else View.GONE
        directoryEditText.setText(viewModel.recording.directory)

        channelNameTextView.text = if (!TextUtils.isEmpty(viewModel.recording.channelName)) viewModel.recording.channelName else getString(R.string.all_channels)
        channelNameTextView.setOnClickListener {
            // Determine if the server supports recording on all channels
            val allowRecordingOnAllChannels = htspVersion >= 21
            RecordingUtils.handleChannelListSelection(activity, channelList, allowRecordingOnAllChannels, this@TimerRecordingAddEditFragment)
        }

        priorityTextView.text = RecordingUtils.getPriorityName(activity, viewModel.recording.priority)
        priorityTextView.setOnClickListener {
            RecordingUtils.handlePrioritySelection(activity, viewModel.recording.priority, this@TimerRecordingAddEditFragment)
        }

        if (recordingProfilesList.isEmpty()) {
            recordingProfileNameTextView.visibility = View.GONE
            recordingProfileLabelTextView.visibility = View.GONE
        } else {
            recordingProfileNameTextView.visibility = View.VISIBLE
            recordingProfileLabelTextView.visibility = View.VISIBLE

            recordingProfileNameTextView.text = recordingProfilesList[viewModel.recordingProfileNameId]
            recordingProfileNameTextView.setOnClickListener {
                RecordingUtils.handleRecordingProfileSelection(activity, recordingProfilesList, viewModel.recordingProfileNameId, this@TimerRecordingAddEditFragment)
            }
        }

        startTimeTextView.text = RecordingUtils.getTimeStringFromTimeInMillis(viewModel.startTimeInMillis)
        startTimeTextView.setOnClickListener {
            RecordingUtils.handleTimeSelection(activity, viewModel.startTimeInMillis, this@TimerRecordingAddEditFragment, "startTime")
        }

        stopTimeTextView.text = RecordingUtils.getTimeStringFromTimeInMillis(viewModel.stopTimeInMillis)
        stopTimeTextView.setOnClickListener {
            RecordingUtils.handleTimeSelection(activity, viewModel.stopTimeInMillis, this@TimerRecordingAddEditFragment, "stopTime")
        }

        daysOfWeekTextView.text = RecordingUtils.getSelectedDaysOfWeekText(activity, viewModel.recording.daysOfWeek)
        daysOfWeekTextView.setOnClickListener {
            RecordingUtils.handleDayOfWeekSelection(activity, viewModel.recording.daysOfWeek, this@TimerRecordingAddEditFragment)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.save_cancel_options_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                cancel()
                true
            }
            R.id.menu_save -> {
                save()
                true
            }
            R.id.menu_cancel -> {
                cancel()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun save() {
        if (TextUtils.isEmpty(viewModel.recording.title)) {
            SnackbarUtils.sendSnackbarMessage(activity, R.string.error_empty_title)
            return
        }
        if (!TextUtils.isEmpty(viewModel.recording.id)) {
            updateTimerRecording()
        } else {
            addTimerRecording()
        }
    }

    private fun cancel() {
        // Show confirmation dialog to cancel
        MaterialDialog.Builder(activity)
                .content(R.string.cancel_add_recording)
                .positiveText(getString(R.string.discard))
                .negativeText(getString(R.string.cancel))
                .onPositive { _, _ -> activity.finish() }
                .onNegative { dialog, _ -> dialog.cancel() }
                .show()
    }

    /**
     * Adds a new timer recording with the given values. This method is also
     * called when a recording is being edited. It adds a recording with edited
     * values which was previously removed.
     */
    private fun addTimerRecording() {
        val intent = intentData
        intent.action = "addTimerecEntry"
        activity.startService(intent)
        activity.finish()
    }

    /**
     * Updates the timer recording with the given values.
     * If the API version supports it, use the native service call method
     * otherwise the old recording is removed and a new one with the
     * edited values is added afterwards. This is done in the service
     */
    private fun updateTimerRecording() {
        val intent = intentData
        intent.action = "updateTimerecEntry"
        intent.putExtra("id", viewModel.recording.id)
        activity.startService(intent)
        activity.finish()
    }

    override fun onChannelSelected(channel: Channel) {
        viewModel.recording.channelId = channel.id
        channelNameTextView.text = channel.name
    }

    override fun onPrioritySelected(which: Int) {
        viewModel.recording.priority = which
        priorityTextView.text = RecordingUtils.getPriorityName(activity, viewModel.recording.priority)
    }

    override fun onProfileSelected(which: Int) {
        recordingProfileNameTextView.text = recordingProfilesList[which]
        viewModel.recordingProfileNameId = which
    }

    override fun onTimeSelected(milliSeconds: Long, tag: String?) {
        if (tag == "startTime") {
            viewModel.startTimeInMillis = milliSeconds
            // If the start time is after the stop time, update the stop time with the start value
            if (milliSeconds > viewModel.stopTimeInMillis) {
                viewModel.stopTimeInMillis = milliSeconds
            }
        } else if (tag == "stopTime") {
            viewModel.stopTimeInMillis = milliSeconds
            // If the stop time is before the start time, update the start time with the stop value
            if (milliSeconds < viewModel.recording.start) {
                viewModel.startTimeInMillis = milliSeconds
            }
        }

        startTimeTextView.text = RecordingUtils.getTimeStringFromTimeInMillis(viewModel.startTimeInMillis)
        stopTimeTextView.text = RecordingUtils.getTimeStringFromTimeInMillis(viewModel.stopTimeInMillis)
    }

    override fun onDateSelected(milliSeconds: Long, tag: String?) {
        // NOP
    }

    override fun onDaysSelected(selectedDays: Int) {
        viewModel.recording.daysOfWeek = selectedDays
        daysOfWeekTextView.text = RecordingUtils.getSelectedDaysOfWeekText(activity, selectedDays)
    }

    override fun onBackPressed() {
        cancel()
    }

    @OnTextChanged(R.id.title)
    internal fun onTitleTextChanged(text: CharSequence, start: Int, count: Int, after: Int) {
        viewModel.recording.title = text.toString()
    }

    @OnTextChanged(R.id.name)
    internal fun onNameTextChanged(text: CharSequence, start: Int, count: Int, after: Int) {
        viewModel.recording.name = text.toString()
    }

    @OnTextChanged(R.id.directory)
    internal fun onDirectoryTextChanged(text: CharSequence, start: Int, count: Int, after: Int) {
        viewModel.recording.directory = text.toString()
    }

    @OnCheckedChanged(R.id.is_enabled)
    internal fun onEnabledCheckboxChanged(buttonView: CompoundButton, isChecked: Boolean) {
        viewModel.recording.isEnabled = isChecked
    }
}