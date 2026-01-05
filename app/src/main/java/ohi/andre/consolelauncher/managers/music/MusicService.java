package ohi.andre.consolelauncher.managers.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.MainManager;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver;
import ohi.andre.consolelauncher.tuils.PublicIOReceiver;
import ohi.andre.consolelauncher.tuils.Tuils;

public class MusicService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {

    public static final int NOTIFY_ID = 100001;
    public static final String CHANNEL_ID = "tui_music_channel";

    private MediaPlayer player;
    private List<Song> songs;
    private int songPosn;
    private final IBinder musicBind = new MusicBinder();
    private String songTitle = Tuils.EMPTYSTRING;
    private boolean shuffle = false;

    private long lastNotificationChange;

    public void onCreate() {
        super.onCreate();
        songPosn = 0;
        initMusicPlayer();
        lastNotificationChange = System.currentTimeMillis();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Controls for music playback");
            serviceChannel.setSound(null, null);
            serviceChannel.enableVibration(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Если сервис перезапускается системой, и у нас нет данных, просто возвращаем STICKY
        if (songTitle == null || songTitle.isEmpty()) {
            return START_STICKY;
        }

        if (System.currentTimeMillis() - lastNotificationChange > 500) {
            lastNotificationChange = System.currentTimeMillis();
            try {
                startForeground(NOTIFY_ID, buildNotification(this, songTitle));
            } catch (Exception e) {
                Tuils.log(e);
            }
        }
        
        return START_STICKY;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (songTitle == null || songTitle.isEmpty()) return;

        lastNotificationChange = System.currentTimeMillis();
        mp.start();
        
        try {
            startForeground(NOTIFY_ID, buildNotification(this, songTitle));
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    public void initMusicPlayer() {
        if (player == null) {
            player = new MediaPlayer();
        } else {
            player.reset();
        }
        
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
        } else {
            // Для старых версий
            // player.setAudioStreamType(AudioManager.STREAM_MUSIC); 
        }
        
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
    }

    public void setList(List<Song> theSongs) {
        songs = theSongs;
        if (shuffle && songs != null) Collections.shuffle(songs);
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        return false;
    }

    public String playSong() {
        if (player == null) initMusicPlayer();
        else player.reset();

        if (songs == null || songs.isEmpty() || songPosn >= songs.size()) return null;

        Song playSong = songs.get(songPosn);
        songTitle = playSong.getTitle();
        long id = playSong.getID();

        try {
            if (id == -1) {
                String path = playSong.getPath();
                if (path != null) {
                    player.setDataSource(path);
                } else {
                    return null;
                }
            } else {
                Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                player.setDataSource(getApplicationContext(), trackUri);
            }
            player.prepareAsync();
        } catch (Exception e) {
            Tuils.log("Error setting data source", e);
            return null;
        }

        return songTitle;
    }

    public void setSong(int songIndex) {
        songPosn = songIndex;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (player != null && player.getCurrentPosition() > 0) {
            mp.reset();
            playNext();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        if (mp != null) mp.reset();
        return false;
    }

    public static Notification buildNotification(Context context, String songTitle) {
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent notIntent = new Intent(context, LauncherActivity.class);
        PendingIntent pendInt = PendingIntent.getActivity(context, 0, notIntent, pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(songTitle)
                .setOngoing(true)
                .setContentTitle("Playing")
                .setContentText(songTitle)
                .setContentIntent(pendInt)
                .setPriority(NotificationCompat.PRIORITY_LOW); // Чтобы не шумело

        // Кнопка для команд (RemoteInput)
        // В Android 12+ (S) для PendingIntent внутри уведомлений, которые запускают BroadcastReceiver,
        // лучше использовать FLAG_MUTABLE, если мы хотим получать данные ввода.
        int broadcastFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            broadcastFlags |= PendingIntent.FLAG_MUTABLE; 
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            broadcastFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        String label = "cmd";
        // RemoteInput поддерживается с API 20 (KitKat Watch), но NotificationCompat это обрабатывает
        RemoteInput remoteInput = new RemoteInput.Builder(PrivateIOReceiver.TEXT)
                .setLabel(label)
                .build();

        Intent i = new Intent(PublicIOReceiver.ACTION_CMD);
        i.putExtra(MainManager.MUSIC_SERVICE, true);
        // Важно: setPackage чтобы интент точно пришел в наше приложение (защита на Android 14)
        i.setPackage(context.getPackageName());

        PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                context.getApplicationContext(), 
                10, 
                i, 
                broadcastFlags
        );

        NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                R.mipmap.ic_launcher, 
                label,
                replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build();

        builder.addAction(action);

        return builder.build();
    }

    public int getPosn() {
        return (player != null) ? player.getCurrentPosition() : 0;
    }

    public int getDur() {
        return (player != null) ? player.getDuration() : 0;
    }

    public boolean isPng() {
        return (player != null) && player.isPlaying();
    }

    public void pausePlayer() {
        if (player != null && player.isPlaying()) player.pause();
    }

    public void stop() {
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (Exception ignored) {}
            player.release();
            player = null;
        }
        setSong(0);
    }

    public void playPlayer() {
        if (player != null) player.start();
    }

    public void seek(int posn) {
        if (player != null) player.seekTo(posn);
    }

    public void go() {
        if (player != null) player.start();
    }

    public String playPrev() {
        if (songs == null || songs.isEmpty()) return getString(R.string.no_songs);
        songPosn--;
        if (songPosn < 0) songPosn = songs.size() - 1;
        return playSong();
    }

    public String playNext() {
        if (songs == null || songs.isEmpty()) return getString(R.string.no_songs);
        songPosn++;
        if (songPosn >= songs.size()) songPosn = 0;
        return playSong();
    }

    public int getSongIndex() {
        return songPosn;
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            try { player.stop(); } catch (Exception e) {}
            player.release();
            player = null;
        }
        if (songs != null) songs.clear();
        stopForeground(true);
        super.onDestroy();
    }

    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;
        if (this.shuffle && songs != null) {
            Collections.shuffle(songs);
        }
    }
}
