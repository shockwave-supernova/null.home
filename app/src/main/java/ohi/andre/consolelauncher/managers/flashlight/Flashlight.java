package ohi.andre.consolelauncher.managers.flashlight;

import android.content.Context;

public abstract class Flashlight extends Torch {

    public static final String TYPE = Constants.ID_DEVICE_OUTPUT_TORCH_FLASH;

    public Flashlight(Context context) {
        super(context);
    }
}
