package com.dianping.pigeon.remoting.common.codec.json;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.dianping.pigeon.remoting.common.exception.SerializationException;
import com.dianping.pigeon.util.ReflectUtils;

public class JacksonObjectMapper {

	static JacksonSerializer jacksonSerializer = new JacksonSerializer();

	public static <T> T convertObject(T obj) throws SerializationException, ClassNotFoundException,
			InstantiationException, IllegalAccessException {
		if (obj == null) {
			return null;
		}
		if (obj instanceof LinkedHashMap) {
			LinkedHashMap map = (LinkedHashMap) obj;
			return (T) convertObject(map);
		} else if (obj instanceof Collection) {
			Collection list = (Collection) obj;
			Collection newList = (Collection) Class.forName(obj.getClass().getName()).newInstance();
			if (!list.isEmpty()) {
				int i = 0;
				String componentType = null;
				for (Iterator ir = list.iterator(); ir.hasNext();) {
					Object o = ir.next();
					if (o instanceof LinkedHashMap) {
						String cls = (String) ((Map) o).get("@class");
						if (StringUtils.isNotBlank(cls)) {
							componentType = cls;
						} else if (componentType != null) {
							((Map) o).put("@class", componentType);
						}
					}
					newList.add(convertObject(o));
					i++;
				}
				return (T) newList;
			}
		} else if (obj.getClass().isArray()) {
			int len = Array.getLength(obj);
			for (int i = 0; i < len; i++) {
				Object o = Array.get(obj, i);
				String componentType = null;
				if (o instanceof LinkedHashMap) {
					String cls = (String) ((Map) o).get("@class");
					if (StringUtils.isNotBlank(cls)) {
						componentType = cls;
					} else if (componentType != null) {
						((Map) o).put("@class", componentType);
					}
				}
				Array.set(obj, i, convertObject(o));
			}
			return obj;
		} else if (obj instanceof Map) {
			Map map = (Map) obj;
			if (!map.isEmpty()) {
				Map finalMap = new HashMap(map.size());
				for (Iterator ir = map.keySet().iterator(); ir.hasNext();) {
					Object k = ir.next();
					Object v = map.get(k);
					Object finalKey = convertObject(k);
					Object finalValue = convertObject(v);
					finalMap.put(finalKey, finalValue);
				}
				return (T) finalMap;
			}
		} else if (obj.getClass().getName().startsWith("com.dianping")) {
			Field[] fields = obj.getClass().getDeclaredFields();
			for (Field field : fields) {
				Class<?> type = field.getType();
				if (type.isPrimitive() || type.isEnum()) {
					continue;
				}
				if (type.getName().startsWith("com.dianping") || Map.class.isAssignableFrom(type)
						|| Collection.class.isAssignableFrom(type) || type.isArray()) {
					Object fieldValue = ReflectUtils.readDeclaredField(obj, field.getName(), true);
					ReflectUtils.writeDeclaredField(obj, field.getName(), convertObject(fieldValue), true);
				}
			}
		}
		return obj;
	}

	public static Object convertObject(LinkedHashMap map) throws SerializationException, ClassNotFoundException,
			InstantiationException, IllegalAccessException {
		String type = (String) map.get("@class");
		if (StringUtils.isNotBlank(type)) {
			Class clazz = Class.forName(type);
			Object obj = clazz.newInstance();
			for (Iterator ir = map.keySet().iterator(); ir.hasNext();) {
				String key = (String) ir.next();
				Object value = map.get(key);
				if (!key.equals("@class")) {
					Field field = ReflectUtils.getDeclaredField(clazz, key, true);
					if (field != null) {
						ReflectUtils.writeDeclaredField(obj, key, value, true);
					}
				}
			}
			return obj;
		}
		return map;
	}
}
