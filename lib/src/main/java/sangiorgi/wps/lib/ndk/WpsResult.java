package sangiorgi.wps.lib.ndk;

import androidx.annotation.NonNull;

/**
 * Result of a WPS connection attempt, returned from native code.
 */
public class WpsResult {

    public enum Status {
        SUCCESS,        // WPS succeeded, networkKey may contain the password
        FOUR_FAIL,      // 4-way handshake failed (wrong PIN, first half)
        THREE_FAIL,     // 3-way failure (wrong PIN, second half)
        LOCKED,         // WPS is locked (config_error=15)
        CRC_FAIL,       // CRC failure (maybe PBC mode only)
        SELINUX,        // SELinux denied the operation
        TIMEOUT,        // No response within timeout
        ERROR;          // Generic error

        public static Status fromCode(int code) {
            switch (code) {
                case 0: return SUCCESS;
                case 1: return FOUR_FAIL;
                case 2: return THREE_FAIL;
                case 3: return LOCKED;
                case 4: return CRC_FAIL;
                case 5: return SELINUX;
                case 6: return TIMEOUT;
                default: return ERROR;
            }
        }
    }

    private final Status status;
    private final String networkKey;
    private final String rawLine;
    private final String exchangeLog;

    /**
     * Constructor called from native code.
     * @param statusCode  Native status code (0-7)
     * @param networkKey  WiFi password on success, null otherwise
     * @param rawLine     Raw output line for debugging
     * @param exchangeLog Accumulated WPS exchange lines from supplicant
     */
    public WpsResult(int statusCode, String networkKey, String rawLine, String exchangeLog) {
        this.status = Status.fromCode(statusCode);
        this.networkKey = networkKey;
        this.rawLine = rawLine;
        this.exchangeLog = exchangeLog;
    }

    public Status getStatus() {
        return status;
    }

    public String getNetworkKey() {
        return networkKey;
    }

    public String getRawLine() {
        return rawLine;
    }

    public String getExchangeLog() {
        return exchangeLog;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    @NonNull
    @Override
    public String toString() {
        return "WpsResult{status=" + status +
                ", networkKey=" + (networkKey != null ? "[present]" : "null") +
                ", rawLine='" + rawLine + "'}";
    }
}
