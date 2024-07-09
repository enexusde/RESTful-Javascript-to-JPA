package de.e_nexus.web.jpa.js;

import de.e_nexus.web.jpa.js.masker.DBColumnSimpleValueMasquerade;
import de.e_nexus.web.jpa.js.mod.DBModelColumn;
import jakarta.inject.Named;

@Named
public class DBMask implements DBColumnSimpleValueMasquerade {

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public <T> T masquerade(final T originValue, final DBModelColumn c, final Object rootInstance) {
		return originValue;
	}

}
