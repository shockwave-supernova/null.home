package ohi.andre.consolelauncher.managers.notifications.reply;

import android.app.PendingIntent;
import android.app.RemoteInput;
import android.os.Bundle;

public class NotificationWear {

    public BoundApp app;
    public PendingIntent pendingIntent;
    public RemoteInput[] remoteInputs;
    public Bundle bundle;
    public int id;
    public CharSequence text;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        NotificationWear that = (NotificationWear) obj;

        // Оригинальная логика: уведомления равны, если они от одного приложения
        if (this.app == null || that.app == null) return false;
        if (this.app.packageName == null || that.app.packageName == null) return false;

        return this.app.packageName.equals(that.app.packageName);
    }

    @Override
    public int hashCode() {
        return (app != null && app.packageName != null) ? app.packageName.hashCode() : 0;
    }
}
