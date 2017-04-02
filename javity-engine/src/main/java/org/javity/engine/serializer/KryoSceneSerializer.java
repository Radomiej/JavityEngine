package org.javity.engine.serializer;

import org.javity.engine.CustomScene;
import org.javity.engine.JGameObjectImpl;
import org.javity.engine.JScene;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class KryoSceneSerializer {

	private Kryo kryo = new Kryo();

	public KryoSceneSerializer() {
		kryo.register(CustomScene.class);
		kryo.register(JGameObjectImpl.class);
	}

	public String serialize(JScene sceneToSerialize) {
		Output output = new Output(new ByteBufferOutputStream());
		kryo.writeObject(output, sceneToSerialize);
		return packToString(output);
	}
	
	private String packToString(Output output) {
		StringBuilder builder = new StringBuilder();
		for(byte b : output.getBuffer()){
			builder.append(Byte.toString(b));
			builder.append(":");
		}
		return builder.toString();
	}

	public CustomScene deserialize(String input){
		return kryo.readObject(unpackFromString(input), CustomScene.class);
	}

	private Input unpackFromString(String inputString) {
		String[] bytes = inputString.split(":");
		byte[] buffer = new byte[bytes.length];
		int x = 0;
		for(String strByte : bytes){
			buffer[x] = Byte.parseByte(strByte);
			x++;
		}
		Input input = new Input(buffer);
		return input;
	}
}
