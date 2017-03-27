package org.javity.ui;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.javity.engine.JComponent;
import org.javity.engine.JGameObject;
import org.javity.engine.JResources;
import org.javity.engine.JSceneManager;
import org.javity.engine.NativeComponent;
import org.javity.engine.gui.JTextField;
import org.javity.engine.resources.SingleSpriteResource;
import org.javity.engine.resources.SpriteAtlasResource;
import org.javity.engine.resources.SpriteResource;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.XmlReader.Element;

import galaxy.rapid.common.DrawableHelper;
import galaxy.rapid.components.ActorComponent;

public class JXmlUi extends NativeComponent {
	private Map<String, Actor> actorsMap = new HashMap<String, Actor>();
	private ActorComponent actorComponent;
	private Actor rootActor;
	private Skin skin;

	public String xmlPath;
	public String skinPath;


	public static JGameObject loadUi(String xmlPath) {
		JGameObject gameObject = JSceneManager.current.instantiateGameObject(new Vector2());
		JXmlUi jCanvas = new JXmlUi(xmlPath);
		gameObject.addComponent(jCanvas);
		return gameObject;
	}

	private JXmlUi() {
	}

	public JXmlUi(String xmlPath) {
		this(xmlPath, SkinsManager.INSTANCE.getDefaultSkin());
	}

	public JXmlUi(String xmlPath, String skinPath) {
		this(xmlPath);
		this.skinPath = skinPath;
	}

	public JXmlUi(String xmlPath, Skin uiSkin) {
		this.xmlPath = xmlPath;
		skin = uiSkin;
	}

	@Override
	public void awake() {
		if(skin == null) skin = new Skin(Gdx.files.internal(skinPath));
		XmlReader reader = new XmlReader();
		Element element = null;
		try {
			element = reader.parse(Gdx.files.internal(xmlPath));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		Table table = new Table(skin);
		rootActor = table;

		parseTable(element, table);
		addChildrens(element, table);
		actorsMap.put(table.getName(), table);

//		Gdx.app.log("JXmlUi", "Parsing complete");

		actorComponent = new ActorComponent();
		actorComponent.setActor(rootActor);
		addNativeComponent(actorComponent);
	}

	public void hide() {
		rootActor.setVisible(false);
	}
	
	public void show() {
		rootActor.setVisible(true);
	}

	private void parseTable(Element element, Table table) {
		ObjectMap<String, String> atrributes = element.getAttributes();
		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("fillParent")) {
				table.setFillParent(Boolean.parseBoolean(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("debug")) {
				boolean debugRecursively = true;
				if (atrributes.containsKey("debugRecursively")) {
					debugRecursively = Boolean.parseBoolean(atrributes.get("debugRecursively"));
				}
				table.setDebug(Boolean.parseBoolean(atrributes.get(key)), debugRecursively);
			} else if (key.equalsIgnoreCase("background")) {
				table.setBackground(atrributes.get(key));
			} else if (key.equalsIgnoreCase("background-resource")) {
				String imagePath = element.get(key);
				SpriteResource spriteResource = JResources.getSprite(imagePath);

				Drawable drawable = DrawableHelper.getDrawableFromAsset(spriteResource.getResourcePath());
				table.setBackground(drawable);
			} else if (key.equalsIgnoreCase("clip")) {
				table.setClip(Boolean.parseBoolean(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("red")) {
				Color color = table.getColor();
				table.setColor(Float.parseFloat(atrributes.get(key)), color.g, color.b, color.a);
			} else if (key.equalsIgnoreCase("green")) {
				Color color = table.getColor();
				table.setColor(color.r, Float.parseFloat(atrributes.get(key)), color.b, color.a);
			} else if (key.equalsIgnoreCase("black")) {
				Color color = table.getColor();
				table.setColor(color.r, color.g, Float.parseFloat(atrributes.get(key)), color.a);
			} else if (key.equalsIgnoreCase("alpha")) {
				Color color = table.getColor();
				table.setColor(color.r, color.g, color.b, Float.parseFloat(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("name")) {
				table.setName(atrributes.get(key));
			}
		}

		// addChildrens(element, table);

	}

	private void addChildrens(Element element, Table table) {
		int childCount = element.getChildCount();
		for (int x = 0; x < childCount; x++) {
			Element child = element.getChild(x);
			if (child.getName().equalsIgnoreCase("button")) {
				addButton(table, child);
			} else if (child.getName().equalsIgnoreCase("row")) {
				addRow(table);
			} else if (child.getName().equalsIgnoreCase("list")) {
				addList(table, child);
			} else if (child.getName().equalsIgnoreCase("scroll-panel")) {
				addScrollPanel(table, child);
			} else if (child.getName().equalsIgnoreCase("image")) {
				addImage(table, child);
			} else if (child.getName().equalsIgnoreCase("label")) {
				addLabel(table, child);
			} else if (child.getName().equalsIgnoreCase("text-area")) {
				addTextArea(table, child);
			} else if (child.getName().equalsIgnoreCase("table")) {
				addTable(table, child);
			} else if (child.getName().equalsIgnoreCase("text-field")) {
				addTextField(table, child);
			} else if (child.getName().equalsIgnoreCase("window")) {
				addWindow(table, child);
			} else if (child.getName().equalsIgnoreCase("vertical-group")) {
				addVerticalGroup(table, child);
			} else if (child.getName().equalsIgnoreCase("horizontal-group")) {
				addHorizontalGroup(table, child);
			}

		}
	}


	private void addHorizontalGroup(Table table, Element element) {
		Table horizontalGroup = new Table();
		ScrollPane scrollPane = new ScrollPane(horizontalGroup, skin);

		Cell<ScrollPane> cell = table.add(scrollPane);
		ObjectMap<String, String> atrributes = element.getAttributes();
		if (atrributes == null) {
			atrributes = new ObjectMap<String, String>();
		}

		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("name")) {
				horizontalGroup.setName(atrributes.get(key));
			}
		}
		cellPrepare(cell, atrributes);
		addChildrens(element, horizontalGroup);

		actorsMap.put(horizontalGroup.getName(), horizontalGroup);
		
	}

	private void addVerticalGroup(Table table, Element element) {
		VerticalGroup verticalGroup = new VerticalGroup();
		ScrollPane scrollPane = new ScrollPane(verticalGroup, skin);

		Cell<ScrollPane> cell = table.add(scrollPane);
		ObjectMap<String, String> atrributes = element.getAttributes();
		if (atrributes == null) {
			atrributes = new ObjectMap<String, String>();
		}

		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("name")) {
				verticalGroup.setName(atrributes.get(key));
			}
		}
		cellPrepare(cell, atrributes);
//		addChildrens(element, horizontalGroup);

		actorsMap.put(verticalGroup.getName(), verticalGroup);		
	}

	private void addScrollPanel(Table table, Element element) {
		Table tableScroll = new Table(skin);
		ScrollPane scrollPane = new ScrollPane(tableScroll, skin);

		Cell<ScrollPane> cell = table.add(scrollPane);
		ObjectMap<String, String> atrributes = element.getAttributes();
		if (atrributes == null) {
			atrributes = new ObjectMap<String, String>();
		}

		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("name")) {
				tableScroll.setName(atrributes.get(key));
			}
		}
		cellPrepare(cell, atrributes);
		addChildrens(element, tableScroll);

		actorsMap.put(tableScroll.getName(), tableScroll);
	}

	private void addTable(Table table, Element element) {
		Table newTable = new Table(skin);
		parseTable(element, newTable);

		Cell<Table> cell = table.add(newTable);
		ObjectMap<String, String> atrributes = element.getAttributes();
		if (atrributes == null) {
			atrributes = new ObjectMap<String, String>();
		}
		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("name")) {
				newTable.setName(atrributes.get(key));
			}
		}
		
		cellPrepare(cell, atrributes);
		addChildrens(element, newTable);
		actorsMap.put(newTable.getName(), newTable);
	}

	private void addWindow(Table table, Element element) {
		Gdx.app.log("JXmlUi", "addWindow");
		String title = element.get("title", "");
		Table newTable = new Window(title, skin);

		Cell<Table> cell = table.add(newTable);
		ObjectMap<String, String> atrributes = element.getAttributes();
		if (atrributes == null) {
			atrributes = new ObjectMap<String, String>();
		}
		cellPrepare(cell, atrributes);
		addChildrens(element, newTable);

	}

	private void addList(Table table, Element element) {
		Gdx.app.log("JXmlUi", "addList");
		ObjectMap<String, String> atrributes = element.getAttributes();
		if (atrributes == null)
			atrributes = new ObjectMap<String, String>();

		List<String> list = new List<String>(skin);
		list.getSelection().setMultiple(false);

		ScrollPane scrollPane = new ScrollPane(list, skin);

		Cell<ScrollPane> cell = table.add(scrollPane);
		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("name")) {
				list.setName(atrributes.get(key));
				scrollPane.setName(atrributes.get(key) + "-scroll-panel");
			}
		}
		cellPrepare(cell, atrributes);

		actorsMap.put(list.getName(), list);
		actorsMap.put(scrollPane.getName(), scrollPane);

		addElementsInList(element, list);
	}

	private void addElementsInList(Element element, List list) {
		Array<String> items = new Array<String>();

		int childCount = element.getChildCount();
		for (int x = 0; x < childCount; x++) {
			Element child = element.getChild(x);
			if (!child.getName().equalsIgnoreCase("list-element"))
				continue;
			// Gdx.app.log("JXmlUi", "addListElement");
			String text = child.getAttribute("text");
			items.add(text);

		}

		list.setItems(items);
	}

	private void cellPrepare(@SuppressWarnings("rawtypes") Cell cell, ObjectMap<String, String> atrributes) {
		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("expand-all") && Boolean.parseBoolean(atrributes.get(key))) {
				cell.expand();
			} else if (key.equalsIgnoreCase("expand-x") && Boolean.parseBoolean(atrributes.get(key))) {
				boolean expandY = atrributes.containsKey("expand-y") ? Boolean.parseBoolean(atrributes.get(key))
						: false;
				cell.expand(true, expandY);
			} else if (key.equalsIgnoreCase("expand-y") && Boolean.parseBoolean(atrributes.get(key))) {
				boolean expandX = atrributes.containsKey("expand-x") ? Boolean.parseBoolean(atrributes.get(key))
						: false;
				cell.expand(expandX, true);
			} else if (key.equalsIgnoreCase("fill-all") && Boolean.parseBoolean(atrributes.get(key))) {
				cell.fill();
			} else if (key.equalsIgnoreCase("fill-x") && Boolean.parseBoolean(atrributes.get(key))) {
				boolean fillY = atrributes.containsKey("fill-y") ? Boolean.parseBoolean(atrributes.get(key)) : false;
				cell.fill(true, fillY);
			} else if (key.equalsIgnoreCase("fill-y") && Boolean.parseBoolean(atrributes.get(key))) {
				boolean fillX = atrributes.containsKey("fill-x") ? Boolean.parseBoolean(atrributes.get(key)) : false;
				cell.fill(fillX, true);
			} else if (key.equalsIgnoreCase("center") && Boolean.parseBoolean(atrributes.get(key))) {
				cell.center();
			} else if (key.equalsIgnoreCase("top") && Boolean.parseBoolean(atrributes.get(key))) {
				cell.top();
			} else if (key.equalsIgnoreCase("bottom") && Boolean.parseBoolean(atrributes.get(key))) {
				cell.bottom();
			} else if (key.equalsIgnoreCase("left") && Boolean.parseBoolean(atrributes.get(key))) {
				cell.left();
			} else if (key.equalsIgnoreCase("right") && Boolean.parseBoolean(atrributes.get(key))) {
				cell.right();
			} else if (key.equalsIgnoreCase("padding")) {
				String[] pads = atrributes.get(key).split(",");
				float up = Float.parseFloat(pads[0]);
				float right = Float.parseFloat(pads[1]);
				float down = Float.parseFloat(pads[2]);
				float left = Float.parseFloat(pads[3]);
				cell.pad(up, left, down, right);
			} else if (key.equalsIgnoreCase("space")) {
				String[] pads = atrributes.get(key).split(",");
				float up = Float.parseFloat(pads[0]);
				float right = Float.parseFloat(pads[1]);
				float down = Float.parseFloat(pads[2]);
				float left = Float.parseFloat(pads[3]);
				cell.space(up, left, down, right);
			} else if (key.equalsIgnoreCase("colspan")) {
				cell.colspan(Integer.parseInt(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("min-height")) {
				cell.minHeight(Float.parseFloat(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("max-height")) {
				cell.maxHeight(Float.parseFloat(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("height")) {
				cell.minHeight(Float.parseFloat(atrributes.get(key)));
				cell.maxHeight(Float.parseFloat(atrributes.get(key)));
				cell.height(Float.parseFloat(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("min-width")) {
				cell.minWidth(Float.parseFloat(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("max-width")) {
				cell.maxWidth(Float.parseFloat(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("width")) {
				cell.minWidth(Float.parseFloat(atrributes.get(key)));
				cell.maxWidth(Float.parseFloat(atrributes.get(key)));
				cell.width(Float.parseFloat(atrributes.get(key)));
			}
		}
	}

	private void addRow(Table table) {
		table.row();
	}

	private void addButton(Table table, Element element) {
		TextButton button = new TextButton("", skin);
		Cell<TextButton> cell = table.add(button);

		ObjectMap<String, String> atrributes = element.getAttributes();
		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("text")) {
				button.setText(atrributes.get(key));
			} else if (key.equalsIgnoreCase("checked")) {
				button.setChecked(Boolean.parseBoolean(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("visible")) {
				button.setVisible(Boolean.parseBoolean(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("enable")) {
				button.setDisabled(!Boolean.parseBoolean(atrributes.get(key)));
			} else if (key.equalsIgnoreCase("name")) {
				button.setName(atrributes.get(key));
			}
		}
		cellPrepare(cell, atrributes);

		// System.out.println("Dodaje button: " + button.getName());
		actorsMap.put(button.getName(), button);
	}

	private void addImage(Table table, Element element) {
		Image image = new Image(new Texture(element.get("path")));
		Cell<Image> cell = table.add(image);

		ObjectMap<String, String> atrributes = element.getAttributes();
		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("name")) {
				image.setName(atrributes.get(key));
			}
		}
		cellPrepare(cell, atrributes);
		actorsMap.put(image.getName(), image);
	}

	private void addLabel(Table table, Element element) {
		ObjectMap<String, String> atrributes = element.getAttributes();

		Label label = new Label(element.get("text", ""), skin);
		Cell<Label> cell = table.add(label);

		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("name")) {
				label.setName(atrributes.get(key));
			}
		}
		cellPrepare(cell, atrributes);
		actorsMap.put(label.getName(), label);

	}

	private void addTextArea(Table table, Element element) {
		ObjectMap<String, String> atrributes = element.getAttributes();

		TextArea textArea = new TextArea(element.get("text", ""), skin);
		Cell<TextArea> cell = table.add(textArea);

		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("name")) {
				textArea.setName(atrributes.get(key));
			}
		}
		cellPrepare(cell, atrributes);
		actorsMap.put(textArea.getName(), textArea);

	}

	private void addTextField(Table table, Element element) {
		ObjectMap<String, String> atrributes = element.getAttributes();

		TextField textField = new TextField(element.get("text", ""), skin);
		Cell<TextField> cell = table.add(textField);

		for (String key : atrributes.keys()) {
			if (key.equalsIgnoreCase("name")) {
				textField.setName(atrributes.get(key));
			}
		}
		cellPrepare(cell, atrributes);
		actorsMap.put(textField.getName(), textField);

	}

	public <T extends Actor> T getActor(String key) {
		T result = (T) actorsMap.get(key);
		// System.out.println("Pobieram actora: " + key + " value: " + result);
		return result;
	}

	public Skin getSkin() {
		return skin;
	}
}
