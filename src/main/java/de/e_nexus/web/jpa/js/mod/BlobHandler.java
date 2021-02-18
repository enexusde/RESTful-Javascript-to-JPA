package de.e_nexus.web.jpa.js.mod;

import java.sql.Blob;

import org.springframework.transaction.annotation.Transactional;

public interface BlobHandler {

	@Transactional
	Blob generateBlob(byte[] ba);

}
