package de.e_nexus.web.jpa.js;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Named;

@Named
public class DefaultStringSerializer implements StringSerializer {

	private final ObjectMapper om = new ObjectMapper();

	@Override
	public String utf8String(final String unserialized) {
		try {
			return om.writeValueAsString(unserialized);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
