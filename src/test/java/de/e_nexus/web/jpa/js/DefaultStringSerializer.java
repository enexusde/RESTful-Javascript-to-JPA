package de.e_nexus.web.jpa.js;

import javax.inject.Named;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Named
public class DefaultStringSerializer implements StringSerializer {

	private final ObjectMapper om = new ObjectMapper();

	@Override
	public String utf8String(String unserialized) {
		try {
			return om.writeValueAsString(unserialized);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
