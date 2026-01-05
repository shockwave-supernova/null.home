package ohi.andre.consolelauncher.managers.flashlight;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;

import ohi.andre.consolelauncher.tuils.PrivateIOReceiver;

@SuppressWarnings("deprecation")
public class Flashlight1 extends Flashlight {

    private Camera mCamera;
    private boolean flashSupported;

    public Flashlight1(Context context) {
        super(context);
        this.flashSupported = false;
    }

    @Override
    protected void turnOn() {
        if (ready() && !getStatus()) {
            try {
                // Для работы вспышки на старом API часто требуется активное превью
                mCamera.setPreviewTexture(new SurfaceTexture(0));
                mCamera.startPreview();
                updateStatus(true);
            } catch (Exception e) {
                releaseCamera();
                sendError(e.toString());
            }
        }
    }

    @Override
    protected void turnOff() {
        if (getStatus() && mCamera != null) {
            try {
                mCamera.stopPreview();
            } catch (Exception e) {
                // Игнорируем ошибки при остановке
            }
            releaseCamera();
            updateStatus(false);
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            try {
                mCamera.release();
            } catch (Exception e) {
                // Игнорируем ошибки при освобождении
            }
            mCamera = null;
        }
    }

    private boolean ready() {
        if (mCamera == null) {
            try {
                mCamera = Camera.open();
            } catch (Exception e) {
                sendError(e.toString());
                return false;
            }
        }

        try {
            Camera.Parameters parameters = mCamera.getParameters();
            List<String> supportedFlashModes = parameters.getSupportedFlashModes();

            if (supportedFlashModes != null) {
                if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                    flashSupported = true;
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                } else if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                    flashSupported = true;
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                }
            }

            if (flashSupported) {
                mCamera.setParameters(parameters);
            }
        } catch (Exception e) {
            sendError(e.toString());
            releaseCamera();
            return false;
        }

        return true;
    }

    private void sendError(String errorMsg) {
        Intent intent = new Intent(PrivateIOReceiver.ACTION_OUTPUT);
        intent.putExtra(PrivateIOReceiver.TEXT, errorMsg);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }
}
