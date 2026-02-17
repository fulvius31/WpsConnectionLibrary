package sangiorgi.wps.lib.models;

import static org.junit.Assert.*;

import org.junit.Test;

public class NetworkToTestTest {

  @Test
  public void testConstructorWithParams() {
    String[] pins = {"12345670", "00000000"};
    NetworkToTest network = new NetworkToTest("AA:BB:CC:DD:EE:FF", "TestNetwork", pins);

    assertEquals("AA:BB:CC:DD:EE:FF", network.getBssid());
    assertEquals("TestNetwork", network.getSsid());
    assertArrayEquals(pins, network.getPins());
    assertNull(network.getPassword());
  }

  @Test
  public void testDefaultConstructor() {
    NetworkToTest network = new NetworkToTest();
    assertNull(network.getBssid());
    assertNull(network.getSsid());
    assertNull(network.getPins());
    assertNull(network.getPassword());
  }

  @Test
  public void testSetBssid() {
    NetworkToTest network = new NetworkToTest();
    network.setBssid("11:22:33:44:55:66");
    assertEquals("11:22:33:44:55:66", network.getBssid());
  }

  @Test
  public void testSetSsid() {
    NetworkToTest network = new NetworkToTest();
    network.setSsid("MyNetwork");
    assertEquals("MyNetwork", network.getSsid());
  }

  @Test
  public void testSetPins() {
    NetworkToTest network = new NetworkToTest();
    String[] pins = {"12345670"};
    network.setPins(pins);
    assertArrayEquals(pins, network.getPins());
  }

  @Test
  public void testSetPassword() {
    NetworkToTest network = new NetworkToTest();
    network.setPassword("SecretPassword");
    assertEquals("SecretPassword", network.getPassword());
  }

  @Test
  public void testToString() {
    String[] pins = {"12345670"};
    NetworkToTest network = new NetworkToTest("AA:BB:CC:DD:EE:FF", "TestNet", pins);
    String str = network.toString();

    assertTrue(str.contains("AA:BB:CC:DD:EE:FF"));
    assertTrue(str.contains("TestNet"));
    assertTrue(str.contains("12345670"));
  }

  @Test
  public void testEmptyPinsArray() {
    NetworkToTest network = new NetworkToTest("AA:BB:CC:DD:EE:FF", "TestNet", new String[] {});
    assertNotNull(network.getPins());
    assertEquals(0, network.getPins().length);
  }

  @Test
  public void testPasswordInitiallyNull() {
    NetworkToTest network =
        new NetworkToTest("AA:BB:CC:DD:EE:FF", "TestNet", new String[] {"12345670"});
    assertNull(network.getPassword());
  }
}
