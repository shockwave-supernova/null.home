package ohi.andre.consolelauncher.tuils;

import ohi.andre.consolelauncher.BuildConfig;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.os.Process;
import android.os.StatFs;
import android.provider.Settings;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat; // Добавлен импорт
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.w3c.dom.Node;
import org.xml.sax.SAXParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import dalvik.system.DexFile;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.music.MusicManager2;
import ohi.andre.consolelauncher.managers.music.Song;
import ohi.andre.consolelauncher.managers.notifications.NotificationService;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.interfaces.OnBatteryUpdate;
import ohi.andre.consolelauncher.tuils.stuff.FakeLauncherActivity;

public class Tuils {

    public static final String SPACE = " ";
    public static final String DOUBLE_SPACE = "  ";
    public static final String NEWLINE = "\n";
    public static final String TRIBLE_SPACE = "   ";
    public static final String DOT = ".";
    public static final String EMPTYSTRING = "";
    private static final String TUI_FOLDER = "t-ui";
    public static final String MINUS = "-";

    public static Pattern patternNewline = Pattern.compile("%n", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);

    private static Typeface globalTypeface = null;
    public static String fontPath = null;

    static Pattern calculusPattern = Pattern.compile("([+\\-*/^])(\\d+\\.?\\d*)");
    public static double textCalculus(double input, String text) {
        Matcher m = calculusPattern.matcher(text);
        while(m.find()) {
            String group1 = m.group(1);
            String group2 = m.group(2);
            if (group1 == null || group2 == null) continue;

            char operator = group1.charAt(0);
            double value = Double.parseDouble(group2);

            switch (operator) {
                case '+': input += value; break;
                case '-': input -= value; break;
                case '*': input *= value; break;
                case '/': input = input / value; break;
                case '^': input = Math.pow(input, value); break;
            }
            Tuils.log("now im", input);
        }
        return input;
    }

    public static Typeface getTypeface(Context context) {
        if(globalTypeface == null) {
            try {
                XMLPrefsManager.loadCommons(context);
            } catch (Exception e) {
                return null;
            }

            boolean systemFont = XMLPrefsManager.getBoolean(Ui.system_font);
            if(systemFont) globalTypeface = Typeface.DEFAULT;
            else {
                File tui = Tuils.getFolder();
                if(tui == null) {
                    return Typeface.createFromAsset(context.getAssets(), "lucida_console.ttf");
                }

                Pattern p = Pattern.compile(".[ot]tf$");

                File font = null;
                File[] files = tui.listFiles();
                if (files != null) {
                    for(File f : files) {
                        String name = f.getName();
                        if(p.matcher(name).find()) {
                            font = f;
                            fontPath = f.getAbsolutePath();
                            break;
                        }
                    }
                }

                if(font != null) {
                    try {
                        globalTypeface = Typeface.createFromFile(font);
                        if(globalTypeface == null) throw new UnsupportedOperationException();
                    } catch (Exception e) {
                        globalTypeface = null;
                    }
                }
            }

            if(globalTypeface == null) globalTypeface = systemFont ? Typeface.DEFAULT : Typeface.createFromAsset(context.getAssets(), "lucida_console.ttf");
        }
        return globalTypeface;
    }

    public static void cancelFont() {
        globalTypeface = null;
        fontPath = null;
    }

    public static String locationName(Context context, double lat, double lng) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                return addresses.get(0).getAddressLine(0);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean notificationServiceIsRunning(Context context) {
        ComponentName collectorComponent = new ComponentName(context, NotificationService.class);
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);
        if (runningServices == null ) return false;

        for (ActivityManager.RunningServiceInfo service : runningServices) {
            if (service.service.equals(collectorComponent)) {
                if (service.pid == Process.myPid()) return true;
            }
        }
        return false;
    }

    public static boolean arrayContains(int[] array, int value) {
        if(array == null) return false;
        for(int i : array) if(i == value) return true;
        return false;
    }

    private static OnBatteryUpdate batteryUpdate;
    private static BroadcastReceiver batteryReceiver = null;

    public static void registerBatteryReceiver(Context context, OnBatteryUpdate listener) {
        try {
            batteryReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if(batteryUpdate == null || intent.getAction() == null) return;
                    switch (intent.getAction()) {
                        case Intent.ACTION_BATTERY_CHANGED:
                            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                            batteryUpdate.update(level);
                            break;
                        case Intent.ACTION_POWER_CONNECTED:
                            batteryUpdate.onCharging();
                            break;
                        case Intent.ACTION_POWER_DISCONNECTED:
                            batteryUpdate.onNotCharging();
                            break;
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            iFilter.addAction(Intent.ACTION_POWER_CONNECTED);
            iFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            context.registerReceiver(batteryReceiver, iFilter);
            batteryUpdate = listener;
        } catch (Exception e) {
            Tuils.toFile(e);
        }
    }

    public static void unregisterBatteryReceiver(Context context) {
        if(batteryReceiver != null) context.unregisterReceiver(batteryReceiver);
    }

    public static boolean containsExtension(String[] array, String value) {
        try {
            String val = value.toLowerCase().trim();
            for (String s : array) if (val.endsWith(s)) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<Song> getSongsInFolder(File folder) {
        List<Song> songs = new ArrayList<>();
        File[] files = folder.listFiles();
        if(files == null) return songs;

        for (File file : files) {
            if (file.isDirectory()) {
                songs.addAll(getSongsInFolder(file));
            } else if (containsExtension(MusicManager2.MUSIC_EXTENSIONS, file.getName())) {
                songs.add(new Song(file));
            }
        }
        return songs;
    }

    public static long download(InputStream in, File file) throws Exception {
        try (OutputStream out = new FileOutputStream(file, false)) {
            byte[] data = new byte[1024];
            long bytes = 0;
            int count;
            while ((count = in.read(data)) != -1) {
                out.write(data, 0, count);
                bytes += count;
            }
            out.flush();
            in.close();
            return bytes;
        }
    }

    public static void write(File file, String separator, String... ss) throws Exception {
        try (FileOutputStream headerStream = new FileOutputStream(file, false)) {
            for(int c = 0; c < ss.length - 1; c++) {
                headerStream.write(ss[c].getBytes());
                headerStream.write(separator.getBytes());
            }
            headerStream.write(ss[ss.length - 1].getBytes());
            headerStream.flush();
        }
    }

    public static float dpToPx(Context context, float valueInDp) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valueInDp, metrics);
    }

    public static boolean hasNotificationAccess(Context context) {
        String pkgName = BuildConfig.APPLICATION_ID;
        final String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) return true;
            }
        }
        return false;
    }

    public static void resetPreferredLauncherAndOpenChooser(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ComponentName componentName = new ComponentName(context, FakeLauncherActivity.class);
            packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            Intent selector = new Intent(Intent.ACTION_MAIN);
            selector.addCategory(Intent.CATEGORY_HOME);
            selector.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(selector);
            packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            Tuils.log(e);
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    public static void openSettingsPage(Context c, String packageName) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", packageName, null);
        intent.setData(uri);
        c.startActivity(intent);
    }

    public static Intent requestAdmin(ComponentName component, String explanation) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation);
        return intent;
    }

    public static double getAvailableSpace(File dir, int unit) {
        if(dir == null) return -1;
        StatFs statFs = new StatFs(dir.getAbsolutePath());
        return formatSize(statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong(), unit);
    }

    public static double getTotaleSpace(File dir, int unit) {
        if(dir == null) return -1;
        StatFs statFs = new StatFs(dir.getAbsolutePath());
        return formatSize(statFs.getBlockCountLong() * statFs.getBlockSizeLong(), unit);
    }

    public static double formatSize(long bytes, int unit) {
        double smallConvert = 1024.0;
        double mega = smallConvert * smallConvert;
        double result;
        switch (unit) {
            case TERA: result = bytes / (mega * mega); break;
            case GIGA: result = bytes / (mega * smallConvert); break;
            case MEGA: result = bytes / mega; break;
            case KILO: result = bytes / smallConvert; break;
            case BYTE: result = bytes; break;
            default: return -1;
        }
        return round(result, 2);
    }

    public static SpannableString span(CharSequence text, int color) {
        return span(null, Integer.MAX_VALUE, color, text, Integer.MAX_VALUE);
    }

    public static SpannableString span(Context context, int bgColor, int foreColor, CharSequence text, int size) {
        if(text == null) text = Tuils.EMPTYSTRING;
        SpannableString spannableString = (text instanceof SpannableString) ? (SpannableString)text : new SpannableString(text);
        if(size != Integer.MAX_VALUE && context != null) spannableString.setSpan(new AbsoluteSizeSpan(convertSpToPixels(size, context)), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if(foreColor != Integer.MAX_VALUE) spannableString.setSpan(new ForegroundColorSpan(foreColor), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if(bgColor != Integer.MAX_VALUE) spannableString.setSpan(new BackgroundColorSpan(bgColor), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannableString;
    }

    public static int convertSpToPixels(float sp, Context context) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }

    public abstract static class ArgsRunnable implements Runnable {
        private Object[] args;
        public void setArgs(Object... args) { this.args = args; }
        @SuppressWarnings("unchecked")
        public <T> T get(Class<T> c, int index) {
            if(args != null && index < args.length) return (T) args[index];
            return null;
        }
    }

    public static void delete(File dir) {
        File[] files = dir.listFiles();
        if(files != null) {
            for(File f : files) {
                if(f.isDirectory()) delete(f);
                f.delete();
            }
        }
        dir.delete();
    }

    public static void sendOutput(int color, Context context, CharSequence s, int type) {
        Intent intent = new Intent(PrivateIOReceiver.ACTION_OUTPUT);
        intent.putExtra(PrivateIOReceiver.TEXT, s);
        intent.putExtra(PrivateIOReceiver.COLOR, color);
        intent.putExtra(PrivateIOReceiver.TYPE, type);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public static void sendOutput(Context context, CharSequence s) {
        sendOutput(Integer.MAX_VALUE, context, s, TerminalManager.CATEGORY_OUTPUT);
    }

    public static final int TERA = 0, GIGA = 1, MEGA = 2, KILO = 3, BYTE = 4;

    public static double round(double value, int places) {
        if (places < 0) return value;
        try {
            return new BigDecimal(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
        } catch (Exception e) { return value; }
    }

    public static String getNetworkType(Context context) {
        return "Cellular";
    }

    public static void setCursorDrawableColor(EditText editText, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Drawable cursorDrawable = editText.getTextCursorDrawable();
            if (cursorDrawable != null) {
                cursorDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                editText.setTextCursorDrawable(cursorDrawable);
            }
        } else {
            try {
                Field fCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
                fCursorDrawableRes.setAccessible(true);
                int mCursorDrawableRes = fCursorDrawableRes.getInt(editText);
                Field fEditor = TextView.class.getDeclaredField("mEditor");
                fEditor.setAccessible(true);
                Object editor = fEditor.get(editText);
                Field fCursorDrawable = editor.getClass().getDeclaredField("mCursorDrawable");
                fCursorDrawable.setAccessible(true);
                Drawable[] drawables = new Drawable[2];
                drawables[0] = ResourcesCompat.getDrawable(editText.getResources(), mCursorDrawableRes, null);
                drawables[1] = ResourcesCompat.getDrawable(editText.getResources(), mCursorDrawableRes, null);
                if (drawables[0] != null) drawables[0].setColorFilter(color, PorterDuff.Mode.SRC_IN);
                if (drawables[1] != null) drawables[1].setColorFilter(color, PorterDuff.Mode.SRC_IN);
                fCursorDrawable.set(editor, drawables);
            } catch (Throwable ignored) {}
        }
    }

    public static int nOfBytes(File file) {
        int count = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            while(in.read() != -1) count++;
        } catch (IOException e) { Tuils.log(e); }
        return count;
    }

    public static void log(Object o) {
        if(o instanceof Throwable) Log.e("andre", "", (Throwable) o);
        else Log.e("andre", o == null ? "null" : o.toString());
    }

    public static void log(Object o, Object o2) {
        Log.e("andre", String.valueOf(o) + " -- " + String.valueOf(o2));
    }

    public static void toFile(Object o) {
        if(o == null) return;
        try (FileOutputStream stream = new FileOutputStream(new File(Tuils.getFolder(), "crash.txt"), true)) {
            stream.write((Tuils.NEWLINE + Tuils.NEWLINE).getBytes());
            if(o instanceof Throwable) ((Throwable) o).printStackTrace(new PrintStream(stream));
            else stream.write(o.toString().getBytes());
            stream.write((Tuils.NEWLINE + "----------------------------").getBytes());
        } catch (Exception e1) {}
    }

    private static File folder = null;
    public static File getFolder() {
        if(folder != null) return folder;
        File tuiFolder = new File(Environment.getExternalStorageDirectory(), TUI_FOLDER);
        if(tuiFolder.exists() || tuiFolder.mkdir()) {
            folder = tuiFolder;
            return folder;
        }
        return null;
    }

    public static String removeSpaces(String string) {
        return string.replaceAll("\\s", EMPTYSTRING);
    }
}
