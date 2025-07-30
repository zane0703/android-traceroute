package com.example.traceroute;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private ListView listView;
    private Button button;
    private EditText editText;
    private Switch isIpv6Switch;
    private ArrayAdapter adapter;
    ArrayList<String> listItems = new ArrayList<String>(65);

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
        button = findViewById(R.id.button);
        listView = findViewById(R.id.listView);
        editText = findViewById(R.id.editTextText);
        isIpv6Switch = findViewById(R.id.switch1);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listItems);
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener((v,v2, a,b)-> {
            if (v2 instanceof TextView) {
                String addr = ((TextView)v2).getText().toString();
                addr = addr.substring(addr.indexOf(')')+2);
                ClipData clip = ClipData.newPlainText("ip address", addr);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this,"Coped", Toast.LENGTH_SHORT).show();
            }

            return  true;
        });
    }
    public void onButtonClick(View v) {
        listItems.clear();
        adapter.notifyDataSetChanged();
        button.setEnabled(false);


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
                        runOnUiThread(()-> showDialog("Unknown Host "+ hostName + (isIPV6?"\nor no IPV6 address available": "") ));
                        return;
                    }

                } catch (UnknownHostException e) {
                    Log.e("err", e.toString(),e);
                    runOnUiThread(()-> showDialog("Unknown Host "+ hostName));
                    return;
                }
                loop:
                for (int ttl = 1; ttl < 65; ++ttl) {
                    Process process = Runtime.getRuntime().exec(new String[]{isIPV6 ? "ping6" : "ping", "-c1", "-t" + ttl, ipAddress});
                    StringWriter errSW = new StringWriter();
                    BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader errIn = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    errIn.transferTo(errSW);
                    switch (process.waitFor()) {
                        case 0:
                            in.readLine();
                            String out = in.readLine();
                            Log.i("output", out);
                            listItems.add(ttl + ") " + out.substring(14).replace(" icmp_seq=1", ""));
                            this.runOnUiThread(()-> {
                                adapter.notifyDataSetChanged();
                                Toast.makeText(this, "Trace Completed", Toast.LENGTH_LONG).show();
                            });
                            in.close();
                            break loop;
                        case 1:
                            in.readLine();
                            String out2 = in.readLine();
                            String[] out3 = out2.split(" ");
                            Log.i("output", out2);
                            if (out3.length < 2) {
                                if (out2.isEmpty()) {
                                    listItems.add(ttl +  ") Request timed out.");
                                } else {
                                    listItems.add(out2);
                                }
                            } else {
                                listItems.add(ttl + ") " + (isIPV6? out3[1]: out3[1].substring(0, out3[1].length() - 1)));
                            }
                            this.runOnUiThread(adapter::notifyDataSetChanged);
                            in.close();
                            break;
                        default:
                            in.close();
                            String errSrr = errSW.toString();
                            Log.e("err", errSrr);
                            runOnUiThread(() -> showDialog(errSrr));

                            break loop;
                    }

                }
            } catch (IOException | InterruptedException e) {
                Log.e("abc", e.toString(), e);
                this.runOnUiThread(()-> showDialog("Exception"));
            } finally {
                this.runOnUiThread(() -> button.setEnabled(true));
            }
        }).start();
    }

    private void showDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        // Create the AlertDialog object and return it.
        builder.create().show();
    }
}