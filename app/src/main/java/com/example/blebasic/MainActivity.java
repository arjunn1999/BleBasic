package com.example.blebasic;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    BluetoothAdapter adapter;
    Button send,pair;
    Context c = this;
    TextView status,messages;
    BluetoothDevice[] devices;
    String[]  deviceNames;
    BluetoothDevice pairdevice;
    SendRecieve sendRecieve;
    EditText msg;
    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED= 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECIEVED = 5;
    static  final  int STATE_MESSAGE_SENT=6;
    static  String APP_NAME;
     UUID MY_UUID;

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message message) {
            switch (message.what){
                case STATE_LISTENING:
                    status.setText("Listening ...");
                    break;
                case STATE_CONNECTING:
                    status.setText("Connecting ...");
                    break;
                case STATE_CONNECTED:
                    status.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    status.setText("Connection Failed");
                    break;
                case STATE_MESSAGE_RECIEVED:
                    status.setText("Message Recieved");
                    byte[] readbuff = (byte[]) message.obj;
                    String st = new String(readbuff,0,message.arg1);
                    messages.setText(messages.getText()+"\n"+st);
                    break;
                case STATE_MESSAGE_SENT:
                    status.setText("Message Sent");
                    break;

                default:
                    break;
            }
            return false;
        }
    });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MY_UUID= UUID.fromString("f5f7410c-25d1-4f37-8a39-45df252aa125");

        setContentView(R.layout.activity_main);
        adapter = BluetoothAdapter.getDefaultAdapter();
        APP_NAME = getResources().getResourceName(R.string.app_name);
        findViews();
        implementOnclickListeners();
    }

    private void findViews() {
        pair = (Button) findViewById(R.id.pair);
        send  = (Button) findViewById(R.id.send);
        status = (TextView) findViewById(R.id.status);
        messages = (TextView) findViewById(R.id.messages);
        msg = (EditText)  findViewById(R.id.sendField);
    }

    private void implementOnclickListeners() {
        pair.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Set <BluetoothDevice> d = adapter.getBondedDevices();
                int i=0;
                devices = new BluetoothDevice[d.size()];
                deviceNames = new String[d.size()];
                for(BluetoothDevice device : d){
                    devices[i] = device;
                    deviceNames[i]= device.getName()+" - "+device.getAddress();
                    i++;
                }
                final Dialog dialog = new Dialog(c);
                dialog.setContentView(R.layout.card_layout);
                ArrayAdapter adapter = new ArrayAdapter <String>(c, android.R.layout.simple_list_item_1,deviceNames);
                ListView lv = (ListView) dialog.findViewById(R.id.lv);
                lv.setAdapter(adapter);
                Button btn = (Button) dialog.findViewById(R.id.cancel);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                    }
                });
                lv.setOnItemClickListener((adapterView, view1, i1, l) -> {
                    Toast.makeText(getApplicationContext(),"Connecting to  "+deviceNames[i1],Toast.LENGTH_SHORT).show();
                    pairdevice=devices[i1];
                    Connect c = new Connect(pairdevice);
                    c.start();
                    dialog.dismiss();
                });
                dialog.show();
            }
        });
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String m = String.valueOf(msg.getText());
                if(m!=null || m!=""){
                    sendRecieve.send(String.valueOf(msg.getText()));
                }
                else{
                    Toast.makeText(c, "Content should not be empty", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    private  class Connect extends  Thread{
        private  BluetoothDevice device;
        private BluetoothSocket socket;
        public Connect(BluetoothDevice d){
            device=d;
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void run(){
            try {
                if(socket==null) {
                System.out.println("socket is null");
                }
                if(device==null){
                    System.out.println("Device is null");
                }
                System.out.println("Connecting to socket");
                socket.connect();
                Message m = Message.obtain();
                m.what = STATE_CONNECTED;
                handler.sendMessage(m);
                sendRecieve = new SendRecieve(socket);
                sendRecieve.start();

            } catch (IOException e) {
                e.printStackTrace();
                Message m = Message.obtain();
                m.what = STATE_CONNECTION_FAILED;
                handler.sendMessage(m);
            }
        }
    }

    private class SendRecieve extends  Thread{
        private  BluetoothSocket socket;
        private final InputStream input;
        private final OutputStream output;

        public SendRecieve(BluetoothSocket s){
            socket =s;
            InputStream tmpIN = null;
            OutputStream tmpOut = null;
            System.out.println("Starting SendRecieve constructor");

            try {
                tmpOut = socket.getOutputStream();
                tmpIN = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            input = tmpIN;
            output = tmpOut;

        }
        public  void  run(){
                System.out.println("Starting SendRecieve");
                byte[] buffer = new byte[1024];
                int bytes;
            try {
                bytes = input.read(buffer);
                handler.obtainMessage(STATE_MESSAGE_RECIEVED,bytes,-1,buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        public void send(String s){
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            try {
                output.write(b);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        adapter.cancelDiscovery();
    }
}