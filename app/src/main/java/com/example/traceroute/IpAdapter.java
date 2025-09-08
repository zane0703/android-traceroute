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

    public static class LookFailException extends Exception {
    }

    public static class IpInfo {
        private String country;
        private String regionName;
        private String city;
        private String isp;
        private String org;

        public IpInfo(JsonReader jsonReader) throws IOException, LookFailException {
            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
                switch (jsonReader.nextName()) {
                    case "status":
                        if (!jsonReader.nextString().equals("success")) {
                            throw new LookFailException();
                        }
                        break;
                    case "country":
                        this.country = jsonReader.nextString();
                        break;
                    case "regionName":
                        this.regionName = jsonReader.nextString();
                        break;
                    case "city":
                        this.city = jsonReader.nextString();
                        break;
                    case "isp":
                        this.isp = jsonReader.nextString();
                        break;
                    case "org":
                        this.org = jsonReader.nextString();
                        break;

                }
            }
        }

        public String getCity() {
            return city;
        }

        public String getCountry() {
            return country;
        }

        public String getIsp() {
            return isp;
        }

        public String getOrg() {
            return org;
        }

        public String getRegionName() {
            return regionName;
        }

    }

    public static class IpAddress {

        private IpInfo ipInfo;
        private boolean ipLookupStatus = true;
        final private String address;
        final private String delay;

        public IpAddress(String address, String delay) {
            this.address = address;
            this.delay = delay;

        }

        public String getAddress() {
            return address;
        }

        public String getDelay() {
            return delay;
        }

        public boolean isIpLookupStatus() {
            return ipLookupStatus;
        }

        public void setIpLookupStatus(boolean ipLookupStatus) {
            this.ipLookupStatus = ipLookupStatus;
        }

        public IpInfo getIpInfo() {
            return ipInfo;
        }

        public void setIpInfo(IpInfo ipInfo) {
            this.ipInfo = ipInfo;
        }
    }
    final private String star = "*";
    final private List<IpAddress> ipAddresses;
    final private Context context;
    final private LayoutInflater inflter;
    final private ClipboardManager clipboard;

    public IpAdapter(Context context, List<IpAddress> ipAddresses) {
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
        IpAddress ipAddress = this.ipAddresses.get(i);
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
            addressView.setText(ipAddress.getAddress());
        }
        return view;
    }
}
