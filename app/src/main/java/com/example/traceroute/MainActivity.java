package com.example.traceroute;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import  com.google.android.material.materialswitch.MaterialSwitch;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;

import com.example.traceroute.IpAdapter;
import com.example.traceroute.IpAdapter.IpAddress;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    private ListView listView;
    private Button button;
    private EditText editText;
    private MaterialSwitch isIpv6Switch;
    private IpAdapter adapter;
    private ArrayList<IpAddress> listItems = new ArrayList<>(65);

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
        adapter = new IpAdapter(this, listItems);
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(this::onItemLongClick);
        listView.setOnItemClickListener(this::onItemClick);
    }

    private void onItemClick(AdapterView<?> var1, View var2, int var3, long var4) {
        Object tag = var2.getTag();
        if (tag instanceof IpAddress) {
            final IpAddress ipAddress = (IpAddress)tag;
            if (ipAddress.isIpLookupStatus()) {
                IpAdapter.IpInfo ipInfo = ipAddress.getIpInfo();
                if (ipInfo == null) {
                    Toast.makeText(this, "Looking Up IP Info...", Toast.LENGTH_LONG).show();

                    new Thread(()-> {
                        try {
                            if (InetAddress.getByName(ipAddress.getAddress()).isSiteLocalAddress()) {
                                this.runOnUiThread(()->showDialog("This is a private IP address"));
                                return;
                            };
                            HttpURLConnection conn = (HttpURLConnection)new URL("http://ip-api.com/json/"+ipAddress.getAddress()+"?fields=isp,city,org,country,status,regionName").openConnection();
                            final IpAdapter.IpInfo ipInfo2 = new IpAdapter.IpInfo(new JsonReader(new InputStreamReader(conn.getInputStream())));
                            ipAddress.setIpInfo(ipInfo2);
                            this.runOnUiThread(()->showIpInfo(ipInfo2));
                        } catch (IOException e) {
                            Log.e("ip lookup", e.toString(), e);
                            this.runOnUiThread(()->showDialog("Error look up IP"));
                        } catch (IpAdapter.LookFailException e) {
                            this.runOnUiThread(()->showDialog("Error look up IP"));
                            ipAddress.setIpLookupStatus(false);
                        }
                    }).start();
                } else {
                    showIpInfo(ipInfo);
                }
            }
        }
    }

    private void showIpInfo(final IpAdapter.IpInfo ipInfo) {
        View view = inflter.inflate(R.layout.activity_ipinfo, null);
        ((TextView)view.findViewById(R.id.isp)).setText(ipInfo.getIsp());
        ((TextView)view.findViewById(R.id.org)).setText(ipInfo.getOrg());
        ((TextView)view.findViewById(R.id.country)).setText(ipInfo.getCountry());
        ((TextView)view.findViewById(R.id.region)).setText(ipInfo.getRegionName());
        ((TextView)view.findViewById(R.id.city)).setText(ipInfo.getCity());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("IP Information")
                .setView(view)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
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
        if (tag instanceof IpAddress) {
            IpAddress ipAddress = (IpAddress)tag;
            ClipData clip = ClipData.newPlainText("ip address", ipAddress.getAddress());
            clipboard.setPrimaryClip(clip);
            return  true;
        }
        return false;
    }
    public void onButtonClick(View v) {
        if (this.start) {
            this.start = false;
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
                        runOnUiThread(()-> showDialog(this.getString(R.string.unknown_host) + hostName + (isIPV6? this.getString(R.string.no_ipv6): "") ));
                        return;
                    }

                } catch (UnknownHostException e) {
                    Log.e("err", e.toString(),e);
                    runOnUiThread(()-> showDialog(this.getString(R.string.unknown_host) + hostName));
                    return;
                }
                long time;
                loop:
                for (int ttl = 1; ttl < 65 && start; ++ttl) {
                    Process process = Runtime.getRuntime().exec(new String[]{isIPV6 ? "ping6" : "ping", "-c1", "-t" + ttl, ipAddress});

                    StringWriter errSW = new StringWriter();
                    BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader errIn = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    time = System.currentTimeMillis();
                    int returnCode = process.waitFor();
                    time = System.currentTimeMillis() - time;
                    switch (returnCode) {
                        case 0: {
                            in.readLine();
                            in.skip(14);
                            String out = in.readLine();
                            String[] outList = out.split(" ");
                            IpAddress address = new IpAddress(isIPV6 ? outList[0] : outList[0].substring(0, outList[0].length() - 1), outList[3].substring(5));
                            Log.i("output", out);

                            listItems.add(address);
                            this.runOnUiThread(() -> {
                                adapter.notifyDataSetChanged();
                                Toast.makeText(this, R.string.complated, Toast.LENGTH_LONG).show();
                            });
                            in.close();
                            break loop;
                        }
                        case 1: {
                            in.readLine();
                            String out = in.readLine();
                            String[] outList = out.split(" ");

                            Log.i("output", out);
                            if (outList.length < 2) {
                                if (out.isEmpty()) {

                                    listItems.add(null);
                                } else {
                                    listItems.add(new IpAddress(out, ""));
                                }
                            } else {
                                listItems.add(new IpAddress(isIPV6 ? outList[1] : outList[1].substring(0, outList[1].length() - 1), Long.toString(time)));
                            }
                            this.runOnUiThread(adapter::notifyDataSetChanged);
                            if (ttl == 64) {
                                this.runOnUiThread(() -> Toast.makeText(this, R.string.unreachable, Toast.LENGTH_LONG).show());
                            }
                            in.close();
                            break;
                        }
                        default:
                            errIn.transferTo(errSW);
                            in.close();
                            String errSrr = errSW.toString();
                            Log.e("err", errSrr);
                            runOnUiThread(() -> showDialog(errSrr));

                            break loop;
                    }

                }
            } catch (IOException | InterruptedException e) {
                Log.e("Exception", e.toString(), e);
                this.runOnUiThread(()-> showDialog("Exception"));
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

    private void showDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        // Create the AlertDialog object and return it.
        builder.create().show();
    }
}