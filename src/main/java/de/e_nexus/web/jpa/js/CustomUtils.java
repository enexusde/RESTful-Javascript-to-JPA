package de.e_nexus.web.jpa.js;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.util.LinkedHashSet;
import java.util.Set;

public class CustomUtils {
	public static <T> Set<T> limit(T[] irs, Object entity, Object fieldtype) {
		Set<T> ts = new LinkedHashSet<T>();
		for (T ir : irs) {
			java.lang.reflect.Type[] genericInterfaces = ir.getClass().getGenericInterfaces();
			for (java.lang.reflect.Type t : genericInterfaces) {
				if (t instanceof ParameterizedType) {
					ParameterizedType parameterizedType = (ParameterizedType) t;
					Class<?> generic1 = (Class<?>) parameterizedType.getActualTypeArguments()[0];
					if (fieldtype != null) {
						Class<?> generic2 = (Class<?>) parameterizedType.getActualTypeArguments()[1];
						if (generic1.isInstance(entity) && generic2.isInstance(fieldtype)) {
							ts.add(ir);
						}
					} else {
						if (generic1.isInstance(entity)) {
							ts.add(ir);
						}
					}
				}
			}
		}
		return ts;
	}

	public static byte[] readBytes(InputStream is) throws IOException {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length;
		while ((length = is.read(buffer)) != -1) {
			result.write(buffer, 0, length);
		}
		return result.toByteArray();
	}
}
