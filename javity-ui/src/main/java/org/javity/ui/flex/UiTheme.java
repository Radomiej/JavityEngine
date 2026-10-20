package org.javity.ui.flex;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.badlogic.gdx.utils.Disposable;
import java.util.HashMap;
import java.util.Map;
import org.javity.ui.yoga.YogaBackends;
import org.javity.ui.yoga.YogaNode;

/** Owns shared UI textures and font. Dispose after the stage and its Flex tree. */
public final class UiTheme implements Disposable {
  private final Texture white;
  private final Pixmap whitePixel;
  private final BitmapFont font;
  private final boolean ownsFont;
  private final FileHandle trueTypeFile;
  private final String characters;
  private final Map<Integer, BitmapFont> sizeFonts = new HashMap<>();

  private record BorrowedFontCopy(float sourceX, float sourceY, BitmapFont font) {}

  private final Map<Float, BorrowedFontCopy> scaledBorrowedFonts = new HashMap<>();
  private final Map<String, TextureRegion> images = new HashMap<>();
  private RectanglePainter rectanglePainter;

  public UiTheme() {
    this(
        Gdx.files.classpath("org/javity/ui/fonts/OpenSans-Regular.ttf"),
        16,
        FreeTypeFontGenerator.DEFAULT_CHARS + "ĄĆĘŁŃÓŚŹŻąćęłńóśźż·—");
  }

  private UiTheme(FileHandle file, int size, String characters) {
    this(generateFont(file, size, characters), true, file, characters);
    sizeFonts.put(size, font);
    float lineHeight = lineHeightFor(size);
    if (lineHeight > 0) font.getData().setLineHeight(lineHeight);
  }

  /** Inject a bitmap font; caller chooses whether this theme owns it. */
  public UiTheme(BitmapFont font, boolean ownsFont) {
    this(font, ownsFont, null, null);
  }

  private UiTheme(BitmapFont font, boolean ownsFont, FileHandle trueTypeFile, String characters) {
    this.font = java.util.Objects.requireNonNull(font);
    this.ownsFont = ownsFont;
    this.trueTypeFile = trueTypeFile;
    this.characters = characters;
    Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixel.setColor(Color.WHITE);
    pixel.fill();
    if (Gdx.app == null) {
      IllegalStateException failure =
          new IllegalStateException("UiTheme requires a libGDX app for managed texture recovery");
      disposeAfterFailure(pixel, failure);
      if (ownsFont) disposeAfterFailure(font, failure);
      throw failure;
    }
    Texture createdWhite;
    try {
      createdWhite =
          new Texture(new PixmapTextureData(pixel, pixel.getFormat(), false, false, true));
    } catch (RuntimeException | Error failure) {
      disposeAfterFailure(pixel, failure);
      if (ownsFont) disposeAfterFailure(font, failure);
      throw failure;
    }
    white = createdWhite;
    whitePixel = pixel;
  }

  /** TTF/OTF generated once on the GL thread; font belongs to the theme. */
  public static UiTheme fromTrueType(FileHandle file, int size) {
    return new UiTheme(file, size, FreeTypeFontGenerator.DEFAULT_CHARS + "ĄĆĘŁŃÓŚŹŻąćęłńóśźż·—");
  }

  /** Explicit glyph set, e.g. additional languages. Rasterized once, not incrementally. */
  public static UiTheme fromTrueType(FileHandle file, int size, String characters) {
    return new UiTheme(file, size, java.util.Objects.requireNonNull(characters));
  }

  private static BitmapFont generateFont(FileHandle file, int size) {
    return generateFont(file, size, FreeTypeFontGenerator.DEFAULT_CHARS + "ĄĆĘŁŃÓŚŹŻąćęłńóśźż·—");
  }

  private static BitmapFont generateFont(FileHandle file, int size, String characters) {
    if (size <= 0) throw new IllegalArgumentException("Positive font size required");
    FreeTypeFontGenerator generator = new FreeTypeFontGenerator(file);
    try {
      var parameters = new FreeTypeFontGenerator.FreeTypeFontParameter();
      parameters.size = size;
      parameters.characters = characters;
      parameters.kerning = true;
      parameters.minFilter = Texture.TextureFilter.Linear;
      parameters.magFilter = Texture.TextureFilter.Linear;
      SubpixelFontData data = SubpixelFontData.read(file, size, characters);
      BitmapFont generated = generator.generateFont(parameters, data);
      generated.setUseIntegerPositions(false);
      return generated;
    } finally {
      generator.dispose();
    }
  }

  public BitmapFont getFont() {
    return font;
  }

  Texture white() {
    return white;
  }

  UtilityStyles.Visual visual(String classes) {
    return visual(classes, "");
  }

  private UtilityStyles.Visual visual(String classes, String state) {
    YogaNode node = YogaBackends.createDefault().createNode();
    try {
      return UtilityStyles.apply(node, classes, state);
    } finally {
      node.freeNode();
    }
  }

  /** Resolves the documented utility vocabulary for hosts building custom controls. */
  public UtilityStyles.Visual visualFor(String classes) {
    return visual(classes);
  }

  /** Resolves a utility string for one live native state such as focus, hover or disabled. */
  public UtilityStyles.Visual visualFor(String classes, String state) {
    return visual(classes, state == null ? "" : state);
  }

  public Rectangle rectangle(String classes) {
    return new Rectangle(this, classes);
  }

  public void drawRectangle(
      Batch batch, float x, float y, float width, float height, UtilityStyles.Visual style) {
    if (rectanglePainter == null) rectanglePainter = new RectanglePainter();
    rectanglePainter.draw(batch, white, x, y, width, height, style);
  }

  /** Registers a named image source by borrowing both the region and its texture. */
  public void registerImage(String name, TextureRegion region) {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Image name is required");
    if (region == null || region.getTexture() == null)
      throw new IllegalArgumentException("Image region and texture are required");
    if (region.getRegionWidth() <= 0 || region.getRegionHeight() <= 0)
      throw new IllegalArgumentException("Image region must have positive dimensions");
    images.put(name, region);
  }

  /** Removes a name from future lookups without disposing its borrowed region or texture. */
  public boolean unregisterImage(String name) {
    return images.remove(name) != null;
  }

  /** Returns a borrowed registered image region or rejects an unknown source name. */
  public TextureRegion lookupImage(String name) {
    TextureRegion region = images.get(name);
    if (region == null)
      throw new IllegalArgumentException("Unknown registered image source: " + name);
    return region;
  }

  void drawImage(
      Batch batch,
      TextureRegion region,
      float x,
      float y,
      float width,
      float height,
      UtilityStyles.Visual style,
      float clipX,
      float clipY,
      float clipWidth,
      float clipHeight,
      float clipRadius,
      float imageX,
      float imageY,
      float imageWidth,
      float imageHeight,
      float u,
      float v,
      float u2,
      float v2) {
    if (rectanglePainter == null) rectanglePainter = new RectanglePainter();
    rectanglePainter.drawImage(
        batch,
        region,
        x,
        y,
        width,
        height,
        style,
        clipX,
        clipY,
        clipWidth,
        clipHeight,
        clipRadius,
        imageX,
        imageY,
        imageWidth,
        imageHeight,
        u,
        v,
        u2,
        v2);
  }

  public Label label(String text, String classes) {
    return label(text, classes, false);
  }

  public Label label(String text, String classes, boolean wrap) {
    var style = visual(classes);
    Label label =
        new TailwindLabel(
            text,
            labelStyle(style, classes, wrap),
            style.lineHeight(),
            UtilityStyles.padding(classes),
            wrap);
    if (!wrap) setLabelScale(label, trueTypeFile == null ? style.textScale() : 1f);
    return label;
  }

  public void updateLabel(Label label, String classes) {
    updateLabel(label, classes, label instanceof TailwindLabel text && text.isWrap());
  }

  public void updateLabel(Label label, String classes, boolean wrap) {
    var style = visual(classes);
    label.setStyle(labelStyle(style, classes, wrap));
    if (!wrap) setLabelScale(label, trueTypeFile == null ? style.textScale() : 1f);
    if (label instanceof TailwindLabel tailwindLabel)
      tailwindLabel.update(style.lineHeight(), UtilityStyles.padding(classes), wrap);
  }

  private static void setLabelScale(Label label, float relativeScale) {
    BitmapFont font = label.getStyle().font;
    label.setFontScale(font.getScaleX() * relativeScale, font.getScaleY() * relativeScale);
  }

  private Label.LabelStyle labelStyle(UtilityStyles.Visual visual, String classes) {
    return labelStyle(visual, classes, false);
  }

  private Label.LabelStyle labelStyle(UtilityStyles.Visual visual, String classes, boolean wrap) {
    UtilityStyles.Insets padding = UtilityStyles.padding(classes);
    float border = visual.borderWidth();
    RectangleDrawable background = new RectangleDrawable(this, visual);
    background.setLeftWidth(border + padding.left());
    background.setRightWidth(border + padding.right());
    background.setTopHeight(border + padding.top());
    background.setBottomHeight(border + padding.bottom());
    var style =
        new Label.LabelStyle(wrap ? wrappedFontFor(visual) : fontFor(visual), visual.text());
    style.background = background;
    return style;
  }

  public TextButton button(String text, String classes, Runnable onClick) {
    TextButton button = button(text, classes);
    button.addListener(
        new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            onClick.run();
          }
        });
    return button;
  }

  /** Creates a button without an event listener, for hosts that own listener lifetimes. */
  public TextButton button(String text, String classes) {
    var visual = visual(classes);
    TextButton button =
        new TailwindTextButton(text, buttonStyle(visual, classes), visual.lineHeight());
    setLabelScale(button.getLabel(), trueTypeFile == null ? visual.textScale() : 1f);
    return button;
  }

  public void updateButton(TextButton button, String classes) {
    UtilityStyles.Visual visual = visual(classes);
    button.setStyle(buttonStyle(visual, classes));
    setLabelScale(button.getLabel(), trueTypeFile == null ? visual.textScale() : 1f);
    if (button instanceof TailwindTextButton tailwindButton)
      tailwindButton.setLineBoxHeight(visual.lineHeight());
  }

  private TextButton.TextButtonStyle buttonStyle(UtilityStyles.Visual visual, String classes) {
    Color color = visual.background() == null ? Color.CLEAR : visual.background();
    var style = new TextButton.TextButtonStyle();
    style.font = fontFor(visual);
    style.fontColor = visual.text();
    style.up = controlDrawable(visual.withBackground(color), classes);
    style.over = stateDrawable(classes, "hover", visual.withBackground(color));
    style.down = stateDrawable(classes, "active", visual.withBackground(color));
    style.focused = stateDrawable(classes, "focus", visual.withBackground(color));
    style.disabled = stateDrawable(classes, "disabled", visual.withBackground(color));
    style.disabledFontColor = stateVisual(classes, "disabled", visual).text();
    style.overFontColor = stateVisual(classes, "hover", visual).text();
    style.downFontColor = stateVisual(classes, "active", visual).text();
    style.focusedFontColor = stateVisual(classes, "focus", visual).text();
    return style;
  }

  private RectangleDrawable stateDrawable(String classes, String state, UtilityStyles.Visual base) {
    UtilityStyles.Visual changed = stateVisual(classes, state, base);
    if (changed == base
        || changed.background() == base.background()
            && changed.text().equals(base.text())
            && changed.borderColor().equals(base.borderColor())) return null;
    Color fill = changed.background() == null ? base.background() : changed.background();
    return controlDrawable(
        new UtilityStyles.Visual(
            fill,
            changed.text(),
            changed.textScale(),
            changed.fontSize(),
            changed.lineHeight(),
            changed.radius(),
            changed.borderWidth(),
            changed.borderColor()),
        classes);
  }

  private RectangleDrawable controlDrawable(UtilityStyles.Visual visual, String classes) {
    UtilityStyles.Insets padding = UtilityStyles.padding(classes);
    RectangleDrawable drawable = new RectangleDrawable(this, visual);
    float border = visual.borderWidth();
    drawable.setLeftWidth(border + padding.left());
    drawable.setRightWidth(border + padding.right());
    drawable.setTopHeight(border + padding.top());
    drawable.setBottomHeight(border + padding.bottom());
    return drawable;
  }

  private UtilityStyles.Visual stateVisual(
      String classes, String state, UtilityStyles.Visual base) {
    YogaNode node = YogaBackends.createDefault().createNode();
    try {
      return UtilityStyles.apply(node, classes, state);
    } finally {
      node.freeNode();
    }
  }

  /** Creates a text field whose textures and font remain owned by this theme. */
  public TextField textField(String value, String classes) {
    var visual = visual(classes);
    TextField field = new TextField(value == null ? "" : value, textFieldStyle(visual, classes));
    field.setMessageText("");
    return field;
  }

  public void updateTextField(TextField field, String classes) {
    updateTextField(field, classes, "");
  }

  public void updateTextField(TextField field, String classes, String state) {
    field.setStyle(textFieldStyle(visual(classes), classes, state));
  }

  private TextField.TextFieldStyle textFieldStyle(UtilityStyles.Visual visual, String classes) {
    return textFieldStyle(visual, classes, "");
  }

  private TextField.TextFieldStyle textFieldStyle(
      UtilityStyles.Visual visual, String classes, String state) {
    Color background = visual.background() == null ? Color.CLEAR : visual.background();
    UtilityStyles.Visual active = state.isEmpty() ? visual : stateVisual(classes, state, visual);
    var style = new TextField.TextFieldStyle();
    style.font = fontFor(visual);
    style.fontColor = active.text();
    style.focusedFontColor = stateVisual(classes, "focus", visual).text();
    style.disabledFontColor = stateVisual(classes, "disabled", visual).text();
    style.cursor = new RectangleDrawable(this, visual.withBackground(visual.text()));
    style.selection =
        new RectangleDrawable(this, visual.withBackground(new Color(0.2f, .45f, .85f, .65f)));
    UtilityStyles.Visual ordinary =
        active.background() == null ? active.withBackground(background) : active;
    style.background = controlDrawable(ordinary, classes);
    RectangleDrawable focused = stateDrawable(classes, "focus", visual.withBackground(background));
    style.focusedBackground = focused == null ? style.background : focused;
    RectangleDrawable disabled =
        stateDrawable(classes, "disabled", visual.withBackground(background));
    style.disabledBackground = disabled == null ? style.background : disabled;
    return style;
  }

  /** Scroll pane using shared theme textures and a Yoga-backed content view. */
  public ScrollPane scrollPane(WidgetGroup content, String classes) {
    var visual = visual(classes);
    var style = scrollPaneStyle(visual);
    ScrollPane pane = new ScrollPane(content, style);
    pane.setFadeScrollBars(false);
    return pane;
  }

  public void updateScrollPane(ScrollPane pane, String classes) {
    pane.setStyle(scrollPaneStyle(visual(classes)));
  }

  private ScrollPane.ScrollPaneStyle scrollPaneStyle(UtilityStyles.Visual visual) {
    var style = new ScrollPane.ScrollPaneStyle();
    style.background = new RectangleDrawable(this, visual.withBackground(visual.background()));
    Color thumb = visual.text();
    Color track = visual.background() == null ? Color.CLEAR : visual.background();
    style.vScroll = new RectangleDrawable(this, scrollbarVisual(track));
    style.vScrollKnob = new RectangleDrawable(this, scrollbarVisual(thumb));
    style.vScroll.setMinWidth(6);
    style.vScrollKnob.setMinWidth(6);
    style.vScrollKnob.setMinHeight(8);
    style.hScroll = new RectangleDrawable(this, scrollbarVisual(track));
    style.hScrollKnob = new RectangleDrawable(this, scrollbarVisual(thumb));
    style.hScroll.setMinHeight(6);
    style.hScrollKnob.setMinHeight(6);
    style.hScrollKnob.setMinWidth(8);
    return style;
  }

  private UtilityStyles.Visual scrollbarVisual(Color color) {
    return new UtilityStyles.Visual(
        color, color, 1, 16, 24, Float.POSITIVE_INFINITY, 0, Color.CLEAR);
  }

  private static void disposeAfterFailure(Disposable resource, Throwable failure) {
    try {
      resource.dispose();
    } catch (RuntimeException | Error cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }

  private static Throwable dispose(Disposable resource, Throwable failure) {
    try {
      resource.dispose();
    } catch (RuntimeException | Error error) {
      if (failure == null) return error;
      if (failure != error) failure.addSuppressed(error);
    }
    return failure;
  }

  @Override
  public void dispose() {
    Throwable failure = null;
    if (rectanglePainter != null) failure = dispose(rectanglePainter, failure);
    images.clear();
    for (BorrowedFontCopy cached : scaledBorrowedFonts.values())
      failure = dispose(cached.font(), failure);
    scaledBorrowedFonts.clear();
    if (ownsFont) {
      for (BitmapFont cached : sizeFonts.values())
        if (cached != font) failure = dispose(cached, failure);
      failure = dispose(font, failure);
    }
    failure = dispose(white, failure);
    failure = dispose(whitePixel, failure);
    if (failure instanceof RuntimeException error) throw error;
    if (failure instanceof Error error) throw error;
  }

  private BitmapFont fontFor(UtilityStyles.Visual visual) {
    if (trueTypeFile == null) return font;
    int size = Math.max(1, Math.round(visual.fontSize()));
    BitmapFont cached = sizeFonts.get(size);
    if (cached == null) {
      cached = generateFont(trueTypeFile, size, characters);
      float lineHeight = lineHeightFor(size);
      if (lineHeight > 0) cached.getData().setLineHeight(lineHeight);
      sizeFonts.put(size, cached);
    }
    return cached;
  }

  private BitmapFont wrappedFontFor(UtilityStyles.Visual visual) {
    BitmapFont base = fontFor(visual);
    if (trueTypeFile != null || visual.textScale() == 1f) return base;
    float relativeScale = visual.textScale();
    BorrowedFontCopy cached = scaledBorrowedFonts.get(relativeScale);
    if (cached != null
        && cached.sourceX() == base.getScaleX()
        && cached.sourceY() == base.getScaleY()) return cached.font();
    BitmapFont copy = copyScaledFont(base, relativeScale);
    // Keep only the current copy per style. Copies own neither textures nor native resources;
    // live labels retain older copies themselves, so eviction drops only this cache reference.
    scaledBorrowedFonts.put(
        relativeScale, new BorrowedFontCopy(base.getScaleX(), base.getScaleY(), copy));
    return copy;
  }

  /** Keeps wrapped per-style scale out of the caller-owned BitmapFontData. */
  static BitmapFont copyScaledFont(BitmapFont source, float scale) {
    BitmapFont.BitmapFontData copy = copyScaledFontData(source.getData(), scale);
    BitmapFont scaled = new BitmapFont(copy, source.getRegions(), source.usesIntegerPositions());
    scaled.setOwnsTexture(false);
    return scaled;
  }

  static BitmapFont.BitmapFontData copyScaledFontData(
      BitmapFont.BitmapFontData original, float scale) {
    BitmapFont.BitmapFontData copy;
    if (original instanceof SubpixelFontData fractional) copy = fractional.copyMetrics();
    else copy = new BitmapFont.BitmapFontData();
    copy.name = original.name;
    copy.imagePaths = original.imagePaths == null ? null : original.imagePaths.clone();
    copy.fontFile = original.fontFile;
    copy.flipped = original.flipped;
    copy.padTop = original.padTop;
    copy.padRight = original.padRight;
    copy.padBottom = original.padBottom;
    copy.padLeft = original.padLeft;
    copy.lineHeight = original.lineHeight;
    copy.capHeight = original.capHeight;
    copy.ascent = original.ascent;
    copy.descent = original.descent;
    copy.down = original.down;
    copy.blankLineScale = original.blankLineScale;
    copy.markupEnabled = original.markupEnabled;
    copy.spaceXadvance = original.spaceXadvance;
    copy.xHeight = original.xHeight;
    copy.breakChars = original.breakChars == null ? null : original.breakChars.clone();
    copy.xChars = original.xChars == null ? null : original.xChars.clone();
    copy.capChars = original.capChars == null ? null : original.capChars.clone();
    // The copied metrics are already at original.scaleX/Y. Initialize those fields before
    // BitmapFontData.setScale applies the additional visual scale to the copied metrics.
    copy.scaleX = original.scaleX;
    copy.scaleY = original.scaleY;
    copy.setScale(original.scaleX * scale, original.scaleY * scale);
    for (int page = 0; original.glyphs != null && page < original.glyphs.length; page++) {
      BitmapFont.Glyph[] glyphPage = original.glyphs[page];
      if (glyphPage == null) continue;
      for (int id = 0; id < glyphPage.length; id++) {
        BitmapFont.Glyph glyph = glyphPage[id];
        if (glyph != null) copy.setGlyph((char) glyph.id, copyGlyph(glyph));
      }
    }
    if (original.missingGlyph != null) {
      copy.missingGlyph = copy.getGlyph((char) original.missingGlyph.id);
      if (copy.missingGlyph == null) copy.missingGlyph = copyGlyph(original.missingGlyph);
    }
    return copy;
  }

  private static BitmapFont.Glyph copyGlyph(BitmapFont.Glyph source) {
    BitmapFont.Glyph copy = new BitmapFont.Glyph();
    copy.id = source.id;
    copy.srcX = source.srcX;
    copy.srcY = source.srcY;
    copy.width = source.width;
    copy.height = source.height;
    copy.u = source.u;
    copy.v = source.v;
    copy.u2 = source.u2;
    copy.v2 = source.v2;
    copy.xoffset = source.xoffset;
    copy.yoffset = source.yoffset;
    copy.xadvance = source.xadvance;
    copy.kerning = source.kerning == null ? null : source.kerning.clone();
    if (copy.kerning != null)
      for (int i = 0; i < copy.kerning.length; i++)
        if (copy.kerning[i] != null) copy.kerning[i] = copy.kerning[i].clone();
    copy.fixedWidth = source.fixedWidth;
    copy.page = source.page;
    return copy;
  }

  private static float lineHeightFor(int size) {
    return switch (size) {
      case 14 -> 20;
      case 16 -> 24;
      case 18, 20 -> 28;
      case 24 -> 32;
      default -> -1;
    };
  }

  private static final class TailwindLabel extends Label implements YogaMeasuredActor {
    private float lineBoxHeight;
    private UtilityStyles.Insets padding;
    private boolean wrap;
    private final GlyphLayout scratch = new GlyphLayout();

    TailwindLabel(
        CharSequence text,
        LabelStyle style,
        float lineBoxHeight,
        UtilityStyles.Insets padding,
        boolean wrap) {
      super(text, style);
      this.lineBoxHeight = lineBoxHeight;
      this.padding = padding;
      this.wrap = wrap;
    }

    boolean isWrap() {
      return wrap;
    }

    void update(float value, UtilityStyles.Insets insets, boolean wrapped) {
      lineBoxHeight = value;
      padding = insets;
      wrap = wrapped;
      invalidateHierarchy();
    }

    @Override
    public Size measureForYoga(float width, int widthMode) {
      java.util.List<String> lines = lines(width, widthMode);
      float maximum = 0;
      for (String line : lines) maximum = Math.max(maximum, textWidth(line));
      return new Size(maximum, lines.size() * lineBoxHeight, lines);
    }

    private java.util.List<String> lines(float width, int widthMode) {
      String value = getText().toString().replace("\r\n", "\n").replace('\r', '\n');
      java.util.List<String> result = new java.util.ArrayList<>();
      boolean trailingBreak = value.endsWith("\n");
      String[] paragraphs = value.split("\n", -1);
      float maxWidth = Float.isFinite(width) ? width : Float.POSITIVE_INFINITY;
      boolean constrained = widthMode != 0 && Float.isFinite(maxWidth);
      for (int paragraphIndex = 0; paragraphIndex < paragraphs.length; paragraphIndex++) {
        if (trailingBreak && paragraphIndex == paragraphs.length - 1) break;
        String paragraph = paragraphs[paragraphIndex];
        if (!wrap || !constrained || !Float.isFinite(maxWidth)) {
          result.add(wrap ? collapseWhitespace(paragraph) : paragraph);
          continue;
        }
        String normalized = collapseWhitespace(paragraph);
        if (normalized.isEmpty()) {
          result.add("");
          continue;
        }
        String current = "";
        for (String word : normalized.split(" ")) {
          String candidate = current.isEmpty() ? word : current + " " + word;
          if (!current.isEmpty() && textWidth(candidate) > maxWidth) {
            result.add(current);
            current = word; // Long words stay intact and may overflow, like HTML normal whitespace.
          } else current = candidate;
        }
        result.add(current);
      }
      if (result.isEmpty()) result.add("");
      return result;
    }

    private static String collapseWhitespace(String value) {
      return value.trim().replaceAll("[\\t\\f\\u000B ]+", " ");
    }

    private float textWidth(String text) {
      if (text.isEmpty()) return 0;
      scratch.setText(getStyle().font, text);
      return scratch.width * (wrap ? 1f : getFontScaleX() / getStyle().font.getScaleX());
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
      if (!wrap) {
        super.draw(batch, parentAlpha);
        return;
      }
      Drawable background = getStyle().background;
      float savedBatchColor = batch.getPackedColor();
      if (background != null) {
        Color actorColor = getColor();
        batch.setColor(actorColor.r, actorColor.g, actorColor.b, actorColor.a * parentAlpha);
        try {
          background.draw(batch, getX(), getY(), getWidth(), getHeight());
        } finally {
          batch.setPackedColor(savedBatchColor);
        }
      }
      float horizontalInsets = padding.horizontal() + 2 * borderInset();
      float verticalInsets = padding.vertical() + 2 * borderInset();
      float contentWidth = Math.max(0, getWidth() - horizontalInsets);
      java.util.List<String> lines = lines(contentWidth, 2);
      BitmapFont font = getStyle().font;
      Color oldFontColor = font.getColor().cpy();
      try {
        Color styleColor = getStyle().fontColor == null ? Color.WHITE : getStyle().fontColor;
        Color actorColor = getColor();
        font.setColor(
            styleColor.r * actorColor.r,
            styleColor.g * actorColor.g,
            styleColor.b * actorColor.b,
            styleColor.a * actorColor.a * parentAlpha);
        float total = lines.size() * lineBoxHeight;
        float top = getY() + getHeight() - padding.top() - borderInset();
        float baseline =
            top
                - Math.max(0, (getHeight() - verticalInsets - total) / 2)
                - font.getCapHeight()
                // Browser line boxes distribute leading above and below the cap-height; center
                // the native glyph band similarly instead of anchoring its cap edge to the box.
                + font.getCapHeight() / 2;
        float x = getX() + padding.left() + borderInset();
        for (String line : lines) {
          font.draw(batch, line, x, baseline);
          baseline -= lineBoxHeight;
        }
      } finally {
        font.setColor(oldFontColor);
      }
    }

    private float borderInset() {
      Drawable background = getStyle().background;
      if (background instanceof RectangleDrawable rectangle)
        return rectangle.getLeftWidth() - padding.left();
      return 0;
    }

    @Override
    public float getPrefWidth() {
      if (wrap) return measureForYoga(Float.NaN, YogaNode.MEASURE_UNDEFINED).width();
      return super.getPrefWidth();
    }

    @Override
    public float getPrefHeight() {
      if (wrap) return measureForYoga(Float.NaN, YogaNode.MEASURE_UNDEFINED).height();
      return Math.max(super.getPrefHeight(), lineBoxHeight);
    }
  }

  private static final class TailwindTextButton extends TextButton {
    private float lineBoxHeight;

    TailwindTextButton(String text, TextButtonStyle style, float lineBoxHeight) {
      super(text, style);
      this.lineBoxHeight = lineBoxHeight;
    }

    void setLineBoxHeight(float value) {
      lineBoxHeight = value;
      invalidateHierarchy();
    }

    @Override
    public float getPrefHeight() {
      var background = getStyle().up;
      return lineBoxHeight
          + (background == null ? 0 : background.getTopHeight() + background.getBottomHeight());
    }
  }
}
