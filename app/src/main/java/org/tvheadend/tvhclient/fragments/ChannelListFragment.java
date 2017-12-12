package org.tvheadend.tvhclient.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v7.widget.PopupMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import org.tvheadend.tvhclient.Constants;
import org.tvheadend.tvhclient.DataStorage;
import org.tvheadend.tvhclient.R;
import org.tvheadend.tvhclient.TVHClientApplication;
import org.tvheadend.tvhclient.adapter.ChannelListAdapter;
import org.tvheadend.tvhclient.interfaces.FragmentControlInterface;
import org.tvheadend.tvhclient.interfaces.FragmentStatusInterface;
import org.tvheadend.tvhclient.interfaces.HTSListener;
import org.tvheadend.tvhclient.interfaces.ToolbarInterface;
import org.tvheadend.tvhclient.model.Channel;
import org.tvheadend.tvhclient.model.ChannelTag;
import org.tvheadend.tvhclient.model.Program;
import org.tvheadend.tvhclient.model.Recording;
import org.tvheadend.tvhclient.utils.MenuTagSelectionCallback;
import org.tvheadend.tvhclient.utils.MenuTimeSelectionCallback;
import org.tvheadend.tvhclient.utils.MenuUtils;
import org.tvheadend.tvhclient.utils.MiscUtils;
import org.tvheadend.tvhclient.utils.Utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class ChannelListFragment extends ListFragment implements HTSListener, FragmentControlInterface, MenuTimeSelectionCallback, MenuTagSelectionCallback, AdapterView.OnItemLongClickListener, OnItemClickListener {

    private final static String TAG = ChannelListFragment.class.getSimpleName();

    private Activity activity;
    private FragmentStatusInterface fragmentStatusInterface;
    private ToolbarInterface toolbarInterface;
    private ChannelListAdapter adapter;

    // Enables scrolling when the user has touch the screen and starts
    // scrolling. When the user is done, scrolling will be disabled to prevent
    // unwanted calls to the interface.
    private boolean isDualPane = false;
    private Runnable channelUpdateTask;
    private final Handler channelUpdateHandler = new Handler();
    private int channelTimeSelection;
    private long showProgramsFromTime;
    private MenuUtils menuUtils;
    private SharedPreferences sharedPreferences;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        activity = getActivity();
        if (activity instanceof ToolbarInterface) {
            toolbarInterface = (ToolbarInterface) activity;
        }
        if (activity instanceof FragmentStatusInterface) {
            fragmentStatusInterface = (FragmentStatusInterface) activity;
        }

        // Check to see if we have a frame in which to embed the details
        // fragment directly in the containing UI.
        View detailsFrame = getActivity().findViewById(R.id.right_fragment);
        isDualPane = detailsFrame != null && detailsFrame.getVisibility() == View.VISIBLE;

        // Check if only channels without any program information shall be
        // visible. This is only the case when this fragment is part of the
        // program guide view.
        Bundle bundle = getArguments();
        if (bundle != null) {
            channelTimeSelection = bundle.getInt("channel_time_selection");
            showProgramsFromTime = bundle.getLong("show_programs_from_time");
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        adapter = new ChannelListAdapter(activity, new ArrayList<>());
        setListAdapter(adapter);
        getListView().setFastScrollEnabled(true);
        getListView().setOnItemClickListener(this);
        getListView().setOnItemLongClickListener(this);
        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        menuUtils = new MenuUtils(getActivity());

        // Enable the action bar menu
        setHasOptionsMenu(true);

        // Initiate a timer that will update the adapter every minute
        // so that the progress bars will be displayed correctly
        // Also update the current adapter time if the current 
        // time was selected from the channel time dialog, otherwise
        // old programs will not be removed when they are over
        channelUpdateTask = new Runnable() {
            public void run() {
                if (channelTimeSelection == 0) {
                    adapter.setTime(new Date().getTime());
                }
                adapter.notifyDataSetChanged();
                channelUpdateHandler.postDelayed(channelUpdateTask, 60000);
            }
        };
    }

    @SuppressLint({"InlinedApi", "NewApi"})
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean showGenreColors = prefs.getBoolean("showGenreColorsChannelsPref", false);
        (menu.findItem(R.id.menu_genre_color_info_channels)).setVisible(showGenreColors);

        // Playing a channel shall not be available in channel only mode or in
        // single pane mode, because no channel is preselected.
        if (!isDualPane) {
            (menu.findItem(R.id.menu_play)).setVisible(false);
        }

        (menu.findItem(R.id.menu_timeframe)).setVisible(TVHClientApplication.getInstance().isUnlocked());

        // Prevent the channel tag menu item from going into the overlay menu
        if (prefs.getBoolean("visibleMenuIconTagsPref", true)) {
            menu.findItem(R.id.menu_tags).setShowAsActionFlags(
                    MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.channel_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_play:
                // Open a new activity to stream the current program to this device
                menuUtils.handleMenuPlaySelection(adapter.getSelectedItem().channelId, -1);
                return true;

            case R.id.menu_tags:
                ChannelTag tag = Utils.getChannelTag(activity);
                menuUtils.handleMenuTagsSelection((tag != null ? tag.tagId : -1), this);
                return true;

            case R.id.menu_timeframe:
                menuUtils.handleMenuTimeSelection(channelTimeSelection, this);
                return true;

            case R.id.menu_genre_color_info_channels:
                menuUtils.handleMenuGenreColorSelection();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Fills the adapter with the available channel data. Only those channels
     * will be added to the adapter that contain the selected channel tag.
     * Populates the channel tag adapter so the user can select a new one.
     * Additionally some status information will be shown in the action bar.
     */
    private void populateList() {
        // Get the currently selected channel tag
        ChannelTag currentTag = Utils.getChannelTag(activity);
        //Log.d(TAG, "populateList: tag " + currentTag != null ? String.valueOf(currentTag.tagId) : "none");
        // Add only those channels that contain the selected channel tag
        adapter.clear();
        for (Channel channel : DataStorage.getInstance().getChannelsFromArray().values()) {
            if (currentTag == null || channel.tags.contains(currentTag.tagId)) {
                adapter.add(channel);
            }
        }

        adapter.sort(Utils.getChannelSortOrder(activity));
        adapter.setTime(showProgramsFromTime);
        adapter.notifyDataSetChanged();

        // Show the name of the selected channel tag and the number of channels
        // in the action bar. If enabled show also the channel tag icon.
        if (toolbarInterface != null) {
            toolbarInterface.setActionBarTitle((currentTag == null) ? getString(R.string.all_channels) : currentTag.tagName);
            String items = getResources().getQuantityString(R.plurals.items, adapter.getCount(), adapter.getCount());
            toolbarInterface.setActionBarSubtitle(items);

            if (sharedPreferences.getBoolean("showIconPref", true)
                    && sharedPreferences.getBoolean("showTagIconPref", false)
                    && currentTag != null
                    && currentTag.tagId != 0) {
                Bitmap iconBitmap = MiscUtils.getCachedIcon(activity, currentTag.tagIcon);
                toolbarInterface.setActionBarIcon(iconBitmap);
            } else {
                toolbarInterface.setActionBarIcon(R.mipmap.ic_launcher);
            }
        }

        // Inform the activity that the channel list has been populated. The
        // activity will then inform the fragment to select the first item in
        // the list or scroll to the previously selected one in case the
        // orientation has changed
        if (fragmentStatusInterface != null) {
            fragmentStatusInterface.onListPopulated(TAG);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        TVHClientApplication.getInstance().addListener(this);
        if (!DataStorage.getInstance().isLoading()) {
            populateList();
        }

        // Start the timer that updates the adapter so 
        // it only shows programs within the current time
        channelUpdateHandler.post(channelUpdateTask);
    }

    @Override
    public void onPause() {
        super.onPause();
        TVHClientApplication.getInstance().removeListener(this);
        channelUpdateHandler.removeCallbacks(channelUpdateTask);
    }

    @Override
    public void onDestroy() {
        fragmentStatusInterface = null;
        toolbarInterface = null;
        super.onDestroy();
    }

    /**
     * This method is part of the HTSListener interface. Whenever the HTSService
     * sends a new message the specified action will be executed here.
     */
    @Override
    public void onMessage(String action, final Object obj) {
        switch (action) {
            case Constants.ACTION_LOADING:
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        boolean loading = (Boolean) obj;
                        if (loading) {
                            adapter.clear();
                            adapter.notifyDataSetChanged();
                        } else {
                            populateList();
                        }
                    }
                });
                break;
            case "channelAdd":
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        adapter.add((Channel) obj);
                        adapter.sort(Utils.getChannelSortOrder(activity));
                        adapter.notifyDataSetChanged();
                    }
                });
                break;
            case "channelDelete":
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        adapter.remove((Channel) obj);
                        adapter.notifyDataSetChanged();
                    }
                });
                break;
            case "channelUpdate":
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        adapter.update((Channel) obj);
                        adapter.notifyDataSetChanged();
                    }
                });
                break;
                /*
            case "tagAdd":
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        ChannelTag tag = (ChannelTag) obj;
                        tagList.add(tag);
                    }
                });
                break;
            case "tagDelete":
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        ChannelTag tag = (ChannelTag) obj;
                        tagList.remove(tag);
                    }
                });
                break;
                */
            case "eventUpdate":
            case "eventDelete":
            case "dvrEntryAdd":
            case "dvrEntryUpdate":
            case "dvrEntryDelete":
                // An existing program has been updated
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                });
                break;
        }
    }

    @Override
    public void reloadData() {
        populateList();
    }

    @Override
    public void setSelection(final int position, final int offset) {
        if (getListView().getCount() > position && position >= 0) {
            getListView().setSelectionFromTop(position, offset);
        }
    }

    @Override
    public void setInitialSelection(final int position) {
        setSelection(position, 0);

        if (adapter != null && adapter.getCount() > position) {
            adapter.setPosition(position);

            if (fragmentStatusInterface != null) {
                final Channel ch = adapter.getItem(position);
                fragmentStatusInterface.onListItemSelected(position, ch, TAG);
            }
        }
    }

    @Override
    public Object getSelectedItem() {
        return adapter.getSelectedItem();
    }

    @Override
    public int getItemCount() {
        return adapter.getCount();
    }

    @Override
    public void menuTimeSelected(int which) {
        channelTimeSelection = which;

        // Get the current time and create the new time from the selection value.
        // 0 is the current time, 1 is 2 hours ahead, 2 is 4 hours ahead and so on
        Calendar c = Calendar.getInstance();
        if (which > 0) {
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            if (which > 0) {
                c.set(Calendar.HOUR_OF_DAY, c.get(Calendar.HOUR_OF_DAY) + which);
            }
        }

        showProgramsFromTime = c.getTimeInMillis();
        adapter.setTime(showProgramsFromTime);
        adapter.notifyDataSetChanged();

        if (fragmentStatusInterface != null) {
            fragmentStatusInterface.onChannelTimeSelected(channelTimeSelection, showProgramsFromTime);
            fragmentStatusInterface.onListPopulated(TAG);
        }
    }

    @Override
    public void menuTagSelected(int which) {
        Utils.setChannelTagId(activity, which);
        if (fragmentStatusInterface != null) {
            fragmentStatusInterface.channelTagChanged(TAG);
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
        final Channel channel = adapter.getItem(position);
        final Program program = getCurrentProgram(channel);
        if (getActivity() == null || program == null) {
            return true;
        }
        PopupMenu popupMenu = new PopupMenu(getActivity(), view);
        popupMenu.getMenuInflater().inflate(R.menu.program_context_menu, popupMenu.getMenu());
        menuUtils.onPreparePopupMenu(popupMenu.getMenu(), program);

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.menu_search_imdb:
                    menuUtils.handleMenuSearchWebSelection(program.title);
                    return true;
                case R.id.menu_search_epg:
                    menuUtils.handleMenuSearchEpgSelection(program.title, channel.channelId);
                    return true;
                case R.id.menu_record_remove:
                    Recording rec = DataStorage.getInstance().getRecordingFromArray(program.dvrId);
                    if (rec != null) {
                        if (rec.isRecording()) {
                            menuUtils.handleMenuStopRecordingSelection(rec.id, rec.title);
                        } else if (rec.isScheduled()) {
                            menuUtils.handleMenuCancelRecordingSelection(rec.id, rec.title);
                        } else {
                            menuUtils.handleMenuRemoveRecordingSelection(rec.id, rec.title);
                        }
                    }
                    return true;
                case R.id.menu_record_once:
                    menuUtils.handleMenuRecordSelection(program.eventId);
                    return true;
                case R.id.menu_record_once_custom_profile:
                    menuUtils.handleMenuCustomRecordSelection(program.eventId, channel.channelId);
                    return true;
                case R.id.menu_record_series:
                    menuUtils.handleMenuSeriesRecordSelection(program.title);
                    return true;
                case R.id.menu_play:
                    // Open a new activity to stream the current program to this device
                    menuUtils.handleMenuPlaySelection(channel.channelId, -1);
                    return true;
                default:
                    return false;
            }
        });
        popupMenu.show();
        return true;
    }

    private Program getCurrentProgram(Channel channel) {
        if (channel == null) {
            return null;
        }
        for (Program program : DataStorage.getInstance().getProgramsFromArray().values()) {
            if (program.channelId == channel.channelId) {
                if (program.start <= showProgramsFromTime && program.stop > showProgramsFromTime) {
                    return program;
                }
            }
        }
        return null;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        final Channel ch = adapter.getItem(position);
        if (fragmentStatusInterface != null) {
            fragmentStatusInterface.onListItemSelected(position, ch, TAG);
        }
        adapter.setPosition(position);
        adapter.notifyDataSetChanged();
    }
}
