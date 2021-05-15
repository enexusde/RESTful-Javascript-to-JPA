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

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.ServletRequestEvent;
import javax.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextListener;

import de.e_nexus.web.jpa.js.mod.DBModelColumn;
import de.e_nexus.web.jpa.js.mod.DBModelTable;

@Named
public class DefaultJSMapperPersistenceManager implements JSMapperPersistenceManager {

	private static final Logger LOG = Logger.getLogger(DefaultJSMapperPersistenceManager.class.getCanonicalName());

	@PersistenceContext
	private final EntityManager entityManager = null;
	@Inject
	private final JSONJavaScriptModificationListener<?, ?>[] listeners = null;

	public void removeAction(DBModelTable entityTable, Object entity, HttpServletRequest request) {
		Map.Entry<ServletRequestEvent, RequestContextListener> state = null;
		try {

			Set<JSONJavaScriptModificationListener> listeners = GenericUtils.limit(this.listeners, entity, null);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					state = initState(request, state);

					l.beforePersist(entityTable, null, entity, null, DatabaseChangeType.REMOVE);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
			entityManager.remove(entity);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					l.afterPersist(entityTable, null, entity, null, DatabaseChangeType.REMOVE);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
		} finally {
			removeState(state);
		}
	}

	private void removeState(Map.Entry<ServletRequestEvent, RequestContextListener> state) {
		if (state != null) {
			state.getValue().requestDestroyed(state.getKey());
		}
	}

	private Map.Entry<ServletRequestEvent, RequestContextListener> initState(HttpServletRequest request,
			Map.Entry<ServletRequestEvent, RequestContextListener> state) {
		if (state == null) {
			ServletRequestEvent requestEvent = new ServletRequestEvent(request.getServletContext(), request);
			RequestContextListener requestContextListener = new RequestContextListener();
			requestContextListener.requestInitialized(requestEvent);
			state = new AbstractMap.SimpleEntry(requestEvent, requestContextListener);
		}
		return state;
	}

	public void persistAction(DBModelTable table, Object entity, DBModelColumn c, Object newValue,
			HttpServletRequest request) {
		Map.Entry<ServletRequestEvent, RequestContextListener> state = null;
		try {
			Set<JSONJavaScriptModificationListener> listeners = GenericUtils.limit(this.listeners, entity, newValue);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					state = initState(request, state);
					l.beforePersist(table, c, entity, newValue, DatabaseChangeType.RELATION);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
			entityManager.persist(entity);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					l.afterPersist(table, c, entity, newValue, DatabaseChangeType.RELATION);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
		} finally {
			removeState(state);
		}
	}

	public void persistN2MAction(DBModelTable ownerMapping, DBModelColumn ownerColumn, Object ownerEntity,
			Object nonOwnerEntity, HttpServletRequest request) {
		Map.Entry<ServletRequestEvent, RequestContextListener> state = null;
		try {
			Set<JSONJavaScriptModificationListener> listeners = GenericUtils.limit(this.listeners, ownerEntity,
					nonOwnerEntity);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					state = initState(request, state);
					l.beforePersist(ownerMapping, ownerColumn, ownerEntity, nonOwnerEntity,
							DatabaseChangeType.MANY_TO_MANY_RELATION);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
			entityManager.persist(ownerEntity);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					l.afterPersist(ownerMapping, ownerColumn, ownerEntity, nonOwnerEntity,
							DatabaseChangeType.MANY_TO_MANY_RELATION);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
		} finally {
			removeState(state);
		}
	}

	public void insertNewAction(DBModelTable t, Object entity, HttpServletRequest request) {
		Map.Entry<ServletRequestEvent, RequestContextListener> state = null;
		try {
			Set<JSONJavaScriptModificationListener> listeners = GenericUtils.limit(this.listeners, entity, null);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					state = initState(request, state);
					l.beforePersist(t, null, entity, null, DatabaseChangeType.PLACE);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
			entityManager.persist(entity);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					l.afterPersist(t, null, entity, null, DatabaseChangeType.PLACE);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
		} finally {
			removeState(state);
		}
	}

	public void persistSingleFieldAction(DBModelTable t, DBModelColumn c, Object entity, Object genuineValue,
			HttpServletRequest request) {
		Map.Entry<ServletRequestEvent, RequestContextListener> state = null;
		try {
			Set<JSONJavaScriptModificationListener> listeners = GenericUtils.limit(this.listeners, entity,
					genuineValue);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					state = initState(request, state);
					l.beforePersist(t, c, entity, genuineValue, DatabaseChangeType.PROPERTY_NOT_RELATION);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
			entityManager.persist(entity);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					l.afterPersist(t, c, entity, genuineValue, DatabaseChangeType.PROPERTY_NOT_RELATION);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
		} finally {
			removeState(state);
		}
	}

}
