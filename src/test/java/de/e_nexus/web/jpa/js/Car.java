package de.e_nexus.web.jpa.js;

import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

@Entity
public class Car {
	private long id;

	private Driver driver;

	@Id
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	@OneToOne
	@JoinColumn(name = "driver_fk", referencedColumnName = "id", foreignKey = @ForeignKey(name = "abc"))
	public Driver getDriver() {
		return driver;
	}

	public void setDriver(Driver driver) {
		this.driver = driver;
	}
}
