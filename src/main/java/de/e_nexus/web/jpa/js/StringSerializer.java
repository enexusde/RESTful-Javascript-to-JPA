package de.e_nexus.web.jpa.js;

public interface StringSerializer {
	/**
	 * @param unserialized The original unserialized string, never
	 *                     <code>null</code>.
	 * @return The string in double-quotas or quotas, never <code>null</code>.
	 */
	public String utf8String(String unserialized);
}
