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

import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLDecoder;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import de.e_nexus.web.jpa.js.masker.DBColumnSimpleValueMasquerade;
import de.e_nexus.web.jpa.js.mod.ColType;
import de.e_nexus.web.jpa.js.mod.DBModelColumn;
import de.e_nexus.web.jpa.js.mod.DBModelHolder;
import de.e_nexus.web.jpa.js.mod.DBModelTable;

@Named
public class DefaultJSMapperController implements JSMapperController {

	private static final Logger LOG = Logger.getLogger(DefaultJSMapperController.class.getCanonicalName());

	private static final int INDEX_ERROR_VALUE = -1;

	@Inject
	private final JSONJavaScriptModificationListener<?, ?>[] listeners = null;

	@Inject
	private final DBColumnSimpleValueMasquerade[] masquerades = {};

	@Inject
	private final IndexFacade indexer = null;

	@PersistenceContext
	private final EntityManager entityManager = null;

	@Inject
	private final StringSerializer serializer = null;

	@Inject
	private final DBModelHolder model = null;

	public Number getIndex(Object val) {
		for (DBModelTable t : model.getModel()) {
			if (t.getEntityClass().isInstance(val)) {
				DBModelColumn c = model.getIdColumn(t);
				BeanWrapperImpl bwi = new BeanWrapperImpl(val);
				Object o = bwi.getPropertyValue(c.getName());

				return indexer.getIndexById(o, c, t);
			}
		}
		return INDEX_ERROR_VALUE;
	}

	@Transactional
	@Override
	public void doGetCount(DBModelTable dbModelTable, HttpServletResponse resp) throws IOException {
		Number n = entityManager.createQuery("SELECT COUNT(id) FROM " + dbModelTable.getEntityClass().getCanonicalName(), Number.class).getSingleResult();
		StringBuilder sb = new StringBuilder();
		sb.append("{\"count\":");
		sb.append(n);
		sb.append("}");
		resp.setContentLength(sb.length());
		ServletOutputStream out = resp.getOutputStream();
		out.write(sb.toString().getBytes());
		out.close();
	}

	@Override
	@Transactional
	public Number put(String entityName, Map<String, String> headers, byte[] ba) {
		DBModelTable table = model.getEntity(entityName);
		Object put = put(table, headers, ba);
		return getIndex(put);
	}

	@Override
	@Transactional
	public String getDetails(String entityName, int index) {
		DBModelTable table = model.getEntity(entityName);
		Object entityId = indexer.findId(index, table);
		Object entity = entityManager.find(table.getEntityClass(), entityId);
		if (entity == null) {
			throw new RuntimeException(entity + " not found by id " + entityId + ".");
		}
		StringBuilder sb = new StringBuilder();
		sb.append("{\"payload\":{");
		boolean mustComma = false;
		BeanWrapperImpl bwi = new BeanWrapperImpl(entity);
		for (DBModelColumn c : table) {
			// key
			if (canWrite(c)) {
				try {
					if (mustComma) {
						sb.append(",");
					}
					mustComma = false;
					writeKey(sb, c);
					// value
					writeValue(sb, bwi, c);
					mustComma = true;
				} catch (RuntimeException e) {
					throw new RuntimeException("Error while write " + entityName + "." + c.getName() + " to JSON.", e);
				}
			}
		}
		sb.append("},\"entity\":\"");
		sb.append(entityName);
		sb.append("\",\"index\":");
		sb.append(index);
		sb.append("}");
		return sb.toString();
	}

	@Override
	@Transactional
	public void updateSimpleFieldValue(File f, HttpServletRequest req, URL url) {
		String property = f.getName();
		String index = f.getParentFile().getName();
		String entity = f.getParentFile().getParentFile().getName();
		DBModelTable table = model.getEntity(entity);
		for (DBModelColumn c : table) {
			if (c.getName().equals(property)) {
				updateSimpleFieldValue(table, c, req, index);
				return;
			}
		}
		throw new IllegalArgumentException("No such field: " + f.getName());
	}

	@Override
	@Transactional
	public void deleteEntity(File f) {
		String entityName = f.getParentFile().getName();
		DBModelTable entityTable = model.getEntity(entityName);
		DBModelColumn idCol = model.getIdColumn(entityTable);

		Object id = indexer.findId(Integer.parseInt(f.getName()), entityTable, idCol);
		Object entity = entityManager.find(entityTable.getEntityClass(), id);
		Set<JSONJavaScriptModificationListener> listeners = GenericUtils.limit(this.listeners, entity, null);
		for (JSONJavaScriptModificationListener l : listeners) {
			try {
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
	}

	@Override
	@Transactional
	public void updateRelation(File f, Integer newIndex, URL url) {
		String propertyName = f.getName();
		int entityIndex = Integer.parseInt(f.getParentFile().getName());
		String entityName = f.getParentFile().getParentFile().getName();
		DBModelTable table = model.getEntity(entityName);
		Object entity = entityManager.find(table.getEntityClass(), indexer.findId(entityIndex, table));
		for (DBModelColumn c : table) {
			if (c.getName().equals(propertyName)) {
				BeanWrapperImpl bwi = new BeanWrapperImpl(entity);
				Object newId;
				if (newIndex == null)
					newId = null;
				else
					newId = indexer.findId(newIndex, model.getEntity(c.getType().getSimpleName()));
				Object newValue;
				if (newId == null)
					newValue = null;
				else
					newValue = entityManager.find(c.getType(), newId);
				bwi.setPropertyValue(propertyName, newValue);
				Set<JSONJavaScriptModificationListener> listeners = GenericUtils.limit(this.listeners, entity, newValue);
				for (JSONJavaScriptModificationListener l : listeners) {
					try {
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
				return;
			}
		}
	}

	@Override
	@Transactional
	public String getIndexJSONById(String entity, int id) {
		DBModelTable t = model.getEntity(entity);
		DBModelColumn idc = model.getIdColumn(t);
		Object pk = parseToFieldNumberType("" + id, idc.getType());
		Object find = entityManager.find(t.getEntityClass(), pk);
		Number index = getIndex(find);
		if (index.intValue() == INDEX_ERROR_VALUE) {
			return "{\"error\":\"No such pk for " + entity + "!\"}";
		}
		return "{\"index\":" + index + "}";
	}

	@Override
	@Transactional
	public int getIndexById(String entity, int id) {
		DBModelTable t = model.getEntity(entity);
		DBModelColumn idc = model.getIdColumn(t);
		Object pk = parseToFieldNumberType("" + id, idc.getType());
		Object find = entityManager.find(t.getEntityClass(), pk);
		Number index = getIndex(find);
		if (index.intValue() == INDEX_ERROR_VALUE) {
			return -1_000;
		}
		return index.intValue();
	}

	@Override
	@Transactional
	public void addN2M(File f, String data) {
		String property = f.getName();
		int ownerTableIndex = Integer.parseInt(f.getParentFile().getName());
		String ownerTable = f.getParentFile().getParentFile().getName();
		DBModelTable ownerMapping = model.getEntity(ownerTable);
		DBModelColumn ownerColumn = null;
		Object ownerId = indexer.findId(ownerTableIndex, ownerMapping);
		Object ownerEntity = entityManager.find(ownerMapping.getEntityClass(), ownerId);
		for (DBModelColumn dbModelColumn : ownerMapping) {
			if (dbModelColumn.getName().equals(property)) {
				ownerColumn = dbModelColumn;
			}
		}
		DBModelTable nonOwnerMapping = model.getEntity(ownerColumn.getType().getSimpleName());
		int nonOwnerIndex = Integer.parseInt(data);
		Object nonOwnerId = indexer.findId(nonOwnerIndex, nonOwnerMapping);
		Object nonOwnerEntity = entityManager.find(nonOwnerMapping.getEntityClass(), nonOwnerId);
		if (ownerColumn.getColtype() == ColType.MANY_TO_MANY_NON_OWNER) {
			for (DBModelColumn dbModelColumn : nonOwnerMapping) {
				if (dbModelColumn.getName().equals(ownerColumn.getN2mOppositeProperty())) {
					addN2M(nonOwnerMapping, dbModelColumn, nonOwnerEntity, ownerEntity);
					return;
				}
			}
		} else {
			addN2M(ownerMapping, ownerColumn, ownerEntity, nonOwnerEntity);
		}
	}

	private void addN2M(DBModelTable ownerMapping, DBModelColumn ownerColumn, Object ownerEntity, Object nonOwnerEntity) {
		BeanWrapperImpl bwi = new BeanWrapperImpl(ownerEntity);
		Object collection = bwi.getPropertyValue(ownerColumn.getName());
		if (collection instanceof Set) {
			Set<Object> set = (Set<Object>) collection;
			set.add(nonOwnerEntity);
			Set<JSONJavaScriptModificationListener> listeners = GenericUtils.limit(this.listeners, ownerEntity, nonOwnerEntity);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					l.beforePersist(ownerMapping, ownerColumn, ownerEntity, nonOwnerEntity, DatabaseChangeType.MANY_TO_MANY_RELATION);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
			entityManager.persist(ownerEntity);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
					l.afterPersist(ownerMapping, ownerColumn, ownerEntity, nonOwnerEntity, DatabaseChangeType.MANY_TO_MANY_RELATION);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Calling js-listener " + l, e);
				}
			}
		}
	}

	private Object parseToFieldNumberType(String stringValue, Class<?> clazz) {
		if (clazz == int.class || clazz == Integer.class) {
			return Integer.valueOf(stringValue);
		} else if (clazz == long.class || clazz == Long.class) {
			return Long.valueOf(stringValue);
		} else if (clazz == byte.class || clazz == byte.class) {
			return Byte.valueOf(stringValue);
		} else if (clazz == short.class || clazz == short.class) {
			return Short.valueOf(stringValue);
		} else if (clazz == Timestamp.class) {
			return parseTimestamp(stringValue);
		} else if (clazz == double.class) {
			return Double.parseDouble(stringValue);
		} else {
			throw new RuntimeException("Unknown number type: " + clazz);
		}
	}

	private Timestamp parseTimestamp(String stringValue) {
		try {
			stringValue = URLDecoder.decode(stringValue, "utf8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		try {
			return Timestamp.valueOf(stringValue);
		} catch (IllegalArgumentException wrongFormat) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			try {
				Date d = sdf.parse(stringValue);
				return new Timestamp(d.getTime());
			} catch (ParseException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private Object sanitize(DBModelColumn c, String decodedStringValue, Class<?> clazz, DBModelTable t) {
		if (c.getColtype() == ColType.REQUIRED_STRING_OR_CHAR || c.getColtype() == ColType.OPTIONAL_STRING_OR_CHAR) {
			if (decodedStringValue == null) {
				return null;
			}
			try {
				return URLDecoder.decode(decodedStringValue.replace("+", "%2B"), "UTF-8").replace("%2B", "+");
			} catch (UnsupportedEncodingException e1) {
				throw new RuntimeException("UTF8 not supported!", e1);
			}
		} else if (c.getColtype() == ColType.REQUIRED_BOOLEAN || c.getColtype() == ColType.OPTIONAL_BOOLEAN) {
			return Boolean.valueOf(decodedStringValue);
		} else if (c.getColtype() == ColType.REQUIRED_NUMBER || c.getColtype() == ColType.OPTIONAL_NUMBER) {
			return parseToFieldNumberType(decodedStringValue, clazz);
		} else if (c.getColtype() == ColType.REQUIRED_TIMESTAMP) {
			return parseToFieldNumberType(decodedStringValue, clazz);
		}
		for (DBModelTable table : model.getModel()) {
			if (table.getEntityClass() == clazz) {
				for (DBModelColumn col : table) {
					if (col.getColtype() == ColType.ID) {
						if (decodedStringValue == null && c.getColtype() == ColType.OPTIONAL_MANY_TO_ONE) {
							return null;
						} else {
							Object id = indexer.findId(Integer.parseInt(decodedStringValue), table, col);
							Object reference = entityManager.find(clazz, id);
							if (reference == null) {
								throw new RuntimeException(clazz.getSimpleName() + " not found! ");
							}
							return reference;
						}
					}
				}
			}
		}
		throw new RuntimeException("Cant sanitize " + t.getName() + "." + c + " to " + clazz);
	}

	private Object put(DBModelTable t, Map<String, String> headers, byte[] ba) {
		try {
			Object entity = t.getEntityClass().newInstance();
			BeanWrapperImpl bwi = new BeanWrapperImpl(t.getEntityClass());
			for (DBModelColumn c : t) {
				PropertyDescriptor pd = bwi.getPropertyDescriptor(c.getName());
				String string = headers.get("x-" + c.getName().toLowerCase());
				boolean isnull = headers.get("x-" + c.getName().toLowerCase() + "-null") != null;
				switch (c.getColtype()) {
				case ID:
				case VERSION:
				case MANY_TO_MANY_OWNER:
				case ONE_TO_MANY:
					continue;
				case REQUIRED_BODY_DATA:
				case OPTIONAL_BODY_DATA:
					pd.getWriteMethod().invoke(entity, ba);
					continue;
				case OPTIONAL_BOOLEAN:
				case OPTIONAL_NUMBER:
				case OPTIONAL_STRING_OR_CHAR:
				case REQUIRED_BOOLEAN:
				case REQUIRED_NUMBER:
				case REQUIRED_STRING_OR_CHAR:
				case REQUIRED_TIMESTAMP:
				case REQUIRED_MANY_TO_ONE:
				case OPTIONAL_MANY_TO_ONE:
					pd.getWriteMethod().invoke(entity, isnull ? null : sanitize(c, string, pd.getPropertyType(), t));

				default:
					break;
				}

			}
			Set<JSONJavaScriptModificationListener> listeners = GenericUtils.limit(this.listeners, entity, null);
			for (JSONJavaScriptModificationListener l : listeners) {
				try {
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
			return entity;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean canWrite(DBModelColumn c) {
		switch (c.getColtype()) {
		case MANY_TO_MANY_OWNER:
		case MANY_TO_MANY_NON_OWNER:
		case ONE_TO_MANY:
		case OPTIONAL_BOOLEAN:
		case OPTIONAL_MANY_TO_ONE:
		case OPTIONAL_NUMBER:
		case OPTIONAL_STRING_OR_CHAR:
		case REQUIRED_BOOLEAN:
		case REQUIRED_MANY_TO_ONE:
		case REQUIRED_NUMBER:
		case REQUIRED_STRING_OR_CHAR:
			return true;
		case ID:
		case REQUIRED_BODY_DATA:
		case OPTIONAL_BODY_DATA:
		case VERSION:
		default:
			return false;
		}
	}

	private void writeValue(StringBuilder sb, BeanWrapperImpl bwi, DBModelColumn c) {
		switch (c.getColtype()) {
		case MANY_TO_MANY_OWNER:
		case MANY_TO_MANY_NON_OWNER:
		case ONE_TO_MANY:
		case OPTIONAL_MANY_TO_ONE:
		case REQUIRED_MANY_TO_ONE:
			Object val = bwi.getPropertyValue(c.getName());
			if (val == null) {
				sb.append("null");
			} else if (val instanceof Set) {
				sb.append("[");
				DBModelColumn ownerIdCol = null;
				DBModelColumn foreignIdCol = null;
				DBModelTable foreignTable = null;
				DBModelTable ownerTable = null;
				boolean first = true;
				for (DBModelTable table : model.getModel()) {
					if (table.getEntityClass() == c.getType()) {
						foreignTable = table;
						for (DBModelColumn col : table) {
							if (col.getColtype() == ColType.ID) {
								foreignIdCol = col;
							}
						}
					}
					if (table.getEntityClass() == bwi.getRootClass()) {
						ownerTable = table;
						for (DBModelColumn col : table) {
							if (col.getColtype() == ColType.ID) {
								ownerIdCol = col;
							}
						}
					}
				}

				String idQuery = "SELECT r." + foreignIdCol.getName() + " FROM " + ownerTable.getEntityClass().getCanonicalName() + " o RIGHT JOIN o." + c.getName() + " r WHERE o."
						+ ownerIdCol.getName() + "=:oid";
				for (Object id : entityManager.createQuery(idQuery, foreignIdCol.getType()).setParameter("oid", bwi.getPropertyValue(ownerIdCol.getName())).getResultList()) {
					if (!first) {
						sb.append(",");
					}
					sb.append(indexer.getIndexById(id, foreignIdCol, foreignTable));
					first = false;
				}
				sb.append("]");
			} else {
				sb.append(getIndex(val));
			}
			break;
		case OPTIONAL_BOOLEAN:
		case REQUIRED_BOOLEAN:
			Object designatedBooleanValue = bwi.getPropertyValue(c.getName());
			for (DBColumnSimpleValueMasquerade k : masquerades) {
				designatedBooleanValue = k.masquerade(designatedBooleanValue, c, bwi.getRootInstance());
			}
			sb.append(designatedBooleanValue);
			break;
		case OPTIONAL_NUMBER:
		case REQUIRED_NUMBER: {
			Object designatedNumberValue = bwi.getPropertyValue(c.getName());
			for (DBColumnSimpleValueMasquerade k : masquerades) {
				designatedNumberValue = k.masquerade(designatedNumberValue, c, bwi.getRootInstance());
			}
			sb.append(designatedNumberValue);
		}
			break;
		case OPTIONAL_STRING_OR_CHAR:
			Object designatedStringValue = bwi.getPropertyValue(c.getName());
			for (DBColumnSimpleValueMasquerade k : masquerades) {
				designatedStringValue = k.masquerade(designatedStringValue, c, bwi.getRootInstance());
			}
			if (designatedStringValue == null) {
				sb.append("null");
			} else {
				sb.append(serializer.utf8String("" + designatedStringValue));
			}
			break;
		case REQUIRED_STRING_OR_CHAR:
			Object designatedStringRequiredValue = bwi.getPropertyValue(c.getName());
			for (DBColumnSimpleValueMasquerade k : masquerades) {
				designatedStringRequiredValue = k.masquerade(designatedStringRequiredValue, c, bwi.getRootInstance());
			}
			sb.append(serializer.utf8String("" + designatedStringRequiredValue));
			break;
		case REQUIRED_BODY_DATA:
		case OPTIONAL_BODY_DATA:
		case VERSION:
		case ID:
		default:
			return;
		}
	}

	private void writeKey(StringBuilder sb, DBModelColumn c) {
		switch (c.getColtype()) {
		case MANY_TO_MANY_OWNER:
		case MANY_TO_MANY_NON_OWNER:
		case ONE_TO_MANY:
		case OPTIONAL_BOOLEAN:
		case OPTIONAL_MANY_TO_ONE:
		case OPTIONAL_NUMBER:
		case OPTIONAL_STRING_OR_CHAR:
		case REQUIRED_BOOLEAN:
		case REQUIRED_MANY_TO_ONE:
		case REQUIRED_NUMBER:
		case REQUIRED_STRING_OR_CHAR:
			sb.append("\"");
			sb.append(c.getName());
			sb.append("\":");
		case REQUIRED_BODY_DATA:
		case OPTIONAL_BODY_DATA:
		case ID:
		case VERSION:
		}
	}

	@Transactional
	private void updateSimpleFieldValue(DBModelTable t, DBModelColumn c, HttpServletRequest req, String index) {

		Object entityId = indexer.findId(Integer.parseInt(index), t);
		Object entity = entityManager.find(t.getEntityClass(), entityId);
		Object genuineValue = null;
		BeanWrapperImpl bwi = new BeanWrapperImpl(entity);

		byte[] newURIEncodedValue;
		if (req.getHeader("x-" + c.getName().toLowerCase() + "-null") != null) {
			newURIEncodedValue = null;
		} else {
			try {
				newURIEncodedValue = StreamUtils.copyToByteArray(req.getInputStream());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		// check
		switch (c.getColtype()) {
		case ID:
		case MANY_TO_MANY_OWNER:
		case ONE_TO_MANY:
		case VERSION:
		case OPTIONAL_MANY_TO_ONE:
		case REQUIRED_MANY_TO_ONE:
			throw new RuntimeException("Not a simple field:" + t + c);
		case REQUIRED_BOOLEAN:
		case REQUIRED_BODY_DATA:
		case REQUIRED_NUMBER:
		case REQUIRED_STRING_OR_CHAR:
			if (newURIEncodedValue == null)
				throw new RuntimeException("Must not be null: " + t + c);
		case OPTIONAL_BODY_DATA:
		case OPTIONAL_BOOLEAN:
		case OPTIONAL_NUMBER:
		case OPTIONAL_STRING_OR_CHAR:
		}
		// update
		switch (c.getColtype()) {
		case ID:
		case MANY_TO_MANY_OWNER:
		case ONE_TO_MANY:
		case VERSION:
		case OPTIONAL_MANY_TO_ONE:
		case REQUIRED_MANY_TO_ONE:
			throw new RuntimeException("Not a simple field:" + t + c);
		case OPTIONAL_BOOLEAN:
			if (newURIEncodedValue == null) {
				bwi.setPropertyValue(c.getName(), genuineValue = null);
				break;
			}
		case REQUIRED_BOOLEAN:
			bwi.setPropertyValue(c.getName(), genuineValue = Boolean.parseBoolean(new String(newURIEncodedValue)));
			break;

		case OPTIONAL_BODY_DATA:
			if (newURIEncodedValue == null) {
				bwi.setPropertyValue(c.getName(), genuineValue = null);
				break;
			}
		case REQUIRED_BODY_DATA:
			bwi.setPropertyValue(c.getName(), genuineValue = newURIEncodedValue);
			break;

		case OPTIONAL_NUMBER:
			if (newURIEncodedValue == null) {
				bwi.setPropertyValue(c.getName(), genuineValue = null);
				break;
			}
		case REQUIRED_NUMBER:
			bwi.setPropertyValue(c.getName(), genuineValue = parseToFieldNumberType(new String(newURIEncodedValue), c.getType()));
			break;
		case OPTIONAL_STRING_OR_CHAR:
			if (newURIEncodedValue == null) {
				bwi.setPropertyValue(c.getName(), genuineValue = null);
				break;
			}
		case REQUIRED_STRING_OR_CHAR:
			bwi.setPropertyValue(c.getName(), new String(newURIEncodedValue));
			break;
		}
		Set<JSONJavaScriptModificationListener> listeners = GenericUtils.limit(this.listeners, entity, genuineValue);
		for (JSONJavaScriptModificationListener l : listeners) {
			try {
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
	}

}
