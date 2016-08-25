package org.javity.ui;

import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.utils.Array;

public class ListHelper {

	public static <T> void addElement(T addItem, List<T> list) {
		Array<T> items = new Array<T>(list.getItems());
		items.add(addItem);
		list.setItems(items);
	}
	
	public static <T> void removeElement(T removeItem, boolean identity, List<T> list) {
		Array<T> items = new Array<T>(list.getItems());
		items.removeValue(removeItem, identity);
		list.setItems(items);
	}

}
