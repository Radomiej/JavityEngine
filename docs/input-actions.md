# Input actions

`InputActions` turns keyboard and host-fed device controls into pollable action state. It has no dependency on a gamepad library or native controller backend. The application owns device polling and forwards normalized samples to the controller.

```java
import com.badlogic.gdx.Input;
import org.javity.input.InputActions;
import org.javity.input.InputContext;

InputActions input = new InputActions();
input.bind("jump", Input.Keys.SPACE, Input.Keys.W);

InputContext gameplay = input.addContext("gameplay", 0);
gameplay.bindAxis("moveRight", "pad0", "leftX", 1, 0.18f)
    .bindButton("jump", "pad0", "south");
InputContext menu = input.addContext("menu", 100);
menu.bindKey("confirm", Input.Keys.ENTER)
    .bindButton("confirm", "pad0", "south")
    .consume("confirm");
menu.deactivate();

// Each frame, feed device samples before polling gameplay:
input.axisValue("pad0", "leftX", 0.65f); // replace with the host's current sample
input.buttonDown("pad0", "south");     // call on the host's button-down event
input.touchDown(3);                      // call on touch start; touchUp(3) on end

if (input.justPressed("jump")) System.out.println("jump edge");
System.out.println("horizontal value: " + input.value("moveRight"));
// Run Game.step(delta) here when using a Game; finishFrame follows it.
input.finishFrame();
```

Register contexts once and toggle them with `activate()` / `deactivate()`. Larger priorities are evaluated first; ties keep registration order. `consume(action)` blocks the held physical controls bound to that action from lower priority contexts, even when those contexts use different action names. For example, a menu's Enter-to-confirm binding can consume Enter before gameplay interprets it as jump. Consumption only applies while the bound control is held. A higher active context that supplies a nonzero value for an action takes precedence over lower contexts with that action. Deactivating a context immediately updates action state and can create release or press edges, including a press when an unchanged held source becomes visible in a lower context again.

Legacy `bind(action, key...)` remains available and stores keys in an initially active `default` context. Each action is down while any active binding supplies a nonzero value. Multiple keys and digital controls combine; an analog binding supplies its normalized magnitude for the selected direction, with digital input taking precedence by magnitude. `value(action)` returns the resulting value, and `axis(negative, positive)` retains the existing paired-action convenience.

Gamepad or other host adapters use `buttonDown(source, control)`, `buttonUp(source, control)` and `axisValue(source, axis, value)`. Names are host-defined, and axis values must be finite in `[-1, 1]`. Bind an axis with `bindAxis(action, source, axis, direction, deadzone)`: direction is `-1` or `1`, deadzone is in `[0, 1)`, and the remaining range is rescaled to `[0, 1]`. Combining both directions can be done with two action names and `axis(negative, positive)`.

Pointer buttons are fed with `pointerDown(pointer, button)` / `pointerUp(pointer, button)` and bind under source `pointer:<pointer>`. `InputActions` also receives libGDX `InputAdapter.touchDown`, `touchUp`, and `touchCancelled` callbacks and feeds both the pointer-button and touch source. Touch is a separate digital source: feed `touchDown(pointer)`, `touchUp(pointer)`, or `touchCancelled(pointer)` and bind to source `touch` with the pointer number as the control. `cancelTouches()` releases all touch controls.

`bind(action, keys...)` replaces that default-context action atomically. Rebinding to the same held key keeps the action down without a new edge. Rebinding to different keys releases the old held action immediately; pressing one of the new keys then emits `justPressed`. `InputContext.unbind(action)` and `clearBindings()` remove context bindings and update state immediately. `removeContext(name)` unregisters a context and releases actions it supplied; the initially active `default` context cannot be removed. `releaseAll()` clears all held inputs; call it on focus loss (`focusLost()` is an alias) so actions cannot remain stuck. Call `finishFrame()` after `Game.step` to clear only edge flags; down state remains available next frame.
