package org.javity.platform;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class PlatformServicesTest {
  @Test
  public void completionsWaitForOwnerPumpAndSaveUsesCas() {
    PlatformServices api = new PlatformServices(new LocalPlatformProvider());
    AtomicInteger calls = new AtomicInteger();
    api.putSave(
        "slot",
        "{\"n\":1}",
        0,
        r -> {
          assertTrue(r.isSuccess());
          calls.incrementAndGet();
        });
    assertEquals(0, calls.get());
    api.getSave(
        "empty",
        r -> {
          assertTrue(r.isSuccess());
          assertNull(r.value());
          calls.incrementAndGet();
        });
    assertEquals(2, api.pump());
    assertEquals(2, calls.get());
    api.putSave(
        "slot",
        "{\"n\":2}",
        0,
        r -> {
          assertEquals(PlatformResult.ErrorCode.CONFLICT, r.errorCode());
          calls.incrementAndGet();
        });
    api.pump();
    assertEquals(3, calls.get());
    api.close();
  }

  @Test
  public void localRoomShapesMatchPlatformContractAndJoiningReportsUnsupported() {
    PlatformServices api = new PlatformServices(new LocalPlatformProvider());
    AtomicInteger calls = new AtomicInteger();
    api.createRoom(
        new PlatformProvider.RoomRequest("lobby", false, false, 4),
        r -> {
          assertTrue(r.isSuccess());
          assertEquals("lobby", r.value().mode());
          calls.incrementAndGet();
        });
    api.pump();
    assertEquals(1, calls.get());
    api.listRooms(
        r -> {
          assertEquals(1, r.value().items().size());
          calls.incrementAndGet();
        });
    api.pump();
    api.joinRoom(
        "local-room-1",
        null,
        r -> {
          assertEquals(PlatformResult.ErrorCode.UNSUPPORTED, r.errorCode());
          calls.incrementAndGet();
        });
    api.pump();
    assertEquals(3, calls.get());
    api.close();
  }

  @Test
  public void savePayloadIsOneCanonicalJsonValueAndCannotEscapeRequestEnvelope() {
    PlatformServices api = new PlatformServices(new LocalPlatformProvider());
    AtomicInteger errors = new AtomicInteger();
    api.putSave(
        "safe",
        "null,\"slot\":\"escaped\"",
        0,
        r -> {
          assertEquals(PlatformResult.ErrorCode.INVALID_ARGUMENT, r.errorCode());
          errors.incrementAndGet();
        });
    api.pump();
    assertEquals(1, errors.get());
    api.getSave(
        "safe",
        r -> {
          assertTrue(r.isSuccess());
          assertNull(r.value());
        });
    api.getSave(
        "escaped",
        r -> {
          assertTrue(r.isSuccess());
          assertNull(r.value());
        });
    api.pump();
    String[] payloads = {"{ \"level\" : 2 }", "[1,true,\"x\"]", "null", "\"plain text\""};
    String[] expected = {"{\"level\":2}", "[1,true,\"x\"]", "null", "\"plain text\""};
    for (int i = 0; i < payloads.length; i++) {
      String slot = "json-" + i;
      String serialized = expected[i];
      api.putSave(
          slot,
          payloads[i],
          0,
          r -> {
            assertTrue(r.isSuccess());
            assertEquals(serialized, r.value().payload());
          });
    }
    api.pump();
    api.close();
  }

  @Test
  public void cancellationSuppressesAlreadyQueuedAndLateCallbacks() {
    ManualProvider provider = new ManualProvider();
    PlatformServices api = new PlatformServices(provider);
    AtomicInteger calls = new AtomicInteger();
    PlatformProvider.Operation op = api.player(r -> calls.incrementAndGet());
    provider.callback.complete(PlatformResult.success(new PlatformProvider.Player("p", "P")));
    op.cancel();
    assertEquals(1, api.pump());
    assertEquals(0, calls.get());
    PlatformProvider.Operation late = api.player(r -> calls.incrementAndGet());
    late.cancel();
    provider.callback.complete(PlatformResult.success(new PlatformProvider.Player("late", "Late")));
    api.pump();
    assertEquals(0, calls.get());
    api.close();
  }

  @Test
  public void closeDisposesProviderAndDropsQueuedDelivery() {
    ManualProvider provider = new ManualProvider();
    PlatformServices api = new PlatformServices(provider);
    AtomicInteger calls = new AtomicInteger();
    api.player(r -> calls.incrementAndGet());
    provider.callback.complete(PlatformResult.success(new PlatformProvider.Player("p", "P")));
    api.close();
    assertEquals(0, api.pump());
    assertEquals(0, calls.get());
    assertTrue(provider.closed);
  }

  @Test
  public void providerCallbackFromWorkerIsDeliveredOnlyByOwnerPumpAndThrowsBecomeErrors()
      throws Exception {
    ManualProvider provider = new ManualProvider();
    PlatformServices api = new PlatformServices(provider);
    Thread owner = Thread.currentThread();
    AtomicInteger calls = new AtomicInteger();
    api.player(
        r -> {
          assertSame(owner, Thread.currentThread());
          calls.incrementAndGet();
        });
    Thread worker =
        new Thread(
            () ->
                provider.callback.complete(
                    PlatformResult.success(new PlatformProvider.Player("p", "P"))));
    worker.start();
    worker.join();
    assertEquals(0, calls.get());
    api.pump();
    assertEquals(1, calls.get());
    provider.throwOnPlayer = true;
    api.player(
        r -> {
          assertEquals(PlatformResult.ErrorCode.REQUEST_FAILED, r.errorCode());
          calls.incrementAndGet();
        });
    api.pump();
    assertEquals(2, calls.get());
    api.close();
  }

  @Test
  public void duplicateAndNullProviderResultsAreHandledOnceAndExplicitly() {
    ManualProvider provider = new ManualProvider();
    PlatformServices api = new PlatformServices(provider);
    AtomicInteger calls = new AtomicInteger();
    api.player(
        r -> {
          assertTrue(r.isSuccess());
          calls.incrementAndGet();
        });
    PlatformResult<PlatformProvider.Player> first =
        PlatformResult.success(new PlatformProvider.Player("p", "P"));
    provider.callback.complete(first);
    provider.callback.complete(
        PlatformResult.success(new PlatformProvider.Player("other", "Other")));
    api.pump();
    assertEquals(1, calls.get());
    api.player(
        r -> {
          assertEquals(PlatformResult.ErrorCode.REQUEST_FAILED, r.errorCode());
          calls.incrementAndGet();
        });
    provider.callback.complete(null);
    api.pump();
    assertEquals(2, calls.get());
    api.close();
  }

  @Test
  public void nullProviderOperationBecomesFailure() {
    ManualProvider provider = new ManualProvider();
    provider.nullOperation = true;
    PlatformServices api = new PlatformServices(provider);
    AtomicInteger calls = new AtomicInteger();
    api.player(
        r -> {
          assertEquals(PlatformResult.ErrorCode.REQUEST_FAILED, r.errorCode());
          calls.incrementAndGet();
        });
    api.pump();
    assertEquals(1, calls.get());
    api.close();
  }

  @Test
  public void pumpOnlyProcessesItsInitialQueueSnapshot() {
    ManualProvider provider = new ManualProvider();
    PlatformServices api = new PlatformServices(provider);
    AtomicInteger calls = new AtomicInteger();
    api.player(
        r -> {
          calls.incrementAndGet();
          api.player(next -> calls.incrementAndGet());
        });
    provider.callback.complete(PlatformResult.success(new PlatformProvider.Player("p", "P")));
    assertEquals(1, api.pump());
    assertEquals(1, calls.get());
    provider.callback.complete(PlatformResult.success(new PlatformProvider.Player("p2", "P2")));
    assertEquals(1, api.pump());
    assertEquals(2, calls.get());
    api.close();
  }

  @Test
  public void closeContinuesCleanupAndPreservesFirstFailureWithoutSelfSuppression() {
    ManualProvider provider = new ManualProvider();
    RuntimeException failure = new RuntimeException("cancel/close fault");
    provider.cancelFailure = failure;
    provider.closeFailure = failure;
    PlatformServices api = new PlatformServices(provider);
    api.player(r -> fail("queued callback must be discarded"));
    api.player(r -> fail("queued callback must be discarded"));
    try {
      api.close();
      fail("expected cleanup failure");
    } catch (RuntimeException actual) {
      assertSame(failure, actual);
    }
    assertEquals(2, provider.cancelCalls);
    assertTrue(provider.closed);
    assertEquals(0, failure.getSuppressed().length);
    assertEquals(0, api.pump());
  }

  @Test
  public void cancellationIsOwnerThreadOnly() throws Exception {
    PlatformServices api = new PlatformServices(new ManualProvider());
    PlatformProvider.Operation operation = api.player(r -> {});
    AtomicInteger rejected = new AtomicInteger();
    Thread worker =
        new Thread(
            () -> {
              try {
                operation.cancel();
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
            });
    worker.start();
    worker.join();
    assertEquals(1, rejected.get());
    api.close();
  }

  private static final class ManualProvider implements PlatformProvider {
    Callback<Player> callback;
    boolean closed, throwOnPlayer, nullOperation;
    int cancelCalls;
    RuntimeException cancelFailure, closeFailure;

    public Capabilities capabilities() {
      return new Capabilities(true, true, true, true);
    }

    public Operation player(Callback<Player> cb) {
      if (throwOnPlayer) throw new IllegalStateException("provider fault");
      callback = cb;
      if (nullOperation) return null;
      return () -> {
        cancelCalls++;
        if (cancelFailure != null) throw cancelFailure;
      };
    }

    public Operation profile(Callback<Profile> cb) {
      return () -> {};
    }

    public Operation getSave(String s, Callback<Save> cb) {
      return () -> {};
    }

    public Operation putSave(String s, String p, long r, Callback<Save> cb) {
      return () -> {};
    }

    public Operation listRooms(Callback<RoomList> cb) {
      return () -> {};
    }

    public Operation createRoom(RoomRequest r, Callback<Room> cb) {
      return () -> {};
    }

    public Operation joinRoom(String id, String inviteCode, Callback<RoomTicket> cb) {
      return () -> {};
    }

    public Operation closeRoom(String id, Callback<RoomCloseResult> cb) {
      return () -> {};
    }

    public void close() {
      closed = true;
      if (closeFailure != null) throw closeFailure;
    }
  }
}
