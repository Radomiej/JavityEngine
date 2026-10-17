package org.javity.components.reflection;

import org.javity.engine.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameObjectReferencesTest {
    public static class ParentLink extends JComponent { public JGameObject target; }
    public static class Link extends ParentLink { public JGameObject absent; public String text = "unchanged"; }

    @Test public void proxyRoundTripPreservesInheritedPublicReferences() {
        JGameObjectImpl owner = new JGameObjectImpl("owner");
        JGameObjectImpl target = new JGameObjectImpl("target");
        Link link = new Link();
        link.target = target;
        owner.addComponent(link);
        GameObjectProxator proxy = new GameObjectProxator();
        proxy.proxy(owner, new CustomScene());
        assertTrue(link.target instanceof GameObjectProxy);
        assertEquals(target.getObjectId(), link.target.getObjectId());
        assertNull(link.absent);
        assertEquals("unchanged", link.text);
        proxy.unproxy(owner, new CustomScene());
        assertSame(target, link.target);
    }
}
