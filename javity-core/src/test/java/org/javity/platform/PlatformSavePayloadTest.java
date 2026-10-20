package org.javity.platform;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlatformSavePayloadTest {
  @Test
  public void canonicalizesOneJsonValue() {
    assertEquals("{\"level\":2}", PlatformSavePayload.canonicalize("{ \"level\" : 2 }"));
    assertEquals("[1,true]", PlatformSavePayload.canonicalize("[1,true]"));
    assertEquals("null", PlatformSavePayload.canonicalize("null"));
    assertEquals("\"text\"", PlatformSavePayload.canonicalize("\"text\""));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsMalformedJson() {
    PlatformSavePayload.canonicalize("{not-json}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsEnvelopeFieldInjection() {
    PlatformSavePayload.canonicalize("null,\"slot\":\"other\"");
  }
}
