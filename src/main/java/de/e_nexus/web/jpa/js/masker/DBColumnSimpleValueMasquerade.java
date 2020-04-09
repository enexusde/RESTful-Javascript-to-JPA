package de.e_nexus.web.jpa.js.masker;

import org.springframework.core.Ordered;

import de.e_nexus.web.jpa.js.mod.DBModelColumn;

public interface DBColumnSimpleValueMasquerade extends Ordered {

	/**
	 * Masquerade a specific field. A password in example.
	 * 
	 * @param <T>          The type, can be <code>null</code>.
	 * @param originValue  The old value, can be <code>null</code>.
	 * @param c            The column, never <code>null</code>.
	 * @param rootInstance The entity instance to expose the value from, never
	 *                     <code>null</code>.
	 * @return
	 */
	<T> T masquerade(T originValue, DBModelColumn c, Object rootInstance);

}
