package ohi.andre.consolelauncher.tuils.interfaces;

import ohi.andre.consolelauncher.BuildConfig;

/**
 * Created by francescoandreuzzi on 04/08/2017.
 */

public interface OnBatteryUpdate {

    void update(float percentage);
    void onCharging();
    void onNotCharging();
}
