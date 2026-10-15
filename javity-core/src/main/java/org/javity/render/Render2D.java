package org.javity.render;

import org.javity.core.Component;

/** Shared stable layer/order across sprites, text and shapes. */
public abstract class Render2D extends Component {
  public int layer, order;
}
