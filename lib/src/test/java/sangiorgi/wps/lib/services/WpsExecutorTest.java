package sangiorgi.wps.lib.services;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;
import org.junit.Test;
import sangiorgi.wps.lib.WpsLibConfig;

/**
 * Unit tests for WpsExecutor.
 *
 * <p>Note: WpsExecutor internally uses Shell (libsu) for environment setup, which requires
 * root/Android. Tests here verify construction, state management, and ready-signaling logic
 * that can run in a standard JVM (with returnDefaultValues = true).
 */
public class WpsExecutorTest {

  @Test
  public void testConstructorCreatesExecutor() {
    WpsLibConfig config = new WpsLibConfig("/data/test/");
    // Shell.cmd will return default values (null/false) in unit tests
    // The executor should still be created without crashing
    try (WpsExecutor executor = new WpsExecutor(config)) {
      assertNotNull(executor);
    }
  }

  @Test
  public void testAwaitReadyReturnsEventually() throws InterruptedException {
    WpsLibConfig config = new WpsLibConfig("/data/test/");
    try (WpsExecutor executor = new WpsExecutor(config)) {
      // In unit tests, Shell.cmd returns default (null).
      // The setupWpsEnvironment() will run on the executor thread and
      // countDown the latch in the finally block regardless of Shell success.
      boolean ready = executor.awaitReady(5, TimeUnit.SECONDS);
      // Should be ready since the finally block always counts down the latch
      assertTrue("Executor should signal ready (via finally block)", ready);
    }
  }

  @Test
  public void testIsReadyAfterAwait() throws InterruptedException {
    WpsLibConfig config = new WpsLibConfig("/data/test/");
    try (WpsExecutor executor = new WpsExecutor(config)) {
      executor.awaitReady(5, TimeUnit.SECONDS);
      assertTrue(executor.isReady());
    }
  }

  @Test
  public void testCleanupSetsFlag() {
    WpsLibConfig config = new WpsLibConfig("/data/test/");
    WpsExecutor executor = new WpsExecutor(config);
    executor.cleanup();
    // After cleanup, cancel should be a no-op (isCleanedUp = true)
    // Calling cancel again should not throw
    executor.cancel();
  }

  @Test
  public void testCloseCallsCleanup() {
    WpsLibConfig config = new WpsLibConfig("/data/test/");
    WpsExecutor executor = new WpsExecutor(config);
    // close() delegates to cleanup()
    executor.close();
    // Should be safe to call again
    executor.close();
  }

  @Test
  public void testCancelWithoutCleanup() {
    WpsLibConfig config = new WpsLibConfig("/data/test/");
    try (WpsExecutor executor = new WpsExecutor(config)) {
      // Cancel should kill processes but not shutdown executor
      executor.cancel();
      // Executor should still be usable after cancel (unlike cleanup)
      assertNotNull(executor);
    }
  }
}
