//package info.mqtt.android.service.ping;
//
//import android.app.AlarmManager;
//import android.app.PendingIntent;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.os.Build;
//
//import info.mqtt.android.service.MqttService;
//import info.mqtt.android.service.MqttServiceConstants;
//
//import org.eclipse.paho.client.mqttv3.MqttException;
//import org.eclipse.paho.client.mqttv3.MqttPingSender;
//import org.eclipse.paho.client.mqttv3.internal.ClientComms;
//
//public class AlarmPingSender implements MqttPingSender {
//    private final MqttService service;
//    private final String clientId;
//
//    private ClientComms comms;
//    private PendingIntent pendingIntent;
//    private AlarmReceiver receiver;
//    private boolean started = false;
//
//    public AlarmPingSender(MqttService service, String clientId) {
//        this.service  = service;
//        this.clientId = clientId;
//    }
//
//    @Override
//    public void init(ClientComms comms) {
//        this.comms = comms;
//    }
//
//    @Override
//    public void start() throws MqttException {
//        if (started) return;
//
//        String action = MqttServiceConstants.PING_SENDER + clientId;
//        receiver = new AlarmReceiver(comms);
//
//        // <-- here we specify the NOT_EXPORTED flag required on Android 14+
//        service.registerReceiver(
//                receiver,
//                new IntentFilter(action),
//                Context.RECEIVER_NOT_EXPORTED,
//                /* permission */ null
//        );
//
//        Intent intent = new Intent(action);
//        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            flags |= PendingIntent.FLAG_IMMUTABLE;
//        }
//        pendingIntent = PendingIntent.getBroadcast(service, 0, intent, flags);
//
//        schedule(comms.getKeepAlive());
//        started = true;
//    }
//
//    @Override
//    public void schedule(long delayInMilliseconds) throws MqttException {
//        AlarmManager alarmManager =
//                (AlarmManager) service.getSystemService(Context.ALARM_SERVICE);
//        long triggerAt = System.currentTimeMillis() + delayInMilliseconds;
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            alarmManager.setExactAndAllowWhileIdle(
//                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
//            );
//        } else {
//            alarmManager.setExact(
//                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
//            );
//        }
//    }
//
//    @Override
//    public void stop() throws MqttException {
//        if (!started) return;
//        AlarmManager alarmManager =
//                (AlarmManager) service.getSystemService(Context.ALARM_SERVICE);
//        alarmManager.cancel(pendingIntent);
//        service.unregisterReceiver(receiver);
//        started = false;
//    }
//}