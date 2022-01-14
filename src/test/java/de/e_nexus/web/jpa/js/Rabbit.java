package de.e_nexus.web.jpa.js;

import java.sql.Blob;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Rabbit {

	private Blob photo;
	private RabbitPk id;

	@Id
	public RabbitPk getId() {
		return id;
	}

	public void setId(RabbitPk id) {
		this.id = id;
	}

	@Column
	public Blob getPhoto() {
		return photo;
	}

	public void setPhoto(Blob photo) {
		this.photo = photo;
	}
}
