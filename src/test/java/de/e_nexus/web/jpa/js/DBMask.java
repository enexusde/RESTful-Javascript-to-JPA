package de.e_nexus.web.jpa.js;

import javax.inject.Named;

import de.e_nexus.web.jpa.js.masker.DBColumnSimpleValueMasquerade;
import de.e_nexus.web.jpa.js.mod.DBModelColumn;

@Named
public class DBMask implements DBColumnSimpleValueMasquerade {

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public <T> T masquerade(T originValue, DBModelColumn c, Object rootInstance) {
		return originValue;
	}

}
