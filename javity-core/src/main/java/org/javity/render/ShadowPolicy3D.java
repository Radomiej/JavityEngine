package org.javity.render;

import org.javity.core.Component;

/**
 * Explicit shadow participation for every ModelRenderer on this GameObject. The first attached
 * policy is authoritative; absent, disabled or uninitialized policy means neither cast nor receive.
 */
public final class ShadowPolicy3D extends Component {
  public boolean cast;
  public boolean receive;

  public ShadowPolicy3D() {}

  public ShadowPolicy3D(boolean cast, boolean receive) {
    this.cast = cast;
    this.receive = receive;
  }
}
