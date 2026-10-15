package org.javity.map;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import java.util.Objects;

/** Local XYZ PNG tiles under root/z/x/y.png. No network requests. */
public final class FileTileSource implements TileSource {
  private final FileHandle root;

  public FileTileSource(FileHandle root) {
    this.root = Objects.requireNonNull(root);
  }

  public Pixmap load(TileKey key) {
    FileHandle file = root.child(key.zoom() + "/" + key.x() + "/" + key.y() + ".png");
    return file.exists() ? new Pixmap(file) : null;
  }
}
