package sangiorgi.wps.lib.services;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import sangiorgi.wps.lib.utils.PinUtils;

public class PinValidationServiceTest {

  private PinUtils mockPinUtils;
  private PinValidationService service;

  @Before
  public void setUp() {
    mockPinUtils = mock(PinUtils.class);
    service = new PinValidationService(mockPinUtils);
  }

  @Test
  public void testIsPinAlreadyTestedDelegates() {
    when(mockPinUtils.isPinAlreadyTested("AA:BB:CC:DD:EE:FF", "12345670")).thenReturn(true);

    assertTrue(service.isPinAlreadyTested("AA:BB:CC:DD:EE:FF", "12345670"));
    verify(mockPinUtils).isPinAlreadyTested("AA:BB:CC:DD:EE:FF", "12345670");
  }

  @Test
  public void testIsPinAlreadyTestedReturnsFalse() {
    when(mockPinUtils.isPinAlreadyTested(anyString(), anyString())).thenReturn(false);

    assertFalse(service.isPinAlreadyTested("AA:BB:CC:DD:EE:FF", "12345670"));
  }

  @Test
  public void testIsPinAlreadyTestedWithNullPin() {
    assertFalse(service.isPinAlreadyTested("AA:BB:CC:DD:EE:FF", null));
    verify(mockPinUtils, never()).isPinAlreadyTested(anyString(), anyString());
  }

  @Test
  public void testIsPinAlreadyTestedWithShortPin() {
    assertFalse(service.isPinAlreadyTested("AA:BB:CC:DD:EE:FF", "1234"));
    verify(mockPinUtils, never()).isPinAlreadyTested(anyString(), anyString());
  }

  @Test
  public void testIsPinAlreadyTestedTruncatesToEightDigits() {
    // PIN longer than 8 chars should be truncated to first 8
    service.isPinAlreadyTested("AA:BB:CC:DD:EE:FF", "1234567890");
    verify(mockPinUtils).isPinAlreadyTested("AA:BB:CC:DD:EE:FF", "12345678");
  }

  @Test
  public void testStorePinResult() {
    service.storePinResult("AA:BB:CC:DD:EE:FF", "12345670", true);
    verify(mockPinUtils).storePinResult("AA:BB:CC:DD:EE:FF", "12345670", true, null);
  }

  @Test
  public void testStorePinResultFailure() {
    service.storePinResult("AA:BB:CC:DD:EE:FF", "12345670", false);
    verify(mockPinUtils).storePinResult("AA:BB:CC:DD:EE:FF", "12345670", false, null);
  }

  @Test
  public void testStoreFirstHalfCorrect() {
    service.storeFirstHalfCorrect("AA:BB:CC:DD:EE:FF", "12345670");
    verify(mockPinUtils).storePinResult("AA:BB:CC:DD:EE:FF", "12345670", false, "last_three");
  }
}
