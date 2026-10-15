package org.javity.components;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpineConfigurationTest {
 @Test public void pathWithoutSkinUsesDefault(){var renderer=new SpineRenderer("animations/skeleton.json");assertEquals("animations/skeleton.json",renderer.getSpine().getResourcePath());assertEquals("default",renderer.getSpine().getSkinName());}
 @Test public void optionalSkinAndEmptySkinAreSafe(){assertEquals("hero",new SpineRenderer("skeleton.json#hero").getSpine().getSkinName());assertEquals("default",new SpineRenderer("skeleton.json#").getSpine().getSkinName());}
}
