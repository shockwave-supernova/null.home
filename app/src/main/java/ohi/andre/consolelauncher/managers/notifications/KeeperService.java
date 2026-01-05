package ohi.andre.consolelauncher.managers.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.text.SpannableString;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.managers.TimeManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver;
import ohi.andre.consolelauncher.tuils.PublicIOReceiver;
import ohi.andre.consolelauncher.tuils.Tuils;

import static ohi.andre.consolelauncher.managers.TerminalManager.FORMAT_INPUT;
import static ohi.andre.consolelauncher.managers.TerminalManager.FORMAT_NEWLINE;
import static ohi.andre.consolelauncher.managers.TerminalManager.FORMAT_PREFIX;

public class KeeperService extends Service {

    public static final int ONGOING_NOTIFICATION_ID = 1001;
    public static final String CMD_KEY = "cmd";
    public static final String PATH_KEY = "path";
    public static final String CHANNEL_ID = "null_home_keeper_channel"; // Уникальный ID канала

    private String title, subtitle, clickCmd, inputFormat, prefix, suPrefix;
    private boolean showHome, upDown;
    private int inputColor, timeColor, priority;

    private CharSequence[] lastCommands = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (startId == 1 || startId == 0) {
            title = XMLPrefsManager.get(Behavior.tui_notification_title);
            subtitle = XMLPrefsManager.get(Behavior.tui_notification_subtitle);
            clickCmd = XMLPrefsManager.get(Behavior.tui_notification_click_cmd);
            inputFormat = XMLPrefsManager.get(Behavior.input_format);
            showHome = XMLPrefsManager.getBoolean(Behavior.tui_notification_click_showhome);
            inputColor = XMLPrefsManager.getColor(Behavior.tui_notification_input_color);
            timeColor = XMLPrefsManager.getColor(Behavior.tui_notification_time_color);
            prefix = XMLPrefsManager.get(Ui.input_prefix);
            upDown = XMLPrefsManager.getBoolean(Behavior.tui_notification_lastcmds_updown);
            suPrefix = XMLPrefsManager.get(Ui.input_root_prefix);

            priority = XMLPrefsManager.getInt(Behavior.tui_notification_priority);
            if (priority > 2) priority = 2;
            if (priority < -2) priority = -2;

            String path = intent != null ? intent.getStringExtra(PATH_KEY) : Environment.getExternalStorageDirectory().getAbsolutePath();

            startForeground(ONGOING_NOTIFICATION_ID, buildNotification(getApplicationContext(), title, subtitle, Tuils.getHint(path),
                    clickCmd, showHome, lastCommands, upDown, priority));

            int lastCmdSize = XMLPrefsManager.getInt(Behavior.tui_notification_lastcmds_size);
            if (lastCmdSize > 0) {
                lastCommands = new CharSequence[lastCmdSize];
            }

        } else {
            // New command, update the list
            if (lastCommands != null && intent != null) {
                updateCmds(intent.getStringExtra(CMD_KEY));
            }

            String path = intent != null ? intent.getStringExtra(PATH_KEY) : Environment.getExternalStorageDirectory().getAbsolutePath();

            try {
                NotificationManagerCompat.from(getApplicationContext()).notify(KeeperService.ONGOING_NOTIFICATION_ID,
                        KeeperService.buildNotification(getApplicationContext(), title, subtitle, Tuils.getHint(path),
                                clickCmd, showHome, lastCommands, upDown, priority));
            } catch (SecurityException e) {
                // Игнорируем SecurityException, если нет разрешения на уведомления (Android 13+)
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void updateCmds(String cmd) {
        if (cmd == null) return;
        try {
            int lastNull = lastNull();
            int toCopy = lastNull == -1 ? lastCommands.length - 1 : lastNull;
            if (toCopy > 0) {
                System.arraycopy(lastCommands, 0, lastCommands, 1, toCopy);
            }
            lastCommands[0] = formatInput(cmd, inputFormat, prefix, suPrefix, inputColor, timeColor);
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    private static CharSequence formatInput(String cmd, String inputFormat, String prefix, String suPrefix, int inputColor, int timeColor) {
        if (cmd == null) return null;
        boolean su = cmd.startsWith("su ");

        SpannableString si = Tuils.span(inputFormat, inputColor);

        CharSequence s = TimeManager.instance.replace(si, timeColor);
        s = TextUtils.replace(s,
                new String[]{FORMAT_INPUT, FORMAT_PREFIX, FORMAT_NEWLINE, FORMAT_INPUT.toUpperCase(), FORMAT_PREFIX.toUpperCase(), FORMAT_NEWLINE.toUpperCase()},
                new CharSequence[]{cmd, su ? suPrefix : prefix, Tuils.NEWLINE, cmd, su ? suPrefix : prefix, Tuils.NEWLINE});

        return s;
    }

    private int lastNull() {
        for (int c = 0; c < lastCommands.length; c++) if (lastCommands[c] == null) return c;
        return -1;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        lastCommands = null;
        return true;
    }

    public static Notification buildNotification(Context c, String title, String subtitle, String cmdLabel, String clickCmd, boolean showHome, CharSequence[] lastCommands, boolean upDown, int priority) {
        if (priority < -2 || priority > 2) priority = NotificationCompat.PRIORITY_DEFAULT;

        // Определяем флаги для PendingIntent (Android 12+)
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent;
        if (showHome) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (clickCmd != null && clickCmd.length() > 0) {
                startMain.putExtra(PrivateIOReceiver.TEXT, clickCmd);
            }

            pendingIntent = PendingIntent.getActivity(c, 0, startMain, pendingFlags);
        } else if (clickCmd != null && clickCmd.length() > 0) {
            Intent cmdIntent = new Intent(PublicIOReceiver.ACTION_CMD);
            cmdIntent.putExtra(PrivateIOReceiver.TEXT, clickCmd);
            // Для BroadcastReceiver нужен package, чтобы избежать SecurityException на Android 14+
            cmdIntent.setPackage(c.getPackageName());

            pendingIntent = PendingIntent.getBroadcast(c, 0, cmdIntent, pendingFlags);
        } else {
            pendingIntent = null;
        }

        // Создаем канал уведомлений (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW; // Default low to avoid sound
            if (priority >= NotificationCompat.PRIORITY_HIGH) importance = NotificationManager.IMPORTANCE_HIGH;
            else if (priority <= NotificationCompat.PRIORITY_MIN) importance = NotificationManager.IMPORTANCE_MIN;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Keeper Service", importance);
            channel.setDescription("Persistent notification for quick access");
            channel.setShowBadge(false);
            
            NotificationManager manager = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(c.getString(R.string.start_notification))
                .setWhen(System.currentTimeMillis())
                .setPriority(priority)
                .setContentTitle(title)
                .setContentIntent(pendingIntent)
                .setOngoing(true); // Важно для постоянного уведомления

        NotificationCompat.Style style = null;
        if (lastCommands != null && lastCommands.length > 0 && lastCommands[0] != null) {
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

            if (upDown) {
                for (CharSequence lastCommand : lastCommands) {
                    if (lastCommand == null) break;
                    inboxStyle.addLine(lastCommand);
                }
            } else {
                for (int j = lastCommands.length - 1; j >= 0; j--) {
                    if (lastCommands[j] == null) continue;
                    inboxStyle.addLine(lastCommands[j]);
                }
            }
            style = inboxStyle;
        }

        if (style != null) builder.setStyle(style);
        else {
            builder.setContentTitle(title);
            builder.setContentText(subtitle);
        }

        RemoteInput remoteInput = new RemoteInput.Builder(PrivateIOReceiver.TEXT)
                .setLabel(cmdLabel)
                .build();

        Intent i = new Intent(PublicIOReceiver.ACTION_CMD);
        i.setPackage(c.getPackageName()); // Важно для безопасности

        // Флаг MUTABLE нужен для RemoteInput на Android 12+
        int remoteInputFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            remoteInputFlags |= PendingIntent.FLAG_MUTABLE;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remoteInputFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                c.getApplicationContext(), 
                40, 
                i, 
                remoteInputFlags
        );

        NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                R.mipmap.ic_launcher,
                cmdLabel,
                replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build();

        builder.addAction(action);

        return builder.build();
    }
}
