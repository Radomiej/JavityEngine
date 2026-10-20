package org.javity.serialization;

/** Host supplied lookup. Resolved objects remain owned by the host. */
@FunctionalInterface
public interface AssetResolver {
  <T> T resolve(AssetId<T> id);
}
