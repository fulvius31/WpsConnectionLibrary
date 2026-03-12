package sangiorgi.wps.lib.ndk;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class WpsNative {

    private static final String TAG = "WpsNative";
    private static boolean libraryLoaded;

    private final String nativeLibDir;
    private final WifiManager wifiManager;

    // Root-accessible directory for deployed executables and runtime files
    private static final String SU_EXEC_DIR = "/data/local/tmp/wpswpa";

    // Su shell for wpa_cli commands
    private Process suShell;
    private DataOutputStream suStdin;
    private Thread shellReaderThread;
    private final BlockingQueue<String> shellOutputQueue = new LinkedBlockingQueue<>();

    // Separate su shell running wpa_supplicant in foreground (stdout captured directly)
    private Process wpaSupplicantProcess;
    private Thread outputPumpThread;

    private String sessionCtrlDir;
    private String sessionIface;

    private static final String[] EXECUTABLES = {
            "libwpa_supplicant_exec.so",
            "libwpa_cli_exec.so",
            "libpixiewps_exec.so"
    };

    static {
        try {
            System.loadLibrary("wpsnative");
            libraryLoaded = true;
            Log.i(TAG, "libwpsnative.so loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load libwpsnative.so: " + e.getMessage(), e);
            libraryLoaded = false;
        }
    }

    public WpsNative(Context context) {
        this.nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        this.wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        // Log what's in nativeLibDir for diagnostics
        Log.i(TAG, "nativeLibDir=" + nativeLibDir);
        File libDir = new File(nativeLibDir);
        File[] allLibs = libDir.listFiles();
        if (allLibs != null) {
            Log.i(TAG, "nativeLibDir contains " + allLibs.length + " files");
            for (String name : EXECUTABLES) {
                File f = new File(nativeLibDir, name);
                if (f.exists()) {
                    Log.i(TAG, "  " + name + " (" + f.length() + " bytes)");
                } else {
                    Log.e(TAG, "  MISSING: " + name);
                }
            }
        } else {
            Log.e(TAG, "nativeLibDir listFiles() returned null");
        }

        if (libraryLoaded) {
            // Use SU_EXEC_DIR for pixiewps - nativeLibDir is mounted noexec on modern Android.
            // Pixiewps is deployed to SU_EXEC_DIR by deployExecutables() before computePixiePin() is called.
            nativeInit(SU_EXEC_DIR, context.getFilesDir().getAbsolutePath());
        }
    }

    /**
     * Deploy executables via su to /data/local/tmp/wpswpa/.
     * Uses libsu to copy from nativeLibDir (root can read /data/app/).
     * If direct cp fails, falls back to piping through stdin.
     */
    private boolean deployExecutables() {
        Log.i(TAG, "deployExecutables: nativeLibDir=" + nativeLibDir + " target=" + SU_EXEC_DIR);

        // Build cp commands for all executables
        StringBuilder cpCmds = new StringBuilder();
        cpCmds.append("killall libwpa_supplicant_exec.so 2>/dev/null; ");
        cpCmds.append("mkdir -p ").append(SU_EXEC_DIR).append("; ");
        for (String name : EXECUTABLES) {
            cpCmds.append("cp ").append(nativeLibDir).append("/").append(name)
                   .append(" ").append(SU_EXEC_DIR).append("/").append(name).append(" 2>&1; ");
        }
        cpCmds.append("chmod 755 ").append(SU_EXEC_DIR).append("/*; ");
        cpCmds.append("ls -la ").append(SU_EXEC_DIR).append("/");

        Shell.Result result = Shell.cmd(cpCmds.toString()).exec();
        List<String> output = result.getOut();

        boolean hasSupplicant = false;
        for (String line : output) {
            Log.i(TAG, "deploy: " + line);
            if (line.contains("libwpa_supplicant_exec.so") && !line.contains("No such file")) {
                hasSupplicant = true;
            }
        }

        if (hasSupplicant) {
            Log.i(TAG, "deployExecutables: success via cp from nativeLibDir");
            return true;
        }

        Log.w(TAG, "deployExecutables: cp failed, trying stdin pipe fallback");
        return deployViaStdinPipe();
    }

    private boolean deployViaStdinPipe() {
        Shell.cmd("mkdir -p " + SU_EXEC_DIR).exec();

        boolean allOk = true;
        for (String name : EXECUTABLES) {
            File src = new File(nativeLibDir, name);
            if (!src.exists()) {
                Log.w(TAG, "deployViaStdinPipe: source not found: " + src);
                allOk = false;
                continue;
            }
            try {
                // Use ProcessBuilder for stdin pipe (libsu doesn't support binary stdin)
                ProcessBuilder pb = new ProcessBuilder("su");
                pb.redirectErrorStream(true);
                Process su = pb.start();
                OutputStream os = su.getOutputStream();
                os.write(("cat > " + SU_EXEC_DIR + "/" + name + "\n").getBytes());
                os.flush();
                FileInputStream fis = new FileInputStream(src);
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
                fis.close();
                os.close();
                su.waitFor();
                Log.i(TAG, "deployViaStdinPipe: " + name + " (" + src.length() + " bytes)");
            } catch (Exception e) {
                Log.e(TAG, "deployViaStdinPipe: failed for " + name, e);
                allOk = false;
            }
        }

        Shell.Result chmodResult = Shell.cmd(
                "chmod 755 " + SU_EXEC_DIR + "/*",
                "ls -la " + SU_EXEC_DIR + "/"
        ).exec();
        for (String line : chmodResult.getOut()) {
            Log.i(TAG, "deploy-pipe: " + line);
        }

        return allOk;
    }

    public boolean isAvailable() {
        return libraryLoaded;
    }

    // =========================================================================
    // Control interface path helper
    // =========================================================================

    public static String getCtrlDir() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? "/data/vendor/wifi/wpa/wpswpatester/"
                : "/data/misc/wifi/wpswpatester/";
    }

    public static String getCtrlSocketPath(String iface) {
        return getCtrlDir() + iface;
    }

    // =========================================================================
    // Two su shell approach:
    // - Shell 1: wpa_supplicant runs in foreground via "exec", stdout captured
    //   directly by Java through the Process — no files, FIFOs, or tail needed
    // - Shell 2: wpa_cli commands sent through a separate su shell
    // =========================================================================

    /**
     * Start wpa_supplicant as root via su.
     * Uses one su shell for wpa_supplicant (foreground, stdout piped to native)
     * and a separate su shell for wpa_cli commands.
     */
    public long startWpaSupplicant(String iface, String configPath,
                                     String ctrlDir, boolean debugMode) {
        if (!libraryLoaded) {
            Log.e(TAG, "startWpaSupplicant: native library not loaded, cannot proceed");
            return 0;
        }

        if (!deployExecutables()) {
            Log.e(TAG, "startWpaSupplicant: deploy failed");
            return 0;
        }

        String supplicantCmd = String.format(
                "%s/libwpa_supplicant_exec.so %s -Dnl80211,wired -i %s -c%s -K -O%s",
                SU_EXEC_DIR, debugMode ? "-d" : "", iface, configPath, ctrlDir);

        Log.i(TAG, "startWpaSupplicant: " + supplicantCmd);

        // === 0. Disable WiFi to cleanly stop the system wpa_supplicant ===
        Log.i(TAG, "Disabling WiFi to stop system wpa_supplicant...");
        try {
            if (wifiManager != null) {
                wifiManager.setWifiEnabled(false);
                SystemClock.sleep(1000);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot disable WiFi (permission denied): " + e.getMessage());
        }

        try {
            // === 1. Start wpa_supplicant su shell (foreground, captures stdout) ===
            ProcessBuilder wpaPb = new ProcessBuilder("su");
            wpaPb.redirectErrorStream(true);
            wpaSupplicantProcess = wpaPb.start();
            DataOutputStream wpaDos = new DataOutputStream(wpaSupplicantProcess.getOutputStream());

            // Kill any lingering instance of our wpa_supplicant
            wpaDos.writeBytes("killall libwpa_supplicant_exec.so 2>/dev/null\n");

            // Ensure the system wpa_supplicant is truly stopped
            // (setWifiEnabled(false) alone may not kill it on all devices)
            wpaDos.writeBytes("stop wpa_supplicant 2>/dev/null\n");
            wpaDos.writeBytes("stop p2p_supplicant 2>/dev/null\n");

            // Create required directories and remove stale ctrl sockets
            wpaDos.writeBytes("mkdir -p /data/local/tmp 2>/dev/null\n");
            wpaDos.writeBytes("mkdir -p " + ctrlDir + " 2>/dev/null\n");
            wpaDos.writeBytes("rm -f " + ctrlDir + iface + "\n");
            // Remove all stale ctrl sockets in the client tmp dir too
            wpaDos.writeBytes("rm -f /data/local/tmp/wpa_ctrl_*  2>/dev/null\n");
            wpaDos.writeBytes(supplicantCmd + " 2>&1\n");
            wpaDos.flush();

            Log.i(TAG, "wpa_supplicant started in su shell (foreground)");

            // === 2. Pump wpa_supplicant stdout → native pipe ===
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            int readFd = pipe[0].detachFd(); // Native code owns this
            ParcelFileDescriptor writePfd = pipe[1];

            InputStream wpaOut = wpaSupplicantProcess.getInputStream();
            outputPumpThread = new Thread(() -> {
                try (FileOutputStream pipeOut = new FileOutputStream(writePfd.getFileDescriptor())) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = wpaOut.read(buf)) > 0) {
                        Log.d(TAG, "pump: " + new String(buf, 0, Math.min(n, 200)));
                        pipeOut.write(buf, 0, n);
                        pipeOut.flush();
                    }
                    Log.d(TAG, "Output pump: wpa_supplicant exited");
                } catch (IOException e) {
                    Log.d(TAG, "Output pump ended: " + e.getMessage());
                } finally {
                    try { writePfd.close(); } catch (IOException e) { /* ignore */ }
                }
            }, "wpa-output-pump");
            outputPumpThread.setDaemon(true);
            outputPumpThread.start();

            // === 3. Start cli su shell (for wpa_cli commands) ===
            ProcessBuilder cliPb = new ProcessBuilder("su");
            cliPb.redirectErrorStream(true);
            suShell = cliPb.start();
            suStdin = new DataOutputStream(suShell.getOutputStream());

            BufferedReader shellReader = new BufferedReader(
                    new InputStreamReader(suShell.getInputStream()));
            shellReaderThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = shellReader.readLine()) != null) {
                        Log.d(TAG, "shell: " + line);
                        shellOutputQueue.put(line);
                    }
                } catch (IOException e) {
                    Log.d(TAG, "Shell reader ended: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.d(TAG, "Shell reader interrupted");
                }
            }, "su-shell-reader");
            shellReaderThread.setDaemon(true);
            shellReaderThread.start();

            this.sessionCtrlDir = ctrlDir;
            this.sessionIface = iface;

            long handle = nativeCreateSession(readFd, ctrlDir, iface);
            // Native dup()s the fd, so close the Java-side original to avoid leak
            ParcelFileDescriptor.adoptFd(readFd).close();
            if (handle == 0) {
                cleanupAll();
                restoreWifi();
            }
            return handle;

        } catch (Exception e) {
            Log.e(TAG, "startWpaSupplicant failed", e);
            cleanupAll();
            restoreWifi();
            return 0;
        }
    }

    /**
     * Stop wpa_supplicant and clean up everything.
     * Restarts the system wpa_supplicant by re-enabling WiFi.
     */
    public void stopWpaSupplicant(long handle) {
        nativeDestroySession(handle);

        // Exit the cli su shell
        if (suShell != null && suStdin != null) {
            try {
                suStdin.writeBytes("exit\n");
                suStdin.flush();
            } catch (IOException e) {
                Log.d(TAG, "stopWpaSupplicant: shell write error: " + e.getMessage());
            }
        }

        cleanupAll();

        // Kill our wpa_supplicant and restart system service
        Shell.cmd(
                "killall libwpa_supplicant_exec.so 2>/dev/null",
                "start wpa_supplicant 2>/dev/null"
        ).exec();

        restoreWifi();
    }

    private void restoreWifi() {
        Log.i(TAG, "Restoring WiFi...");
        try {
            if (wifiManager != null) {
                wifiManager.setWifiEnabled(true);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot re-enable WiFi (permission denied): " + e.getMessage());
        }
    }

    private void cleanupAll() {
        // Destroy wpa_supplicant process (kills the exec'd wpa_supplicant)
        if (wpaSupplicantProcess != null) {
            wpaSupplicantProcess.destroy();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                wpaSupplicantProcess.destroyForcibly();
            }
            wpaSupplicantProcess = null;
        }
        if (suShell != null) {
            suShell.destroy();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                suShell.destroyForcibly();
            }
            suShell = null;
        }
        if (shellReaderThread != null) {
            shellReaderThread.interrupt();
            shellReaderThread = null;
        }
        if (outputPumpThread != null) {
            outputPumpThread.interrupt();
            outputPumpThread = null;
        }
        suStdin = null;
        shellOutputQueue.clear();
    }

    /**
     * Send WPS_REG command via wpa_cli in the cli su shell.
     * Both su shells run as root and can access the control socket.
     */
    private static final String BSSID_PATTERN = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$";
    // PIN can be numeric (4-8 digits), alphanumeric, or empty — reject shell metacharacters
    private static final String SAFE_PIN_PATTERN = "^[0-9A-Za-z]*$";

    public String wpsReg(String bssid, String pin) {
        if (suStdin == null) {
            Log.e(TAG, "wpsReg: su shell not running");
            return null;
        }

        // Validate inputs before interpolating into a root shell command
        if (bssid == null || !bssid.matches(BSSID_PATTERN)) {
            Log.e(TAG, "wpsReg: invalid BSSID: " + bssid);
            return null;
        }
        if (pin == null || !pin.matches(SAFE_PIN_PATTERN)) {
            Log.e(TAG, "wpsReg: invalid PIN: " + pin);
            return null;
        }

        String cmd = String.format(
                "%s/libwpa_cli_exec.so -p %s -i %s WPS_REG %s %s",
                SU_EXEC_DIR, sessionCtrlDir, sessionIface, bssid, pin);

        // Use unique markers to delimit wpa_cli output
        String marker = "__WPSREG_" + System.nanoTime() + "__";
        String endMarker = "__WPSREG_END_" + System.nanoTime() + "__";

        Log.i(TAG, "wpsReg: " + cmd);

        try {
            // Drain any pending output from the queue
            shellOutputQueue.clear();

            suStdin.writeBytes("echo " + marker + "\n");
            suStdin.writeBytes(cmd + "\n");
            suStdin.writeBytes("echo " + endMarker + "\n");
            suStdin.flush();

            // Read response between markers
            StringBuilder sb = new StringBuilder();
            boolean inResponse = false;
            long deadline = System.currentTimeMillis() + 10000;

            while (System.currentTimeMillis() < deadline) {
                String line = shellOutputQueue.poll(500, TimeUnit.MILLISECONDS);
                if (line == null) continue;
                if (line.contains(endMarker)) break;
                if (line.contains(marker)) {
                    inResponse = true;
                    continue;
                }
                if (inResponse) {
                    sb.append(line).append("\n");
                }
            }

            String response = sb.toString().trim();
            Log.i(TAG, "wpsReg response: " + response);
            return response;

        } catch (Exception e) {
            Log.e(TAG, "wpsReg failed", e);
            return null;
        }
    }

    // =========================================================================
    // Native methods
    // =========================================================================

    private native void nativeInit(String execDir, String filesDir);

    private native long nativeCreateSession(int stdoutFd, String ctrlDir, String iface);

    private native void nativeDestroySession(long handle);

    public native WpsResult readWpsResult(long handle, int timeoutMs);

    public native String[] extractPixieDustParams(long handle, int timeoutMs);

    public native String computePixiePin(String pke, String pkr,
                                          String eHash1, String eHash2,
                                          String authKey, String eNonce,
                                          boolean force);
}
