package org.javity.ui;

import java.io.IOException;

import org.javity.engine.JComponent;
import org.javity.engine.NativeComponent;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.XmlReader.Element;

import galaxy.rapid.components.ActorComponent;

public class JXmlUi extends NativeComponent {
	private ActorComponent actorComponent;
	private Actor rootActor;
	private Skin skin;

	public String xmlPath;
	public String skinPath = "internal/ui/uiskin.json";

	public JXmlUi() {
	}

	public JXmlUi(String xmlPath) {
		this.xmlPath = xmlPath;
	}

	public JXmlUi(String xmlPath, String skinPath) {
		this(xmlPath);
		this.skinPath = skinPath;
	}

	@Override
	public void awake() {
		skin = new Skin(Gdx.files.internal(skinPath));
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
		Gdx.app.log("JXmlUi", "Parsing complete");

		actorComponent = new ActorComponent();
		actorComponent.setActor(rootActor);
		addNativeComponent(actorComponent);
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

		addChildrens(element, table);

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
			}

		}
	}

	private void addScrollPanel(Table table, Element element) {
		if (element.getChildCount() > 1) {
			Gdx.app.error("JXmlUi",
					"ScrollPanel has more than one element. Pleace use Table if you need add multiple elements");
		}
		Table tableScroll = new Table(skin);
		ScrollPane scrollPane = new ScrollPane(tableScroll, skin); 
		
		Cell<ScrollPane> cell = table.add(scrollPane);
		ObjectMap<String, String> atrributes = element.getAttributes();
		if (atrributes == null){
			atrributes = new ObjectMap<String, String>();
		}
		cellPrepare(cell, atrributes);
		addChildrens(element, tableScroll);
		
	}

	private void addList(Table table, Element element) {
		Gdx.app.log("JXmlUi", "addList");
		ObjectMap<String, String> atrributes = element.getAttributes();
		if (atrributes == null)
			atrributes = new ObjectMap<String, String>();

		List<String> list = new List<String>(skin);
		ScrollPane scrollPane = new ScrollPane(list, skin);

		Cell<ScrollPane> cell = table.add(scrollPane);
		cellPrepare(cell, atrributes);

		addElementsInList(element, list);
	}

	private void addElementsInList(Element element, List list) {
		Array<String> items = new Array<String>();

		int childCount = element.getChildCount();
		for (int x = 0; x < childCount; x++) {
			Element child = element.getChild(x);
			if (!child.getName().equalsIgnoreCase("list-element"))
				continue;
			Gdx.app.log("JXmlUi", "addListElement");
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
			}
		}
		cellPrepare(cell, atrributes);
	}

	private void addImage(Table table, Element element) {
		Image image = new Image(new Texture(element.get("path")));
		Cell<Image> cell = table.add(image);

		ObjectMap<String, String> atrributes = element.getAttributes();
		for (String key : atrributes.keys()) {
		}
		cellPrepare(cell, atrributes);
	}
}
