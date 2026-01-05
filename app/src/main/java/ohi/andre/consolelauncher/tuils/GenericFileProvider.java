package ohi.andre.consolelauncher.tuils;

import ohi.andre.consolelauncher.BuildConfig;

import androidx.core.content.FileProvider;



public class GenericFileProvider extends FileProvider {
    public static final String PROVIDER_NAME = BuildConfig.APPLICATION_ID + ".FILE_PROVIDER";
}
