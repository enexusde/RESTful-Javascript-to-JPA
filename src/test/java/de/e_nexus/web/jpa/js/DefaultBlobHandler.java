package de.e_nexus.web.jpa.js;

import java.sql.Blob;

import org.hibernate.Session;

import de.e_nexus.web.jpa.js.mod.BlobHandler;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Named
public class DefaultBlobHandler implements BlobHandler {

	@PersistenceContext
	private final EntityManager entityManager = null;

	@Override
	public Blob generateBlob(final byte[] ba) {
		Session s = (Session) entityManager.getDelegate();
		return s.getLobHelper().createBlob(ba);
	}

}
