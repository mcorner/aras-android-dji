package com.dji.sdk.sample.internal.utils;

import com.dji.sdk.sample.R;
import com.koushikdutta.async.*;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.dji.sdk.sample.internal.controller.ARASMessageManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class SocketServer extends Service {

    private InetAddress host;
    private int port;
    private Handler handler;
    private Runnable runnable;
    public static final int NOTIFICATION_ID = 1337;

    public SocketServer() { }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();
        runnable = () -> {
            try {
                host = InetAddress.getByName("0.0.0.0");
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }

            port = 11111;
            setup();
        };
        handler.post(runnable);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startMyOwnForeground();
        else
            startForeground(1, new Notification());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void setup() {
        AsyncServer.getDefault().listen(host, port, new ListenCallback() {

            @Override
            public void onAccepted(final AsyncSocket socket) {
                handleAccept(socket);
            }

            @Override
            public void onListening(AsyncServerSocket socket) {
                ToastUtils.setResultToToast(getApplicationContext().getResources().getString(R.string.server_started_listening_message));
            }

            @Override
            public void onCompleted(Exception ex) {
                // please dont do it, fix it IMPORTANT
                //if(ex != null) throw new RuntimeException(ex);
                ToastUtils.setResultToToast(getApplicationContext().getResources().getString(R.string.server_shutdow_sucessfuly_message));
            }
        });
    }

    private void handleAccept(final AsyncSocket socket) {
        //ToastUtils.setResultToToast(getApplicationContext().getResources().getString(R.string.server_new_connection_message));

        socket.setDataCallback(new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                // please dont do it, fix it IMPORTANT
                try{
                    String receivedMessage = new String(bb.getAllByteArray());
                    //ToastUtils.setResultToToast(getApplicationContext().getResources().getString(R.string.server_received_message) + receivedMessage);
                    ARASMessageManager.getInstance().setContext(SocketServer.this);
                    if(socket.isOpen()){
                        ARASMessageManager.getInstance().processMessage(socket, receivedMessage); // Process received message
                    }
                }catch (Exception e){
                }
            }
        });

        socket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                // please dont do it, fix it IMPORTANT
                //if (ex != null) throw new RuntimeException(ex);
                //ToastUtils.setResultToToast(getApplicationContext().getResources().getString(R.string.server_closed_connection_sucessfully_message));
            }
        });

        socket.setEndCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                // please dont do it, fix it IMPORTANT
                //if (ex != null) throw new RuntimeException(ex);
                //ToastUtils.setResultToToast(getApplicationContext().getResources().getString(R.string.server_end_connection_successfully_message));
            }
        });
    }

    @Override
    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacks(runnable);
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    private void startMyOwnForeground(){
        String NOTIFICATION_CHANNEL_ID = "com.dji.sdk.sample";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            String channelName = "ARAS - Background Service";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
            Notification notification = notificationBuilder.setOngoing(true)
                    .setSmallIcon(R.drawable.aircraft)
                    .setContentTitle("ARAS is running in background")
                    .setPriority(NotificationManager.IMPORTANCE_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build();
            startForeground(2, notification);
        }
    }



    @SuppressWarnings("deprecation")
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }
}