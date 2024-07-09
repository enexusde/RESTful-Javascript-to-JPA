package de.e_nexus.web.jpa.js;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

@Entity
public class Driver {
	private int id;

	private Car car;

	public void setCar(final Car car) {
		this.car = car;
	}

	@OneToOne(mappedBy = "driver")
	public Car getCar() {
		return car;
	}

	public void setId(final int id) {
		this.id = id;
	}

	@Id
	public int getId() {
		return id;
	}
}
