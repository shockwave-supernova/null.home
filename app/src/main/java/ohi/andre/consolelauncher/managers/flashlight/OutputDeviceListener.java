package ohi.andre.consolelauncher.managers.flashlight;

public interface OutputDeviceListener extends DeviceListener {
    void onStatusChanged(boolean status);
}
