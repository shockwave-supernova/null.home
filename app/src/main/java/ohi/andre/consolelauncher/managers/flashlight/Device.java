package ohi.andre.consolelauncher.managers.flashlight;

import android.content.Context;

public abstract class Device {

    protected final Context mContext;
    protected boolean isEnabled;

    public Device(Context context) {
        this.mContext = context;
        this.isEnabled = false;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }
}
