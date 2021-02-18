package de.e_nexus.web.jpa.js;

import java.sql.Blob;

import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.hibernate.Session;

import de.e_nexus.web.jpa.js.mod.BlobHandler;

@Named
public class DefaultBlobHandler implements BlobHandler {

	@PersistenceContext
	private final EntityManager entityManager = null;

	@Override
	public Blob generateBlob(byte[] ba) {
		Session s = (Session) entityManager.getDelegate();
		return s.getLobHelper().createBlob(ba);
	}

}
