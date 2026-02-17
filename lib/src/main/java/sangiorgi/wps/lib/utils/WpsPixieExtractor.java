package sangiorgi.wps.lib.utils;

import android.util.Log;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import sangiorgi.wps.lib.models.PixieDustParameters;

/** Extracts Pixie Dust parameters from WPS connection output */
public class WpsPixieExtractor {
  private static final String TAG = "WpsPixieExtractor";

  private static final String ENROLLEE_NONCE = "Enrollee Nonce";
  private static final String DH_OWN_PUBLIC = "DH own Public Key";
  private static final String DH_PEER_PUBLIC = "DH peer Public Key";
  private static final String AUTH_KEY = "AuthKey";
  private static final String E_HASH1 = "E-Hash1";
  private static final String E_HASH2 = "E-Hash2";
  private static final String HEXDUMP = "hexdump";

  private static final Pattern HEX_PATTERN = Pattern.compile("\\):\\s*([0-9a-fA-F\\s]+)");

  /** Extract Pixie Dust parameters from WPS output lines */
  public static PixieDustParameters extractParameters(List<String> outputLines) {
    String enonce = null;
    String pkr = null;
    String pke = null;
    String authKey = null;
    String ehash1 = null;
    String ehash2 = null;

    for (String line : outputLines) {
      if (line == null || !line.contains(HEXDUMP)) {
        continue;
      }

      String hexValue = extractHexValue(line);
      if (hexValue == null) {
        continue;
      }

      if (line.contains(ENROLLEE_NONCE)) {
        enonce = hexValue;
        Log.d(TAG, "Found Enrollee Nonce: " + enonce);
      } else if (line.contains(DH_OWN_PUBLIC)) {
        pkr = hexValue;
        Log.d(TAG, "Found DH own Public Key (PKR): " + pkr);
      } else if (line.contains(DH_PEER_PUBLIC)) {
        pke = hexValue;
        Log.d(TAG, "Found DH peer Public Key (PKE): " + pke);
      } else if (line.contains(AUTH_KEY)) {
        authKey = hexValue;
        Log.d(TAG, "Found AuthKey: " + authKey);
      } else if (line.contains(E_HASH1)) {
        ehash1 = hexValue;
        Log.d(TAG, "Found E-Hash1: " + ehash1);
      } else if (line.contains(E_HASH2)) {
        ehash2 = hexValue;
        Log.d(TAG, "Found E-Hash2: " + ehash2);
      }
    }

    // Check if all parameters were found
    if (enonce != null
        && pkr != null
        && pke != null
        && authKey != null
        && ehash1 != null
        && ehash2 != null) {
      Log.i(TAG, "Successfully extracted all Pixie Dust parameters");
      return new PixieDustParameters(pke, pkr, ehash1, ehash2, authKey, enonce);
    } else {
      Log.w(
          TAG,
          "Failed to extract all parameters. Found: "
              + "enonce="
              + (enonce != null)
              + ", pkr="
              + (pkr != null)
              + ", pke="
              + (pke != null)
              + ", authKey="
              + (authKey != null)
              + ", ehash1="
              + (ehash1 != null)
              + ", ehash2="
              + (ehash2 != null));
      return null;
    }
  }

  /** Extract hex value from a line containing hexdump output */
  private static String extractHexValue(String line) {
    try {
      Matcher matcher = HEX_PATTERN.matcher(line);
      if (matcher.find()) {
        String hex = matcher.group(1);
        if (hex != null) {
          // Remove all whitespace from hex string
          return hex.replaceAll("\\s+", "");
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Error extracting hex value from line: " + line, e);
    }
    return null;
  }

  /** Check if output contains Pixie Dust parameters */
  public static boolean hasPixieParameters(List<String> outputLines) {
    int paramCount = 0;

    for (String line : outputLines) {
      if (line == null || !line.contains(HEXDUMP)) {
        continue;
      }

      if (line.contains(ENROLLEE_NONCE)
          || line.contains(DH_OWN_PUBLIC)
          || line.contains(DH_PEER_PUBLIC)
          || line.contains(AUTH_KEY)
          || line.contains(E_HASH1)
          || line.contains(E_HASH2)) {
        paramCount++;
      }
    }

    return paramCount >= 6;
  }
}
