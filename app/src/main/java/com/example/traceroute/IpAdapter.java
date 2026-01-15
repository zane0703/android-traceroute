package com.example.traceroute;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.json.JSONObject;

import android.util.JsonReader;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;

public class IpAdapter extends BaseAdapter {

    final private String star = "*";
    final private List<ping.Result> ipAddresses;
    final private Context context;
    final private LayoutInflater inflter;
    final private ClipboardManager clipboard;

    public IpAdapter(Context context, List<ping.Result> ipAddresses) {
        this.context = context;
        this.ipAddresses = ipAddresses;
        this.inflter = LayoutInflater.from(context);
        this.clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

    }

    @Override
    public int getCount() {
        return this.ipAddresses.size();
    }

    @Override
    public Object getItem(int i) {
        return this.ipAddresses.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }
    @Override
    public boolean hasStableIds(){return true;}
    @Override
    public View getView(final int i, View view,final ViewGroup viewGroup) {
        if (view == null) {
            view = inflter.inflate(R.layout.activity_listview, viewGroup, false);
        }
        ping.Result ipAddress = this.ipAddresses.get(i);
        view.setTag(ipAddress);
        final TextView countView = view.findViewById(R.id.count);
        final TextView delayView = view.findViewById(R.id.delay);
        final TextView addressView = view.findViewById(R.id.address);
        countView.setText(Integer.toString(i));
        if (ipAddress == null) {
            addressView.setText(R.string.time_out);
            delayView.setText(star);
        } else {
            delayView.setText(ipAddress.getDelay());
            addressView.setText(ipAddress.getIpAddress());
        }
        return view;
    }
}
