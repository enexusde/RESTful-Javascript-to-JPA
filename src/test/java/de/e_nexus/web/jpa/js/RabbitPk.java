package de.e_nexus.web.jpa.js;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class RabbitPk implements Serializable {
	long id1, id2;

	@Column
	public long getId1() {
		return id1;
	}

	@Column
	public long getId2() {
		return id2;
	}

	public void setId1(long id1) {
		this.id1 = id1;
	}

	public void setId2(long id2) {
		this.id2 = id2;
	}
}
