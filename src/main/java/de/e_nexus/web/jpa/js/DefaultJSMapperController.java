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
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.springframework.beans.BeanWrapperImpl;

import de.e_nexus.web.jpa.js.masker.DBColumnSimpleValueMasquerade;
import de.e_nexus.web.jpa.js.mod.BlobHandler;
import de.e_nexus.web.jpa.js.mod.ColType;
import de.e_nexus.web.jpa.js.mod.DBModelColumn;
import de.e_nexus.web.jpa.js.mod.DBModelHolder;
import de.e_nexus.web.jpa.js.mod.DBModelTable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;

@Named
public class DefaultJSMapperController implements JSMapperController {

	private static final Logger LOG = Logger.getLogger(DefaultJSMapperController.class.getCanonicalName());

	private static final int INDEX_ERROR_VALUE = -1;

	@Inject
	private final JSMapperPersistenceManager persistenceManager = null;

	@Inject
	private final DBColumnSimpleValueMasquerade[] masquerades = {};

	@Inject
	private final IndexFacade indexer = null;

	@PersistenceContext
	private final EntityManager entityManager = null;

	@Inject
	private final StringSerializer serializer = null;

	@Inject
	private final BlobHandler blobHandler = null;

	@Inject
	private final DBModelHolder model = null;

	public Number getId(final Object entity) {
		for (DBModelTable t : model.getModel()) {
			if (t.getEntityClass().isInstance(entity)) {
				for (DBModelColumn col : model.getIdColumns(t)) {
					if (col.getColtype() == ColType.ID) {
						BeanWrapperImpl bwi = new BeanWrapperImpl(entity);
						Object o = bwi.getPropertyValue(col.getName());
						if (o instanceof Number)
							return (Number) o;
					}
				}
			}
		}
		return null;
	}

	public Number getIndex(final Object val) {
		for (DBModelTable t : model.getModel()) {
			if (t.getEntityClass().isInstance(val)) {
				for (DBModelColumn col : model.getIdColumns(t)) {
					if (col.getColtype() == ColType.ID) {
						BeanWrapperImpl bwi = new BeanWrapperImpl(val);
						Object o = bwi.getPropertyValue(col.getName());
						return indexer.getIndexById(o, col, t);
					}
				}
			}
		}
		return INDEX_ERROR_VALUE;
	}

	@Transactional
	@Override
	public void doGetCount(final DBModelTable dbModelTable, final HttpServletResponse resp) throws IOException {
		Number n = entityManager
				.createQuery("SELECT COUNT(*) FROM " + dbModelTable.getEntityClass().getCanonicalName(), Number.class)
				.getSingleResult();
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
	public Number put(final String entityName, final Map<String, String> headers, final byte[] ba,
			final HttpServletRequest request) {
		DBModelTable table = model.getEntity(entityName);
		Object put = sanitizedPut(table, headers, ba, request);
		return getId(put);
	}

	@Override
	@Transactional
	public String getDetails(final String entityName, final int index) {
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
	public void updateSimpleFieldValue(final File f, final HttpServletRequest req, final URL url) {
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
	public void deleteEntity(final File f, final HttpServletRequest request) {
		String entityName = f.getParentFile().getName();
		DBModelTable entityTable = model.getEntity(entityName);
		for (DBModelColumn col : model.getIdColumns(entityTable)) {
			if (col.getColtype() == ColType.ID) {
				Object id = indexer.findId(Integer.parseInt(f.getName()), entityTable, Collections.singleton(col));
				Object entity = entityManager.find(entityTable.getEntityClass(), id);
				persistenceManager.removeAction(entityTable, entity, request);
				return;
			}
		}
	}

	@Override
	@Transactional
	public void updateRelation(final File f, final Integer newIndex, final URL url, final HttpServletRequest request) {
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
				persistenceManager.persistAction(table, entity, c, newValue, request);
				return;
			}
		}
	}

	@Override
	@Transactional
	public String getIndexJSONById(final String entity, final int id) {
		DBModelTable t = model.getEntity(entity);
		for (DBModelColumn idc : model.getIdColumns(t)) {
			if (idc.getColtype() == ColType.ID) {
				Object pk = parseToFieldNumberType("" + id, idc.getType());
				Object find = entityManager.find(t.getEntityClass(), pk);
				Number index = getIndex(find);
				if (index.intValue() == INDEX_ERROR_VALUE) {
					return "{\"error\":\"No such pk for " + entity + "!\"}";
				}
				return "{\"index\":" + index + "}";
			}
		}
		throw null;
	}

	@Override
	@Transactional
	public int getIndexById(final String entity, final int id) {
		DBModelTable t = model.getEntity(entity);
		for (DBModelColumn idc : model.getIdColumns(t)) {
			if (idc.getColtype() == ColType.ID) {
				Object pk = parseToFieldNumberType("" + id, idc.getType());
				Object find = entityManager.find(t.getEntityClass(), pk);
				Number index = getIndex(find);
				if (index.intValue() == INDEX_ERROR_VALUE) {
					return -1_000;
				}
				return index.intValue();
			}
		}
		throw null;
	}

	@Override
	@Transactional
	public void addN2M(final File f, final String data, final HttpServletRequest request) {
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
					addN2M(nonOwnerMapping, dbModelColumn, nonOwnerEntity, ownerEntity, request);
					return;
				}
			}
		} else {
			addN2M(ownerMapping, ownerColumn, ownerEntity, nonOwnerEntity, request);
		}
	}

	private void addN2M(final DBModelTable ownerMapping, final DBModelColumn ownerColumn, final Object ownerEntity,
			final Object nonOwnerEntity, final HttpServletRequest request) {
		BeanWrapperImpl bwi = new BeanWrapperImpl(ownerEntity);
		Object collection = bwi.getPropertyValue(ownerColumn.getName());
		if (collection instanceof Set) {
			Set<Object> set = (Set<Object>) collection;
			set.add(nonOwnerEntity);
			persistenceManager.persistN2MAction(ownerMapping, ownerColumn, ownerEntity, nonOwnerEntity, request);
		}
	}

	private Object parseToFieldNumberType(final String stringValue, final Class<?> clazz) {
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

	private Object sanitize(final DBModelColumn c, final String decodedStringValue, final Class<?> clazz,
			final DBModelTable t) {
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
							Object id = indexer.findId(Integer.parseInt(decodedStringValue), table,
									Collections.singleton(col));
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

	private Object sanitizedPut(final DBModelTable t, final Map<String, String> headers, final byte[] ba,
			final HttpServletRequest request) {
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
				case REQUIRED_BODY_DATA_BLOB:
				case OPTIONAL_BODY_DATA_BLOB:

					pd.getWriteMethod().invoke(entity, blobHandler.generateBlob(ba));
					continue;
				case OPTIONAL_BOOLEAN:
				case OPTIONAL_NUMBER:
				case OPTIONAL_STRING_OR_CHAR:
				case REQUIRED_BOOLEAN:
				case REQUIRED_NUMBER:
				case REQUIRED_STRING_OR_CHAR:
				case REQUIRED_TIMESTAMP:
				case REQUIRED_DATE:
				case REQUIRED_MANY_TO_ONE:
				case OPTIONAL_TIMESTAMP:
				case OPTIONAL_DATE:
				case OPTIONAL_MANY_TO_ONE:
				case ONE_TO_ONE_NON_OWNING_SIDE:
				case ONE_TO_ONE_OWNING_SIDE:
					pd.getWriteMethod().invoke(entity, isnull ? null : sanitize(c, string, pd.getPropertyType(), t));

				default:
					break;
				}

			}
			persistenceManager.insertNewAction(t, entity, request);
			return entity;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean canWrite(final DBModelColumn c) {
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

	private void writeValue(final StringBuilder sb, final BeanWrapperImpl bwi, final DBModelColumn c) {
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

				String idQuery = "SELECT r." + foreignIdCol.getName() + " FROM "
						+ ownerTable.getEntityClass().getCanonicalName() + " o RIGHT JOIN o." + c.getName()
						+ " r WHERE o." + ownerIdCol.getName() + "=:oid";
				for (Object id : entityManager.createQuery(idQuery, foreignIdCol.getType())
						.setParameter("oid", bwi.getPropertyValue(ownerIdCol.getName())).getResultList()) {
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

	private void writeKey(final StringBuilder sb, final DBModelColumn c) {
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
	private void updateSimpleFieldValue(final DBModelTable t, final DBModelColumn c, final HttpServletRequest request,
			final String index) {

		Object entityId = indexer.findId(Integer.parseInt(index), t);
		Object entity = entityManager.find(t.getEntityClass(), entityId);
		Object genuineValue = null;
		BeanWrapperImpl bwi = new BeanWrapperImpl(entity);

		byte[] newURIEncodedValue;
		if (request.getHeader("x-" + c.getName().toLowerCase() + "-null") != null) {
			newURIEncodedValue = null;
		} else {
			try {
				newURIEncodedValue = CustomUtils.readBytes(request.getInputStream());
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
			bwi.setPropertyValue(c.getName(),
					genuineValue = parseToFieldNumberType(new String(newURIEncodedValue), c.getType()));
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
		persistenceManager.persistSingleFieldAction(t, c, entity, genuineValue, request);
	}

}
