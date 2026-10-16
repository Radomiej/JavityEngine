package org.javity.physics3d;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javity.core.Component;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.core.TransformEditPolicy;
import org.javity.services.TimeManager;
import org.junit.Test;

/** Native desktop Bullet tests; these deliberately do not create an OpenGL application. */
public class Physics3DTest {
  private static final class OtherMovementOwner extends Component implements TransformEditPolicy {
    @Override
    public boolean allowsInspectorTransformEdit() {
      return false;
    }
  }

  private static final class WorldAction extends Component {
    private final Runnable action;
    private boolean ran;

    WorldAction(Runnable action) {
      this.action = action;
    }

    @Override
    public void onUpdate(float delta) {
      if (!ran) {
        ran = true;
        action.run();
      }
    }
  }

  private static GameObject body(
      Game game,
      String name,
      Vector3 position,
      RigidBody3D.Type type,
      float mass,
      Collider3D collider) {
    GameObject object = game.createObject(name);
    Transform3D transform = object.addComponent(new Transform3D());
    transform.position.set(position);
    if (type != null) {
      RigidBody3D rigid = object.addComponent(new RigidBody3D(type));
      if (type == RigidBody3D.Type.DYNAMIC) rigid.setMass(mass);
    }
    object.addComponent(collider);
    return object;
  }

  private static Game game(Physics3D physics) {
    Game game = new Game();
    game.addSystem(physics);
    return game;
  }

  @Test
  public void debugViewRequestWithoutGlFailsBeforeAttachingNativeDrawer() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    game.start();
    try {
      physics.createDebugView();
      fail("debug drawing should require an active GL context");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("OpenGL context"));
    }
    assertFalse(physics.isDebugViewAttached());
    game.close();
  }

  @Test
  public void sphereFallsAndRestsOnStaticBox() {
    Game game = game(new Physics3D(new Vector3(0, -9.81f, 0)));
    body(
        game,
        "ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(20, 1, 20));
    GameObject ball =
        body(
            game,
            "ball",
            new Vector3(0, 3, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.5f));
    game.start();
    for (int i = 0; i < 240; i++) game.step(Physics3D.FIXED_STEP);
    assertEquals(2, game.getSystem(Physics3D.class).getBodyCount());
    assertEquals(.5f, ball.getComponent(Transform3D.class).position.y, .035f);
    assertTrue(Math.abs(ball.getComponent(RigidBody3D.class).getLinearVelocity().y) < .08f);
    game.close();
  }

  @Test
  public void nativeCharacterCapsuleRestsJumpsSlidesAndPublishesFeetMotion() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "character-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(20, 1, 20));
    body(
        game,
        "character-wall",
        new Vector3(1.4f, 1.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(.2f, 3, 10));
    GameObject player = game.createObject("native-character");
    Transform3D transform = player.addComponent(new Transform3D());
    transform.position.set(0, .02f, 0);
    BulletCharacterController3D character =
        player.addComponent(new BulletCharacterController3D(.4f, 1.8f, .3f).setSpeed(3.5f));
    List<BulletCharacterController3D.State> states = new ArrayList<>();
    character.onState(states::add);
    game.start();
    for (int i = 0; i < 30; i++) game.step(Physics3D.FIXED_STEP);
    assertEquals(1, physics.getCharacterCount());
    assertTrue(character.isGrounded());
    assertEquals(0, character.feet().y, .06f);
    assertEquals(character.feet().y + .9f, physics.getCharacterCentreForTest(player).y, .01f);
    assertEquals(.5f, physics.getCharacterCapsuleDimensionsForTest(player).y, .001f);
    character.setMoveAxes(1, 0);
    for (int i = 0; i < 45; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue("the real Bullet capsule should stop at the wall", transform.position.x < .9f);
    assertTrue(
        "wall response should publish executed rather than requested speed",
        character.velocity().x < 3f);
    character.setMoveAxes(0, 1);
    for (int i = 0; i < 15; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "capsule should slide along a wall while moving tangentially", transform.position.z < -.2f);
    character.setMoveAxes(0, 0).setJumpHeld(true);
    for (int i = 0; i < 30; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue("native jump should move the capsule centre and feet", transform.position.y > .6f);
    assertTrue(states.stream().anyMatch(BulletCharacterController3D.State::jumped));
    character.setJumpHeld(false);
    for (int i = 0; i < 180; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(character.isGrounded());
    assertEquals(0, transform.position.y, .07f);
    assertFalse(states.isEmpty());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void characterReadinessBlocksFullCapsuleAndPauseDoesNotConsumeJumpBuffer() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "ready-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(20, 1, 20));
    GameObject player = game.createObject("ready-character");
    Transform3D transform = player.addComponent(new Transform3D());
    transform.position.set(0, .02f, 0);
    boolean[] ready = {true};
    List<BulletCharacterController3D.State> states = new ArrayList<>();
    BulletCharacterController3D character =
        player.addComponent(
            new BulletCharacterController3D(.4f, 1.8f, .3f)
                .setReadinessPolicy((minX, minY, minZ, maxX, maxY, maxZ) -> ready[0]));
    character.onState(states::add);
    game.start();
    ready[0] = false;
    game.step(0);
    assertFalse("initial pending character is not registered", character.isBoundToPhysics());
    assertEquals(0, physics.getCharacterCount());
    assertTrue(states.isEmpty());
    ready[0] = true;
    game.step(0);
    assertTrue(character.isBoundToPhysics());
    assertEquals(1, physics.getCharacterCount());
    for (int i = 0; i < 30; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(states.getLast().boundToPhysics());
    ready[0] = false;
    character.setMoveAxes(1, 0).setJumpHeld(true);
    Vector3 before = transform.position.cpy();
    for (int i = 0; i < 3; i++) {
      game.step(Physics3D.FIXED_STEP);
      assertTrue(
          "denied movement keeps the ghost and action registered", character.isBoundToPhysics());
      assertTrue(states.getLast().boundToPhysics());
      assertEquals(1, physics.getCharacterCount());
      assertEquals(1, physics.getNativeActionCountForTest());
    }
    assertEquals(before.x, transform.position.x, .01f);
    assertEquals(before.y, transform.position.y, .01f);
    game.pause();
    game.step(1);
    assertEquals(before.y, transform.position.y, .01f);
    ready[0] = true;
    game.resume();
    for (int i = 0; i < 3; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "the buffered rising edge should jump once readiness returns",
        transform.position.y > before.y + .04f);
    assertTrue(character.isBoundToPhysics());
    assertTrue(states.getLast().boundToPhysics());
    character.setEnabled(false);
    game.step(0);
    assertFalse(character.isBoundToPhysics());
    assertEquals(0, physics.getCharacterCount());
    character.setEnabled(true);
    game.step(0);
    assertTrue(character.isBoundToPhysics());
    assertEquals(1, physics.getCharacterCount());
    game.close();
  }

  @Test
  public void characterGhostQueriesFiltersAndTriggersUseNativeRegistry() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    GameObject floor =
        body(
            game,
            "registry-ground",
            new Vector3(0, -.5f, 0),
            RigidBody3D.Type.STATIC,
            0,
            new BoxCollider3D(20, 1, 20).setFilter(4, 0xffff));
    GameObject triggerObject = game.createObject("character-trigger");
    Transform3D triggerTransform = triggerObject.addComponent(new Transform3D());
    triggerTransform.position.set(0, .5f, 0);
    Collider3D trigger =
        triggerObject.addComponent(new BoxCollider3D(2, 2, 2).setFilter(4, 2).setTrigger(true));
    List<Contact3D.Phase> phases = new ArrayList<>();
    trigger.onContact(event -> phases.add(event.phase()));
    GameObject player = game.createObject("query-character");
    Transform3D transform = player.addComponent(new Transform3D());
    transform.position.set(0, .02f, 0);
    BulletCharacterController3D character =
        player.addComponent(new BulletCharacterController3D(.4f, 1.8f, .3f).setFilter(2, 4));
    game.start();
    for (int i = 0; i < 15; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(
        physics
            .raycast(
                new Vector3(30, 3, 0),
                new Vector3(30, -1, 0),
                new PhysicsFilter3D(1, 0xffff, false))
            .isEmpty());
    assertTrue(
        "mutual native query filters exclude the capsule",
        physics
            .raycast(new Vector3(0, 3, 0), new Vector3(0, -1, 0), new PhysicsFilter3D(4, 2, false))
            .isPresent());
    assertTrue("character ghost reports a trigger enter", phases.contains(Contact3D.Phase.ENTER));
    character.warp(new Vector3(5, 0, 0));
    for (int i = 0; i < 3; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "surviving trigger receives exit when the character leaves",
        phases.contains(Contact3D.Phase.EXIT));
    game.close();
  }

  @Test
  public void characterAllocationFailuresRollbackAndBalanceNativeOwners() {
    for (String failureStage :
        List.of(
            "ghost-pair-callback",
            "character-capsule",
            "character-ghost",
            "character-action",
            "character-configured",
            "character-inserted")) {
      String selected = failureStage;
      Physics3D physics =
          new Physics3D(
              new Vector3(0, -9.81f, 0),
              stage -> {
                if (stage.equals(selected)) throw new IllegalStateException(selected);
              });
      Game game = game(physics);
      GameObject player = game.createObject("partial-character-" + failureStage);
      player.addComponent(new Transform3D());
      player.addComponent(new BulletCharacterController3D(.4f, 1.8f, .3f));
      game.start();
      assertThrows(IllegalStateException.class, () -> game.step(Physics3D.FIXED_STEP));
      game.close();
      assertEquals(
          "allocation/disposal mismatch at " + failureStage,
          physics.getNativeAllocationCount(),
          physics.getNativeDisposalCount());
    }
  }

  @Test
  public void characterCeilingStopsNativeJumpAndFocusLossClearsHeldIntent() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "ceiling-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(10, 1, 10));
    body(
        game,
        "low-ceiling",
        new Vector3(0, 2.45f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(4, .1f, 4));
    GameObject player = game.createObject("ceiling-character");
    Transform3D transform = player.addComponent(new Transform3D());
    transform.position.set(0, .02f, 0);
    BulletCharacterController3D character =
        player.addComponent(new BulletCharacterController3D(.4f, 1.8f, .3f).setJumpBufferTicks(0));
    game.start();
    for (int i = 0; i < 30; i++) game.step(Physics3D.FIXED_STEP);
    float jumpStartFeet = transform.position.y;
    character.setJumpHeld(true);
    for (int i = 0; i < 5; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "a zero-buffer grounded edge must still launch upward",
        transform.position.y > jumpStartFeet + .08f);
    for (int i = 0; i < 45; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue("native capsule must stop below a low ceiling", transform.position.y < .35f);
    assertTrue(transform.position.y > -.08f);
    character.setJumpHeld(false);
    character.setMoveAxes(1, 0);
    character.clearInput();
    float x = transform.position.x;
    for (int i = 0; i < 20; i++) game.step(Physics3D.FIXED_STEP);
    assertEquals(x, transform.position.x, .01f);
    game.close();
  }

  @Test
  public void nativeKccClimbsWithinStepHeightAndRejectsTallerStep() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "step-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(12, 1, 8));
    body(
        game,
        "low-step",
        new Vector3(2.5f, .1f, -1.5f),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(5, .2f, 1.5f));
    body(
        game,
        "high-step",
        new Vector3(2.5f, .4f, 1.5f),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(5, .8f, 1.5f));
    GameObject low = game.createObject("low-step-character");
    Transform3D lowTransform = low.addComponent(new Transform3D());
    lowTransform.position.set(-1.5f, .02f, -1.5f);
    BulletCharacterController3D lowController =
        low.addComponent(new BulletCharacterController3D(.35f, 1.8f, .3f).setSpeed(2.5f));
    GameObject high = game.createObject("high-step-character");
    Transform3D highTransform = high.addComponent(new Transform3D());
    highTransform.position.set(-1.5f, .02f, 1.5f);
    BulletCharacterController3D highController =
        high.addComponent(new BulletCharacterController3D(.35f, 1.8f, .3f).setSpeed(2.5f));
    game.start();
    for (int i = 0; i < 30; i++) game.step(Physics3D.FIXED_STEP);
    lowController.setMoveAxes(1, 0);
    highController.setMoveAxes(1, 0);
    for (int i = 0; i < 150; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue("native stepHeight climbs a step shorter than 0.3", lowTransform.position.x > 1.1f);
    assertEquals(.2f, lowTransform.position.y, .08f);
    assertTrue(
        "native stepHeight does not climb a 0.45 step: " + highTransform.position,
        highTransform.position.x < .2f);
    assertEquals(0, highTransform.position.y, .08f);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void nativeKccAcceptsBelowLimitSlopeAndRejectsAboveLimitSlope() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "slope-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(12, 1, 8));
    GameObject shallowRamp =
        body(
            game,
            "shallow-ramp",
            new Vector3(0, 1.2f, -1.5f),
            RigidBody3D.Type.STATIC,
            0,
            new BoxCollider3D(6, .15f, 1.5f));
    shallowRamp.getComponent(Transform3D.class).rotation.setFromAxis(Vector3.Z, 20f);
    GameObject steepRamp =
        body(
            game,
            "steep-ramp",
            new Vector3(0, 2.2f, 1.5f),
            RigidBody3D.Type.STATIC,
            0,
            new BoxCollider3D(6, .15f, 1.5f));
    steepRamp.getComponent(Transform3D.class).rotation.setFromAxis(Vector3.Z, 55f);
    GameObject shallow = game.createObject("shallow-slope-character");
    Transform3D shallowTransform = shallow.addComponent(new Transform3D());
    shallowTransform.position.set(-2.2f, .62f, -1.5f);
    BulletCharacterController3D shallowController =
        shallow.addComponent(
            new BulletCharacterController3D(.3f, 1.8f, .25f)
                .setSpeed(2)
                .setMaxSlopeRadians((float) Math.toRadians(35)));
    GameObject steep = game.createObject("steep-slope-character");
    Transform3D steepTransform = steep.addComponent(new Transform3D());
    steepTransform.position.set(-2.2f, .5f, 1.5f);
    BulletCharacterController3D steepController =
        steep.addComponent(
            new BulletCharacterController3D(.3f, 1.8f, .25f)
                .setSpeed(2)
                .setMaxSlopeRadians((float) Math.toRadians(35)));
    game.start();
    for (int i = 0; i < 45; i++) game.step(Physics3D.FIXED_STEP);
    shallowController.setMoveAxes(1, 0);
    steepController.setMoveAxes(1, 0);
    for (int i = 0; i < 150; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue("native KCC climbs a 20 degree slope", shallowTransform.position.x > .8f);
    assertTrue(
        "native maxSlope blocks a 55 degree slope: " + steepTransform.position,
        steepTransform.position.x < -1.4f);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void nativeCharacterSpeedIsPartitionStableAndDiagonalNormalized() {
    Vector3 cardinal = runCharacterMotion(60, 1, 0);
    Vector3 diagonal = runCharacterMotion(20, 1, 1);
    assertEquals(
        "diagonal input has the same world speed as cardinal input",
        cardinal.len(),
        diagonal.len(),
        .08f);
    assertEquals(3f, cardinal.len(), .12f);
  }

  private static Vector3 runCharacterMotion(int frames, float right, float forward) {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "partition-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(30, 1, 30));
    GameObject player = game.createObject("partition-character");
    Transform3D transform = player.addComponent(new Transform3D());
    transform.position.set(0, .02f, 0);
    BulletCharacterController3D character =
        player.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f).setSpeed(3));
    game.start();
    for (int i = 0; i < 30; i++) game.step(Physics3D.FIXED_STEP);
    Vector3 start = transform.position.cpy();
    character.setMoveAxes(right, forward);
    for (int i = 0; i < frames; i++) game.step(1f / frames);
    Vector3 moved = transform.position.cpy().sub(start);
    character.setMoveAxes(0, 0);
    Vector3 stoppedAt = transform.position.cpy();
    for (int i = 0; i < 30; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue("zero input stops horizontal intent", transform.position.dst(stoppedAt) < .03f);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
    return new Vector3(moved.x, 0, moved.z);
  }

  @Test
  public void characterLifecycleReplacementReenableAndTwoWorldsAreIsolated() {
    Physics3D firstPhysics = new Physics3D(new Vector3(0, -9.81f, 0));
    Physics3D secondPhysics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game first = game(firstPhysics), second = game(secondPhysics);
    for (Game target : List.of(first, second))
      body(
          target,
          "lifecycle-ground",
          new Vector3(0, -.5f, 0),
          RigidBody3D.Type.STATIC,
          0,
          new BoxCollider3D(10, 1, 10));
    GameObject a = first.createObject("lifecycle-a"), b = second.createObject("lifecycle-b");
    Transform3D ta = a.addComponent(new Transform3D()), tb = b.addComponent(new Transform3D());
    ta.position.set(0, .02f, 0);
    tb.position.set(0, .02f, 0);
    BulletCharacterController3D ca =
        a.addComponent(new BulletCharacterController3D(.4f, 1.8f, .3f));
    b.addComponent(new BulletCharacterController3D(.4f, 1.8f, .3f));
    first.start();
    second.start();
    first.step(Physics3D.FIXED_STEP);
    second.step(Physics3D.FIXED_STEP);
    assertEquals(1, firstPhysics.getCharacterCount());
    assertEquals(1, secondPhysics.getCharacterCount());
    ca.setEnabled(false);
    first.step(0);
    assertEquals(0, firstPhysics.getCharacterCount());
    ca.setEnabled(true);
    first.step(0);
    assertEquals(1, firstPhysics.getCharacterCount());
    assertTrue(
        firstPhysics
            .raycast(
                new Vector3(0, 3, 0), new Vector3(0, -1, 0), new PhysicsFilter3D(1, 0xffff, false))
            .isPresent());
    BulletCharacterController3D replacement = new BulletCharacterController3D(.35f, 1.7f, .25f);
    a.removeComponent(ca);
    a.addComponent(replacement);
    first.step(Physics3D.FIXED_STEP);
    assertEquals(1, firstPhysics.getCharacterCount());
    first.close();
    assertEquals(1, secondPhysics.getCharacterCount());
    second.step(Physics3D.FIXED_STEP);
    assertEquals(1, secondPhysics.getCharacterCount());
    second.close();
    assertEquals(firstPhysics.getNativeAllocationCount(), firstPhysics.getNativeDisposalCount());
    assertEquals(secondPhysics.getNativeAllocationCount(), secondPhysics.getNativeDisposalCount());
  }

  @Test
  public void disabledCharacterCanReenterAfterLastOtherCharacterIsRemoved() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "pair-lifetime-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(20, 1, 20));
    body(
        game,
        "pair-lifetime-wall",
        new Vector3(1.5f, 1, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(.2f, 2, 20));
    GameObject lastActive = game.createObject("last-active-character");
    Transform3D activeTransform = lastActive.addComponent(new Transform3D());
    activeTransform.position.set(-5, .02f, 0);
    BulletCharacterController3D active =
        lastActive.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f));
    GameObject retained = game.createObject("disabled-retained-character");
    Transform3D retainedTransform = retained.addComponent(new Transform3D());
    retainedTransform.position.set(0, .02f, 0);
    BulletCharacterController3D retainedController =
        retained.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f).setSpeed(3));

    game.start();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(2, physics.getCharacterCount());
    retainedController.setEnabled(false);
    game.step(0);
    assertEquals(1, physics.getCharacterCount());
    lastActive.removeComponent(active);
    game.step(0);
    assertEquals(0, physics.getCharacterCount());
    retainedController.setEnabled(true);
    game.step(0);
    assertEquals(1, physics.getCharacterCount());

    retainedController.setMoveAxes(1, 0);
    for (int i = 0; i < 60; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "reinserted ghost retains native wall collisions: " + retainedTransform.position,
        retainedTransform.position.x < 1.15f);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void characterStateCallbackMayCloseSceneAfterAllNativeReadback() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "close-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(10, 1, 10));
    int[] callbacks = {0};
    for (int i = 0; i < 2; i++) {
      int index = i;
      GameObject player = game.createObject("close-character-" + i);
      player.addComponent(new Transform3D()).position.set(i, .02f, 0);
      BulletCharacterController3D character =
          player.addComponent(new BulletCharacterController3D(.4f, 1.8f, .3f));
      character.onState(
          state -> {
            callbacks[0]++;
            if (index == 0) game.close();
          });
    }
    game.start();
    game.step(Physics3D.FIXED_STEP);
    assertTrue(game.isDisposed());
    assertEquals("a close from the first listener suppresses later listeners", 1, callbacks[0]);
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void characterStateCallbackCanRemoveLaterListenerAndQueryAfterNativeReadback() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "readback-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(10, 1, 10));
    GameObject first = game.createObject("readback-first"),
        second = game.createObject("readback-second");
    first.addComponent(new Transform3D()).position.set(-1, .02f, 0);
    second.addComponent(new Transform3D()).position.set(1, .02f, 0);
    BulletCharacterController3D a =
        first.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f));
    BulletCharacterController3D b =
        second.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f));
    int[] secondNotifications = {0};
    a.onState(
        state -> {
          second.removeComponent(b);
          assertTrue(
              physics
                  .raycast(new Vector3(1, 3, 0), new Vector3(1, -1, 0), PhysicsFilter3D.all())
                  .isPresent());
        });
    b.onState(state -> secondNotifications[0]++);
    game.start();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(
        "removed receiver is skipped after earlier native readback callback",
        0,
        secondNotifications[0]);
    physics.refresh();
    assertEquals(1, physics.getCharacterCount());
    assertFalse(b.isBoundToPhysics());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void characterTriggerRemovalDeliversSurvivingExitAndSuppressesRemovedReceiver() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "trigger-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(10, 1, 10));
    GameObject triggerObject = game.createObject("surviving-trigger");
    triggerObject.addComponent(new Transform3D()).position.set(0, .5f, 0);
    Collider3D trigger = triggerObject.addComponent(new BoxCollider3D(2, 2, 2).setTrigger(true));
    List<Contact3D.Phase> triggerPhases = new ArrayList<>();
    trigger.onContact(event -> triggerPhases.add(event.phase()));
    GameObject player = game.createObject("removed-character");
    player.addComponent(new Transform3D()).position.set(0, .02f, 0);
    BulletCharacterController3D character =
        player.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f));
    List<Contact3D.Phase> characterPhases = new ArrayList<>();
    character.onTrigger(event -> characterPhases.add(event.phase()));
    game.start();
    for (int i = 0; i < 3; i++) game.step(Physics3D.FIXED_STEP);
    assertEquals(
        List.of(Contact3D.Phase.ENTER, Contact3D.Phase.STAY, Contact3D.Phase.STAY),
        characterPhases);
    player.removeComponent(character);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(
        "retired character receives no callback phase after removal",
        List.of(Contact3D.Phase.ENTER, Contact3D.Phase.STAY, Contact3D.Phase.STAY),
        characterPhases);
    assertTrue(
        "surviving trigger receives pending EXIT after character removal",
        triggerPhases.contains(Contact3D.Phase.EXIT));
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void cameraObstructionAdapterIgnoresCharacterAndReportsInitialOverlap() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "camera-wall",
        new Vector3(5, 1, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(1, 3, 3));
    GameObject player = game.createObject("camera-owner");
    player.addComponent(new Transform3D()).position.set(0, .02f, 0);
    player.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f));
    game.start();
    game.step(Physics3D.FIXED_STEP);
    Physics3DCameraObstructionQuery query = new Physics3DCameraObstructionQuery(physics);
    float fraction = query.sweepFraction(new Vector3(0, 1, 0), new Vector3(10, 1, 0), .2f, player);
    assertTrue(
        "sweep ignores its own character but sees the wall", fraction > .3f && fraction < .5f);
    assertEquals(
        0, query.sweepFraction(new Vector3(5, 1, 0), new Vector3(10, 1, 0), .2f, player), 0);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void deeplyOverlappingCharacterSpawnAndWarpAreRejectedWithoutLeaking() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "overlap-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(10, 1, 10));
    GameObject player = game.createObject("overlap-character");
    Transform3D transform = player.addComponent(new Transform3D());
    transform.position.set(0, -.35f, 0);
    BulletCharacterController3D character =
        player.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f));
    game.start();
    assertThrows(IllegalArgumentException.class, () -> game.step(Physics3D.FIXED_STEP));
    assertEquals(0, physics.getCharacterCount());
    transform.position.set(0, .02f, 0);
    game.step(Physics3D.FIXED_STEP);
    assertTrue(character.isBoundToPhysics());
    Vector3 feet = transform.position.cpy();
    character.warp(new Vector3(0, -.35f, 0));
    assertThrows(IllegalArgumentException.class, () -> game.step(Physics3D.FIXED_STEP));
    assertTrue(
        "failed warp does not move the published feet pose",
        transform.position.epsilonEquals(feet, .01f));
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void characterNativeDomainRejectsUnsafeInitialDerivedValuesBeforeAllocation() {
    for (boolean policyReturnsTrue : new boolean[] {false, true}) {
      assertInvalidCharacterSpawn(new Vector3(1e20f, 0, 0), .3f, 1.8f, 6f, policyReturnsTrue);
      assertInvalidCharacterSpawn(new Vector3(0, 0, 0), .3f, 1e20f, 6f, policyReturnsTrue);
      assertInvalidCharacterSpawn(
          new Vector3(0, 0, 0), .3f, 1.8f, Float.MAX_VALUE, policyReturnsTrue);
    }
  }

  private static void assertInvalidCharacterSpawn(
      Vector3 position, float radius, float height, float jumpSpeed, boolean policyReturnsTrue) {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    GameObject player = game.createObject("invalid-native-character");
    player.addComponent(new Transform3D()).position.set(position);
    BulletCharacterController3D character =
        new BulletCharacterController3D(radius, height, .2f).setJumpSpeed(jumpSpeed);
    if (policyReturnsTrue)
      character.setReadinessPolicy((minX, minY, minZ, maxX, maxY, maxZ) -> true);
    player.addComponent(character);
    game.start();
    long allocationsBefore = physics.getNativeAllocationCount();
    assertThrows(IllegalArgumentException.class, () -> game.step(0));
    assertEquals(0, physics.getCharacterCount());
    assertEquals(
        "domain validation precedes overlap probes and character allocation",
        allocationsBefore,
        physics.getNativeAllocationCount());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void invalidRuntimeCharacterSpeedPreservesNativePoseWarpAndJumpCommand() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "numeric-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(50, 1, 50));
    GameObject player = game.createObject("numeric-character");
    Transform3D transform = player.addComponent(new Transform3D());
    transform.position.set(0, .02f, 0);
    BulletCharacterController3D character =
        player.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f));
    game.start();
    for (int i = 0; i < 30; i++) game.step(Physics3D.FIXED_STEP);
    Vector3 nativeBefore = physics.getCharacterCentreForTest(player);
    character
        .setMoveAxes(0, 0)
        .setJumpHeld(true)
        .warp(new Vector3(.4f, .02f, 0))
        .setSpeed(Float.MAX_VALUE)
        .setSprintMultiplier(2)
        .setSprint(true);

    assertThrows(IllegalArgumentException.class, () -> game.step(Physics3D.FIXED_STEP));

    assertTrue(
        "invalid effective speed does not mutate the previously valid native pose",
        physics.getCharacterCentreForTest(player).epsilonEquals(nativeBefore, 1e-6f));
    assertTrue(character.isBoundToPhysics());
    character.setSpeed(4);
    for (int i = 0; i < 4; i++) game.step(Physics3D.FIXED_STEP);
    assertEquals(
        "queued warp is applied after numeric correction", .4f, transform.position.x, .04f);
    assertTrue("queued jump edge survives numeric rejection", transform.position.y > .3f);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void unsupportedWarpIsRejectedBeforePoseMutationWithOrWithoutReadinessPolicy() {
    for (boolean withPolicy : new boolean[] {false, true}) {
      Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
      Game game = game(physics);
      body(
          game,
          "warp-domain-ground",
          new Vector3(0, -.5f, 0),
          RigidBody3D.Type.STATIC,
          0,
          new BoxCollider3D(50, 1, 50));
      GameObject player = game.createObject("warp-domain-character");
      Transform3D transform = player.addComponent(new Transform3D());
      transform.position.set(0, .02f, 0);
      BulletCharacterController3D character = new BulletCharacterController3D(.3f, 1.8f, .2f);
      if (withPolicy) character.setReadinessPolicy((minX, minY, minZ, maxX, maxY, maxZ) -> true);
      player.addComponent(character);
      game.start();
      for (int i = 0; i < 30; i++) game.step(Physics3D.FIXED_STEP);
      Vector3 nativeBefore = physics.getCharacterCentreForTest(player);
      character.warp(new Vector3(1e20f, .02f, 0));
      assertThrows(IllegalArgumentException.class, () -> game.step(Physics3D.FIXED_STEP));
      assertTrue(physics.getCharacterCentreForTest(player).epsilonEquals(nativeBefore, 1e-6f));
      character.warp(new Vector3(.5f, .02f, 0));
      game.step(Physics3D.FIXED_STEP);
      assertEquals(.5f, transform.position.x, .04f);
      game.close();
      assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
    }
  }

  @Test
  public void characterNativeDomainAllowsLargerUsefulCapsuleAndDoubleNormalizedAxes() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    body(
        game,
        "larger-character-ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(100, 1, 100));
    GameObject player = game.createObject("larger-character");
    Transform3D transform = player.addComponent(new Transform3D());
    transform.position.set(0, .02f, 0);
    BulletCharacterController3D character =
        player.addComponent(
            new BulletCharacterController3D(.5f, 2.4f, .4f)
                .setSpeed(8)
                .setSprintMultiplier(2)
                .setJumpSpeed(10)
                .setMaxFallSpeed(90));
    game.start();
    for (int i = 0; i < 30; i++) game.step(Physics3D.FIXED_STEP);
    character.setSprint(true).setMoveAxes(Float.MAX_VALUE, Float.MAX_VALUE);
    for (int i = 0; i < 30; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "supported larger capsule executes meaningful native motion",
        transform.position.dst(0, .02f, 0) > 5);
    assertEquals(transform.position.x, -transform.position.z, .2f);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void readinessMutationCannotSubmitMovementDifferentFromAdmittedSnapshot() {
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = game(physics);
    GameObject player = game.createObject("admitted-motion-snapshot");
    Transform3D transform = player.addComponent(new Transform3D());
    transform.position.set(0, 5, 0);
    BulletCharacterController3D character =
        player.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f));
    boolean[] mutateControls = {false};
    boolean[] gapReady = {false};
    boolean[] rejectedGap = {false};
    float[][] mutatedAdmission = {null};
    character.setReadinessPolicy(
        (minX, minY, minZ, maxX, maxY, maxZ) -> {
          if (mutateControls[0]) {
            mutateControls[0] = false;
            mutatedAdmission[0] = new float[] {minX, maxX};
            character.setSpeed(180).setMoveAxes(1, 0);
          }
          boolean gapEnvelope = minX < .5f && maxX > 2.5f;
          if (gapEnvelope && !gapReady[0]) rejectedGap[0] = true;
          return gapReady[0] || maxX < .5f || minX > 2.5f;
        });
    game.start();
    game.step(0);
    assertEquals(1, physics.getCharacterCount());
    Vector3 before = physics.getCharacterCentreForTest(player);
    mutateControls[0] = true;

    game.step(Physics3D.FIXED_STEP);

    assertNotNull("policy observed and admitted the original short envelope", mutatedAdmission[0]);
    assertTrue(mutatedAdmission[0][1] < .5f);
    assertTrue(
        "a callback's larger finite controls are deferred instead of using old admission",
        physics.getCharacterCentreForTest(player).epsilonEquals(before, 1e-6f));
    game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "the changed full sweep was checked and denied across the unready gap", rejectedGap[0]);
    assertTrue(physics.getCharacterCentreForTest(player).epsilonEquals(before, 1e-6f));

    gapReady[0] = true;
    game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "a later fully admitted sweep executes normally",
        physics.getCharacterCentreForTest(player).x > 2.5f);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void readinessCallbackDisablingItselfIsDrainedBeforeNativeStep() {
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = game(physics);
    GameObject player = game.createObject("readiness-self-disable");
    player.addComponent(new Transform3D()).position.set(0, 5, 0);
    BulletCharacterController3D character =
        player.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f).setSpeed(3));
    boolean[] armed = {false};
    int[] notifications = {0};
    character
        .setReadinessPolicy(
            (minX, minY, minZ, maxX, maxY, maxZ) -> {
              if (armed[0]) {
                armed[0] = false;
                character.setEnabled(false);
              }
              return true;
            })
        .onState(state -> notifications[0]++);
    game.start();
    game.step(0);
    for (int i = 0; i < 3; i++) game.step(Physics3D.FIXED_STEP);
    assertEquals(1, physics.getCharacterCount());
    assertEquals(1, physics.getNativeActionCountForTest());
    Vector3 nativeCentre = physics.getCharacterCentreForTest(player);
    int notificationsBefore = notifications[0];
    character.setMoveAxes(1, 0);
    armed[0] = true;

    game.step(Physics3D.FIXED_STEP);

    assertTrue(
        "self-disabled ghost does not move in the imminent native tick",
        physics.getCharacterCentreForTest(player).epsilonEquals(nativeCentre, 1e-6f));
    assertEquals(0, physics.getCharacterCount());
    assertEquals(0, physics.getNativeActionCountForTest());
    assertEquals(0, physics.getNativeCollisionObjectCount());
    assertEquals(
        "disabled recipient gets no state notification", notificationsBefore, notifications[0]);
    assertFalse(character.isBoundToPhysics());

    character.setEnabled(true);
    game.step(0);
    assertEquals(1, physics.getCharacterCount());
    assertTrue(character.isBoundToPhysics());
    character.setMoveAxes(1, 0);
    game.step(Physics3D.FIXED_STEP);
    assertTrue(physics.getCharacterCentreForTest(player).x > nativeCentre.x);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void readinessCallbackDisablingAnotherCharacterIsDrainedBeforeNativeStep() {
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = game(physics);
    GameObject first = game.createObject("readiness-disabler");
    GameObject second = game.createObject("readiness-disabled-peer");
    first.addComponent(new Transform3D()).position.set(0, 5, 0);
    second.addComponent(new Transform3D()).position.set(5, 5, 0);
    BulletCharacterController3D a =
        first.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f));
    BulletCharacterController3D b =
        second.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f).setSpeed(3));
    boolean[] armed = {false};
    int[] secondNotifications = {0};
    a.setReadinessPolicy(
        (minX, minY, minZ, maxX, maxY, maxZ) -> {
          if (armed[0]) {
            armed[0] = false;
            b.setEnabled(false);
          }
          return true;
        });
    b.setReadinessPolicy((minX, minY, minZ, maxX, maxY, maxZ) -> true);
    b.onState(state -> secondNotifications[0]++);
    game.start();
    game.step(0);
    for (int i = 0; i < 3; i++) game.step(Physics3D.FIXED_STEP);
    Vector3 secondCentre = physics.getCharacterCentreForTest(second);
    int notificationsBefore = secondNotifications[0];
    b.setMoveAxes(1, 0);
    armed[0] = true;

    game.step(Physics3D.FIXED_STEP);

    assertTrue(
        "peer disabled by readiness policy has no imminent solver movement",
        physics.getCharacterCentreForTest(second).epsilonEquals(secondCentre, 1e-6f));
    assertEquals(1, physics.getCharacterCount());
    assertEquals(1, physics.getNativeActionCountForTest());
    assertEquals(1, physics.getNativeCollisionObjectCount());
    assertEquals(notificationsBefore, secondNotifications[0]);
    assertFalse(b.isBoundToPhysics());

    b.setEnabled(true);
    game.step(0);
    assertEquals(2, physics.getCharacterCount());
    assertTrue(b.isBoundToPhysics());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void readinessCallbackReplacingAnotherCharacterRetiresOldNativeBindingBeforeStep() {
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = game(physics);
    GameObject first = game.createObject("readiness-replacer");
    GameObject second = game.createObject("readiness-replaced-peer");
    first.addComponent(new Transform3D()).position.set(0, 5, 0);
    Transform3D secondTransform = second.addComponent(new Transform3D());
    secondTransform.position.set(5, 5, 0);
    BulletCharacterController3D a =
        first.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f));
    BulletCharacterController3D retired =
        second.addComponent(new BulletCharacterController3D(.3f, 1.8f, .2f).setSpeed(3));
    BulletCharacterController3D replacement =
        new BulletCharacterController3D(.3f, 1.8f, .2f).setSpeed(3);
    boolean[] armed = {false};
    int[] retiredNotifications = {0};
    int[] replacementNotifications = {0};
    a.setReadinessPolicy(
        (minX, minY, minZ, maxX, maxY, maxZ) -> {
          if (armed[0]) {
            armed[0] = false;
            second.removeComponent(retired);
            second.addComponent(replacement);
          }
          return true;
        });
    retired.onState(state -> retiredNotifications[0]++);
    replacement.onState(state -> replacementNotifications[0]++);
    game.start();
    game.step(0);
    for (int i = 0; i < 3; i++) game.step(Physics3D.FIXED_STEP);
    Vector3 retiredCentre = physics.getCharacterCentreForTest(second);
    int notificationsBefore = retiredNotifications[0];
    retired.setMoveAxes(1, 0);
    armed[0] = true;

    game.step(Physics3D.FIXED_STEP);

    assertNull("old exact component binding is retired", physics.getCharacterCentreForTest(second));
    assertEquals(1, physics.getCharacterCount());
    assertEquals(1, physics.getNativeActionCountForTest());
    assertEquals(notificationsBefore, retiredNotifications[0]);
    assertEquals(0, replacementNotifications[0]);
    assertFalse(retired.isBoundToPhysics());
    assertNotNull(retiredCentre);

    game.step(0);
    assertEquals(2, physics.getCharacterCount());
    assertTrue(replacement.isBoundToPhysics());
    game.step(Physics3D.FIXED_STEP);
    assertTrue(replacementNotifications[0] > 0);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void readinessPolicyMayCloseGameWithoutFurtherNativeAccess() {
    Physics3D physics = new Physics3D(new Vector3(0, -9.81f, 0));
    Game game = game(physics);
    GameObject player = game.createObject("readiness-closing-character");
    player.addComponent(new Transform3D());
    int[] calls = {0};
    player.addComponent(
        new BulletCharacterController3D(.3f, 1.8f, .2f)
            .setReadinessPolicy(
                (minX, minY, minZ, maxX, maxY, maxZ) -> {
                  calls[0]++;
                  game.close();
                  return true;
                }));
    game.start();
    game.step(Physics3D.FIXED_STEP);
    assertTrue(game.isDisposed());
    assertEquals(1, calls[0]);
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void beforeFixedStepRunsOutsideJniOnlyForTicksAndFailurePreservesCommands() {
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = game(physics);
    GameObject ball =
        body(
            game,
            "hook-ball",
            Vector3.Zero,
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.5f));
    RigidBody3D rigid = ball.getComponent(RigidBody3D.class);
    rigid.applyImpulse(new Vector3(1, 0, 0));
    int[] firstCalls = {0};
    int[] secondCalls = {0};
    AutoCloseable[] second = {null};
    AutoCloseable first =
        physics.beforeFixedStep(
            () -> {
              firstCalls[0]++;
              physics.refresh(); // Must synchronize without recursively delivering hooks.
              if (firstCalls[0] == 1) throw new IllegalStateException("preparation failed");
              if (second[0] != null) {
                try {
                  second[0].close();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }
            });
    second[0] = physics.beforeFixedStep(() -> secondCalls[0]++);
    game.start();
    assertThrows(IllegalStateException.class, () -> game.step(Physics3D.FIXED_STEP));
    assertEquals(0, physics.getFixedTick());
    assertEquals(1, firstCalls[0]);
    assertEquals(0, secondCalls[0]);
    assertEquals(0f, rigid.getLinearVelocity().x, .001f);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(2, physics.getFixedTick());
    assertEquals(3, firstCalls[0]);
    assertEquals(0, secondCalls[0]);
    assertEquals(1f, rigid.getLinearVelocity().x, .02f);
    game.step(0);
    assertEquals(3, firstCalls[0]);
    try {
      first.close();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    game.close();
  }

  @Test
  public void primitiveBudgetCountsTerrainAndSeparateTriggerChildrenTogether() {
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = new Game();
    game.addSystem(physics);
    GameObject terrain = game.createObject("bounded-terrain");
    terrain.addComponent(new Transform3D());
    terrain.addComponent(new RigidBody3D(RigidBody3D.Type.STATIC));
    CompoundCollider3D.Box box =
        new CompoundCollider3D.Box(new Vector3(1, 1, 1), Vector3.Zero, new Quaternion());
    StaticCompoundCollider3D staticCollider =
        new StaticCompoundCollider3D(java.util.Collections.nCopies(4090, box));
    terrain.addComponent(staticCollider);
    terrain.addComponent(
        new CompoundCollider3D(java.util.Collections.nCopies(7, box)).setTrigger(true));
    game.start();
    assertEquals(2, terrain.getComponents(Collider3D.class).size());
    assertEquals(
        7,
        ((CompoundCollider3D) terrain.getComponents(Collider3D.class).get(1)).getChildren().size());
    long baselineAllocations = physics.getNativeAllocationCount();
    physics.beforeFixedStep(() -> physics.publishStaticCollision(terrain, staticCollider));

    assertThrows(IllegalArgumentException.class, () -> game.step(Physics3D.FIXED_STEP));
    assertEquals(baselineAllocations, physics.getNativeAllocationCount());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void exactPublicationWaitsForPendingEnabledTriggerAndExplicitCommit() throws Exception {
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = new Game();
    game.addSystem(physics);
    GameObject terrainObject = game.createObject("staged-terrain-and-trigger");
    terrainObject.addComponent(new Transform3D());
    terrainObject.addComponent(new RigidBody3D(RigidBody3D.Type.STATIC));
    StaticCompoundCollider3D terrain =
        terrainObject.addComponent(
            new StaticCompoundCollider3D(
                List.of(
                    new CompoundCollider3D.Box(
                        new Vector3(4, 1, 4), Vector3.Zero, new Quaternion()))));
    game.start();
    AutoCloseable hook =
        physics.beforeFixedStep(() -> physics.publishStaticCollision(terrainObject, terrain));
    game.step(Physics3D.FIXED_STEP);
    assertTrue(physics.isCollisionPublished(terrainObject, List.of(terrain)));
    assertEquals(0, physics.getTriggerObjectCount());

    Collider3D trigger = terrainObject.addComponent(new BoxCollider3D(1, 1, 1).setTrigger(true));
    List<Collider3D> complete = List.of(terrain, trigger);
    assertFalse(physics.isCollisionPublished(terrainObject, List.of(terrain)));
    assertFalse(physics.isCollisionPublished(terrainObject, complete));
    physics.refresh();
    assertFalse(physics.isCollisionPublished(terrainObject, List.of(terrain)));
    assertFalse(physics.isCollisionPublished(terrainObject, complete));
    assertEquals(0, physics.getTriggerObjectCount());

    game.initializePendingComponents();
    physics.refresh(); // Initialization alone cannot publish the staged sibling.
    assertFalse(physics.isCollisionPublished(terrainObject, List.of(terrain)));
    assertFalse(physics.isCollisionPublished(terrainObject, complete));
    assertEquals(0, physics.getTriggerObjectCount());

    game.step(Physics3D.FIXED_STEP);
    assertFalse(physics.isCollisionPublished(terrainObject, List.of(terrain)));
    assertTrue(physics.isCollisionPublished(terrainObject, complete));
    assertEquals(1, physics.getTriggerObjectCount());
    hook.close();
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void bodyContactsAreWorldLocalCopiedAndTransitionPerFixedSubstep() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject fixed =
        body(game, "fixed", Vector3.Zero, RigidBody3D.Type.STATIC, 0, new SphereCollider3D(.5f));
    GameObject moving =
        body(
            game,
            "moving",
            new Vector3(.8f, 0, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.5f));
    Collider3D extra = moving.addComponent(new SphereCollider3D(.25f));
    List<Contact3D> fixedEvents = new ArrayList<>(), movingEvents = new ArrayList<>();
    fixed.getComponent(SphereCollider3D.class).onContact(fixedEvents::add);
    moving.getComponent(SphereCollider3D.class).onContact(movingEvents::add);
    extra.onContact(movingEvents::add);
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    game.step(Physics3D.FIXED_STEP);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(
        List.of(Contact3D.Phase.ENTER, Contact3D.Phase.STAY),
        fixedEvents.stream().map(Contact3D::phase).toList());
    assertEquals(4, movingEvents.size()); // two collider listeners, one body-level event each tick
    assertNull(fixedEvents.getFirst().colliderA());
    assertTrue(fixedEvents.getFirst().bodyLifetimeA() < fixedEvents.getFirst().bodyLifetimeB());
    Vector3 copy = fixedEvents.getFirst().normalOnB();
    copy.setZero();
    assertFalse(fixedEvents.getFirst().normalOnB().isZero());
    assertTrue(
        fixedEvents
            .getFirst()
            .normalOnB()
            .epsilonEquals(movingEvents.getFirst().normalOnB().scl(-1), .0001f));
    moving.getComponent(RigidBody3D.class).teleport(new Vector3(5, 0, 0), new Quaternion(), true);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(Contact3D.Phase.EXIT, fixedEvents.getLast().phase());
    assertEquals(Contact3D.Phase.EXIT, movingEvents.getLast().phase());
    game.close();
  }

  @Test
  public void mixedTriggerHasIndependentResponseAndStandaloneTriggersUseNarrowphase() {
    Game game = game(new Physics3D(new Vector3(0, -9.81f, 0)));
    GameObject floor =
        body(
            game,
            "floor",
            new Vector3(0, -.5f, 0),
            RigidBody3D.Type.STATIC,
            0,
            new BoxCollider3D(20, 1, 20));
    GameObject ball =
        body(
            game,
            "ball",
            new Vector3(0, 2, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.5f));
    BoxCollider3D sensor = new BoxCollider3D(2, 1, 2);
    sensor
        .setTrigger(true)
        .setLocalPose(new Vector3(0, -.5f, 0), new Quaternion().setFromAxis(Vector3.Z, 12));
    ball.addComponent(sensor);
    List<Contact3D.Phase> events = new ArrayList<>();
    sensor.onContact(c -> events.add(c.phase()));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    for (int i = 0; i < 150; i++) game.step(Physics3D.FIXED_STEP);
    assertEquals(2, physics.getBodyCount());
    assertEquals(1, physics.getTriggerObjectCount());
    assertTrue(
        "trigger erased solid response", ball.getComponent(Transform3D.class).position.y > .45f);
    assertTrue(events.contains(Contact3D.Phase.ENTER));
    assertEquals(3, physics.getNativeCollisionObjectCount());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());

    Game sensors = game(new Physics3D(Vector3.Zero));
    BoxCollider3D a = new BoxCollider3D(1, 1, 1);
    a.setTrigger(true);
    BoxCollider3D b = new BoxCollider3D(1, 1, 1);
    b.setTrigger(true);
    GameObject sensorA = body(sensors, "sensor-a", Vector3.Zero, null, 0, a);
    GameObject sensorB = body(sensors, "sensor-b", new Vector3(.5f, 0, 0), null, 0, b);
    List<Contact3D> pair = new ArrayList<>();
    a.onContact(pair::add);
    sensors.start();
    sensors.step(Physics3D.FIXED_STEP);
    assertEquals(2, sensors.getSystem(Physics3D.class).getNativeCollisionObjectCount());
    assertEquals(2, sensors.getSystem(Physics3D.class).getTriggerObjectCount());
    assertEquals(1, pair.size());
    assertTrue(pair.getFirst().isTrigger());
    assertSame(sensorA, pair.getFirst().objectA());
    assertSame(sensorB, pair.getFirst().objectB());
    sensors.close();
  }

  @Test
  public void copiedRayAndSweepQueriesSkipIgnoredObjectsAndTriggersBeforeClosestHit() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject ignored =
        body(
            game,
            "ignored",
            new Vector3(1, 0, 0),
            RigidBody3D.Type.STATIC,
            0,
            new BoxCollider3D(.5f, 2, 2));
    GameObject trigger =
        body(
            game,
            "trigger",
            new Vector3(2, 0, 0),
            RigidBody3D.Type.STATIC,
            0,
            new BoxCollider3D(.5f, 2, 2).setTrigger(true));
    GameObject wall =
        body(
            game,
            "wall",
            new Vector3(4, 0, 0),
            RigidBody3D.Type.STATIC,
            0,
            new BoxCollider3D(.5f, 2, 2));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    PhysicsFilter3D filter = new PhysicsFilter3D(1, 0xffff, false, ignored.id);
    Optional<PhysicsHit3D> hit = physics.raycast(Vector3.Zero, new Vector3(6, 0, 0), filter);
    assertTrue(hit.isPresent());
    assertSame(wall, hit.get().object());
    assertTrue(
        physics
            .raycast(
                Vector3.Zero, new Vector3(6, 0, 0), new PhysicsFilter3D(1, 0, false, ignored.id))
            .isEmpty());
    Vector3 point = hit.get().point();
    point.setZero();
    assertFalse(hit.get().point().isZero());
    assertTrue(physics.raycast(Vector3.Zero, Vector3.Zero, filter).isEmpty());
    Optional<PhysicsHit3D> start =
        physics.sweepSphere(
            .2f, new Vector3(3.8f, 0, 0), new Vector3(5, 0, 0), PhysicsFilter3D.all());
    assertTrue(start.isPresent());
    assertSame(wall, start.get().object());
    Optional<PhysicsHit3D> sphereSweep =
        physics.sweepSphere(
            .2f,
            Vector3.Zero,
            new Vector3(6, 0, 0),
            new PhysicsFilter3D(1, 0xffff, false, ignored.id));
    assertTrue(sphereSweep.isPresent());
    assertSame(wall, sphereSweep.get().object());
    Optional<PhysicsHit3D> capsuleSweep =
        physics.sweepCapsule(
            .2f,
            1,
            new com.badlogic.gdx.math.Matrix4()
                .set(new Vector3(), new Quaternion().setFromAxis(Vector3.X, 35), Vector3.One),
            new com.badlogic.gdx.math.Matrix4()
                .set(
                    new Vector3(6, 0, 0), new Quaternion().setFromAxis(Vector3.Z, 25), Vector3.One),
            new PhysicsFilter3D(1, 0xffff, false, ignored.id));
    assertTrue(capsuleSweep.isPresent());
    assertSame(wall, capsuleSweep.get().object());
    assertNotSame(trigger, hit.get().object());
    game.close();
  }

  @Test
  public void bodylessTriggerLifecycleAndFilterChangesTrackNativeOwnership() {
    Game game = game(new Physics3D(Vector3.Zero));
    BoxCollider3D sensor = new BoxCollider3D(2, 2, 2);
    sensor.setTrigger(true).setFilter(4, 2);
    GameObject a = body(game, "sensor", Vector3.Zero, null, 0, sensor);
    BoxCollider3D other = new BoxCollider3D(2, 2, 2);
    other.setTrigger(true).setFilter(1, 0xffff);
    body(game, "other", new Vector3(.5f, 0, 0), null, 0, other);
    List<Contact3D> events = new ArrayList<>();
    sensor.onContact(events::add);
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    physics.refresh();
    assertEquals(0, physics.getBodyCount());
    assertEquals(2, physics.getNativeCollisionObjectCount());
    game.step(Physics3D.FIXED_STEP);
    assertTrue("one-way trigger mask admitted the pair", events.isEmpty());
    sensor.setFilter(4, 1);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(Contact3D.Phase.ENTER, events.getFirst().phase());
    a.setActive(false);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(1, physics.getNativeCollisionObjectCount());
    a.setActive(true);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(2, physics.getNativeCollisionObjectCount());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void solidFilterRevisionsRebuildNativeMembershipAndMaterialsRejectConflicts() {
    Game game = game(new Physics3D(Vector3.Zero));
    BoxCollider3D fixedCollider = new BoxCollider3D(1, 1, 1);
    fixedCollider.setMaterial(.2f, .1f).setFilter(1, 2);
    GameObject fixed = body(game, "fixed", Vector3.Zero, RigidBody3D.Type.STATIC, 0, fixedCollider);
    SphereCollider3D movingCollider = new SphereCollider3D(.5f);
    movingCollider.setMaterial(.2f, .1f).setFilter(2, 4);
    GameObject moving =
        body(game, "moving", new Vector3(.5f, 0, 0), RigidBody3D.Type.DYNAMIC, 1, movingCollider);
    List<Contact3D> events = new ArrayList<>();
    fixedCollider.onContact(events::add);
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    physics.refresh();
    assertEquals(.2f, physics.getNativeBodyForTest(fixed).getFriction(), 1e-6f);
    game.step(Physics3D.FIXED_STEP);
    assertTrue(events.isEmpty());
    movingCollider.setFilter(2, 1);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(Contact3D.Phase.ENTER, events.getFirst().phase());
    movingCollider.setMaterial(.8f, .7f);
    physics.refresh();
    assertEquals(.8f, physics.getNativeBodyForTest(moving).getFriction(), 1e-6f);
    game.close();

    Game invalid = game(new Physics3D(Vector3.Zero));
    GameObject object =
        body(
            invalid,
            "conflict",
            Vector3.Zero,
            RigidBody3D.Type.STATIC,
            0,
            new BoxCollider3D(1, 1, 1));
    object.addComponent(new SphereCollider3D(.5f).setMaterial(.9f, 0));
    Physics3D invalidPhysics = invalid.getSystem(Physics3D.class);
    invalid.start();
    assertThrows(IllegalArgumentException.class, invalidPhysics::refresh);
    assertEquals(0, invalidPhysics.getNativeCollisionObjectCount());
    invalid.close();
  }

  @Test
  public void callbackCanQueryThenItsExceptionPropagatesAfterJniAndCloseCleansNativeState() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject fixed =
        body(game, "fixed", Vector3.Zero, RigidBody3D.Type.STATIC, 0, new SphereCollider3D(.5f));
    GameObject moving =
        body(
            game,
            "moving",
            new Vector3(.8f, 0, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.5f));
    GameObject wall =
        body(
            game,
            "wall",
            new Vector3(4, 3, 0),
            RigidBody3D.Type.STATIC,
            0,
            new BoxCollider3D(.5f, 2, 2));
    Physics3D physics = game.getSystem(Physics3D.class);
    List<GameObject> queried = new ArrayList<>();
    fixed
        .getComponent(SphereCollider3D.class)
        .onContact(
            c -> {
              throw new IllegalStateException("listener failure");
            });
    fixed
        .getComponent(SphereCollider3D.class)
        .onContact(
            c ->
                queried.add(
                    physics
                        .raycast(new Vector3(0, 3, 0), new Vector3(6, 3, 0), PhysicsFilter3D.all())
                        .orElseThrow()
                        .object()));
    game.start();
    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> game.step(Physics3D.FIXED_STEP));
    assertEquals("listener failure", error.getMessage());
    assertEquals(List.of(wall), queried);
    assertEquals(3, physics.getNativeCollisionObjectCount());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void callbackDeactivationIsSynchronizedBeforeCatchupSubsteps() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject fixed =
        body(game, "fixed", Vector3.Zero, RigidBody3D.Type.STATIC, 0, new SphereCollider3D(.5f));
    GameObject moving =
        body(
            game,
            "moving",
            new Vector3(.8f, 0, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.5f));
    RigidBody3D rigid = moving.getComponent(RigidBody3D.class);
    fixed.getComponent(SphereCollider3D.class).onContact(c -> rigid.setEnabled(false));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    game.step(4 * Physics3D.FIXED_STEP);
    assertEquals(4, physics.getFixedTick());
    assertEquals(1, physics.getNativeCollisionObjectCount());
    assertEquals(1, physics.getBodyCount());
    game.close();
  }

  @Test
  public void enterAndExitAreBothDeliveredAcrossCatchupSubstepsInOneFrame() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject fixed =
        body(game, "fixed", Vector3.Zero, RigidBody3D.Type.STATIC, 0, new SphereCollider3D(.5f));
    GameObject moving =
        body(
            game,
            "moving",
            new Vector3(.8f, 0, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.5f));
    RigidBody3D rigid = moving.getComponent(RigidBody3D.class);
    List<Contact3D.Phase> phases = new ArrayList<>();
    fixed
        .getComponent(SphereCollider3D.class)
        .onContact(
            c -> {
              phases.add(c.phase());
              if (c.phase() == Contact3D.Phase.ENTER)
                rigid.teleport(new Vector3(5, 0, 0), new Quaternion(), true);
            });
    game.start();
    game.step(2 * Physics3D.FIXED_STEP);
    assertEquals(List.of(Contact3D.Phase.ENTER, Contact3D.Phase.EXIT), phases);
    game.close();
  }

  @Test
  public void contactStateAndEventsStayInsideTheirOwningDispatcherAcrossGames() {
    Game first = game(new Physics3D(Vector3.Zero)), second = game(new Physics3D(Vector3.Zero));
    GameObject firstA =
        body(first, "a", Vector3.Zero, RigidBody3D.Type.STATIC, 0, new SphereCollider3D(.5f));
    body(
        first, "b", new Vector3(.8f, 0, 0), RigidBody3D.Type.DYNAMIC, 1, new SphereCollider3D(.5f));
    GameObject secondA =
        body(second, "a", Vector3.Zero, RigidBody3D.Type.STATIC, 0, new SphereCollider3D(.5f));
    body(second, "b", new Vector3(5, 0, 0), RigidBody3D.Type.DYNAMIC, 1, new SphereCollider3D(.5f));
    List<Contact3D> firstEvents = new ArrayList<>(), secondEvents = new ArrayList<>();
    firstA.getComponent(SphereCollider3D.class).onContact(firstEvents::add);
    secondA.getComponent(SphereCollider3D.class).onContact(secondEvents::add);
    first.start();
    second.start();
    first.step(Physics3D.FIXED_STEP);
    second.step(Physics3D.FIXED_STEP);
    assertEquals(1, firstEvents.size());
    assertTrue(secondEvents.isEmpty());
    first.close();
    second.step(Physics3D.FIXED_STEP);
    second.close();
  }

  @Test
  public void invalidAndPrestartQueriesAllocateNoNativeObjectsAndQueryFailureCleansCandidates() {
    Physics3D prestartPhysics = new Physics3D(Vector3.Zero);
    Game prestart = game(prestartPhysics);
    assertThrows(
        IllegalStateException.class,
        () -> prestartPhysics.raycast(Vector3.Zero, new Vector3(1, 0, 0), PhysicsFilter3D.all()));
    prestart.close();

    AtomicBoolean fail = new AtomicBoolean();
    Physics3D physics =
        new Physics3D(
            Vector3.Zero,
            stage -> {
              if (fail.get() && stage.equals("sweep-sphere-shape"))
                throw new IllegalStateException("injected");
            });
    Game game = game(physics);
    body(
        game, "wall", new Vector3(3, 0, 0), RigidBody3D.Type.STATIC, 0, new BoxCollider3D(1, 2, 2));
    game.start();
    physics.refresh();
    com.badlogic.gdx.math.Matrix4 shear =
        new com.badlogic.gdx.math.Matrix4().set(new Vector3(), new Quaternion(), Vector3.One);
    shear.val[com.badlogic.gdx.math.Matrix4.M01] = .25f;
    com.badlogic.gdx.math.Matrix4 reflection =
        new com.badlogic.gdx.math.Matrix4()
            .set(new Vector3(), new Quaternion(), Vector3.One)
            .scale(-1, 1, 1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            physics.sweepCapsule(
                .5f,
                1,
                shear,
                new com.badlogic.gdx.math.Matrix4()
                    .set(new Vector3(1, 0, 0), new Quaternion(), Vector3.One),
                PhysicsFilter3D.all()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            physics.sweepCapsule(
                .5f,
                1,
                reflection,
                new com.badlogic.gdx.math.Matrix4()
                    .set(new Vector3(1, 0, 0), new Quaternion(), Vector3.One),
                PhysicsFilter3D.all()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            physics.sweepCapsule(
                .5f,
                1,
                new com.badlogic.gdx.math.Matrix4()
                    .set(new Vector3(), new Quaternion(), Vector3.One),
                new com.badlogic.gdx.math.Matrix4()
                    .set(new Vector3(1, 0, 0), new Quaternion(), Vector3.One)
                    .rotate(Vector3.Y, 20)
                    .scale(1, .8f, 1),
                PhysicsFilter3D.all()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            physics.raycast(
                new Vector3(-Float.MAX_VALUE, 0, 0),
                new Vector3(Float.MAX_VALUE, 0, 0),
                PhysicsFilter3D.all()));
    long allocations = physics.getNativeAllocationCount(),
        disposals = physics.getNativeDisposalCount();
    fail.set(true);
    assertThrows(
        IllegalStateException.class,
        () -> physics.sweepSphere(.25f, Vector3.Zero, new Vector3(4, 0, 0), PhysicsFilter3D.all()));
    assertEquals(
        physics.getNativeAllocationCount() - allocations,
        physics.getNativeDisposalCount() - disposals);
    game.close();
  }

  @Test
  public void rotatedTriggerUsesNarrowphaseInsteadOfOverlappingBounds() {
    Game game = game(new Physics3D(Vector3.Zero));
    BoxCollider3D sensor = new BoxCollider3D(4, .2f, .2f);
    sensor.setTrigger(true);
    sensor.setLocalPose(Vector3.Zero, new Quaternion().setFromAxis(Vector3.Z, 45));
    GameObject owner = body(game, "sensor", Vector3.Zero, null, 0, sensor);
    body(
        game,
        "outside",
        new Vector3(1.2f, -1.2f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(.1f, .1f, .1f));
    List<Contact3D> events = new ArrayList<>();
    sensor.onContact(events::add);
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    game.step(Physics3D.FIXED_STEP);
    assertTrue("overlapping AABBs were mistaken for a trigger contact", events.isEmpty());
    owner.destroy();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(1, physics.getNativeCollisionObjectCount());
    game.close();
  }

  @Test
  public void triggerPairKeysKeepEverySensorPairDistinct() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject a = game.createObject("a");
    a.addComponent(new Transform3D());
    GameObject b = game.createObject("b");
    b.addComponent(new Transform3D());
    List<Contact3D> aEvents = new ArrayList<>(), bEvents = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      BoxCollider3D ca = new BoxCollider3D(2, 2, 2);
      ca.setTrigger(true);
      a.addComponent(ca);
      ca.onContact(aEvents::add);
      BoxCollider3D cb = new BoxCollider3D(2, 2, 2);
      cb.setTrigger(true);
      b.addComponent(cb);
      cb.onContact(bEvents::add);
    }
    b.getComponent(Transform3D.class).position.set(.5f, 0, 0);
    game.start();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(4, aEvents.size());
    assertEquals(4, bEvents.size());
    assertEquals(
        4, aEvents.stream().map(c -> List.of(c.colliderA(), c.colliderB())).distinct().count());
    game.close();
  }

  @Test
  public void standaloneAndMixedSensorsUseTheCommittedPeerSolidFilter() {
    Game standalone = game(new Physics3D(Vector3.Zero));
    BoxCollider3D sensor = new BoxCollider3D(8, 2, 2);
    sensor.setTrigger(true).setFilter(2, 4);
    GameObject sensorOwner = body(standalone, "sensor", Vector3.Zero, null, 0, sensor);
    BoxCollider3D staticSolid = new BoxCollider3D(1, 1, 1);
    staticSolid.setFilter(4, 2);
    GameObject staticPeer =
        body(
            standalone,
            "static-peer",
            new Vector3(.5f, 0, 0),
            RigidBody3D.Type.STATIC,
            0,
            staticSolid);
    BoxCollider3D dynamicSolid = new BoxCollider3D(1, 1, 1);
    dynamicSolid.setFilter(4, 2);
    GameObject dynamicPeer =
        body(
            standalone,
            "dynamic-peer",
            new Vector3(3, 0, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            dynamicSolid);
    List<Contact3D> contacts = new ArrayList<>();
    List<Contact3D> staticPeerContacts = new ArrayList<>();
    sensor.onContact(contacts::add);
    staticSolid.onContact(staticPeerContacts::add);
    Physics3D standalonePhysics = standalone.getSystem(Physics3D.class);
    standalone.start();
    for (int i = 0; i < 240; i++) standalone.step(Physics3D.FIXED_STEP);
    assertFalse(
        "dynamic peer should be sleeping",
        standalonePhysics.getNativeBodyForTest(dynamicPeer).isActive());
    assertTrue(
        contacts.stream()
            .anyMatch(c -> c.objectB() == staticPeer && c.phase() == Contact3D.Phase.ENTER));
    assertTrue(
        contacts.stream()
            .anyMatch(c -> c.objectB() == dynamicPeer && c.phase() == Contact3D.Phase.ENTER));
    Contact3D staticContact =
        contacts.stream().filter(c -> c.objectB() == staticPeer).findFirst().orElseThrow();
    assertTrue(staticContact.isTrigger());
    assertSame(sensor, staticContact.colliderA());
    assertNull(staticContact.colliderB());
    Contact3D reverseStatic = staticPeerContacts.getFirst();
    assertSame(staticPeer, reverseStatic.objectA());
    assertEquals(staticContact.pointB().x, reverseStatic.pointA().x, .01f);
    assertTrue(staticContact.normalOnB().epsilonEquals(reverseStatic.normalOnB().scl(-1), .01f));
    standalone.close();

    Game mixed = game(new Physics3D(Vector3.Zero));
    BoxCollider3D ownerSolid = new BoxCollider3D(.5f, .5f, .5f);
    ownerSolid.setFilter(32, 0).setLocalPose(new Vector3(20, 0, 0), new Quaternion());
    GameObject mixedOwner =
        body(mixed, "mixed-owner", Vector3.Zero, RigidBody3D.Type.STATIC, 0, ownerSolid);
    BoxCollider3D mixedSensor = new BoxCollider3D(2, 2, 2);
    mixedSensor.setTrigger(true).setFilter(2, 4);
    mixedOwner.addComponent(mixedSensor);
    BoxCollider3D admittedPeer = new BoxCollider3D(1, 1, 1);
    admittedPeer.setFilter(4, 2);
    GameObject peer =
        body(mixed, "peer", new Vector3(.5f, 0, 0), RigidBody3D.Type.STATIC, 0, admittedPeer);
    List<Contact3D> mixedContacts = new ArrayList<>();
    List<Contact3D> peerContacts = new ArrayList<>();
    mixedSensor.onContact(mixedContacts::add);
    admittedPeer.onContact(peerContacts::add);
    mixed.start();
    mixed.step(Physics3D.FIXED_STEP);
    Contact3D accepted =
        mixedContacts.stream()
            .filter(c -> c.phase() == Contact3D.Phase.ENTER)
            .findFirst()
            .orElseThrow();
    assertSame(mixedOwner, accepted.objectA());
    assertSame(peer, accepted.objectB());
    assertSame(mixedSensor, accepted.colliderA());
    assertNull(accepted.colliderB());
    Contact3D reverseAccepted = peerContacts.getFirst();
    assertSame(peer, reverseAccepted.objectA());
    assertTrue(accepted.normalOnB().epsilonEquals(reverseAccepted.normalOnB().scl(-1), .01f));
    admittedPeer.setFilter(4, 8);
    mixed.getSystem(Physics3D.class).refresh();
    mixed.step(Physics3D.FIXED_STEP);
    assertEquals(1, mixedContacts.stream().filter(c -> c.phase() == Contact3D.Phase.ENTER).count());
    assertTrue(mixedContacts.stream().anyMatch(c -> c.phase() == Contact3D.Phase.EXIT));
    mixed.close();
  }

  @Test
  public void bodylessKinematicAndStaticSensorsCommitPoseCommandsAtFixedBoundaries() {
    Game game = game(new Physics3D(Vector3.Zero));
    BoxCollider3D kinematicSensor = new BoxCollider3D(2, .2f, .2f);
    kinematicSensor.setTrigger(true);
    GameObject kinematic =
        body(
            game, "kinematic-sensor", Vector3.Zero, RigidBody3D.Type.KINEMATIC, 0, kinematicSensor);
    GameObject peer =
        body(
            game,
            "peer",
            new Vector3(3, .8f, 0),
            RigidBody3D.Type.STATIC,
            0,
            new BoxCollider3D(.5f, .5f, .5f));
    RigidBody3D kinematicBody = kinematic.getComponent(RigidBody3D.class);
    Physics3D physics = game.getSystem(Physics3D.class);
    List<Contact3D> events = new ArrayList<>();
    kinematicSensor.onContact(events::add);
    game.start();
    physics.refresh();
    assertEquals(2, physics.getNativeCollisionObjectCount());
    assertEquals(1, physics.getBodyCount());
    PhysicsFilter3D skipPeer = new PhysicsFilter3D(1, 0xffff, true, peer.id);
    assertTrue(physics.raycast(new Vector3(2, .8f, 0), new Vector3(4, .8f, 0), skipPeer).isEmpty());
    Quaternion tilted = new Quaternion().setFromAxis(Vector3.Z, 90);
    kinematicBody.moveKinematic(new Vector3(3, 0, 0), tilted);
    game.pause();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(0, kinematic.getComponent(Transform3D.class).position.x, 1e-6f);
    assertTrue(physics.raycast(new Vector3(2, .8f, 0), new Vector3(4, .8f, 0), skipPeer).isEmpty());
    game.resume();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(3, kinematic.getComponent(Transform3D.class).position.x, 1e-6f);
    assertTrue(
        physics.raycast(new Vector3(2, .8f, 0), new Vector3(4, .8f, 0), skipPeer).isPresent());
    assertTrue(
        events.stream().anyMatch(c -> c.objectB() == peer && c.phase() == Contact3D.Phase.ENTER));
    assertEquals(1, physics.getBodyCount());
    kinematic.setActive(false);
    kinematicBody.moveKinematic(new Vector3(4, 0, 0), tilted);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(3, kinematic.getComponent(Transform3D.class).position.x, 1e-6f);
    assertEquals(1, physics.getTriggerObjectCount());
    kinematic.setActive(true);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(4, kinematic.getComponent(Transform3D.class).position.x, 1e-6f);
    assertEquals(1, physics.getTriggerObjectCount());

    BoxCollider3D staticSensor = new BoxCollider3D(1, 1, 1);
    staticSensor.setTrigger(true);
    GameObject fixed =
        body(game, "static-sensor", Vector3.Zero, RigidBody3D.Type.STATIC, 0, staticSensor);
    RigidBody3D fixedBody = fixed.getComponent(RigidBody3D.class);
    PhysicsFilter3D includeSensors = new PhysicsFilter3D(0xffff, 0xffff, true);
    assertTrue(
        physics.raycast(new Vector3(4, 0, 0), new Vector3(6, 0, 0), includeSensors).isEmpty());
    fixedBody.teleport(new Vector3(5, 0, 0), new Quaternion(), true);
    game.pause();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(0, fixed.getComponent(Transform3D.class).position.x, 1e-6f);
    game.resume();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(5, fixed.getComponent(Transform3D.class).position.x, 1e-6f);
    assertTrue(
        physics.raycast(new Vector3(4, 0, 0), new Vector3(6, 0, 0), includeSensors).isPresent());
    assertEquals(2, physics.getTriggerObjectCount());
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());

    Game soloGame = game(new Physics3D(Vector3.Zero));
    BoxCollider3D soloSensor = new BoxCollider3D(1, 1, 1);
    soloSensor.setTrigger(true);
    GameObject solo =
        body(soloGame, "solo", Vector3.Zero, RigidBody3D.Type.KINEMATIC, 0, soloSensor);
    Physics3D soloPhysics = soloGame.getSystem(Physics3D.class);
    soloGame.start();
    soloPhysics.refresh();
    assertEquals(0, soloPhysics.getBodyCount());
    assertNull(soloPhysics.getNativeBodyForTest(solo));
    solo.getComponent(RigidBody3D.class).moveKinematic(new Vector3(2, 0, 0), new Quaternion());
    soloGame.step(Physics3D.FIXED_STEP);
    assertEquals(2, solo.getComponent(Transform3D.class).position.x, 1e-6f);
    assertEquals(0, soloPhysics.getBodyCount());
    soloGame.close();
    assertEquals(soloPhysics.getNativeAllocationCount(), soloPhysics.getNativeDisposalCount());
  }

  @Test
  public void callbackQueriesUsePublishedFiltersUntilNextSynchronization() {
    Game game = game(new Physics3D(Vector3.Zero));
    BoxCollider3D sensor = new BoxCollider3D(2, 2, 2);
    sensor.setTrigger(true);
    body(game, "sensor", Vector3.Zero, null, 0, sensor);
    body(
        game,
        "mover",
        new Vector3(.5f, 0, 0),
        RigidBody3D.Type.DYNAMIC,
        1,
        new SphereCollider3D(.5f));
    BoxCollider3D wall = new BoxCollider3D(1, 1, 1);
    GameObject wallObject =
        body(game, "wall", new Vector3(5, 0, 0), RigidBody3D.Type.STATIC, 0, wall);
    Physics3D physics = game.getSystem(Physics3D.class);
    List<Boolean> callbackResults = new ArrayList<>();
    AtomicBoolean queried = new AtomicBoolean();
    sensor.onContact(
        c -> {
          if (queried.compareAndSet(false, true)) {
            wall.setFilter(1, 0);
            callbackResults.add(
                physics
                    .raycast(new Vector3(4, 0, 0), new Vector3(6, 0, 0), PhysicsFilter3D.all())
                    .map(h -> h.object() == wallObject)
                    .orElse(false));
            callbackResults.add(
                physics
                    .sweepSphere(
                        .2f, new Vector3(5, 0, 0), new Vector3(6, 0, 0), PhysicsFilter3D.all())
                    .map(h -> h.object() == wallObject)
                    .orElse(false));
          }
        });
    game.start();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(List.of(true, true), callbackResults);
    assertTrue(
        physics
            .raycast(new Vector3(4, 0, 0), new Vector3(6, 0, 0), PhysicsFilter3D.all())
            .isEmpty());
    assertTrue(
        physics
            .sweepSphere(.2f, new Vector3(5, 0, 0), new Vector3(6, 0, 0), PhysicsFilter3D.all())
            .isEmpty());
    game.close();
  }

  @Test
  public void removingContactPeerDeliversExitToTheSurvivingReceiverOnly() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject fixed =
        body(game, "fixed", Vector3.Zero, RigidBody3D.Type.STATIC, 0, new SphereCollider3D(.5f));
    GameObject moving =
        body(
            game,
            "moving",
            new Vector3(.8f, 0, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.5f));
    SphereCollider3D fixedCollider = fixed.getComponent(SphereCollider3D.class);
    SphereCollider3D movingCollider = moving.getComponent(SphereCollider3D.class);
    List<Contact3D.Phase> survivor = new ArrayList<>(), removed = new ArrayList<>();
    fixedCollider.onContact(c -> survivor.add(c.phase()));
    movingCollider.onContact(
        c -> {
          removed.add(c.phase());
          if (c.phase() == Contact3D.Phase.ENTER) moving.removeComponent(movingCollider);
        });
    game.start();
    game.step(2 * Physics3D.FIXED_STEP);
    assertEquals(List.of(Contact3D.Phase.ENTER, Contact3D.Phase.EXIT), survivor);
    assertEquals(List.of(Contact3D.Phase.ENTER), removed);
    game.close();
  }

  @Test
  public void triggerAllocationInsertionAndContactCallbackFailuresBalanceNativeOwnership() {
    AtomicBoolean fail = new AtomicBoolean();
    String[] stage = {"trigger-complete"};
    Physics3D physics =
        new Physics3D(
            Vector3.Zero,
            s -> {
              if (fail.get() && stage[0].equals(s))
                throw new IllegalStateException("injected " + s);
            });
    Game game = game(physics);
    GameObject owner =
        body(game, "owner", Vector3.Zero, RigidBody3D.Type.STATIC, 0, new SphereCollider3D(.5f));
    BoxCollider3D sensor = new BoxCollider3D(2, 2, 2);
    sensor.setTrigger(true);
    owner.addComponent(sensor);
    GameObject target =
        body(
            game,
            "target",
            new Vector3(.5f, 0, 0),
            RigidBody3D.Type.STATIC,
            0,
            new SphereCollider3D(.5f));
    game.start();
    long a = physics.getNativeAllocationCount(), d = physics.getNativeDisposalCount();
    fail.set(true);
    assertThrows(IllegalStateException.class, physics::refresh);
    assertEquals(physics.getNativeAllocationCount() - a, physics.getNativeDisposalCount() - d);
    fail.set(false);
    physics.refresh();
    assertEquals(3, physics.getNativeCollisionObjectCount());
    a = physics.getNativeAllocationCount();
    d = physics.getNativeDisposalCount();
    stage[0] = "trigger-world-insert";
    fail.set(true);
    sensor.setLocalPose(new Vector3(.1f, 0, 0), new Quaternion());
    assertThrows(IllegalStateException.class, physics::refresh);
    assertEquals(3, physics.getNativeCollisionObjectCount());
    assertEquals(physics.getNativeAllocationCount() - a, physics.getNativeDisposalCount() - d);
    fail.set(false);
    physics.refresh();
    a = physics.getNativeAllocationCount();
    d = physics.getNativeDisposalCount();
    stage[0] = "trigger-contact-callback";
    fail.set(true);
    assertThrows(IllegalStateException.class, () -> game.step(Physics3D.FIXED_STEP));
    assertEquals(physics.getNativeAllocationCount() - a, physics.getNativeDisposalCount() - d);
    fail.set(false);
    game.close();
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
    assertTrue(target.isDestroyed());
  }

  @Test
  public void closingTheGameFromAContactCallbackStopsDeliveryAndReleasesBothBindings() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject fixed =
        body(game, "fixed", Vector3.Zero, RigidBody3D.Type.STATIC, 0, new SphereCollider3D(.5f));
    body(
        game,
        "moving",
        new Vector3(.8f, 0, 0),
        RigidBody3D.Type.DYNAMIC,
        1,
        new SphereCollider3D(.5f));
    Physics3D physics = game.getSystem(Physics3D.class);
    List<Integer> delivered = new ArrayList<>();
    fixed
        .getComponent(SphereCollider3D.class)
        .onContact(
            c -> {
              delivered.add(1);
              game.close();
            });
    fixed.getComponent(SphereCollider3D.class).onContact(c -> delivered.add(2));
    game.start();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(List.of(1), delivered);
    assertEquals(0, physics.getNativeCollisionObjectCount());
    assertEquals(physics.getNativeAllocationCount(), physics.getNativeDisposalCount());
  }

  @Test
  public void forceAndImpulseAreConsumedOnceAndPausePreservesQueuedAction() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject ball =
        body(game, "ball", Vector3.Zero, RigidBody3D.Type.DYNAMIC, 2, new SphereCollider3D(.5f));
    RigidBody3D rigid = ball.getComponent(RigidBody3D.class);
    game.start();
    game.getSystem(Physics3D.class).refresh();
    rigid.applyImpulse(new Vector3(1, 0, 0));
    game.pause();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(0, game.getSystem(Physics3D.class).getFixedTick());
    assertEquals(0, rigid.getLinearVelocity().x, 1e-5f);
    game.resume();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(.5f, rigid.getLinearVelocity().x, .015f);
    rigid.applyForce(new Vector3(12, 0, 0));
    game.step(Physics3D.FIXED_STEP);
    assertEquals(.6f, rigid.getLinearVelocity().x, .02f);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(.6f, rigid.getLinearVelocity().x, .02f);
    game.close();
  }

  @Test
  public void equalElapsedTimeWithDifferentFrameDeltasAndKnownMassResponse() {
    Game game = game(new Physics3D(Vector3.Zero));
    Game coarse = game(new Physics3D(Vector3.Zero));
    Game mixed = game(new Physics3D(Vector3.Zero));
    GameObject light =
        body(
            game,
            "light",
            new Vector3(-3, 0, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.25f));
    GameObject heavy =
        body(
            game,
            "heavy",
            new Vector3(3, 0, 0),
            RigidBody3D.Type.DYNAMIC,
            2,
            new SphereCollider3D(.25f));
    GameObject coarseLight =
        body(
            coarse,
            "light",
            new Vector3(-3, 0, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.25f));
    GameObject mixedLight =
        body(
            mixed,
            "light",
            new Vector3(-3, 0, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.25f));
    RigidBody3D lightBody = light.getComponent(RigidBody3D.class),
        heavyBody = heavy.getComponent(RigidBody3D.class);
    Physics3D physics = game.getSystem(Physics3D.class),
        coarsePhysics = coarse.getSystem(Physics3D.class),
        mixedPhysics = mixed.getSystem(Physics3D.class);
    game.start();
    coarse.start();
    mixed.start();
    physics.refresh();
    coarsePhysics.refresh();
    mixedPhysics.refresh();
    lightBody.applyImpulse(new Vector3(1, 0, 0));
    heavyBody.applyImpulse(new Vector3(1, 0, 0));
    coarseLight.getComponent(RigidBody3D.class).applyImpulse(new Vector3(1, 0, 0));
    mixedLight.getComponent(RigidBody3D.class).applyImpulse(new Vector3(1, 0, 0));
    for (int i = 0; i < 12; i++) game.step(1f / 120f);
    for (int i = 0; i < 6; i++) coarse.step(1f / 60f);
    for (float delta : new float[] {1f / 120f, 1f / 40f, 1f / 120f, 1f / 40f, 1f / 60f, 1f / 60f})
      mixed.step(delta);
    assertEquals(6, physics.getFixedTick());
    assertEquals(6, coarsePhysics.getFixedTick());
    assertEquals(6, mixedPhysics.getFixedTick());
    assertEquals(1, lightBody.getLinearVelocity().x, .02f);
    assertEquals(.5f, heavyBody.getLinearVelocity().x, .02f);
    assertEquals(
        light.getComponent(Transform3D.class).position.x,
        coarseLight.getComponent(Transform3D.class).position.x,
        .002f);
    assertEquals(
        light.getComponent(Transform3D.class).position.x,
        mixedLight.getComponent(Transform3D.class).position.x,
        .002f);
    Vector3 lightAfterHalfSecond = light.getComponent(Transform3D.class).position.cpy();
    for (int i = 0; i < 48; i++) game.step(1f / 120f);
    Vector3 reference = light.getComponent(Transform3D.class).position.cpy();
    assertEquals(30, physics.getFixedTick());
    assertTrue(reference.x > lightAfterHalfSecond.x);
    game.close();
    coarse.close();
    mixed.close();
  }

  @Test
  public void dampingAngularVelocityAndRotatedTeleportUseNativeBodyState() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject object =
        body(game, "box", Vector3.Zero, RigidBody3D.Type.DYNAMIC, 2, new BoxCollider3D(1, 2, 3));
    RigidBody3D rigid = object.getComponent(RigidBody3D.class);
    rigid
        .setDamping(.5f, .25f)
        .setLinearVelocity(new Vector3(10, 0, 0))
        .setAngularVelocity(new Vector3(0, 1, 0));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    physics.refresh();
    Quaternion turn = new Quaternion(Vector3.Y, 90);
    rigid.teleport(new Vector3(4, 2, -1), turn, false);
    game.step(Physics3D.FIXED_STEP);
    assertTrue(object.getComponent(Transform3D.class).position.epsilonEquals(4.15f, 2, -1, .04f));
    assertTrue(Math.abs(object.getComponent(Transform3D.class).rotation.y) > .5f);
    assertTrue(rigid.getLinearVelocity().x < 10);
    assertTrue(Math.abs(rigid.getAngularVelocity().y) > 0);
    for (int i = 0; i < 60; i++) game.step(Physics3D.FIXED_STEP);
    assertTrue(rigid.getLinearVelocity().x < 9.5f);
    assertTrue(rigid.getAngularVelocity().y < 1);
    game.close();
  }

  @Test
  public void fixedStepTimeScaleAndBoundedBacklog() {
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = new Game();
    TimeManager time = new TimeManager();
    game.addSystem(time);
    game.addSystem(physics);
    body(game, "ball", Vector3.Zero, RigidBody3D.Type.DYNAMIC, 1, new SphereCollider3D(.5f));
    game.start();
    time.setBaseScale(0);
    game.step(.1f);
    assertEquals(0, physics.getFixedTick());
    time.setBaseScale(1);
    game.step(.1f);
    assertEquals(6, physics.getFixedTick());
    game.step(.1f);
    assertEquals(12, physics.getFixedTick());
    time.setBaseScale(4);
    game.step(.1f);
    assertEquals(20, physics.getFixedTick());
    assertTrue(physics.getDiscardedTimeSeconds() > .25);
    game.close();
  }

  @Test
  public void directWritesAndInvalidOwnershipAreRejected() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject object =
        body(game, "ball", Vector3.Zero, RigidBody3D.Type.DYNAMIC, 1, new SphereCollider3D(.5f));
    game.start();
    game.getSystem(Physics3D.class).refresh();
    object.getComponent(Transform3D.class).position.x = 1;
    assertThrows(IllegalStateException.class, () -> game.step(Physics3D.FIXED_STEP));
    object.getComponent(Transform3D.class).position.x = 0;
    object.getComponent(Transform3D.class).rotation.set(0, 0, 0, 2);
    assertThrows(IllegalStateException.class, () -> game.step(Physics3D.FIXED_STEP));
    game.close();
    assertThrows(IllegalArgumentException.class, () -> new CapsuleCollider3D(.5f, .9f));
    assertThrows(IllegalArgumentException.class, () -> new BoxCollider3D(1, Float.NaN, 1));
  }

  @Test
  public void transformRequirementsAndCompetingOwnersFailBeforeBodyAllocation() {
    Physics3D physics = new Physics3D(Vector3.Zero);
    Game game = game(physics);
    GameObject missing = game.createObject("missing-transform");
    missing.addComponent(new SphereCollider3D(.5f));
    game.start();
    assertThrows(IllegalStateException.class, physics::refresh);
    assertEquals(0, physics.getNativeCollisionObjectCount());
    game.close();

    for (int invalid = 0; invalid < 3; invalid++) {
      Physics3D candidatePhysics = new Physics3D(Vector3.Zero);
      Game candidate = game(candidatePhysics);
      GameObject object = candidate.createObject("invalid");
      Transform3D transform = object.addComponent(new Transform3D());
      object.addComponent(new RigidBody3D(RigidBody3D.Type.STATIC));
      object.addComponent(new BoxCollider3D(1, 1, 1));
      if (invalid == 0) transform.scale.set(-1, 1, 1);
      if (invalid == 1) transform.setParent(new Transform3D());
      if (invalid == 2) object.addComponent(new OtherMovementOwner());
      candidate.start();
      assertThrows(IllegalStateException.class, candidatePhysics::refresh);
      assertEquals(0, candidatePhysics.getNativeCollisionObjectCount());
      candidate.close();
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> new RigidBody3D(RigidBody3D.Type.DYNAMIC).setDamping(1.1f, 0));
  }

  @Test
  public void failedCandidateKeepsOldBodyAndTwoGamesRemainIndependent() {
    AtomicBoolean fail = new AtomicBoolean();
    String[] failureStage = {"complete"};
    Physics3D firstPhysics =
        new Physics3D(
            Vector3.Zero,
            stage -> {
              if (fail.get() && stage.equals(failureStage[0]))
                throw new IllegalStateException("injected");
            });
    Game first = game(firstPhysics);
    GameObject ball =
        body(first, "ball", Vector3.Zero, RigidBody3D.Type.DYNAMIC, 1, new SphereCollider3D(.5f));
    first.start();
    firstPhysics.refresh();
    assertEquals(1, firstPhysics.getNativeCollisionObjectCount());
    Game second = game(new Physics3D(new Vector3(0, -10, 0)));
    Physics3D secondPhysics = second.getSystem(Physics3D.class);
    GameObject other =
        body(
            second,
            "other",
            new Vector3(0, 5, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.5f));
    second.start();
    second.getSystem(Physics3D.class).refresh();
    ball.getComponent(SphereCollider3D.class)
        .setLocalPose(new Vector3(.1f, 0, 0), new Quaternion());
    fail.set(true);
    long allocated = firstPhysics.getNativeAllocationCount(),
        disposed = firstPhysics.getNativeDisposalCount();
    assertThrows(IllegalStateException.class, () -> first.step(Physics3D.FIXED_STEP));
    assertEquals(1, firstPhysics.getBodyCount());
    assertEquals(1, firstPhysics.getNativeCollisionObjectCount());
    assertEquals(
        firstPhysics.getNativeAllocationCount() - allocated,
        firstPhysics.getNativeDisposalCount() - disposed);
    fail.set(false);
    firstPhysics.refresh();
    allocated = firstPhysics.getNativeAllocationCount();
    disposed = firstPhysics.getNativeDisposalCount();
    failureStage[0] = "shape-child-1";
    ball.getComponent(SphereCollider3D.class)
        .setLocalPose(new Vector3(.2f, 0, 0), new Quaternion());
    fail.set(true);
    assertThrows(IllegalStateException.class, firstPhysics::refresh);
    assertEquals(1, firstPhysics.getBodyCount());
    assertEquals(1, firstPhysics.getNativeCollisionObjectCount());
    assertEquals(
        firstPhysics.getNativeAllocationCount() - allocated,
        firstPhysics.getNativeDisposalCount() - disposed);
    fail.set(false);
    firstPhysics.refresh();
    allocated = firstPhysics.getNativeAllocationCount();
    disposed = firstPhysics.getNativeDisposalCount();
    failureStage[0] = "world-insert";
    ball.getComponent(SphereCollider3D.class)
        .setLocalPose(new Vector3(.3f, 0, 0), new Quaternion());
    fail.set(true);
    assertThrows(IllegalStateException.class, firstPhysics::refresh);
    assertEquals(1, firstPhysics.getNativeCollisionObjectCount());
    fail.set(false);
    firstPhysics.refresh();
    first.step(Physics3D.FIXED_STEP);
    second.step(Physics3D.FIXED_STEP);
    assertEquals(1, firstPhysics.getBodyCount());
    assertEquals(1, second.getSystem(Physics3D.class).getBodyCount());
    assertTrue(other.getComponent(Transform3D.class).position.y < 5);
    second.close();
    first.step(Physics3D.FIXED_STEP);
    first.close();
    assertEquals(0, firstPhysics.getBodyCount());
    assertEquals(firstPhysics.getNativeAllocationCount(), firstPhysics.getNativeDisposalCount());
    assertEquals(secondPhysics.getNativeAllocationCount(), secondPhysics.getNativeDisposalCount());
    assertEquals(0, firstPhysics.getNativeCollisionObjectCount());
  }

  @Test
  public void nativeConstructionFailureAndCloseBeforeStartAreSafe() {
    Physics3D unused = new Physics3D(Vector3.Zero);
    Game notStarted = game(unused);
    notStarted.close();
    notStarted.close();
    for (String stage :
        new String[] {"collision-configuration", "dispatcher", "broadphase", "solver", "world"}) {
      Physics3D failing =
          new Physics3D(
              Vector3.Zero,
              s -> {
                if (stage.equals(s)) throw new IllegalStateException(stage);
              });
      Game game = game(failing);
      assertThrows(IllegalStateException.class, game::start);
      game.close();
      assertEquals(failing.getNativeAllocationCount(), failing.getNativeDisposalCount());
    }
  }

  @Test
  public void worldSpawnedPhysicsWaitsForNextInitializationBoundary() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject[] spawned = new GameObject[1];
    GameObject spawner = game.createObject("spawner");
    spawner.addComponent(
        new WorldAction(
            () -> {
              GameObject object = game.createObject("spawned");
              object.addComponent(new Transform3D());
              object.addComponent(new RigidBody3D(RigidBody3D.Type.DYNAMIC));
              object.addComponent(new SphereCollider3D(.5f));
              spawned[0] = object;
            }));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    game.step(Physics3D.FIXED_STEP);
    assertNotNull(spawned[0]);
    assertEquals(0, physics.getBodyCount());
    assertEquals(0, physics.getNativeCollisionObjectCount());
    game.step(Physics3D.FIXED_STEP);
    assertEquals(1, physics.getBodyCount());
    assertEquals(1, physics.getNativeCollisionObjectCount());
    game.close();
  }

  @Test
  public void pendingRigidAdditionToImplicitStaticWaitsWithoutOwnerConflict() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject object = game.createObject("implicit");
    object.addComponent(new Transform3D());
    object.addComponent(new SphereCollider3D(.5f));
    RigidBody3D[] replacement = new RigidBody3D[1];
    game.createObject("spawner")
        .addComponent(
            new WorldAction(
                () ->
                    replacement[0] =
                        object.addComponent(new RigidBody3D(RigidBody3D.Type.STATIC))));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    physics.refresh();
    game.step(Physics3D.FIXED_STEP);
    assertNotNull(replacement[0]);
    assertEquals(1, physics.getNativeCollisionObjectCount());
    assertEquals(1, physics.getBodyCount());
    game.step(Physics3D.FIXED_STEP);
    assertEquals(1, physics.getNativeCollisionObjectCount());
    assertEquals(1, physics.getBodyCount());
    game.close();
  }

  @Test
  public void pendingRigidReplacementKeepsPreviousNativeBodyUntilInitialized() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject object =
        body(game, "body", Vector3.Zero, RigidBody3D.Type.STATIC, 0, new SphereCollider3D(.5f));
    RigidBody3D original = object.getComponent(RigidBody3D.class);
    RigidBody3D[] replacement = new RigidBody3D[1];
    game.createObject("spawner")
        .addComponent(
            new WorldAction(
                () -> {
                  object.removeComponent(original);
                  replacement[0] = object.addComponent(new RigidBody3D(RigidBody3D.Type.KINEMATIC));
                }));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    physics.refresh();
    game.step(Physics3D.FIXED_STEP);
    assertNotNull(replacement[0]);
    assertEquals(1, physics.getNativeCollisionObjectCount());
    assertEquals(1, physics.getBodyCount());
    game.step(Physics3D.FIXED_STEP);
    assertEquals(1, physics.getNativeCollisionObjectCount());
    assertEquals(1, physics.getBodyCount());
    game.close();
  }

  @Test
  public void deactivationWithPendingColliderDetachesUntilReactivatedAndRetainsCommands() {
    for (boolean deactivateObject : new boolean[] {false, true}) {
      Game game = game(new Physics3D(Vector3.Zero));
      GameObject object =
          body(game, "body", Vector3.Zero, RigidBody3D.Type.DYNAMIC, 1, new SphereCollider3D(.5f));
      RigidBody3D rigid = object.getComponent(RigidBody3D.class);
      rigid.setLinearVelocity(new Vector3(2, 0, 0)).applyImpulse(new Vector3(1, 0, 0));
      game.createObject("spawner")
          .addComponent(
              new WorldAction(
                  () -> {
                    if (deactivateObject) object.setActive(false);
                    else rigid.setEnabled(false);
                    object.addComponent(new SphereCollider3D(.25f));
                  }));
      Physics3D physics = game.getSystem(Physics3D.class);
      game.start();
      physics.refresh();
      game.step(Physics3D.FIXED_STEP);
      assertEquals(0, physics.getNativeCollisionObjectCount());
      assertEquals(0, physics.getBodyCount());
      assertEquals(0, object.getComponent(Transform3D.class).position.x, 1e-6f);
      assertEquals(2, rigid.getLinearVelocity().x, 1e-6f);

      if (deactivateObject) object.setActive(true);
      else rigid.setEnabled(true);
      game.step(Physics3D.FIXED_STEP);
      assertEquals(1, physics.getNativeCollisionObjectCount());
      assertEquals(1, physics.getBodyCount());
      assertEquals(3, rigid.getLinearVelocity().x, .02f);
      assertEquals(.05f, object.getComponent(Transform3D.class).position.x, .003f);
      game.close();
    }
  }

  @Test
  public void derivedMassAndComposedOffsetOverflowRejectBeforeNativePublication() {
    Game supportedGame = game(new Physics3D(Vector3.Zero));
    body(
        supportedGame,
        "large-supported",
        Vector3.Zero,
        RigidBody3D.Type.DYNAMIC,
        1_000_000,
        new SphereCollider3D(1_000));
    Physics3D supportedPhysics = supportedGame.getSystem(Physics3D.class);
    supportedGame.start();
    supportedPhysics.refresh();
    assertEquals(1, supportedPhysics.getNativeCollisionObjectCount());
    supportedGame.close();

    Game overflowGame = game(new Physics3D(Vector3.Zero));
    body(
        overflowGame,
        "overflow-mass",
        Vector3.Zero,
        RigidBody3D.Type.DYNAMIC,
        3e38f,
        new SphereCollider3D(10));
    Physics3D overflowPhysics = overflowGame.getSystem(Physics3D.class);
    overflowGame.start();
    assertThrows(IllegalArgumentException.class, overflowPhysics::refresh);
    assertEquals(0, overflowPhysics.getNativeCollisionObjectCount());
    overflowGame.close();

    Game tinyGame = game(new Physics3D(Vector3.Zero));
    body(
        tinyGame,
        "tiny-mass",
        Vector3.Zero,
        RigidBody3D.Type.DYNAMIC,
        Float.MIN_VALUE,
        new SphereCollider3D(.5f));
    Physics3D tinyPhysics = tinyGame.getSystem(Physics3D.class);
    tinyGame.start();
    assertThrows(IllegalArgumentException.class, tinyPhysics::refresh);
    assertEquals(0, tinyPhysics.getNativeCollisionObjectCount());
    tinyGame.close();

    Game offsetGame = game(new Physics3D(Vector3.Zero));
    GameObject offset = offsetGame.createObject("overflow-offset");
    offset.addComponent(new Transform3D());
    offset.addComponent(
        new CompoundCollider3D(
                List.of(
                    new CompoundCollider3D.Sphere(.5f, new Vector3(3e38f, 0, 0), new Quaternion())))
            .setLocalPose(new Vector3(3e38f, 0, 0), new Quaternion()));
    Physics3D offsetPhysics = offsetGame.getSystem(Physics3D.class);
    offsetGame.start();
    assertThrows(IllegalArgumentException.class, offsetPhysics::refresh);
    assertEquals(0, offsetPhysics.getNativeCollisionObjectCount());
    offsetGame.close();
  }

  @Test
  public void rejectedNumericReplacementDisposesCandidateAndRetainsActualNativeBody() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject object =
        body(game, "body", Vector3.Zero, RigidBody3D.Type.DYNAMIC, 1, new SphereCollider3D(10));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    physics.refresh();
    Object originalNativeBody = physics.getNativeBodyForTest(object);
    long allocations = physics.getNativeAllocationCount();
    long disposals = physics.getNativeDisposalCount();
    object.getComponent(RigidBody3D.class).setMass(3e38f);
    object.getComponent(SphereCollider3D.class).setLocalPose(Vector3.Zero, new Quaternion());
    assertThrows(IllegalArgumentException.class, physics::refresh);
    assertSame(originalNativeBody, physics.getNativeBodyForTest(object));
    assertEquals(1, physics.getBodyCount());
    assertEquals(1, physics.getNativeCollisionObjectCount());
    assertEquals(
        physics.getNativeAllocationCount() - allocations,
        physics.getNativeDisposalCount() - disposals);
    object.getComponent(RigidBody3D.class).setMass(Float.MIN_VALUE);
    assertThrows(IllegalArgumentException.class, physics::refresh);
    assertSame(originalNativeBody, physics.getNativeBodyForTest(object));
    assertEquals(1, physics.getNativeCollisionObjectCount());
    assertEquals(
        physics.getNativeAllocationCount() - allocations,
        physics.getNativeDisposalCount() - disposals);
    game.close();
  }

  @Test
  public void compoundOffsetsAndReplacementTransformKeepTheNativePose() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject object = game.createObject("compound");
    Transform3D original = object.addComponent(new Transform3D());
    original.position.set(3, 2, 1);
    RigidBody3D rigid = object.addComponent(new RigidBody3D(RigidBody3D.Type.DYNAMIC));
    rigid.setMass(2).setLinearVelocity(new Vector3(1, 0, 0));
    object.addComponent(new BoxCollider3D(1, 1, 1));
    object.addComponent(
        new SphereCollider3D(.5f).setLocalPose(new Vector3(0, 1, 0), new Quaternion()));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    physics.refresh();
    game.step(Physics3D.FIXED_STEP);
    Vector3 before = original.position.cpy();
    assertTrue(before.x > 3);
    assertTrue(rigid.allowsInspectorTransformEdit() == false);
    object.removeComponent(original);
    Transform3D replacement = object.addComponent(new Transform3D());
    game.initializePendingComponents();
    physics.refresh();
    assertTrue(replacement.position.epsilonEquals(before, .02f));
    assertEquals(1, physics.getBodyCount());
    game.close();
  }

  @Test
  public void kinematicPlatformCarriesARealDynamicBodyAcrossSteps() {
    Game game = game(new Physics3D(new Vector3(0, -9.81f, 0)));
    body(
        game,
        "ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(20, 1, 20));
    GameObject platform =
        body(
            game,
            "platform",
            new Vector3(0, 1, 0),
            RigidBody3D.Type.KINEMATIC,
            0,
            new BoxCollider3D(4, .5f, 4));
    GameObject ball =
        body(
            game,
            "ball",
            new Vector3(0, 2, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.5f));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    physics.refresh();
    for (int i = 0; i < 90; i++) game.step(Physics3D.FIXED_STEP);
    float settledY = ball.getComponent(Transform3D.class).position.y;
    RigidBody3D mover = platform.getComponent(RigidBody3D.class);
    mover.moveKinematic(new Vector3(0, 2, 0), new Quaternion());
    game.step(Physics3D.FIXED_STEP);
    assertTrue(mover.getLinearVelocity().y > 0);
    game.step(Physics3D.FIXED_STEP);
    assertTrue(platform.getComponent(Transform3D.class).position.y > 1.9f);
    assertTrue(ball.getComponent(Transform3D.class).position.y > settledY + .05f);
    game.close();
  }

  @Test
  public void ccdFastSphereStopsAtThinNativeWall() {
    Game game = game(new Physics3D(Vector3.Zero));
    body(game, "wall", Vector3.Zero, RigidBody3D.Type.STATIC, 0, new BoxCollider3D(.05f, 10, 10));
    GameObject ball =
        body(
            game,
            "ball",
            new Vector3(-2, 0, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new SphereCollider3D(.1f));
    RigidBody3D rigid = ball.getComponent(RigidBody3D.class);
    rigid.setCcd(.05f, .09f).setLinearVelocity(new Vector3(300, 0, 0));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    physics.refresh();
    game.step(Physics3D.FIXED_STEP);
    assertTrue(
        "CCD sphere crossed the thin wall: " + ball.getComponent(Transform3D.class).position.x,
        ball.getComponent(Transform3D.class).position.x < 0);
    game.close();
  }

  @Test
  public void totalHeightYUpCapsuleRestsAtItsExpectedNativeExtent() {
    Game game = game(new Physics3D(new Vector3(0, -9.81f, 0)));
    body(
        game,
        "ground",
        new Vector3(0, -.5f, 0),
        RigidBody3D.Type.STATIC,
        0,
        new BoxCollider3D(20, 1, 20));
    GameObject capsule =
        body(
            game,
            "capsule",
            new Vector3(0, 3, 0),
            RigidBody3D.Type.DYNAMIC,
            1,
            new CapsuleCollider3D(.4f, 2));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    physics.refresh();
    for (int i = 0; i < 240; i++) game.step(Physics3D.FIXED_STEP);
    assertEquals(1, capsule.getComponent(Transform3D.class).position.y, .04f);
    game.close();
  }

  @Test
  public void disableReenableAndDestroyRetainDeterministicOwnership() {
    Game game = game(new Physics3D(Vector3.Zero));
    GameObject ball =
        body(game, "ball", Vector3.Zero, RigidBody3D.Type.DYNAMIC, 1, new SphereCollider3D(.5f));
    Physics3D physics = game.getSystem(Physics3D.class);
    game.start();
    physics.refresh();
    RigidBody3D rigid = ball.getComponent(RigidBody3D.class);
    rigid.setLinearVelocity(new Vector3(2, 0, 0));
    game.step(Physics3D.FIXED_STEP);
    float x = ball.getComponent(Transform3D.class).position.x;
    rigid.setEnabled(false);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(0, physics.getBodyCount());
    rigid.setEnabled(true);
    game.step(Physics3D.FIXED_STEP);
    assertEquals(1, physics.getBodyCount());
    assertTrue(ball.getComponent(Transform3D.class).position.x >= x);
    ball.destroy();
    game.step(Physics3D.FIXED_STEP);
    assertEquals(0, physics.getBodyCount());
    game.close();
    game.close();
    assertEquals(0, physics.getBodyCount());
  }
}
