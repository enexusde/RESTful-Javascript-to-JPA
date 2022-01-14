package de.e_nexus.web.jpa.js;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;

@Entity
public class Driver {
	private int id;

	private Car car;

	public void setCar(Car car) {
		this.car = car;
	}

	@OneToOne(mappedBy = "driver")
	public Car getCar() {
		return car;
	}

	public void setId(int id) {
		this.id = id;
	}

	@Id
	public int getId() {
		return id;
	}
}
