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
import  com.google.android.material.materialswitch.MaterialSwitch;

import androidx.activity.EdgeToEdge;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import ping.Ping;
import ping.Result;

public class MainActivity extends AppCompatActivity {
    private ListView listView;
    private Button button;
    private EditText editText;
    private MaterialSwitch isIpv6Switch;
    private IpAdapter adapter;
    private ArrayList<Result> listItems = new ArrayList<>(65);

    private LayoutInflater inflter;
    private ClipboardManager clipboard;

    private boolean start = false;

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
        editText = findViewById(R.id.editTextText);
        isIpv6Switch = findViewById(R.id.switch1);
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        adapter = new IpAdapter(this.getBaseContext(), listItems);

        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(this::onItemLongClick);
        listView.setOnItemClickListener(this::onItemClick);
    }

    private void onItemClick(AdapterView<?> var1, View var2, int var3, long var4) {
        Object tag = var2.getTag();
        if (tag instanceof Result) {
            final Result ipAddress = (Result)tag;
            if (ipAddress.getIpLookupStatus()) {
                Toast toast = Toast.makeText(this, R.string.ip_lookup, Toast.LENGTH_LONG);
                toast.show();
                    new Thread(()-> {
                        try {
                            if (InetAddress.getByName(ipAddress.getIpAddress()).isSiteLocalAddress()) {
                                this.runOnUiThread(()-> showDialogBox(R.string.private_IP));
                                return;
                            };
                            ping.IpInfo ipInfo = ipAddress.lookupIpInfo(false);
                            this.runOnUiThread(()->showIpInfo(ipInfo));
                        } catch (Exception e) {
                            this.runOnUiThread(()-> showDialogBox(R.string.ip_lookup_error));
                            //ipAddress.setIpLookupStatus(false);
                        } finally {
                            this.runOnUiThread(toast::cancel);
                        }
                    }).start();
            }
        }
    }

    private void showIpInfo(final ping.IpInfo ipInfo) {
        View view = inflter.inflate(R.layout.activity_ipinfo, null);
        ((TextView)view.findViewById(R.id.isp)).setText(ipInfo.getIsp());
        ((TextView)view.findViewById(R.id.org)).setText(ipInfo.getOrg());
        ((TextView)view.findViewById(R.id.country)).setText(ipInfo.getCountry());
        ((TextView)view.findViewById(R.id.region)).setText(ipInfo.getRegionName());
        ((TextView)view.findViewById(R.id.city)).setText(ipInfo.getCity());
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
        if (tag instanceof ping.Result) {
            ping.Result ipAddress = (ping.Result)tag;
            ClipData clip = ClipData.newPlainText("ip address", ipAddress.getIpAddress());
            clipboard.setPrimaryClip(clip);
            return  true;
        }
        return false;
    }
    public void onButtonClick(View v) {
        if (this.start) {
            this.start = false;
            Toast.makeText(this, R.string.stopping, Toast.LENGTH_LONG).show();
            return;
        }

        listItems.clear();
        adapter.notifyDataSetChanged();
        this.start = true;
        button.setText(R.string.stop);
        editText.setEnabled(false);
        isIpv6Switch.setEnabled(false);

        boolean isIPV6 = isIpv6Switch.isChecked();
        new Thread(() -> {
            String ipAddress = null;
            try {
                String hostName =editText.getText().toString().trim();
                try {

                    InetAddress[] inetAddresses =  InetAddress.getAllByName(hostName);

                    for (InetAddress inetAddress: inetAddresses) {
                        if (isIPV6) {
                            if (inetAddress instanceof  Inet6Address) {
                                ipAddress = inetAddress.getHostAddress();
                                break;
                            }
                        } else {
                            if (inetAddress instanceof  Inet4Address) {
                                ipAddress = inetAddress.getHostAddress();
                                break;
                            }
                        }
                    }
                    if (ipAddress == null) {
                        runOnUiThread(()-> showDialogBox(this.getString(R.string.unknown_host) + hostName + (isIPV6? this.getString(R.string.no_ipv6): "") ));
                        return;
                    }

                } catch (UnknownHostException e) {
                    Log.e("err", e.toString(),e);
                    runOnUiThread(()-> showDialogBox(this.getString(R.string.unknown_host) + hostName));
                    return;
                }
                loop:
                for (int ttl = 1; ttl < 65 && start; ++ttl) {
                    Result result = Ping.ping(ipAddress, (byte) ttl, isIPV6);
                    switch (result.getStatus()) {
                        case 0: {
                            //IpAddress address = new IpAddress(result.getIpAddress(), result.getDelay());
                            listItems.add(result);
                            this.runOnUiThread(() -> {
                                adapter.notifyDataSetChanged();
                                Toast.makeText(this, R.string.complated, Toast.LENGTH_LONG).show();
                            });
                            break loop;
                        }
                        case 1: {
                            //IpAddress address = new IpAddress(result.getIpAddress(), result.getDelay());
                            listItems.add(result);
                            this.runOnUiThread(adapter::notifyDataSetChanged);
                            if (ttl == 64) {
                                this.runOnUiThread(() -> Toast.makeText(this, R.string.unreachable, Toast.LENGTH_LONG).show());
                            }
                            break;
                        }
                        case -1:
                            listItems.add(null);
                            this.runOnUiThread(adapter::notifyDataSetChanged);
                            break;
                        default:
                            runOnUiThread(() -> showDialogBox("error "+ result.getStatus()));
                            break loop;
                    }

                }
            } catch (Exception e) {
                Log.e("Exception", e.toString(), e);
                this.runOnUiThread(()-> showDialogBox(e.toString()));
            } finally {
                if (this.start) {
                    this.start = false;
                } else {
                    this.runOnUiThread(()->Toast.makeText(this,R.string.trace_stop, Toast.LENGTH_LONG).show());
                }
                this.runOnUiThread(() -> {
                    button.setText(R.string.start);
                    editText.setEnabled(true);
                    isIpv6Switch.setEnabled(true);
                });

            }
        }).start();
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