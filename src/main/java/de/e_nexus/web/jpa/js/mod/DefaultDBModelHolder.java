/*     ____  _____________________      __       __                  _____           _       __     __             _
 *    / __ \/ ____/ ___/_  __/ __/_  __/ /      / /___ __   ______ _/ ___/__________(_)___  / /_   / /_____       (_)___  ____ _
 *   / /_/ / __/  \__ \ / / / /_/ / / / /  __  / / __ `/ | / / __ `/\__ \/ ___/ ___/ / __ \/ __/  / __/ __ \     / / __ \/ __ `/
 *  / _, _/ /___ ___/ // / / __/ /_/ / /  / /_/ / /_/ /| |/ / /_/ /___/ / /__/ /  / / /_/ / /_   / /_/ /_/ /    / / /_/ / /_/ /
 * /_/ |_/_____//____//_/ /_/  \__,_/_/   \____/\__,_/ |___/\__,_//____/\___/_/  /_/ .___/\__/   \__/\____/  __/ / .___/\__,_/
 * Copyright 2019 by Peter Rader (e-nexus.de)                                     /_/                       /___/_/ 
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, 
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package de.e_nexus.web.jpa.js.mod;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.SmartInitializingSingleton;

import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Named
public class DefaultDBModelHolder implements DBModelHolder, SmartInitializingSingleton {

	@PersistenceContext
	private final EntityManager entityManager = null;

	private DBSchemaModel model;

	@Override
	public DBSchemaModel getModel() {
		return model;
	}

	@Override
	public void afterSingletonsInstantiated() {
		DBModelBuilder builder = new DBModelBuilder();
		builder.setEntityManager(entityManager);
		model = builder.build();
	}

	@Override
	public DBModelTable getEntity(final String entityName) {
		for (DBModelTable t : getModel()) {
			if (t.getName().equals(entityName)) {
				return t;
			}
		}
		return null;
	}

	@Override
	public Set<DBModelColumn> getIdColumns(final DBModelTable entityTable) {
		Set<DBModelColumn> cols = new LinkedHashSet<DBModelColumn>();
		for (DBModelColumn c : entityTable) {
			if (c.getColtype() == ColType.ID | c.getColtype() == ColType.ID_COMPOSED) {
				cols.add(c);
			}
		}
		return Collections.unmodifiableSet(cols);
	}
}
