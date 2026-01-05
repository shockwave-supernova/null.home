package ohi.andre.consolelauncher.managers.flashlight;

import android.content.Context;

public abstract class OutputDevice extends Device {

    private boolean mStatus;
    private OutputDeviceListener mListener;

    public OutputDevice(Context context) {
        super(context);
        this.mStatus = false;
    }

    protected abstract void turnOn();

    protected abstract void turnOff();

    public final void start(boolean status) {
        if (isEnabled) {
            if (status && !mStatus) {
                turnOn();
            } else if (!status && mStatus) {
                turnOff();
            }
        }
    }

    public final void toggle() {
        start(!mStatus);
    }

    public final boolean getStatus() {
        return mStatus;
    }

    public final void setListener(OutputDeviceListener listener) {
        this.mListener = listener;
    }

    protected final void updateStatus(boolean status) {
        this.mStatus = status;
        if (mListener != null) {
            mListener.onStatusChanged(status);
        }
    }

    protected final void updateError(String error) {
        if (mListener != null) {
            mListener.onError(error);
        }
    }
}
