package sangiorgi.wps.lib.ndk;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Manages wpa_supplicant configuration for the NDK-based WPS implementation.
 */
public class WpsConfig {

    private static final String TAG = "WpsConfig";
    private static final String CONFIG_FILENAME = "wpa_supplicant.conf";

    /**
     * Ensure wpa_supplicant.conf exists in the app's files directory.
     * Always rewrites to ensure ctrl_interface matches the Android version.
     *
     * @return Absolute path to the config file
     */
    public static String ensureConfigFile(Context context) {
        File configFile = new File(context.getFilesDir(), CONFIG_FILENAME);

        String ctrlDir = WpsNative.getCtrlDir();
        String content = "ctrl_interface=DIR=" + ctrlDir + " GROUP=wifi\n" +
                "update_config=0\n";

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(content);
            Log.i(TAG, "Config file written: " + configFile.getAbsolutePath()
                    + " ctrl_interface=" + ctrlDir);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create config file", e);
            return null;
        }

        return configFile.getAbsolutePath();
    }
}
