package sangiorgi.wps.lib.services;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import sangiorgi.wps.lib.assets.WpaToolsPaths;

public class PinDatabaseService {

  private static final String TAG = "PinDatabaseService";
  private static final String TABLE_NAME = "pins";
  private static final String COLUMN_MAC = "MAC";
  private static final String COLUMN_PIN = "pin";
  private static final int MAX_PINS = 8;

  private final String databasePath;
  private SQLiteDatabase database;

  public PinDatabaseService(Context context) {
    this.databasePath = new WpaToolsPaths(context).getPinDatabasePath();
  }

  private void openDatabase() {
    if (database != null && database.isOpen()) {
      return;
    }
    try {
      database =
          SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READONLY);
    } catch (SQLiteException e) {
      Log.e(TAG, "Error opening pin database at: " + databasePath, e);
    }
  }

  /**
   * Get known default PINs for a given BSSID.
   *
   * @param bssid The router's BSSID (e.g., "AA:BB:CC:DD:EE:FF")
   * @return List of known PINs for this MAC prefix, or empty list if none found
   */
  public List<String> getPinsByMac(String bssid) {
    if (bssid == null || bssid.isEmpty()) {
      return Collections.emptyList();
    }

    String macPrefix = normalizeMacPrefix(bssid);
    if (macPrefix == null) {
      return Collections.emptyList();
    }

    openDatabase();
    if (database == null || !database.isOpen()) {
      return Collections.emptyList();
    }

    try {
      Cursor cursor =
          database.rawQuery(
              "SELECT " + COLUMN_PIN + " FROM " + TABLE_NAME + " WHERE " + COLUMN_MAC + " = ? LIMIT " + MAX_PINS,
              new String[] {macPrefix});

      List<String> pins = new ArrayList<>();
      try {
        while (cursor.moveToNext()) {
          String pin = cursor.getString(0);
          if (pin != null && !pin.isEmpty()) {
            pins.add(pin);
          }
        }
      } finally {
        cursor.close();
      }

      if (!pins.isEmpty()) {
        Log.d(TAG, "Found " + pins.size() + " database PINs for MAC prefix: " + macPrefix);
      }

      return pins;
    } catch (Exception e) {
      Log.e(TAG, "Error querying PINs for BSSID: " + bssid, e);
      return Collections.emptyList();
    }
  }

  /**
   * Normalize BSSID to a 6-char lowercase MAC prefix.
   * "AA:BB:CC:DD:EE:FF" -> "aabbcc"
   */
  static String normalizeMacPrefix(String bssid) {
    String normalized = bssid.toUpperCase().replace(":", "").replace("-", "");
    if (normalized.length() < 6) {
      return null;
    }
    return normalized.substring(0, 6).toLowerCase();
  }
}
