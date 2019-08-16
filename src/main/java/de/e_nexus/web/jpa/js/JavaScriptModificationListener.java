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
package de.e_nexus.web.jpa.js;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;

import org.springframework.core.Ordered;

import de.e_nexus.web.jpa.js.mod.DBModelColumn;
import de.e_nexus.web.jpa.js.mod.DBModelTable;

/**
 * Listener for incomming changes from the Javascript.
 * <p>
 * The execution of the methods are inside an http transaction. All exceptions
 * that will be thrown here are swallowed. The changes require an database
 * transaction, all methods will be transactional.
 * 
 * <p>
 * The beans are generic-aware and their execution is limited to the affected
 * generics.
 * 
 * @param <T>  The entity-type that is affected, {@link Object} will match all
 *             entitys.
 * @param <FT> The type of property in the entity that is affected, relations
 *             match {@link Set}, or {@link List} but always {@link Collection}.
 */
public interface JavaScriptModificationListener<T, FT> extends Ordered {
	/**
	 * Before {@link EntityManager#persist(Object)} or
	 * {@link EntityManager#remove(Object)} is called for recording into the
	 * write-ahead-log (WAL). You are inside the affected transaction.
	 * 
	 * @param table      The model of the table that is affected, never
	 *                   <code>null</code>.
	 * @param column     The model of the column that is affected, may be
	 *                   <code>null</code> for basic operations like insert or
	 *                   remove.
	 * @param entity     The entity that is going to be persisted, never
	 *                   <code>null</code>.
	 * @param fieldType  The type of the field, may be <code>null</code> for basic
	 *                   operations like insert or remove..
	 * @param changeType The type of change, never <code>null</code>.
	 */
	void beforePersist(DBModelTable table, DBModelColumn column, T entity, FT fieldType, DatabaseChangeType changeType);

	/**
	 * After {@link EntityManager#persist(Object)} or
	 * {@link EntityManager#remove(Object)} is called for recording into the
	 * write-ahead-log (WAL). You are still inside the affected transaction.
	 * 
	 * @param table      The model of the table that is affected, never
	 *                   <code>null</code>.
	 * @param column     The model of the column that is affected, may be
	 *                   <code>null</code> for basic operations like insert or
	 *                   remove.
	 * @param entity     The entity that is going to be persisted, never
	 *                   <code>null</code>.
	 * @param fieldType  The type of the field, may be <code>null</code> for basic
	 *                   operations like insert or remove..
	 * @param changeType The type of change, never <code>null</code>.
	 */
	void afterPersist(DBModelTable table, DBModelColumn column, T entity, FT fieldType, DatabaseChangeType changeType);
}
