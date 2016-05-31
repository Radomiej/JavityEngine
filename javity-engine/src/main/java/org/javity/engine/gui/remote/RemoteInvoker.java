package org.javity.engine.gui.remote;

import java.util.ArrayList;
import java.util.List;

import org.javity.engine.Component;
import org.javity.engine.GameObject;
import org.javity.engine.gui.JLabel;

import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Method;
import com.badlogic.gdx.utils.reflect.ReflectionException;

public class RemoteInvoker {

	private String invokeComponent;
	private String invokeMethod;
	private List<Object> args = new ArrayList<Object>();

	public RemoteInvoker() {
	}
	
	public RemoteInvoker(Class<? extends Component> class1, String method, Object... argsTab) {
		this.invokeComponent = class1.getName();
		this.invokeMethod = method;
		for(Object arg : argsTab){
			args.add(arg);
		}
	}

	public void invoke(GameObject clickTarget) {
		for (Component component : clickTarget.getAllComponents()) {
			if (component.getClass().getName().equals(invokeComponent)) {
				Object[] parameters = args.toArray(new Object[args.size()]);
				Class[] parametersType = new Class[args.size()];
				for (int x = 0; x < parameters.length; x++) {
					parametersType[x] = parameters[x].getClass();
				}

				try {
					Method method = ClassReflection.getDeclaredMethod(component.getClass(), invokeMethod,
							parametersType);
					method.invoke(component, parameters);
				} catch (ReflectionException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
