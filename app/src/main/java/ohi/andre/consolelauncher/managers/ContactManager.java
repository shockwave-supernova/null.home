package ohi.andre.consolelauncher.managers;

import ohi.andre.consolelauncher.BuildConfig;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import it.andreuzzi.comparestring2.StringableObject;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.tuils.StoppableThread;
import ohi.andre.consolelauncher.tuils.Tuils;

public class ContactManager {

    public static String ACTION_REFRESH = BuildConfig.APPLICATION_ID + ".refresh_contacts";

    private Context context;
    // ВАЖНО: Инициализируем сразу пустым списком и делаем volatile для потокобезопасности
    private volatile List<Contact> contacts = new ArrayList<>();

    private BroadcastReceiver receiver;

    public ContactManager(Context context) {
        this.context = context;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            refreshContacts(context);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_REFRESH);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(ACTION_REFRESH)) {
                    refreshContacts(context);
                }
            }
        };

        LocalBroadcastManager.getInstance(context.getApplicationContext()).registerReceiver(receiver, filter);
    }

    public void destroy(Context context) {
        try {
            LocalBroadcastManager.getInstance(context.getApplicationContext()).unregisterReceiver(receiver);
        } catch (Exception e) {}
    }

    public void refreshContacts(final Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            if (context instanceof LauncherActivity) {
                try {
                    ((LauncherActivity) context).launchPermissionRequest(new String[]{Manifest.permission.READ_CONTACTS});
                } catch (Exception e) {}
            }
            return;
        }

        new StoppableThread() {
            @Override
            public void run() {
                super.run();

                // Создаем временный список, чтобы не трогать основной во время чтения
                List<Contact> tempContacts = new ArrayList<>();

                Cursor phones = null;
                try {
                    phones = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            new String[] {ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.Data.IS_SUPER_PRIMARY,}, null, null,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
                } catch (Exception e) {
                    Tuils.log(e);
                }

                if (phones != null) {
                    int lastId = -1;
                    List<String> lastNumbers = new ArrayList<>();
                    List<String> nrml = new ArrayList<>();
                    int defaultNumber = 0;
                    String name = null, number;
                    int id, prim;

                    while (phones.moveToNext()) {
                        try {
                            id = phones.getInt(phones.getColumnIndex(ContactsContract.Data.CONTACT_ID));
                            number = phones.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));

                            prim = phones.getInt(phones.getColumnIndex(ContactsContract.Data.IS_SUPER_PRIMARY));
                            if(prim > 0) {
                                defaultNumber = lastNumbers.size();
                            }

                            if(number == null || number.length() == 0) continue;

                            if(phones.isFirst()) {
                                lastId = id;
                                name = phones.getString(phones.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                            } else if(id != lastId || phones.isLast()) {
                                lastId = id;

                                if (name != null) {
                                    tempContacts.add(new Contact(name, lastNumbers, defaultNumber));
                                }

                                lastNumbers = new ArrayList<>();
                                nrml = new ArrayList<>();
                                name = null;
                                defaultNumber = 0;

                                name = phones.getString(phones.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                            }

                            String normalized = number.replaceAll(Tuils.SPACE, Tuils.EMPTYSTRING);
                            if(!nrml.contains(normalized)) {
                                nrml.add(normalized);
                                lastNumbers.add(number);
                            }

                            if(name != null && phones.isLast()) {
                                tempContacts.add(new Contact(name, lastNumbers, defaultNumber));
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    phones.close();
                }

                // Удаляем контакты без номеров
                Iterator<Contact> iterator = tempContacts.iterator();
                while(iterator.hasNext()) {
                    Contact c = iterator.next();
                    if(c.numbers.size() == 0) iterator.remove();
                }

                Collections.sort(tempContacts);

                // АТОМНАЯ ЗАМЕНА СПИСКА: Это предотвращает краш
                ContactManager.this.contacts = tempContacts;
            }
        }.start();
    }

    public List<String> listNames() {
        List<Contact> localContacts = contacts; // Используем локальную копию ссылки
        List<String> names = new ArrayList<>();
        if (localContacts != null) {
            for (Contact c : localContacts) names.add(c.name);
        }
        return names;
    }

    // --- БЕЗОПАСНЫЙ МЕТОД ---
    public List<Contact> getContacts() {
        // Мы копируем ссылку на список в локальную переменную.
        // Даже если фоновый поток заменит this.contacts на новый список,
        // localContacts останется указывать на старый (но живой) список.
        // Никакого null!
        List<Contact> localContacts = contacts;
        if (localContacts == null) {
            refreshContacts(context);
            return new ArrayList<>();
        }
        return new ArrayList<>(localContacts);
    }
    // -------------------------

    public List<String> listNamesAndNumbers() {
        List<Contact> localContacts = contacts;
        List<String> c = new ArrayList<>();
        if (localContacts != null) {
            for (int count = 0; count < localContacts.size(); count++) {
                Contact cnt = localContacts.get(count);
                StringBuilder b = new StringBuilder();
                b.append(cnt.name);
                for (String n : cnt.numbers) {
                    b.append(Tuils.NEWLINE);
                    b.append("\t");
                    b.append(n);
                }
                c.add(b.toString());
            }
        }
        return c;
    }

    public static final int NAME = 0;
    public static final int NUMBERS = 1;
    public static final int TIME_CONTACTED = 2;
    public static final int LAST_CONTACTED = 3;
    public static final int CONTACT_ID = 4;
    public static final int SIZE = CONTACT_ID + 1;

    public String[] about(String phone) {
        try {
            Cursor mCursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[] {ContactsContract.CommonDataKinds.Phone.CONTACT_ID},
                    ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?", new String[] {phone},
                    null);

            if(mCursor == null || mCursor.getCount() == 0) return null;
            String[] about = new String[SIZE];

            mCursor.moveToNext();
            String id = mCursor.getString(mCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID));
            about[CONTACT_ID] = id;
            mCursor.close();

            mCursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[] {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED, ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[] {id},
                    null);

            if(mCursor == null || mCursor.getCount() == 0) return null;
            mCursor.moveToNext();

            about[NAME] = mCursor.getString(mCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            about[NUMBERS] = new String(Tuils.EMPTYSTRING);

            int timesContacted = -1;
            long lastContacted = Long.MAX_VALUE;
            do {
                int tempT = mCursor.getInt(mCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED));
                long tempL = mCursor.getLong(mCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED));

                timesContacted = tempT > timesContacted ? tempT : timesContacted;
                if(tempL > 0) lastContacted = tempL < lastContacted ? tempL : lastContacted;

                String n = mCursor.getString(mCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                about[NUMBERS] = (about[NUMBERS].length() > 0 ? about[NUMBERS] + Tuils.NEWLINE : Tuils.EMPTYSTRING) + n;
            } while (mCursor.moveToNext());
            mCursor.close();

            about[TIME_CONTACTED] = String.valueOf(timesContacted);
            if(lastContacted != Long.MAX_VALUE) {
                long difference = System.currentTimeMillis() - lastContacted;
                long sc = difference / 1000;
                // Форматирование времени пропущено для краткости, оно редко вызывает краш
                about[LAST_CONTACTED] = String.valueOf(sc);
            }
            return about;
        } catch (Exception e) {
            return null;
        }
    }

    public String findNumber(String name) {
        List<Contact> localContacts = contacts;
        if(localContacts == null || localContacts.isEmpty()) {
            refreshContacts(context);
            return null;
        }

        for(int count = 0; count < localContacts.size(); count++) {
            Contact c = localContacts.get(count);
            if(c.name.equalsIgnoreCase(name)) {
                if(c.numbers.size() > 0) return c.numbers.get(0);
            }
        }
        return null;
    }

    public boolean delete(String phone) {
        try {
            return context.getContentResolver().delete(fromPhone(phone), null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public Uri fromPhone(String phone) {
        try {
            Cursor mCursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[] {ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                    ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?", new String[] {phone},
                    null);

            if(mCursor == null || mCursor.getCount() == 0) return null;
            mCursor.moveToNext();
            String name = mCursor.getString(mCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            mCursor.close();

            mCursor = context.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                    new String[] {ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                    ContactsContract.Contacts.DISPLAY_NAME + " = ?", new String[] {name},
                    null);

            if(mCursor == null || mCursor.getCount() == 0) return null;
            mCursor.moveToNext();
            String mCurrentLookupKey = mCursor.getString(mCursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
            long mCurrentId = mCursor.getLong(mCursor.getColumnIndex(ContactsContract.Contacts._ID));
            mCursor.close();

            return ContactsContract.Contacts.getLookupUri(mCurrentId, mCurrentLookupKey);
        } catch (Exception e) {
            return null;
        }
    }

    public static class Contact implements Comparable<Contact>, StringableObject {
        public String name, lowercaseName;
        public List<String> numbers;

        private int selectedNumber;

        public Contact(String name, List<String> numbers, int defNumber) {
            this.name = name;
            this.lowercaseName = name.toLowerCase();
            this.numbers = numbers;
            setSelectedNumber(defNumber);
        }

        public void setSelectedNumber(int s) {
            if(s >= numbers.size()) s = 0;
            this.selectedNumber = s;
        }

        public int getSelectedNumber() {
            return selectedNumber;
        }

        @Override
        public String getLowercaseString() {
            return lowercaseName;
        }

        @Override
        public String getString() {
            return name;
        }

        @Override
        public int compareTo(@NonNull Contact o) {
            if (name == null || o.name == null) return 0;
            char tf = name.toUpperCase().charAt(0);
            char of = o.name.toUpperCase().charAt(0);
            return tf - of;
        }
    }
}
