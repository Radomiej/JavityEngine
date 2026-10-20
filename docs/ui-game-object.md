# UiDocument panels on GameObjects

`UiDocumentComponent` mounts one declarative `UiDocument` as a panel on a caller-owned Scene2D
`Stage`. It is intended for a screen or panel, not one GameObject per UI node. Construct the
component on the UI/render owner thread with a Stage, a document, and a `Supplier<UiNode>` that
returns the panel tree. The component owns and closes the document; the Stage and the document's
`UiTheme` are borrowed and remain owned by the host.

When initialized, the component mounts the document and renders the supplier's current tree. It
refreshes that tree during the UI update phase using unscaled delta time, including while world
simulation is paused. `document()` is available for keyed actor access and `refreshPanel()` can be
called for an explicit update. All component/document operations must run on the thread that
constructed the component. This is a UI owner-thread contract, not a claim that headless tests
exercise native GL rendering.

The panel is visible and touchable only while its component is enabled and its GameObject is
active. Lifecycle activation notifications synchronize visibility on either state transition;
`syncVisibility()` is also available for explicit host synchronization. Hiding the panel clears
Stage keyboard, scroll and touch focus belonging to that subtree. Removal or scene close
unmounts and closes the document, even if the component never reached `onCreate`.
The Stage and theme are never disposed by the component.

Core components may register closeable resources with protected `own(resource)`. Resources close
once in reverse registration order. Disposal calls `onDestroy()` when initialized, then
`onRelease()`, then closes owned resources. Put subscription handles in `onRelease()` or register
them after the resources they use so subscriptions unregister before backing resources are closed.
Each cleanup phase runs even if an earlier phase throws, and later failures are suppressed onto the
first failure. ResourceScope does not dispatch cleanup to a resource's owner thread; component
disposal must run on the UI owner thread for components that own UI resources.

```java
// game, stage and theme belong to the host, on its UI/render thread.
UiDocument document = new UiDocument(theme);
UiDocumentComponent panel = game.createObject("HUD").addComponent(
    new UiDocumentComponent(stage, document, () -> new UiNode(
        "View", "hud", "p-4", Map.of(), List.of(new UiNode(
            "Text", "title", "", Map.of("text", "My game"), List.of())))));
game.start();
// Host frame: game.step(delta), stage.act(delta), stage.draw().
panel.getGameObject().setActive(false); // Immediately hides the mounted panel.
```

Close the game before the borrowed Stage and theme. A host can register `theme::dispose`, then
`stage::dispose`, then the `Game` in a `ResourceScope`, and close that scope during application
shutdown to enforce the reverse order even when a cleanup callback fails. Components allocate
an owned scope lazily on the first `own(...)`; components without owned resources do not allocate
one. This is an allocation-policy fact, not a measured RAM or FPS claim.

The existing `--ui-parity` native demo now mounts its panel through this component. Its smoke test
checks hidden/restored state on both owner-active and component-enabled transitions.
