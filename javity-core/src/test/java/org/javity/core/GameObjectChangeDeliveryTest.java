package org.javity.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;

public class GameObjectChangeDeliveryTest {
  @Test
  public void firstListenerFailureStillDeliversTheCommittedAddition() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("addition");
      List<String> calls = new ArrayList<>();
      object.addComponent(recording(calls, "component"));
      game.start();
      assertTrue(game.getObjectsWith(Marker.class).isEmpty());
      RuntimeException failure = new RuntimeException("listener");
      object.onComponentChange(
          change -> {
            calls.add("first");
            throw failure;
          });
      object.onComponentChange(change -> calls.add("second"));
      Marker marker = new Marker();

      assertSame(failure, assertThrows(RuntimeException.class, () -> object.addComponent(marker)));

      assertEquals(List.of("first", "second", "component"), calls);
      assertTrue(object.containsComponent(marker));
      assertSame(object, marker.getGameObject());
      assertEquals(List.of(object), game.getObjectsWith(Marker.class));
    }
  }

  @Test
  public void componentFailuresPreserveTheFirstErrorAndDeliverToLaterComponents() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("components");
      List<String> calls = new ArrayList<>();
      AssertionError first = new AssertionError("first component");
      RuntimeException second = new RuntimeException("second component");
      object.addComponent(throwing(calls, "first", first));
      object.addComponent(throwing(calls, "second", second));
      object.addComponent(recording(calls, "last"));
      game.start();
      object.onComponentChange(change -> calls.add("listener"));

      assertSame(
          first, assertThrows(AssertionError.class, () -> object.addComponent(new Marker())));

      assertEquals(List.of("listener", "first", "second", "last"), calls);
      assertArrayEquals(new Throwable[] {second}, first.getSuppressed());
    }
  }

  @Test
  public void repeatedThrowableAcrossListenersAndComponentsIsNotSelfSuppressed() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("repeated");
      List<String> calls = new ArrayList<>();
      AssertionError shared = new AssertionError("shared");
      object.addComponent(throwing(calls, "component", shared));
      object.addComponent(recording(calls, "last"));
      game.start();
      object.onComponentChange(
          change -> {
            calls.add("first");
            throw shared;
          });
      object.onComponentChange(
          change -> {
            calls.add("second");
            throw shared;
          });

      assertSame(
          shared, assertThrows(AssertionError.class, () -> object.addComponent(new Marker())));

      assertEquals(List.of("first", "second", "component", "last"), calls);
      assertEquals(0, shared.getSuppressed().length);
    }
  }

  @Test
  public void failedRemovalNotificationLeavesMembershipAndIndexCommitted() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("removal");
      List<String> calls = new ArrayList<>();
      Marker marker = object.addComponent(new Marker());
      object.addComponent(recording(calls, "component"));
      game.start();
      assertEquals(List.of(object), game.getObjectsWith(Marker.class));
      RuntimeException first = new RuntimeException("listener");
      AssertionError second = new AssertionError("later listener");
      object.onComponentChange(
          change -> {
            calls.add("first");
            throw first;
          });
      object.onComponentChange(
          change -> {
            calls.add("second");
            throw second;
          });

      assertSame(first, assertThrows(RuntimeException.class, () -> object.removeComponent(marker)));

      assertEquals(List.of("first", "second", "component"), calls);
      assertArrayEquals(new Throwable[] {second}, first.getSuppressed());
      assertFalse(object.containsComponent(marker));
      assertFalse(marker.isInitialized());
      assertTrue(game.getObjectsWith(Marker.class).isEmpty());
      assertFalse(object.removeComponent(marker));
    }
  }

  @Test
  public void unsubscribedListenersAreSkippedAndNewListenersWaitForTheNextChange() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("listeners");
      List<String> calls = new ArrayList<>();
      Marker firstChange = new Marker();
      Runnable[] unsubscribe = new Runnable[1];
      object.onComponentChange(
          change -> {
            calls.add("first");
            if (change.component() == firstChange) {
              unsubscribe[0].run();
              object.onComponentChange(next -> calls.add("new"));
            }
          });
      unsubscribe[0] = object.onComponentChange(change -> calls.add("removed"));
      object.onComponentChange(change -> calls.add("last"));

      object.addComponent(firstChange);
      assertEquals(List.of("first", "last"), calls);
      calls.clear();
      object.addComponent(new Marker());
      assertEquals(List.of("first", "last", "new"), calls);
    }
  }

  @Test
  public void cancellingOneDuplicateRegistrationDoesNotCancelItsSibling() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("duplicates");
      List<String> calls = new ArrayList<>();
      Runnable[] unsubscribe = new Runnable[1];
      object.onComponentChange(change -> unsubscribe[0].run());
      Consumer<ComponentChange> listener = change -> calls.add("duplicate");
      object.onComponentChange(listener);
      unsubscribe[0] = object.onComponentChange(listener);

      object.addComponent(new Marker());
      assertEquals(List.of("duplicate"), calls);
      calls.clear();
      object.addComponent(new Marker());
      assertEquals(List.of("duplicate"), calls);
    }
  }

  @Test
  public void reentrantChangesUseTheirOwnSnapshotsAndRecheckComponentMembership() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("reentrant");
      List<String> calls = new ArrayList<>();
      Component removed = object.addComponent(recording(calls, "removed"));
      object.addComponent(recording(calls, "survivor"));
      game.start();
      Marker outer = new Marker();
      Component added = recording(calls, "added");
      object.onComponentChange(
          change -> {
            if (change.component() == outer) {
              calls.add("outer listener");
              object.removeComponent(removed);
              object.addComponent(added);
              game.initializePendingComponents();
            }
          });

      object.addComponent(outer);

      assertEquals(List.of("outer listener", "survivor", "survivor", "survivor"), calls);
      assertFalse(object.containsComponent(removed));
      assertTrue(added.isInitialized());
      calls.clear();
      object.addComponent(new Marker());
      assertEquals(List.of("survivor", "added"), calls);
    }
  }

  @Test
  public void initializingPendingComponentsDuringDeliveryDoesNotMakeThemCurrentRecipients() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("pending recipients");
      List<String> calls = new ArrayList<>();
      object.addComponent(recording(calls, "initialized"));
      game.start();
      Component pending = object.addComponent(recording(calls, "pending"));
      calls.clear();
      Component added = recording(calls, "added");
      object.onComponentChange(change -> game.initializePendingComponents());

      object.addComponent(added);

      assertTrue(pending.isInitialized());
      assertTrue(added.isInitialized());
      assertEquals(List.of("initialized"), calls);
      calls.clear();
      object.addComponent(new Marker());
      assertEquals(List.of("initialized", "pending", "added"), calls);
    }
  }

  @Test
  public void aFailingComponentCanRemoveALaterRecipientWithoutSkippingSurvivors() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("component removal");
      List<String> calls = new ArrayList<>();
      Marker outer = new Marker();
      Component removedRecipient = recording(calls, "removed");
      AssertionError failure = new AssertionError("after removal");
      object.addComponent(
          new Component() {
            @Override
            public void onComponentChange(ComponentChange change) {
              calls.add("first");
              if (change.component() == outer) {
                object.removeComponent(removedRecipient);
                throw failure;
              }
            }
          });
      object.addComponent(removedRecipient);
      object.addComponent(recording(calls, "survivor"));
      game.start();

      assertSame(failure, assertThrows(AssertionError.class, () -> object.addComponent(outer)));

      assertEquals(List.of("first", "first", "survivor", "survivor"), calls);
      assertFalse(object.containsComponent(removedRecipient));
    }
  }

  @Test
  public void destroyingTheOwnerMakesRemainingRecipientsIneligibleEvenAfterFailure() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("destroy");
      List<String> calls = new ArrayList<>();
      object.addComponent(recording(calls, "component"));
      game.start();
      RuntimeException failure = new RuntimeException("after destroy");
      object.onComponentChange(
          change -> {
            calls.add("destroy");
            object.destroy();
            throw failure;
          });
      object.onComponentChange(change -> calls.add("later"));
      Marker marker = new Marker();

      assertSame(failure, assertThrows(RuntimeException.class, () -> object.addComponent(marker)));

      assertEquals(List.of("destroy"), calls);
      assertTrue(object.isDestroyed());
      assertTrue(object.containsComponent(marker));
      assertTrue(game.getObjectsWith(Marker.class).isEmpty());
    }
  }

  @Test
  public void closingTheGameDuringAListenerSkipsTheRemainingSnapshot() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("close");
      List<String> calls = new ArrayList<>();
      object.addComponent(recording(calls, "component"));
      game.start();
      object.onComponentChange(
          change -> {
            calls.add("close");
            game.close();
          });
      object.onComponentChange(change -> calls.add("later"));

      object.addComponent(new Marker());

      assertEquals(List.of("close"), calls);
      assertTrue(game.isDisposed());
      assertTrue(object.getComponents().isEmpty());
    }
  }

  @Test
  public void closingTheGameDuringAComponentSkipsLaterComponents() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("component close");
      List<String> calls = new ArrayList<>();
      object.addComponent(
          new Component() {
            @Override
            public void onComponentChange(ComponentChange change) {
              calls.add("close");
              game.close();
            }
          });
      object.addComponent(recording(calls, "later"));
      game.start();

      object.addComponent(new Marker());

      assertEquals(List.of("close"), calls);
      assertTrue(game.isDisposed());
    }
  }

  @Test
  public void inactiveAndDisabledInitializedComponentsStillReceiveStructuralChanges() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("inactive");
      List<String> calls = new ArrayList<>();
      Component initialized = object.addComponent(recording(calls, "initialized"));
      game.start();
      object.setActive(false);
      initialized.setEnabled(false);
      object.addComponent(recording(calls, "pending"));
      calls.clear();

      object.addComponent(new Marker());

      assertEquals(List.of("initialized"), calls);
    }
  }

  private static Component recording(List<String> calls, String name) {
    return new Component() {
      @Override
      public void onComponentChange(ComponentChange change) {
        calls.add(name);
      }
    };
  }

  private static Component throwing(List<String> calls, String name, Throwable failure) {
    return new Component() {
      @Override
      public void onComponentChange(ComponentChange change) {
        calls.add(name);
        if (failure instanceof RuntimeException exception) throw exception;
        throw (Error) failure;
      }
    };
  }

  private static final class Marker extends Component {}
}
