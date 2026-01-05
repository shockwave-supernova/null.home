package ohi.andre.consolelauncher;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context; // ВАЖНО
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity;
import ohi.andre.consolelauncher.managers.ContactManager;
import ohi.andre.consolelauncher.managers.RegexManager;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.TimeManager;
import ohi.andre.consolelauncher.managers.TuiLocationManager;
import ohi.andre.consolelauncher.managers.notifications.KeeperService;
import ohi.andre.consolelauncher.managers.notifications.NotificationManager;
import ohi.andre.consolelauncher.managers.notifications.NotificationMonitorService;
import ohi.andre.consolelauncher.managers.notifications.NotificationService;
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Notifications;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Assist;
import ohi.andre.consolelauncher.tuils.CustomExceptionHandler;
import ohi.andre.consolelauncher.tuils.LongClickableSpan;
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver;
import ohi.andre.consolelauncher.tuils.PublicIOReceiver;
import ohi.andre.consolelauncher.tuils.SimpleMutableEntry;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Inputable;
import ohi.andre.consolelauncher.tuils.interfaces.Outputable;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

public class LauncherActivity extends AppCompatActivity implements Reloadable {

    // --- КОНСТАНТЫ СОВМЕСТИМОСТИ ---
    public static final int COMMAND_REQUEST_PERMISSION = 10;
    public static final int STARTING_PERMISSION = 11;
    public static final int COMMAND_SUGGESTION_REQUEST_PERMISSION = 12;
    public static final int LOCATION_REQUEST_PERMISSION = 13;
    public static final int TUIXT_REQUEST = 10;
    // -------------------------------

    private ActivityResultLauncher<Intent> tuixtLauncher;
    private ActivityResultLauncher<Intent> storagePermissionLauncher;
    private ActivityResultLauncher<String[]> permissionsLauncher;

    private UIManager ui;
    private MainManager main;

    private PrivateIOReceiver privateIOReceiver;
    private PublicIOReceiver publicIOReceiver;

    private boolean openKeyboardOnStart, canApplyTheme, backButtonEnabled;
    private Set<ReloadMessageCategory> categories;

    private final Runnable stopActivity = () -> {
        dispose();
        finish();

        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);

        CharSequence reloadMessage = Tuils.EMPTYSTRING;
        for (ReloadMessageCategory c : categories) {
            reloadMessage = TextUtils.concat(reloadMessage, Tuils.NEWLINE, c.text());
        }
        startMain.putExtra(Reloadable.MESSAGE, reloadMessage);

        startActivity(startMain);
    };

    private final Inputable in = new Inputable() {
        @Override
        public void in(String s) {
            if (ui != null) ui.setInput(s);
        }

        @Override
        public void changeHint(final String s) {
            runOnUiThread(() -> {
                if (ui != null) ui.setHint(s);
            });
        }

        @Override
        public void resetHint() {
            runOnUiThread(() -> {
                if (ui != null) ui.resetHint();
            });
        }
    };

    private final Outputable out = new Outputable() {
        private final int DELAY = 500;
        Queue<SimpleMutableEntry<CharSequence, Integer>> textColor = new LinkedList<>();
        Queue<SimpleMutableEntry<CharSequence, Integer>> textCategory = new LinkedList<>();
        boolean charged = false;

        Handler handler = new Handler(Looper.getMainLooper());

        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (ui == null) {
                    if (handler != null) handler.postDelayed(this, DELAY);
                    return;
                }

                SimpleMutableEntry<CharSequence, Integer> sm;
                while ((sm = textCategory.poll()) != null) {
                    ui.setOutput(sm.getKey(), sm.getValue());
                }
                while ((sm = textColor.poll()) != null) {
                    ui.setOutput(sm.getValue(), sm.getKey());
                }
            }
        };

        private void scheduleOutput() {
            if (!charged && handler != null) {
                charged = true;
                handler.postDelayed(r, DELAY);
            }
        }

        @Override
        public void onOutput(CharSequence output) {
            if (ui != null) ui.setOutput(output, TerminalManager.CATEGORY_OUTPUT);
            else {
                textCategory.add(new SimpleMutableEntry<>(output, TerminalManager.CATEGORY_OUTPUT));
                scheduleOutput();
            }
        }

        @Override
        public void onOutput(CharSequence output, int category) {
            if (ui != null) ui.setOutput(output, category);
            else {
                textCategory.add(new SimpleMutableEntry<>(output, category));
                scheduleOutput();
            }
        }

        @Override
        public void onOutput(int color, CharSequence output) {
            if (ui != null) ui.setOutput(color, output);
            else {
                textColor.add(new SimpleMutableEntry<>(output, color));
                scheduleOutput();
            }
        }

        @Override
        public void dispose() {
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
                handler = null;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        if (isFinishing()) return;

        registerActivityResults();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (backButtonEnabled && main != null) {
                    ui.onBackPressed();
                }
            }
        });

        if (!hasStoragePermission()) {
            requestStoragePermission();
        } else {
            canApplyTheme = true;
            finishOnCreate();
        }
    }

    private void registerActivityResults() {
        tuixtLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == TuixtActivity.BACK_PRESSED) {
                        Tuils.sendOutput(this, R.string.tuixt_back_pressed);
                    } else if (result.getData() != null) {
                        Tuils.sendOutput(this, result.getData().getStringExtra(TuixtActivity.ERROR_KEY));
                    }
                }
        );

        storagePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (hasStoragePermission()) {
                        canApplyTheme = true;
                        finishOnCreate();
                    } else {
                        Toast.makeText(this, R.string.permissions_toast, Toast.LENGTH_LONG).show();
                    }
                }
        );

        permissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean contactsGranted = false;
                    for (String key : permissions.keySet()) {
                        if (Boolean.TRUE.equals(permissions.get(key))) {
                            if (key.equals(Manifest.permission.READ_CONTACTS)) contactsGranted = true;
                        }
                    }

                    if (contactsGranted) {
                        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ContactManager.ACTION_REFRESH));
                    }

                    if (!canApplyTheme) {
                        if (hasStoragePermission()) {
                            canApplyTheme = true;
                            finishOnCreate();
                        } else {
                            Toast.makeText(this, R.string.permissions_toast, Toast.LENGTH_LONG).show();
                            new Handler(Looper.getMainLooper()).postDelayed(() -> runOnUiThread(stopActivity), 2000);
                        }
                    }
                }
        );
    }

    public void launchTuixt(Intent intent) {
        tuixtLauncher.launch(intent);
    }

    public void launchPermissionRequest(String[] permissions) {
        permissionsLauncher.launch(permissions);
    }

    // Сохраняем старый метод для совместимости с TuixtActivity
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TUIXT_REQUEST && resultCode != 0) {
            if (resultCode == TuixtActivity.BACK_PRESSED) {
                Tuils.sendOutput(this, R.string.tuixt_back_pressed);
            } else {
                Tuils.sendOutput(this, data.getStringExtra(TuixtActivity.ERROR_KEY));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Оставляем пустую реализацию или логику, если она вызывается старыми методами
        // Основная логика перенесена в launchPermissionRequest
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                storagePermissionLauncher.launch(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                storagePermissionLauncher.launch(intent);
            }
        } else {
            permissionsLauncher.launch(new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    private void finishOnCreate() {
        Thread.currentThread().setUncaughtExceptionHandler(new CustomExceptionHandler());

        XMLPrefsManager.loadCommons(this);
        new RegexManager(LauncherActivity.this);
        new TimeManager(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(PrivateIOReceiver.ACTION_INPUT);
        filter.addAction(PrivateIOReceiver.ACTION_OUTPUT);
        filter.addAction(PrivateIOReceiver.ACTION_REPLY);

        privateIOReceiver = new PrivateIOReceiver(this, out, in);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(privateIOReceiver, filter);

        IntentFilter filter1 = new IntentFilter();
        filter1.addAction(PublicIOReceiver.ACTION_CMD);
        filter1.addAction(PublicIOReceiver.ACTION_OUTPUT);

        publicIOReceiver = new PublicIOReceiver();

        // --- ИСПРАВЛЕНИЕ: SecurityException на Android 14 ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplicationContext().registerReceiver(publicIOReceiver, filter1, Context.RECEIVER_EXPORTED);
        } else {
            getApplicationContext().registerReceiver(publicIOReceiver, filter1);
        }
        // -----------------------------------------------------

        int requestedOrientation = XMLPrefsManager.getInt(Behavior.orientation);
        if (requestedOrientation >= 0 && requestedOrientation != 2) {
            if (getResources().getConfiguration().orientation != requestedOrientation) {
                setRequestedOrientation(requestedOrientation);
            }
        }

        if (!XMLPrefsManager.getBoolean(Ui.ignore_bar_color)) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(XMLPrefsManager.getColor(Theme.statusbar_color));
            window.setNavigationBarColor(XMLPrefsManager.getColor(Theme.navigationbar_color));

            WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(window, window.getDecorView());
            boolean lightIcons = !XMLPrefsManager.getBoolean(Ui.statusbar_light_icons);
            windowInsetsController.setAppearanceLightStatusBars(lightIcons);
            windowInsetsController.setAppearanceLightNavigationBars(lightIcons);
        }

        backButtonEnabled = XMLPrefsManager.getBoolean(Behavior.back_button_enabled);

        boolean showNotification = XMLPrefsManager.getBoolean(Behavior.tui_notification);
        Intent keeperIntent = new Intent(this, KeeperService.class);
        if (showNotification) {
            keeperIntent.putExtra(KeeperService.PATH_KEY, XMLPrefsManager.get(Behavior.home_path));
            startService(keeperIntent);
        } else {
            try {
                stopService(keeperIntent);
            } catch (Exception e) {}
        }

        boolean fullscreen = XMLPrefsManager.getBoolean(Ui.fullscreen);
        if (fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        boolean useSystemWP = XMLPrefsManager.getBoolean(Ui.system_wallpaper);
        setTheme(useSystemWP ? R.style.Custom_SystemWP : R.style.Custom_Solid);

        try {
            NotificationManager.create(this);
        } catch (Exception e) {
            Tuils.toFile(e);
        }

        boolean notifications = XMLPrefsManager.getBoolean(Notifications.show_notifications) || XMLPrefsManager.get(Notifications.show_notifications).equalsIgnoreCase("enabled");
        if (notifications) {
            try {
                ComponentName notificationComponent = new ComponentName(this, NotificationService.class);
                PackageManager pm = getPackageManager();
                pm.setComponentEnabledSetting(notificationComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

                if (!Tuils.hasNotificationAccess(this)) {
                    Intent i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                    if (i.resolveActivity(getPackageManager()) == null) {
                        Toast.makeText(this, R.string.no_notification_access, Toast.LENGTH_LONG).show();
                    } else {
                        startActivity(i);
                    }
                }

                startService(new Intent(this, NotificationMonitorService.class));
                startService(new Intent(this, NotificationService.class));
            } catch (Exception er) {
                Intent intent = new Intent(PrivateIOReceiver.ACTION_OUTPUT);
                intent.putExtra(PrivateIOReceiver.TEXT, getString(R.string.output_notification_error) + Tuils.SPACE + er.toString());
            }
        }

        LongClickableSpan.longPressVibrateDuration = XMLPrefsManager.getInt(Behavior.long_click_vibration_duration);

        openKeyboardOnStart = XMLPrefsManager.getBoolean(Behavior.auto_show_keyboard);
        if (!openKeyboardOnStart) {
            this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        setContentView(R.layout.base_view);

        if (XMLPrefsManager.getBoolean(Ui.show_restart_message)) {
            CharSequence s = getIntent().getCharSequenceExtra(Reloadable.MESSAGE);
            if (s != null)
                out.onOutput(Tuils.span(s, XMLPrefsManager.getColor(Theme.restart_message_color)));
        }

        categories = new HashSet<>();
        main = new MainManager(this);

        ViewGroup mainView = findViewById(R.id.mainview);
        ui = new UIManager(this, mainView, main.getMainPack(), canApplyTheme, main.executer());

        main.setRedirectionListener(ui.buildRedirectionListener());
        ui.pack = main.getMainPack();

        in.in(Tuils.EMPTYSTRING);
        ui.focusTerminal();

        if (fullscreen) Assist.assistActivity(this);

        System.gc();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (ui != null) ui.onStart(openKeyboardOnStart);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_UPDATE_SUGGESTIONS));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ui != null && main != null) {
            ui.pause();
            main.dispose();
        }
    }

    private boolean disposed = false;

    private void dispose() {
        if (disposed) return;

        try {
            LocalBroadcastManager.getInstance(this.getApplicationContext()).unregisterReceiver(privateIOReceiver);
            getApplicationContext().unregisterReceiver(publicIOReceiver);
        } catch (Exception e) {}

        try {
            stopService(new Intent(this, NotificationMonitorService.class));
            stopService(new Intent(this, KeeperService.class));

            Intent notificationIntent = new Intent(this, NotificationService.class);
            notificationIntent.putExtra(NotificationService.DESTROY, true);
            startService(notificationIntent);
        } catch (Exception e) {
            Tuils.log(e);
        }

        overridePendingTransition(0, 0);

        if (main != null) main.destroy();
        if (ui != null) ui.dispose();

        XMLPrefsManager.dispose();

        // --- ИСПРАВЛЕНИЕ: Проверка на null перед вызовом dispose (защита от NPE при краше) ---
        if (RegexManager.instance != null) {
            RegexManager.instance.dispose();
        }
        if (TimeManager.instance != null) {
            TimeManager.instance.dispose();
        }
        // -------------------------------------------------------------------------------------

        disposed = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dispose();
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_BACK)
            return super.onKeyLongPress(keyCode, event);

        if (main != null)
            main.onLongBack();
        return true;
    }

    @Override
    public void reload() {
        runOnUiThread(stopActivity);
    }

    @Override
    public void addMessage(String header, String message) {
        for (ReloadMessageCategory cs : categories) {
            if (cs.header.equals(header)) {
                cs.lines.add(message);
                return;
            }
        }

        ReloadMessageCategory c = new ReloadMessageCategory(header);
        if (message != null) c.lines.add(message);
        categories.add(c);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && ui != null) {
            ui.focusTerminal();
        }
    }

    private SuggestionsManager.Suggestion suggestion;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        Object tag = v.getTag(R.id.suggestion_id);
        if (tag instanceof SuggestionsManager.Suggestion) {
            suggestion = (SuggestionsManager.Suggestion) tag;

            if (suggestion.type == SuggestionsManager.Suggestion.TYPE_CONTACT) {
                ContactManager.Contact contact = (ContactManager.Contact) suggestion.object;
                menu.setHeaderTitle(contact.name);
                for (int count = 0; count < contact.numbers.size(); count++) {
                    menu.add(0, count, count, contact.numbers.get(count));
                }
            }
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (suggestion != null) {
            if (suggestion.type == SuggestionsManager.Suggestion.TYPE_CONTACT) {
                ContactManager.Contact contact = (ContactManager.Contact) suggestion.object;
                contact.setSelectedNumber(item.getItemId());
                Tuils.sendInput(this, suggestion.getText());
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        String cmd = intent.getStringExtra(PrivateIOReceiver.TEXT);
        if (cmd != null) {
            Intent i = new Intent(MainManager.ACTION_EXEC);
            i.putExtra(MainManager.CMD_COUNT, MainManager.commandCount);
            i.putExtra(MainManager.CMD, cmd);
            i.putExtra(MainManager.NEED_WRITE_INPUT, true);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
