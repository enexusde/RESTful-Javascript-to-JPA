package de.e_nexus.web.jpa.js;

import java.sql.Blob;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Rabbit {

	private Blob photo;
	private RabbitPk id;
	private java.sql.Date born;

	@Id
	public RabbitPk getId() {
		return id;
	}

	public void setId(final RabbitPk id) {
		this.id = id;
	}

	@Column
	public Blob getPhoto() {
		return photo;
	}

	public void setPhoto(final Blob photo) {
		this.photo = photo;
	}

	@Column
	public java.sql.Date getBorn() {
		return born;
	}

	public void setBorn(final java.sql.Date born) {
		this.born = born;
	}
}
