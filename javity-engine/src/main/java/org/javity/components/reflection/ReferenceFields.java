package org.javity.components.reflection;

import com.badlogic.gdx.utils.reflect.Field;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import org.javity.engine.JGameObject;

/** Field access used only when saving/loading object references, never per frame. */
final class ReferenceFields {
    private ReferenceFields() {}

    static JGameObject read(Field field, Object owner) {
        try { return (JGameObject) field.get(owner); }
        catch (ReflectionException e) { throw new IllegalStateException("Cannot read " + field.getName(), e); }
    }

    static void write(Field field, Object owner, JGameObject value) {
        try { field.set(owner, value); }
        catch (ReflectionException e) { throw new IllegalStateException("Cannot write " + field.getName(), e); }
    }
}
