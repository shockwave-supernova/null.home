package ohi.andre.consolelauncher.managers.music;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.widget.MediaController;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Created by francescoandreuzzi on 17/08/2017.
 * Updated/Refactored for modern Android standards.
 */

public class MusicManager2 implements MediaController.MediaPlayerControl {

    public static final String[] MUSIC_EXTENSIONS = {".mp3", ".wav", ".ogg", ".flac", ".m4a", ".aac"};

    private static final int WAITING_NEXT = 10;
    private static final int WAITING_PREVIOUS = 11;
    private static final int WAITING_PLAY = 12;
    private static final int WAITING_LISTEN = 13;

    private final Context mContext;
    private final List<Song> songs = new ArrayList<>();

    private MusicService musicSrv;
    private boolean musicBound = false;
    private Intent playIntent;

    private boolean playbackPaused = true;
    private boolean stopped = true;

    // Используем Executor вместо Thread для переиспользования потоков
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int waitingMethod = 0;
    private String savedParam;

    private final BroadcastReceiver headsetBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 0 = unplugged, 1 = plugged
            if (intent.getIntExtra("state", -1) == 0) {
                pause();
            }
        }
    };

    private final ServiceConnection musicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicSrv = binder.getService();
            musicSrv.setShuffle(XMLPrefsManager.getBoolean(Behavior.random_play));
            
            // Передаем песни в сервис
            musicSrv.setList(songs);
            musicBound = true;

            // Выполняем отложенное действие
            switch (waitingMethod) {
                case WAITING_NEXT:
                    playNext();
                    break;
                case WAITING_PREVIOUS:
                    playPrev();
                    break;
                case WAITING_PLAY:
                    play();
                    break;
                case WAITING_LISTEN:
                    select(savedParam);
                    break;
            }

            waitingMethod = 0;
            savedParam = null;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    public MusicManager2(Context c) {
        // Используем Application Context для ресивера, чтобы избежать утечек
        mContext = c.getApplicationContext(); 
        
        updateSongs();

        String action = AudioManager.ACTION_HEADSET_PLUG;
        // Регистрация ресивера. На новых Android лучше делать это динамически, но для наушников это работает.
        // Используем флаг экспорта для Android 14+ если потребуется, но для системных экшенов это обычно не нужно в этом контексте
        try {
            mContext.registerReceiver(headsetBroadcast, new IntentFilter(action));
        } catch (Exception e) {
            Tuils.log(e);
        }

        init();
    }

    public void init() {
        if (playIntent == null) {
            playIntent = new Intent(mContext, MusicService.class);
            try {
                mContext.bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mContext.startForegroundService(playIntent);
                } else {
                    mContext.startService(playIntent);
                }
            } catch (Exception e) {
                Tuils.log("MusicManager init error: " + e.getMessage());
            }
        }
    }

    public void refresh() {
        // Не обязательно полностью уничтожать сервис для обновления списка песен
        updateSongs();
    }

    public void destroy() {
        if (musicSrv != null && musicBound) {
            // Останавливаем сервис только если это действительно нужно
            // musicSrv.stop(); // Обычно мы хотим, чтобы музыка играла в фоне
            try {
                mContext.unbindService(musicConnection);
            } catch (Exception e) {
                Tuils.log(e);
            }
            musicBound = false;
        }
        
        // playIntent не останавливаем, чтобы музыка играла при выходе из лаунчера (если так задумано)
        // Если нужно убить музыку при выходе - раскомментируйте:
        /*
        if (playIntent != null) {
            mContext.stopService(playIntent);
            playIntent = null;
        }
        */

        try {
            mContext.unregisterReceiver(headsetBroadcast);
        } catch (IllegalArgumentException e) {
            // Ресивер не был зарегистрирован или уже удален
        } catch (Exception e) {
            Tuils.log(e);
        }

        musicSrv = null;
    }

    public String playNext() {
        if (!musicBound) {
            init();
            waitingMethod = WAITING_NEXT;
            return null;
        }

        playbackPaused = false;
        stopped = false;
        return musicSrv.playNext();
    }

    public String playPrev() {
        if (!musicBound) {
            init();
            waitingMethod = WAITING_PREVIOUS;
            return null;
        }

        playbackPaused = false;
        stopped = false;
        return musicSrv.playPrev();
    }

    @Override
    public void pause() {
        if (musicSrv == null) return;
        playbackPaused = true;
        musicSrv.pausePlayer();
    }

    public String play() {
        if (!musicBound) {
            init();
            waitingMethod = WAITING_PLAY;
            return null;
        }

        if (stopped) {
            musicSrv.playSong();
            playbackPaused = false;
            stopped = false;
        } else if (playbackPaused) {
            playbackPaused = false;
            musicSrv.playPlayer();
        } else {
            pause();
        }

        return null;
    }

    public String lsSongs() {
        if (songs.isEmpty()) return "[]";

        List<String> ss = new ArrayList<>();
        for (Song s : songs) {
            ss.add(s.getTitle());
        }

        Collections.sort(ss, String::compareToIgnoreCase);
        Tuils.addPrefix(ss, Tuils.DOUBLE_SPACE);
        Tuils.insertHeaders(ss, false);

        return Tuils.toPlanString(ss, Tuils.NEWLINE);
    }

    public void updateSongs() {
        executor.submit(() -> {
            try {
                List<Song> loadedSongs = new ArrayList<>();

                if (XMLPrefsManager.getBoolean(Behavior.songs_from_mediastore)) {
                    loadFromMediaStore(loadedSongs);
                } else {
                    String path = XMLPrefsManager.get(Behavior.songs_folder);
                    if (path.length() > 0) {
                        File file;
                        if (path.startsWith(File.separator)) {
                            file = new File(path);
                        } else {
                            file = new File(XMLPrefsManager.get(File.class, Behavior.home_path), path);
                        }

                        if (file.exists() && file.isDirectory()) {
                            loadedSongs.addAll(Tuils.getSongsInFolder(file));
                        }
                    }
                }

                // Обновляем список атомарно
                synchronized (songs) {
                    songs.clear();
                    songs.addAll(loadedSongs);
                }
                
                // Если сервис уже подключен, обновляем список и там
                if (musicSrv != null) {
                    musicSrv.setList(songs);
                }

            } catch (Exception e) {
                Tuils.toFile(e);
            }
        });
    }

    private void loadFromMediaStore(List<Song> targetList) {
        ContentResolver musicResolver = mContext.getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        
        // Проекция для запроса (оптимизация)
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST
        };
        
        // Фильтр для музыки
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        // Сортировка
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor musicCursor = musicResolver.query(musicUri, projection, selection, null, sortOrder)) {
            if (musicCursor != null && musicCursor.moveToFirst()) {
                int titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
                int artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);

                do {
                    long thisId = musicCursor.getLong(idColumn);
                    String thisTitle = musicCursor.getString(titleColumn);
                    String thisArtist = musicCursor.getString(artistColumn);
                    
                    // Создаем URI для проигрывания (полезно для новых API)
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, thisId);
                    
                    // Предполагаем, что Song конструктор принимает (id, title) или (id, title, artist)
                    // Если в Song нет поля для URI, старый подход с ID тоже сработает, 
                    // если MusicService умеет с ним работать.
                    targetList.add(new Song(thisId, thisTitle)); 
                } while (musicCursor.moveToNext());
            }
        } catch (Exception e) {
            Tuils.log("Error loading from MediaStore: " + e);
        }
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return musicSrv != null ? musicSrv.getAudioSessionId() : 0;
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public int getCurrentPosition() {
        if (musicSrv != null && musicBound && musicSrv.isPng())
            return musicSrv.getPosn();
        else return 0;
    }

    @Override
    public int getDuration() {
        if (musicSrv != null && musicBound && musicSrv.isPng())
            return musicSrv.getDur();
        else return 0;
    }

    public int getSongIndex() {
        if (musicSrv != null) return musicSrv.getSongIndex();
        return -1;
    }

    @Override
    public boolean isPlaying() {
        if (musicSrv != null && musicBound)
            return musicSrv.isPng();
        return false;
    }

    public void stop() {
        if (musicSrv != null) musicSrv.pausePlayer();
        playbackPaused = true;
        stopped = true;
    }

    public Song get(int index) {
        if (index < 0 || index >= songs.size()) return null;
        return songs.get(index);
    }

    @Override
    public void seekTo(int pos) {
        if (musicSrv != null) musicSrv.seek(pos);
    }

    public void select(String song) {
        if (!musicBound) {
            init();
            waitingMethod = WAITING_LISTEN;
            savedParam = song;
            return;
        }

        int i = -1;
        // Поиск песни. Можно оптимизировать Map-ом, если песен много, 
        // но для списка в пару сотен линейный поиск OK.
        for (int index = 0; index < songs.size(); index++) {
            if (songs.get(index).getTitle().equalsIgnoreCase(song)) { // equalsIgnoreCase лучше для поиска
                i = index;
                break;
            }
        }

        if (i != -1) {
            musicSrv.setSong(i);
            musicSrv.playSong();
            playbackPaused = false;
            stopped = false;
        }
    }

    @Override
    public void start() {
        if (musicSrv != null) {
            musicSrv.go();
            playbackPaused = false;
            stopped = false;
        }
    }

    public List<Song> getSongs() {
        return new ArrayList<>(songs);
    }
}
