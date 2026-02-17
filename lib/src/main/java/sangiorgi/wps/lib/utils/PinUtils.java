package sangiorgi.wps.lib.utils;

import android.content.Context;
import android.util.Log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class PinUtils {

  private static final String TAG = "PinUtils";
  private static final String SUCCESS_SUFFIX = "SUCCESS";

  private final String sessionPath;

  public PinUtils(Context context) {
    this.sessionPath = context.getFilesDir().getAbsolutePath() + "/Sessions/";
  }

  public void storePinResult(String bssid, String pin, boolean success, String additionalInfo) {
    if (bssid == null || pin == null) {
      Log.w(TAG, "Cannot store pin result - BSSID or PIN is null");
      return;
    }

    File sessionDir = new File(sessionPath);
    if (!sessionDir.exists() && !sessionDir.mkdirs()) {
      Log.e(TAG, "Failed to create session directory: " + sessionDir.getPath());
      return;
    }

    File pinFile = new File(sessionPath + bssid);
    String pinEntry = buildPinEntry(pin, success, additionalInfo);

    try (FileOutputStream fileOutput = new FileOutputStream(pinFile, true);
        BufferedWriter writer =
            new BufferedWriter(new OutputStreamWriter(fileOutput, StandardCharsets.UTF_8))) {

      writer.append(pinEntry).append('\n');

    } catch (IOException e) {
      Log.e(TAG, "Error storing pin result for BSSID: " + bssid, e);
    }
  }

  private String buildPinEntry(String pin, boolean success, String additionalInfo) {
    StringBuilder entry = new StringBuilder(pin);

    if (success) {
      entry.append(SUCCESS_SUFFIX);
    }

    if (additionalInfo != null && !additionalInfo.isEmpty()) {
      entry.append(additionalInfo);
    }

    return entry.toString();
  }

  // Using .name() for API 24 compatibility - Scanner(File, Charset) requires API 34
  @SuppressWarnings("CharsetObjectCanBeUsed")
  public boolean isPinAlreadyTested(String bssid, String pin) {
    if (bssid == null || pin == null) {
      return false;
    }

    File file = new File(sessionPath + bssid);
    if (!file.exists()) {
      return false;
    }

    try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8.name())) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        if (line.contains(pin) && !line.contains(SUCCESS_SUFFIX)) {
          return true;
        }
      }
    } catch (IOException e) {
      Log.e(TAG, "Error checking if pin was tested for BSSID: " + bssid, e);
    }

    return false;
  }
}
