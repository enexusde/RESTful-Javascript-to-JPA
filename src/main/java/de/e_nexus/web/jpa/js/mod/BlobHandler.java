package de.e_nexus.web.jpa.js.mod;

import java.sql.Blob;

import jakarta.transaction.Transactional;

public interface BlobHandler {

	@Transactional
	Blob generateBlob(byte[] ba);

}
