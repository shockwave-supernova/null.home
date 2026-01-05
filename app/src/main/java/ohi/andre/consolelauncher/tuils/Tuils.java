package ohi.andre.consolelauncher.tuils;

import ohi.andre.consolelauncher.BuildConfig;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
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
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
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
import android.widget.EditText;
import android.widget.TextView;

import org.xml.sax.SAXParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream; // Был пропущен!
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dalvik.system.DexFile;
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
        while (m.find()) {
            char operator = m.group(1).charAt(0);
            double value = Double.parseDouble(m.group(2));
            switch (operator) {
                case '+': input += value; break;
                case '-': input -= value; break;
                case '*': input *= value; break;
                case '/': input /= value; break;
                case '^': input = Math.pow(input, value); break;
            }
        }
        return input;
    }

    public static Typeface getTypeface(Context context) {
        if (globalTypeface == null) {
            try { XMLPrefsManager.loadCommons(context); } catch (Exception e) { return null; }
            if (XMLPrefsManager.getBoolean(Ui.system_font)) globalTypeface = Typeface.DEFAULT;
            else {
                File tui = getFolder();
                if (tui == null) return Typeface.createFromAsset(context.getAssets(), "lucida_console.ttf");
                File[] files = tui.listFiles();
                if (files != null) {
                    Pattern p = Pattern.compile(".[ot]tf$");
                    for (File f : files) {
                        if (p.matcher(f.getName()).find()) {
                            try { globalTypeface = Typeface.createFromFile(f); fontPath = f.getAbsolutePath(); break; } catch (Exception ignored) {}
                        }
                    }
                }
            }
            if (globalTypeface == null) globalTypeface = Typeface.createFromAsset(context.getAssets(), "lucida_console.ttf");
        }
        return globalTypeface;
    }

    public static void cancelFont() { globalTypeface = null; fontPath = null; }

    public static String locationName(Context context, double lat, double lng) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) return addresses.get(0).getAddressLine(0);
        } catch (Exception ignored) {}
        return null;
    }

    @SuppressWarnings("deprecation")
    public static boolean notificationServiceIsRunning(Context context) {
        ComponentName collectorComponent = new ComponentName(context, NotificationService.class);
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);
        if (runningServices == null) return false;
        for (ActivityManager.RunningServiceInfo service : runningServices) {
            if (service.service.equals(collectorComponent) && service.pid == Process.myPid()) return true;
        }
        return false;
    }

    public static void registerBatteryReceiver(Context context, OnBatteryUpdate listener) {
        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (listener == null || intent.getAction() == null) return;
                if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) listener.update(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0));
                else if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) listener.onCharging();
                else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) listener.onNotCharging();
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        context.registerReceiver(br, filter);
    }

    public static void unregisterBatteryReceiver(Context context) { /* dummy */ }

    public static List<Song> getSongsInFolder(File folder) {
        List<Song> songs = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return songs;
        for (File f : files) {
            if (f.isDirectory()) songs.addAll(getSongsInFolder(f));
            else if (containsExtension(MusicManager2.MUSIC_EXTENSIONS, f.getName())) songs.add(new Song(f));
        }
        return songs;
    }

    public static boolean containsExtension(String[] array, String value) {
        String val = value.toLowerCase().trim();
        for (String s : array) if (val.endsWith(s)) return true;
        return false;
    }

    public static void resetPreferredLauncherAndOpenChooser(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            ComponentName cn = new ComponentName(context, FakeLauncherActivity.class);
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            Intent s = new Intent(Intent.ACTION_MAIN);
            s.addCategory(Intent.CATEGORY_HOME);
            s.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(s);
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void openSettingsPage(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", pkg, null));
        c.startActivity(i);
    }

    public static Intent requestAdmin(ComponentName cn, String ex) {
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ex);
        return i;
    }

    public static double formatSize(long bytes, int unit) {
        double kb = 1024.0, mb = kb * kb;
        double res;
        switch (unit) {
            case TERA: res = bytes / (mb * mb); break;
            case GIGA: res = bytes / (mb * kb); break;
            case MEGA: res = bytes / mb; break;
            case KILO: res = bytes / kb; break;
            default: res = (double) bytes;
        }
        return round(res, 2);
    }

    public static double percentage(long part, long total) {
        if (total == 0) return 0;
        return round((double) part * 100 / total, 2);
    }

    // --- SPAN METHODS ---
    
    // ЭТОТ МЕТОД НУЖЕН RegexManager (line 206)
    public static SpannableString span(int bgColor, int foreColor, CharSequence text) {
        return span(null, bgColor, foreColor, text, Integer.MAX_VALUE);
    }
    
    // ЭТОТ МЕТОД НУЖЕН RegexManager (line 213)
    public static int span(int bgColor, SpannableString text, String section, int fromIndex) {
        int index = text.toString().indexOf(section, fromIndex);
        if(index == -1) return index;
        text.setSpan(new BackgroundColorSpan(bgColor), index, index + section.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return index + section.length();
    }

    public static SpannableString span(CharSequence text, int color) {
        if (text == null) text = EMPTYSTRING;
        SpannableString ss = new SpannableString(text);
        ss.setSpan(new ForegroundColorSpan(color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    public static SpannableString span(Context context, int size, CharSequence text) {
        if (text == null) text = EMPTYSTRING;
        SpannableString ss = new SpannableString(text);
        if (context != null) ss.setSpan(new AbsoluteSizeSpan(convertSpToPixels(size, context)), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    public static SpannableString span(Context context, CharSequence text, int color, int size) {
        if (text == null) text = EMPTYSTRING;
        SpannableString ss = new SpannableString(text);
        if (context != null) ss.setSpan(new AbsoluteSizeSpan(convertSpToPixels(size, context)), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    public static SpannableString span(Context context, int bgColor, int foreColor, CharSequence text, int size) {
        if (text == null) text = EMPTYSTRING;
        SpannableString ss = new SpannableString(text);
        if (size != Integer.MAX_VALUE && context != null) ss.setSpan(new AbsoluteSizeSpan(convertSpToPixels(size, context)), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (foreColor != Integer.MAX_VALUE) ss.setSpan(new ForegroundColorSpan(foreColor), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bgColor != Integer.MAX_VALUE) ss.setSpan(new BackgroundColorSpan(bgColor), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    public static int convertSpToPixels(float sp, Context context) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }

    // --- SEND OUTPUT METHODS ---
    public static void sendOutput(int color, Context context, CharSequence s) {
        sendOutput(color, context, s, TerminalManager.CATEGORY_OUTPUT);
    }

    public static void sendOutput(int color, Context context, int resId) {
        sendOutput(color, context, context.getString(resId), TerminalManager.CATEGORY_OUTPUT);
    }

    public static void sendOutput(Context context, int resId) {
        sendOutput(Integer.MAX_VALUE, context, context.getString(resId), TerminalManager.CATEGORY_OUTPUT);
    }

    public static void sendOutput(Context context, CharSequence s) {
        sendOutput(Integer.MAX_VALUE, context, s, TerminalManager.CATEGORY_OUTPUT);
    }

    public static void sendOutput(Context context, CharSequence s, int type) {
        sendOutput(Integer.MAX_VALUE, context, s, type);
    }

    public static void sendOutput(MainPack pack, CharSequence s, int type) {
        sendOutput(pack.commandColor, pack.context, s, type);
    }

    public static void sendOutput(int color, Context context, CharSequence s, int type) {
        Intent i = new Intent(PrivateIOReceiver.ACTION_OUTPUT);
        i.putExtra(PrivateIOReceiver.TEXT, s);
        i.putExtra(PrivateIOReceiver.COLOR, color);
        i.putExtra(PrivateIOReceiver.TYPE, type);
        LocalBroadcastManager.getInstance(context).sendBroadcast(i);
    }

    public static void sendOutput(Context context, CharSequence s, int type, Object action) {
        sendOutput(Integer.MAX_VALUE, context, s, type, action);
    }

    public static void sendOutput(int color, Context context, CharSequence s, int type, Object action) {
        Intent i = new Intent(PrivateIOReceiver.ACTION_OUTPUT);
        i.putExtra(PrivateIOReceiver.TEXT, s);
        i.putExtra(PrivateIOReceiver.COLOR, color);
        i.putExtra(PrivateIOReceiver.TYPE, type);
        if (action instanceof Parcelable) i.putExtra(PrivateIOReceiver.ACTION, (Parcelable) action);
        else if (action instanceof String) i.putExtra(PrivateIOReceiver.ACTION, (String) action);
        LocalBroadcastManager.getInstance(context).sendBroadcast(i);
    }

    public static void sendOutput(Context context, CharSequence s, int type, Object action, Object longAction) {
        Intent i = new Intent(PrivateIOReceiver.ACTION_OUTPUT);
        i.putExtra(PrivateIOReceiver.TEXT, s);
        i.putExtra(PrivateIOReceiver.TYPE, type);
        if (action instanceof Parcelable) i.putExtra(PrivateIOReceiver.ACTION, (Parcelable) action);
        if (longAction instanceof Parcelable) i.putExtra(PrivateIOReceiver.LONG_ACTION, (Parcelable) longAction);
        LocalBroadcastManager.getInstance(context).sendBroadcast(i);
    }

    public static void sendInput(Context context, String text) {
        Intent i = new Intent(PrivateIOReceiver.ACTION_INPUT);
        i.putExtra(PrivateIOReceiver.TEXT, text);
        LocalBroadcastManager.getInstance(context).sendBroadcast(i);
    }

    public static final int TERA = 0, GIGA = 1, MEGA = 2, KILO = 3, BYTE = 4;

    public static double round(double value, int places) {
        if (places < 0) return value;
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    public static String getNetworkType(Context context) { return "Cellular"; }

    public static boolean hasInternetAccess() {
        try {
            HttpURLConnection urlc = (HttpURLConnection) (new URL("http://clients3.google.com/generate_204").openConnection());
            urlc.setConnectTimeout(1500);
            return (urlc.getResponseCode() == 204);
        } catch (Exception e) { return false; }
    }

    public static boolean hasNotificationAccess(Context context) {
        String pkg = BuildConfig.APPLICATION_ID;
        final String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            for (String name : flat.split(":")) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkg, cn.getPackageName())) return true;
            }
        }
        return false;
    }

    public static void setCursorDrawableColor(EditText editText, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Drawable d = editText.getTextCursorDrawable();
            if (d != null) { d.setColorFilter(color, PorterDuff.Mode.SRC_IN); editText.setTextCursorDrawable(d); }
        } else {
            try {
                Field fRes = TextView.class.getDeclaredField("mCursorDrawableRes");
                fRes.setAccessible(true);
                int resId = fRes.getInt(editText);
                Field fEditor = TextView.class.getDeclaredField("mEditor");
                fEditor.setAccessible(true);
                Object editor = fEditor.get(editText);
                Field fCursor = editor.getClass().getDeclaredField("mCursorDrawable");
                fCursor.setAccessible(true);
                Drawable[] ds = { ResourcesCompat.getDrawable(editText.getResources(), resId, null), ResourcesCompat.getDrawable(editText.getResources(), resId, null) };
                if (ds[0] != null) ds[0].setColorFilter(color, PorterDuff.Mode.SRC_IN);
                if (ds[1] != null) ds[1].setColorFilter(color, PorterDuff.Mode.SRC_IN);
                fCursor.set(editor, ds);
            } catch (Exception ignored) {}
        }
    }

    public static int nOfBytes(File file) {
        int count = 0;
        try (FileInputStream in = new FileInputStream(file)) { while (in.read() != -1) count++; } catch (IOException e) { log(e); }
        return count;
    }

    public static void log(Object o) { Log.e("andre", String.valueOf(o)); }
    public static void log(Object o1, Object o2) { Log.e("andre", o1 + " -- " + o2); }

    public static void toFile(Object o) {
        File folder = getFolder();
        if (folder == null || o == null) return;
        try (FileOutputStream stream = new FileOutputStream(new File(folder, "crash.txt"), true)) {
            stream.write((NEWLINE + new Date().toString() + NEWLINE).getBytes());
            if (o instanceof Throwable) ((Throwable) o).printStackTrace(new PrintStream(stream));
            else stream.write(String.valueOf(o).getBytes());
        } catch (Exception ignored) {}
    }

    public static File getFolder() {
        File tui = new File(Environment.getExternalStorageDirectory(), TUI_FOLDER);
        if (tui.exists() || tui.mkdir()) return tui;
        return null;
    }

    public static String removeUnncesarySpaces(String s) { return s.replaceAll("\\s{2,}", SPACE); }
    public static String removeSpaces(String s) { return s.replaceAll("\\s", EMPTYSTRING); }

    public static void sendXMLParseError(Context context, String path) { sendOutput(Color.RED, context, "XML Problem: " + path, TerminalManager.CATEGORY_OUTPUT); }
    public static void sendXMLParseError(Context context, String path, SAXParseException e) { sendOutput(Color.RED, context, "XML Error in " + path + ": " + e.getMessage(), TerminalManager.CATEGORY_OUTPUT); }

    public static int find(Object o, Object[] array) {
        for (int i = 0; i < array.length; i++) if (o.equals(array[i])) return i;
        return -1;
    }

    public static int find(Object o, List list) {
        for (int i = 0; i < list.size(); i++) {
            Object x = list.get(i);
            if (x instanceof XMLPrefsSave && o instanceof String && ((XMLPrefsSave) x).label().equals(o)) return i;
            if (o.equals(x)) return i;
        }
        return -1;
    }

    public static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public static boolean arrayContains(int[] array, int value) {
        if (array == null) return false;
        for (int i : array) if (i == value) return true;
        return false;
    }

    public static char firstNonDigit(String s) {
        for (char c : s.toCharArray()) if (!Character.isDigit(c)) return c;
        return 0;
    }

    public static int alphabeticCompare(String s1, String s2) { return s1.toLowerCase().compareTo(s2.toLowerCase()); }

    public static boolean isMyLauncherDefault(PackageManager pm) {
        final IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        List<IntentFilter> filters = new ArrayList<>(); filters.add(filter);
        List<ComponentName> activities = new ArrayList<>();
        pm.getPreferredActivities(filters, activities, null);
        for (ComponentName activity : activities) if (BuildConfig.APPLICATION_ID.equals(activity.getPackageName())) return true;
        return false;
    }

    public static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) if (!Character.isDigit(c) && c != '-' && c != '.') return false;
        return true;
    }

    public static boolean isAlpha(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) if (!Character.isLetter(c)) return false;
        return true;
    }

    public static boolean isPhoneNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) if (Character.isLetter(c)) return false;
        return true;
    }

    public static void addPrefix(List<String> list, String prefix) { for (int i = 0; i < list.size(); i++) list.set(i, prefix.concat(list.get(i))); }
    public static void addSeparator(List<String> list, String sep) { for (int i = 0; i < list.size(); i++) list.set(i, list.get(i).concat(sep)); }

    public static void insertHeaders(List<String> s, boolean newLine) {
        char current = 0;
        for (int i = 0; i < s.size(); i++) {
            String st = s.get(i).trim().toUpperCase();
            if (st.isEmpty()) continue;
            char c = st.charAt(0);
            if (current != c) { s.add(i, (newLine ? NEWLINE : EMPTYSTRING) + c + (newLine ? NEWLINE : EMPTYSTRING)); current = c; i++; }
        }
    }

    public static String toPlanString(List<String> list) { return toPlanString(list, NEWLINE); }
    public static String toPlanString(List<String> list, String separator) {
        if (list == null || list.isEmpty()) return EMPTYSTRING;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) { sb.append(list.get(i)); if (i < list.size() - 1) sb.append(separator); }
        return sb.toString();
    }

    public static String toPlanString(Object[] arr, String sep) {
        if (arr == null || arr.length == 0) return EMPTYSTRING;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) { sb.append(arr[i]); if (i < arr.length - 1) sb.append(sep); }
        return sb.toString();
    }

    public static boolean insertOld(File f) {
        if (f == null || !f.exists()) return false;
        File old = new File(getFolder(), "old"); if (!old.exists()) old.mkdir();
        return f.renameTo(new File(old, f.getName()));
    }

    public static File getOld(String name) { File f = new File(new File(getFolder(), "old"), name); return f.exists() ? f : null; }
    
    public static String inputStreamToString(InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : EMPTYSTRING;
    }

    public static String getTextFromClipboard(Context context) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                return cm.getPrimaryClip().getItemAt(0).getText().toString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static int mmToPx(DisplayMetrics metrics, int mm) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, mm, metrics); }
    public static int dpToPx(Context context, int dp) { return Math.round(dp * context.getResources().getDisplayMetrics().density); }

    public static void deleteContentOnly(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) delete(f);
    }

    public static void delete(File f) {
        if (f.isDirectory()) { File[] files = f.listFiles(); if (files != null) for (File sub : files) delete(sub); }
        f.delete();
    }

    public static long download(InputStream in, File file) throws Exception {
        try (OutputStream out = new FileOutputStream(file, false)) {
            byte[] data = new byte[1024]; long bytes = 0; int count;
            while ((count = in.read(data)) != -1) { out.write(data, 0, count); bytes += count; }
            out.flush(); return bytes;
        }
    }

    public static Intent shareFile(Context c, File f) {
        Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain");
        Uri u = FileProvider.getUriForFile(c, BuildConfig.APPLICATION_ID + ".fileprovider", f);
        i.putExtra(Intent.EXTRA_STREAM, u); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return i;
    }

    public static Intent openFile(Context c, File f) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        Uri u = FileProvider.getUriForFile(c, BuildConfig.APPLICATION_ID + ".fileprovider", f);
        i.setDataAndType(u, "text/plain"); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return i;
    }

    public static Intent webPage(String url) { return new Intent(Intent.ACTION_VIEW, Uri.parse(url)); }
    public static List<String> getClassesInPackage(String pkg, Context c) throws IOException {
        List<String> list = new ArrayList<>(); DexFile df = new DexFile(c.getPackageCodePath());
        Enumeration<String> it = df.entries();
        while (it.hasMoreElements()) { String s = it.nextElement(); if (s.contains(pkg) && !s.contains("$")) list.add(s.substring(s.lastIndexOf(".") + 1)); }
        return list;
    }

    public static int scale(int[] from, int[] to, int n) { return (to[1] - to[0]) * (n - from[0]) / (from[1] - from[0]) + to[0]; }
    public static String getHint(String path) { return path; }

    public static <T> T getDefaultValue(Class<T> clazz) { return (T) Array.get(Array.newInstance(clazz, 1), 0); }
    
    // Калькулятор
    public static double eval(final String str) {
        return new Object() {
            int pos = -1, ch;
            void nextChar() { ch = (++pos < str.length()) ? str.charAt(pos) : -1; }
            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) { nextChar(); return true; }
                return false;
            }
            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char)ch);
                return x;
            }
            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }
            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }
            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();
                double x;
                int startPos = this.pos;
                if (eat('(')) { x = parseExpression(); eat(')'); }
                else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else if (ch >= 'a' && ch <= 'z') {
                    while (ch >= 'a' && ch <= 'z') nextChar();
                    String func = str.substring(startPos, this.pos);
                    x = parseFactor();
                    if (func.equals("sqrt")) x = Math.sqrt(x);
                    else if (func.equals("sin")) x = Math.sin(Math.toRadians(x));
                    else if (func.equals("cos")) x = Math.cos(Math.toRadians(x));
                    else if (func.equals("tan")) x = Math.tan(Math.toRadians(x));
                    else throw new RuntimeException("Unknown function: " + func);
                } else {
                    throw new RuntimeException("Unexpected: " + (char)ch);
                }
                if (eat('^')) x = Math.pow(x, parseFactor());
                return x;
            }
        }.parse();
    }
}
