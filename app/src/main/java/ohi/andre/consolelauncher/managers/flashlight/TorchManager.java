package ohi.andre.consolelauncher.managers.flashlight;

import android.content.Context;
import android.os.Build;

public class TorchManager {

    private static TorchManager mInstance;
    private final String flashType;
    private Torch mTorch;
    private String torchType;

    private TorchManager() {
        // Если Android 6.0+ (Marshmallow), используем Flashlight2 (Camera2 API), иначе Flashlight1
        this.flashType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? Flashlight2.TYPE : Flashlight1.TYPE;
        this.setTorchType(Constants.ID_DEVICE_OUTPUT_TORCH_FLASH);
    }

    public static TorchManager getInstance() {
        if (mInstance == null) {
            mInstance = new TorchManager();
        }
        return mInstance;
    }

    public boolean isOn() {
        return this.mTorch != null;
    }

    public void setTorchType(String torchType) {
        this.torchType = torchType;
    }

    public void turnOn(Context context) {
        if (this.mTorch == null) {
            if (Flashlight.TYPE.equals(this.torchType)) {
                if (Flashlight2.TYPE.equals(this.flashType)) {
                    this.mTorch = new Flashlight2(context);
                } else {
                    this.mTorch = new Flashlight1(context);
                }
            }
        }

        // Проверка на null, чтобы не было краша, если mTorch не создался
        if (this.mTorch != null) {
            this.mTorch.setEnabled(true);
            this.mTorch.start(true);
        }
    }

    public void turnOff() {
        if (this.mTorch != null) {
            this.mTorch.start(false);
            this.mTorch = null;
        }
    }

    public void toggle(Context context) {
        if (this.mTorch == null) {
            this.turnOn(context);
        } else {
            if (this.mTorch.getStatus()) {
                this.turnOff();
            } else {
                this.turnOn(context);
            }
        }
    }
}
