package org.tvheadend.tvhclient.adapter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.tvheadend.tvhclient.R;
import org.tvheadend.tvhclient.model.Program2;
import org.tvheadend.tvhclient.utils.MiscUtils;
import org.tvheadend.tvhclient.utils.Utils;

import java.util.Comparator;
import java.util.List;

public class ProgramListAdapter extends ArrayAdapter<Program2> {

    private final static String TAG = ProgramListAdapter.class.getSimpleName();
    private final Activity context;
    private final List<Program2> list;
    private final SharedPreferences prefs;

    public ProgramListAdapter(Activity context, List<Program2> list) {
        super(context, R.layout.program_list_widget, list);
        this.context = context;
        this.list = list;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void sort() {
        sort(new Comparator<Program2>() {
            public int compare(Program2 x, Program2 y) {
                if (x != null && y != null) {
                    if (x.start > y.start) {
                        return 1;
                    } else if (x.start < y.start) {
                        return -1;
                    } else {
                        return 0;
                    }
                }
                return 0;
            }
        });
    }

    static class ViewHolder {
        public TextView title;
        public TextView time;
        public TextView date;
        public TextView duration;
        public TextView progress;
        public TextView summary;
        public TextView description;
        public TextView seriesInfo;
        public TextView subtitle;
        public TextView contentType;
        public ImageView state;
        public TextView genre;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        ViewHolder holder;

        if (view == null) {
            view = context.getLayoutInflater().inflate(R.layout.program_list_widget, parent, false);
            holder = new ViewHolder();
            holder.title = view.findViewById(R.id.title);
            holder.state = view.findViewById(R.id.state);
            holder.time = view.findViewById(R.id.time);
            holder.date = view.findViewById(R.id.date);
            holder.duration = view.findViewById(R.id.duration);
            holder.progress = view.findViewById(R.id.progress);
            holder.seriesInfo = view.findViewById(R.id.series_info);
            holder.contentType = view.findViewById(R.id.content_type);
            holder.summary = view.findViewById(R.id.summary);
            holder.subtitle = view.findViewById(R.id.subtitle);
            holder.description = view.findViewById(R.id.description);
            holder.genre = view.findViewById(R.id.genre);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        // Get the program and assign all the values
        Program2 p = getItem(position);
        if (p != null) {
            holder.title.setText(p.title);
            Utils.setState(context, holder.state, p);
            Utils.setDate2(holder.date, p.start);
            Utils.setTime2(holder.time, p.start, p.stop);
            Utils.setDuration2(holder.duration, p.start, p.stop);
            Utils.setProgressText2(holder.progress, p.start, p.stop);
            Utils.setDescription(null, holder.summary, p.summary);
            Utils.setDescription(null, holder.subtitle, p.subtitle);
            Utils.setDescription(null, holder.description, p.description);
            Utils.setContentType(null, holder.contentType, p.contentType);
            Utils.setSeriesInfo(getContext(), null, holder.seriesInfo, p);

            MiscUtils.setGenreColor(context, holder.genre, p.contentType, TAG);

            if (holder.subtitle != null) {
                holder.subtitle.setVisibility(prefs.getBoolean("showProgramSubtitlePref", true) ? View.VISIBLE : View.GONE);
            }
        }
        return view;
    }

    public void update(Program2 p) {
        int length = list.size();

        // Go through the list of programs and find the
        // one with the same id. If its been found, replace it.
        for (int i = 0; i < length; ++i) {
            if (list.get(i).eventId == p.eventId) {
                list.set(i, p);
                break;
            }
        }
    }

    public List<Program2> getList() {
        return list;
    }
}
