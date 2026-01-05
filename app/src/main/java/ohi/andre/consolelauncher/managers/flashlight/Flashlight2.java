package ohi.andre.consolelauncher.managers.flashlight;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ohi.andre.consolelauncher.tuils.PrivateIOReceiver;

@TargetApi(23)
public class Flashlight2 extends Flashlight {

    public static final String TYPE = Constants.ID_DEVICE_OUTPUT_TORCH_FLASH_NEW;

    private String mCameraId;

    public Flashlight2(Context context) {
        super(context);
    }

    @Override
    protected void turnOn() {
        if (getStatus()) return;

        try {
            CameraManager manager = getCameraManager();
            if (mCameraId == null) {
                mCameraId = findFlashCameraId(manager);
            }

            if (mCameraId != null) {
                manager.setTorchMode(mCameraId, true);
                updateStatus(true);
            } else {
                sendError("No flashlight found on this device");
            }
        } catch (Exception e) {
            sendError(e.toString());
        }
    }

    @Override
    protected void turnOff() {
        if (!getStatus()) return;

        try {
            if (mCameraId != null) {
                getCameraManager().setTorchMode(mCameraId, false);
            }
            updateStatus(false);
        } catch (Exception e) {
            sendError(e.toString());
        }
    }

    private CameraManager getCameraManager() {
        return (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
    }

    // Ищем камеру, у которой реально есть вспышка (обычно задняя)
    private String findFlashCameraId(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);

            // Идеальный вариант: Задняя камера со вспышкой
            if (flashAvailable != null && flashAvailable && 
                lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }

        // Запасной вариант: Любая камера со вспышкой
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (flashAvailable != null && flashAvailable) {
                return id;
            }
        }
        return null;
    }

    private void sendError(String error) {
        Intent intent = new Intent(PrivateIOReceiver.ACTION_OUTPUT);
        intent.putExtra(PrivateIOReceiver.TEXT, error);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }
}
