package com.example.traceroute;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.materialswitch.MaterialSwitch;

import androidx.activity.EdgeToEdge;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import java.net.InetAddress;
import java.util.ArrayList;

import nettools.PingResult;
import nettools.Tracetool;

public class MainActivity extends AppCompatActivity {
    private ListView listView;
    private Button button;
    private EditText editHostname;
    private EditText editTimeout;
    private MaterialSwitch isIpv6Switch;
    private IpAdapter adapter;
    private ArrayList<PingResult> listItems = new ArrayList<>(65);

    private LayoutInflater inflter;
    private ClipboardManager clipboard;

    private View mProgressBarFooter;

    private boolean start = false;
    private Tracetool tracetool = new Tracetool(this::PingResultCallback, this::onErrorCallback);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        inflter = LayoutInflater.from(this);
        button = findViewById(R.id.button);
        listView = findViewById(R.id.listView);
        editHostname = findViewById(R.id.editHostname);
        editTimeout = findViewById(R.id.editTimeout);
        isIpv6Switch = findViewById(R.id.switch1);
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        adapter = new IpAdapter(this.getBaseContext(), listItems);
        mProgressBarFooter = inflter.inflate(R.layout.progress_bar_footer, null, false);
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(this::onItemLongClick);
        listView.setOnItemClickListener(this::onItemClick);
        button.setOnClickListener(this::onButtonClick);
    }

    private void onItemClick(AdapterView<?> var1, View var2, int var3, long var4) {
        Object tag = var2.getTag();
        if (tag instanceof PingResult) {
            final PingResult ipAddress = (PingResult) tag;
            if (ipAddress.getIpLookupStatus()) {
                Toast toast = Toast.makeText(this, R.string.ip_lookup, Toast.LENGTH_LONG);
                toast.show();
                try {
                    if (InetAddress.getByName(ipAddress.getIpAddress()).isSiteLocalAddress()) {
                        this.runOnUiThread(()-> showDialogBox(R.string.private_IP));
                        toast.cancel();
                        return;
                    }
                    ipAddress.lookupIpInfo(false, ipInfo -> this.runOnUiThread(() -> {
                        showIpInfo(ipInfo);
                        toast.cancel();
                    }), err -> this.runOnUiThread(() -> {
                        showDialogBox(R.string.ip_lookup_error);
                        toast.cancel();
                    }));

                } catch (Exception e) {
                    this.runOnUiThread(() -> {
                        showDialogBox(R.string.ip_lookup_error);
                        toast.cancel();
                    });
                    //ipAddress.setIpLookupStatus(false);
                }
            }
        }
    }
    private void showIpInfo(final nettools.IpInfo ipInfo) {
        View view = inflter.inflate(R.layout.activity_ipinfo, null);
        ((TextView) view.findViewById(R.id.isp)).setText(ipInfo.getIsp());
        ((TextView) view.findViewById(R.id.org)).setText(ipInfo.getOrg());
        ((TextView) view.findViewById(R.id.country)).setText(ipInfo.getCountry());
        ((TextView) view.findViewById(R.id.region)).setText(ipInfo.getRegionName());
        ((TextView) view.findViewById(R.id.city)).setText(ipInfo.getCity());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.ip_info_title)
                .setView(view)
                .setPositiveButton(R.string.ok, this::closeDialog);
        // Create the AlertDialog object and return it.
        AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        dialog.show();


    }

    private boolean onItemLongClick(AdapterView<?> var1, View var2, int var3, long var4) {
        Object tag = var2.getTag();
        if (tag instanceof PingResult) {
            PingResult ipAddress = (PingResult) tag;
            ClipData clip = ClipData.newPlainText("ip address", ipAddress.getIpAddress());
            clipboard.setPrimaryClip(clip);
            return true;
        }
        return false;
    }

    public void onButtonClick(View v) {
        if (this.start) {
            this.start = false;
            Toast.makeText(this, R.string.stopping, Toast.LENGTH_LONG).show();
            try {
                tracetool.stop();
            } catch (Exception e) {
                Log.e("Exception", e.toString(), e);
            }
            return;
        }
        byte timeout;
        try {
            timeout = Byte.parseByte(editTimeout.getText().toString());
            if (timeout < 1) {
                throw new NumberFormatException();
            }
        }catch(NumberFormatException e) {
            showDialogBox(R.string.Invalid_timeout);
            return;
        }
        listItems.clear();
        adapter.notifyDataSetChanged();
        this.start = true;
        listView.addFooterView(mProgressBarFooter);
        button.setText(R.string.stop);
        editHostname.setEnabled(false);
        editTimeout.setEnabled(false);
        isIpv6Switch.setEnabled(false);

        boolean isIPV6 = isIpv6Switch.isChecked();
        String hostName = editHostname.getText().toString().trim();
        tracetool.tracertRoute(hostName, timeout,isIPV6);
    }

    private void PingResultCallback(PingResult result) {
        listItems.add(result);
        this.runOnUiThread(adapter::notifyDataSetChanged);
        if (result != null && result.getComplated()) {
            this.runOnUiThread(() -> {
                Toast.makeText(this, R.string.completed, Toast.LENGTH_LONG).show();
                onTraceStopped();
            });
        }
    }

    private void onErrorCallback(Exception e) {
        String errMsg = e.getMessage();
        if (errMsg != null) {
            switch (errMsg) {
                case "unreachable":
                    this.runOnUiThread(() -> Toast.makeText(this, R.string.unreachable, Toast.LENGTH_LONG).show());
                    break;
                case "stoped":
                    this.runOnUiThread(() -> Toast.makeText(this, R.string.trace_stop, Toast.LENGTH_LONG).show());
                    break;
                case "noHost" :
                    this.runOnUiThread(() -> showDialogBox(this.getString(R.string.unknown_host) + this.editHostname.getText()));
                    break;
                case "noIPv6":
                    this.runOnUiThread(() -> showDialogBox(this.getString(R.string.no_ipv6, this.editHostname.getText())));
                    break;
                case "noIPv4":
                    this.runOnUiThread(() -> showDialogBox(this.getString(R.string.no_ipv4, this.editHostname.getText())));
                    break;
                case "noNet6":
                    this.runOnUiThread(() -> showDialogBox(R.string.no_net6));
                    break;
                case "noNet":
                    this.runOnUiThread(() -> showDialogBox(R.string.no_net));
                    break;
                default:
                    Log.e("Exception", e.toString(), e);
                    this.runOnUiThread(() -> showDialogBox(errMsg));
                    break;
            }
            this.runOnUiThread(this::onTraceStopped);
            return;

        }
        Log.e("Exception", e.toString(), e);
        this.runOnUiThread(() -> {
            showDialogBox(e.toString());
            this.onTraceStopped();
        });
    }

    private void onTraceStopped() {
        button.setText(R.string.start);
        editHostname.setEnabled(true);
        isIpv6Switch.setEnabled(true);
        editTimeout.setEnabled(true);
        this.start = false;
        listView.removeFooterView(mProgressBarFooter);
    }

    private void showDialogBox(CharSequence message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton(R.string.ok, this::closeDialog);
        // Create the AlertDialog object and return it.
        builder.create().show();
    }

    private void showDialogBox(@StringRes int messageId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(messageId)
                .setPositiveButton(R.string.ok, this::closeDialog);
        // Create the AlertDialog object and return it.
        builder.create().show();
    }

    private void closeDialog(DialogInterface dialog, int id) {
        dialog.cancel();
    }

}