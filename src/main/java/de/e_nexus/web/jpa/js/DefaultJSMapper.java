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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.StreamUtils;

import de.e_nexus.web.jpa.js.mod.ColType;
import de.e_nexus.web.jpa.js.mod.DBModelColumn;
import de.e_nexus.web.jpa.js.mod.DBModelHolder;
import de.e_nexus.web.jpa.js.mod.DBModelTable;
import de.e_nexus.web.jpa.js.mod.DeleteRequestType;
import de.e_nexus.web.jpa.js.mod.GetRequestType;
import de.e_nexus.web.jpa.js.mod.PushRequestType;

@Named
public class DefaultJSMapper implements JSMapperHandler {


	private static final String JS_CODE;
	static {
		InputStream js = DefaultJSMapper.class.getResourceAsStream("/nofold.js");
		try {
			JS_CODE = StreamUtils.copyToString(js, JSMapperServlet.UTF8);
			js.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static final Logger LOG = Logger.getLogger(DefaultJSMapper.class.getCanonicalName());

	@PersistenceContext
	private final EntityManager entityManager = null;

	@Inject
	private final DBModelHolder model = null;

	@Inject
	private final IndexFacade indexer = null;

	public String getJavascriptCode() {

		StringBuilder sb = new StringBuilder("var tm={");
		boolean first = true;
		for (ColType colType : ColType.values()) {
			if (first) {
				first = false;
			} else {
				sb.append(",");
			}
			sb.append(colType.name());
			sb.append(":");
			sb.append(Integer.toString(colType.ordinal()));
		}
		sb.append("};");
		sb.append("var jsm= {");
		first = true;
		for (DBModelTable t : model.getModel()) {
			if (first) {
				first = false;
			} else {
				sb.append(",");
			}
			sb.append(t.getName());
			sb.append(":{");
			boolean firstCol = true;
			for (DBModelColumn c : t) {
				if (c.getColtype() == ColType.VERSION) {
					continue;
				}
				if (firstCol) {
					firstCol = false;
				} else {
					sb.append(",");
				}
				sb.append(c.getName());
				sb.append(":{t:");
				if (c.getColtype() == null) {
					throw new RuntimeException("Type of " + t + "." + c
							+ " is unknown but must be known in order to expose field as javascript-property.");
				}
				sb.append(c.getColtype().ordinal());
				if (c.getColtype() == ColType.ONE_TO_MANY || c.getColtype() == ColType.REQUIRED_MANY_TO_ONE
						|| c.getColtype() == ColType.MANY_TO_MANY_NON_OWNER
						|| c.getColtype() == ColType.MANY_TO_MANY_OWNER) {
					sb.append(",type:'");
					sb.append(c.getType().getSimpleName());
					sb.append("'");
				}
				sb.append("}");

			}
			sb.append("}");
		}
		sb.append("};\n");
		String overrideLocation = System.getProperty("de.e_nexus.web.jpa.js.nofold");
		if (overrideLocation != null) {

			try {
				FileInputStream fis = new FileInputStream(overrideLocation);
				sb.append(StreamUtils.copyToString(fis, JSMapperServlet.UTF8));
				fis.close();
			} catch (IOException e) {
				LOG.log(Level.SEVERE, overrideLocation, e);
				sb.append(JS_CODE);
			}
		} else {
			sb.append(JS_CODE);
		}
		LOG.fine("Deliver javascript code to client.");
		LOG.fine("Javascript code to send:'" + sb.toString() + "'.");
		return sb.toString();
	}

	@Override
	public GetRequestType calculateGetRequestType(File f, HttpServletRequest req, URL url) {
		if (f.getName().equals("jsm.js"))
			return GetRequestType.JAVASCRIPT;
		if (url.getPath().endsWith("/")) {
			if (req.getParameter("id") != null) {
				return GetRequestType.INDEX_OF_ID;
			}
			return GetRequestType.COUNT;
		}

		String entityForLazyLoad = f.getParentFile().getParentFile().getName();
		DBModelTable entity = model.getEntity(entityForLazyLoad);
		if (entity != null) {
			for (DBModelColumn dbModelColumn : entity) {
				String name = dbModelColumn.getName();
				if (name.equals(f.getName())) {
					return GetRequestType.LAZY_BINARY_DATA_FIELD;
				}
			}
		}

		return GetRequestType.DETAILS;
	}

	@Override
	public PushRequestType calculatePushRequestType(File f, HttpServletRequest req, URL url) {
		String entity = f.getParentFile().getParentFile().getName();
		String property = f.getName();
		DBModelTable table = model.getEntity(entity);
		for (DBModelColumn c : table) {
			if (c.getName().equals(property)) {
				switch (c.getColtype()) {
				case MANY_TO_MANY_OWNER:
				case MANY_TO_MANY_NON_OWNER:
					return PushRequestType.ADD_N2M;
				case ID:
				case ONE_TO_MANY:
				case VERSION:
					break;
				case OPTIONAL_MANY_TO_ONE:
				case REQUIRED_MANY_TO_ONE:
					return PushRequestType.UPDATE_RELATION;
				case OPTIONAL_BODY_DATA:
				case OPTIONAL_BODY_DATA_BLOB:
				case OPTIONAL_BOOLEAN:
				case OPTIONAL_NUMBER:
				case OPTIONAL_STRING_OR_CHAR:
				case REQUIRED_BODY_DATA:
				case REQUIRED_BODY_DATA_BLOB:
				case REQUIRED_BOOLEAN:
				case REQUIRED_NUMBER:
				case REQUIRED_STRING_OR_CHAR:
					return PushRequestType.UPDATE_FIELD;
				}
			}
		}

		return null;
	}

	@Override
	public DeleteRequestType calculateDeleteRequestType(File f, HttpServletRequest req, URL url) {
		String entityName = f.getParentFile().getName();
		DBModelTable entity = model.getEntity(entityName);
		if (entity != null) {
			return DeleteRequestType.DELETE_ENTITY;
		}
		return null;
	}

	@Override
	@Transactional
	public void writeBinaryDataField(File f, HttpServletResponse resp) {
		String entityForLazyLoad = f.getParentFile().getParentFile().getName();
		DBModelColumn field = null;
		DBModelTable table = model.getEntity(entityForLazyLoad);
		if (table != null) {
			for (DBModelColumn col : table) {
				String name = col.getName();
				if (name.equals(f.getName())) {
					field = col;
					break;
				}
			}
		}
		int entityIndex = Integer.parseInt(f.getParentFile().getName());
		Object findId = indexer.findId(entityIndex, table);
		Object entity = entityManager.find(table.getEntityClass(), findId);
		BeanWrapperImpl bwi = new BeanWrapperImpl(entity);
		Object propertyValue = bwi.getPropertyValue(field.getName());

		try {
			ServletOutputStream os = resp.getOutputStream();
			if (propertyValue instanceof byte[]) {
				byte[] bs = (byte[]) propertyValue;
				boolean first = true;
				os.write('[');
				for (byte b : bs) {
					if (first) {
						first = false;
					} else {
						os.write(',');

					}
					os.write(Byte.toString(b).getBytes(JSMapperServlet.UTF8));
				}
				os.write(']');
			}
		} catch (IOException e) {
			throw new RuntimeException("Can not write value to response!", e);
		}
	}

}