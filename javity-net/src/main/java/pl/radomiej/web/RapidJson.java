package pl.radomiej.web;

import java.io.IOException;
import java.util.Collection;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public enum RapidJson {
	INSTANCE;
	
	private ObjectMapper mapper = new ObjectMapper();
	
	public <T> T  parseJsonSingle(String json, Class<T> object){
		try {
			T result = mapper.readValue(json, object);
			return result;
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public <E extends Collection<T>, T> E  parseJsonCollection(String json, Class<E> collectionClass, Class<T> elementsClass){
		try {
			E result = mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(collectionClass, elementsClass));
			return result;
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			return (E) collectionClass.newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public <T extends Collection> T  parseJsonCollection(String json, Class<T> collectionClass){
		try {
			T result = mapper.readValue(json, new TypeReference<T>(){});
			return result;
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			return collectionClass.newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		
		return null;
	}
}
