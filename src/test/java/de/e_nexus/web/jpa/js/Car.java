package de.e_nexus.web.jpa.js;

import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

@Entity
public class Car {
	private long id;

	private Driver driver;

	@Id
	public long getId() {
		return id;
	}

	public void setId(final long id) {
		this.id = id;
	}

	@OneToOne
	@JoinColumn(name = "driver_fk", referencedColumnName = "id", foreignKey = @ForeignKey(name = "abc"))
	public Driver getDriver() {
		return driver;
	}

	public void setDriver(final Driver driver) {
		this.driver = driver;
	}
}
