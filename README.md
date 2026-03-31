# WpsConnectionLibrary

An Android library for WiFi WPS (WiFi Protected Setup) security testing. Provides a clean, asynchronous API for PIN testing, brute force, Belkin-specific attacks, and Pixie Dust vulnerability exploitation.

[![](https://jitpack.io/v/fulvius31/WpsConnectionLibrary.svg)](https://jitpack.io/#fulvius31/WpsConnectionLibrary)

## Requirements

- Android API 24+ (Android 7.0 Nougat)
- Rooted device (uses `libsu` for root shell commands)
- WPS-enabled target network

## Installation

### Gradle (Kotlin DSL)

Add the JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.fulvius31:WpsConnectionLibrary:v1.0.0")
}
```

### Gradle (Groovy)

Add the JitPack repository to your `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your module's `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.fulvius31:WpsConnectionLibrary:v1.0.0'
}
```

## Quick Start

```java
// 1. Create manager
WpsConnectionManager manager = new WpsConnectionManager(context);

// 2. Initialize (extracts assets from APK to app files dir — call once)
manager.initialize();
manager.awaitReady();

// 3. Test PINs
String[] pins = {"12345670", "00000000", "11111111"};
manager.testPins("AA:BB:CC:DD:EE:FF", "TargetNetwork", pins, callback);

// 4. Cleanup when done
manager.cleanup();   // Stop active operation
manager.shutdown();  // Full shutdown (release thread pool)
```

## API Reference

### WpsConnectionManager

The main entry point. Create once as a singleton and reuse across connections.

```java
WpsConnectionManager(Context context)
```

#### Initialization

| Method | Description |
|--------|-------------|
| `CompletableFuture<Boolean> initialize()` | Extract required assets from the APK's `assets/` folder to the app's files directory. Call once at app startup. |
| `void awaitReady()` | Block until the executor environment is ready. Can be called eagerly to pre-warm the thread pool. |

#### Connection Methods

All connection methods return `CompletableFuture<Void>` and report progress through the `ConnectionUpdateCallback`.

| Method | Description |
|--------|-------------|
| `testPins(bssid, ssid, pins, callback)` | Test an array of WPS PINs. Database PINs matching the BSSID are automatically prepended. |
| `testBelkinPin(bssid, ssid, callback)` | Generate and test Belkin-specific PINs derived from the BSSID. |
| `bruteForce(bssid, ssid, delayMs, callback)` | Brute force all WPS PINs. `delayMs` controls the pause between attempts. |
| `pixieDust(bssid, ssid, callback)` | Execute a Pixie Dust attack to exploit the WPS key exchange vulnerability. |

#### PIN Database Auto-Lookup

When you call `testPins()`, the library automatically queries `pin.db` for known default PINs matching the target's BSSID. It extracts the MAC prefix (first 3 octets, e.g. `"AA:BB:CC:DD:EE:FF"` becomes `"aabbcc"`), looks up the `pins` table, and prepends any matches (up to 8) before the PINs you provided. Duplicates are removed.

This means database PINs are always tested first, giving the best chance of a quick match without the caller needing to query the database manually.

You can also query the database directly via `PinDatabaseService`:

```java
PinDatabaseService pinDb = new PinDatabaseService(context);
List<String> knownPins = pinDb.getPinsByMac("AA:BB:CC:DD:EE:FF");
// Returns e.g. ["12345670", "00000000"] — known defaults for this vendor
```

The `pin.db` SQLite database has a single table:

| Table | Column | Type | Description |
|-------|--------|------|-------------|
| `pins` | `MAC` | TEXT | Lowercase 6-char MAC prefix (e.g. `"aabbcc"`) |
| `pins` | `pin` | TEXT | 8-digit WPS PIN (e.g. `"12345670"`) |

#### Lifecycle

| Method | Description |
|--------|-------------|
| `void cancel()` | Kill all running WPS processes immediately. |
| `void cleanup()` | Stop the active operation without killing processes. |
| `void shutdown()` | Full shutdown — cleanup and close the executor thread pool. Call only when the manager is no longer needed. |

### ConnectionUpdateCallback

Implement this interface to receive real-time progress updates.

```java
public interface ConnectionUpdateCallback {

    // Error type constants
    int TYPE_LOCKED = 0;                    // WPS is locked on the router
    int TYPE_SELINUX = 1;                   // SELinux blocking execution
    int TYPE_PIXIE_DUST_NOT_COMPATIBLE = 3; // Router not vulnerable to Pixie Dust

    // Called when a connection test session starts
    void create(String title, String message, int progress);

    // Called with status messages during testing
    void updateMessage(String message);

    // Called to increment the tested PIN count
    void updateCount(int increment);

    // Called on failure with an error type constant
    void error(String message, int type);

    // Called when a PIN is found and the connection succeeds
    void success(NetworkToTest networkToTest, boolean isRoot);

    // Pixie Dust specific — called when the attack discovers a PIN
    default void onPixieDustSuccess(String pin, String password) {}

    // Pixie Dust specific — includes full WPS exchange log (hexdump lines)
    default void onPixieDustSuccess(String pin, String password, String wpsExchangeLog) {
        onPixieDustSuccess(pin, password);
    }

    // Pixie Dust specific — called when the attack fails
    default void onPixieDustFailure(String error) {}
}
```

### NetworkToTest

Data model passed to the `success` callback.

| Field | Type | Description |
|-------|------|-------------|
| `bssid` | `String` | MAC address of the target network |
| `ssid` | `String` | Name of the target network |
| `pins` | `String[]` | PINs that were tested |
| `password` | `String` | WiFi password extracted on success |
| `wpsExchangeLog` | `String` | WPS handshake log (available for Pixie Dust results) |

## Usage Examples

### Standard PIN Testing

```java
ConnectionUpdateCallback callback = new ConnectionUpdateCallback() {
    @Override
    public void create(String title, String message, int progress) {
        Log.d("WPS", "Starting: " + message + " (" + progress + " PINs)");
    }

    @Override
    public void updateMessage(String message) {
        Log.d("WPS", message);
    }

    @Override
    public void updateCount(int increment) {
        // Update progress UI
    }

    @Override
    public void error(String message, int type) {
        if (type == TYPE_LOCKED) {
            Log.e("WPS", "Router locked WPS!");
        } else {
            Log.e("WPS", "Error: " + message);
        }
    }

    @Override
    public void success(NetworkToTest network, boolean isRoot) {
        Log.i("WPS", "Password found: " + network.getPassword());
    }
};

String[] pins = {"12345670", "00000000", "11111111"};
manager.testPins("AA:BB:CC:DD:EE:FF", "MyNetwork", pins, callback);
```

### Pixie Dust Attack

```java
ConnectionUpdateCallback callback = new ConnectionUpdateCallback() {
    // ... implement required methods ...

    @Override
    public void onPixieDustSuccess(String pin, String password) {
        Log.i("WPS", "PIN discovered: " + pin);
        Log.i("WPS", "Password: " + password);
    }

    @Override
    public void onPixieDustFailure(String error) {
        Log.e("WPS", "Pixie Dust failed: " + error);
    }
};

manager.pixieDust("AA:BB:CC:DD:EE:FF", "MyNetwork", callback);
```

### Brute Force

```java
// Test all PINs with a 1-second delay between attempts
manager.bruteForce("AA:BB:CC:DD:EE:FF", "MyNetwork", 1000, callback);
```

> WPS uses two-phase PIN verification (4 digits + 3 digits + 1 checksum digit), so the effective search space is ~11,000 attempts, not 100 million.

### Integration with Hilt

```kotlin
// Dagger/Hilt module
@Module
@InstallIn(SingletonComponent::class)
object WpsModule {
    @Provides
    @Singleton
    fun provideWpsConnectionManager(
        @ApplicationContext context: Context,
    ): WpsConnectionManager {
        return WpsConnectionManager(context)
    }
}

// ViewModel
@HiltViewModel
class WpsViewModel @Inject constructor(
    private val wpsManager: WpsConnectionManager,
) : ViewModel(), ConnectionUpdateCallback {

    fun connect(bssid: String, ssid: String, pins: Array<String>) {
        viewModelScope.launch {
            wpsManager.testPins(bssid, ssid, pins, this@WpsViewModel)
        }
    }

    override fun success(networkToTest: NetworkToTest, isRoot: Boolean) {
        // Handle success — networkToTest.password contains the WiFi password
    }

    // ... implement other callback methods ...

    override fun onCleared() {
        super.onCleared()
        wpsManager.cleanup()
    }
}
```

## Architecture

```
WpsConnectionManager          Entry point, singleton
  |
  +-- ConnectionService        Orchestrates attack strategies
  |     +-- ConnectionHandler  Sequential PIN testing with state tracking
  |     +-- PixieDustExecutor  Pixie Dust attack workflow
  |
  +-- WpsExecutor              Async command execution, thread pool
  |     +-- WpaSupplicantSession   Background wpa_supplicant process
  |
  +-- Command layer            Shell command builders
  |     +-- WpaCliCommand          Standard wpa_cli commands
  |     +-- GlobalControlCommand   Android P+ control interface
  |     +-- PixieDustCommand       Pixie Dust binary invocation
  |
  +-- Services
  |     +-- PinValidationService   Skip already-tested PINs
  |     +-- PinDatabaseService     Lookup known default PINs by MAC
  |     +-- ConnectionStateManager WiFi state and result tracking
  |
  +-- Assets
        +-- WpaToolsInitializer    Extract bundled binaries
        +-- AssetExtractor         Low-level APK asset copying
```

### API Version Handling

The library automatically selects the WPS method based on Android API level:

- **API 28+ (Android P):** Uses `wpa_supplicant` with `wpa_cli` via global control interface
- **Below API 28:** Uses direct `wpa_cli` commands

Architecture (32-bit vs 64-bit) is detected automatically and the correct binaries are used.

**Supported Architectures:** `armeabi-v7a`, `arm64-v8a`, `x86_64`.

### Required Assets

With the NDK migration, executables and shared libraries are bundled as native libraries in the library APK.  
The consuming app only needs to include:

| Asset | Target name | Purpose |
|-------|-------------|---------|
| `pin.db` | `pin.db` | SQLite database of known default PINs |

At initialization, `pin.db` is extracted from `assets/` to the app files directory.

## Building from Source

```bash
git clone https://github.com/fulvius31/WpsConnectionLibrary.git
cd WpsConnectionLibrary
./gradlew :lib:build
```

### Run Tests

```bash
./gradlew :lib:test
```

### Publish to Local Maven

```bash
./gradlew :lib:publishToMavenLocal
```

The artifact is published as `com.github.fulvius31:WpsConnectionLibrary:1.0.0`.

## CI/CD

- **CI workflow** runs on every push to `main` and on pull requests: lint, unit tests, and compile check.
- **Release workflow** runs when a version tag (`v*`) is pushed: runs tests, verifies `publishToMavenLocal`, and creates a GitHub Release. JitPack automatically picks up the new tag.

To create a release:

```bash
git tag v1.0.1
git push origin v1.0.1
```

## License

AGPL v3.0
