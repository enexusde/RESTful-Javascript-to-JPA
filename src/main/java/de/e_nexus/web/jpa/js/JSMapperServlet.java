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
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.BeanFactory;

import de.e_nexus.web.jpa.js.mod.DBModelHolder;
import de.e_nexus.web.jpa.js.mod.DBModelTable;
import de.e_nexus.web.jpa.js.mod.GetRequestType;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns = { "/jms/*" })
public class JSMapperServlet extends HttpServlet {
	public static final String ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE = "org.springframework.web.context.WebApplicationContext.ROOT";

	/**
	 * The logger for the the servlet.
	 */
	private static final Logger LOG = Logger.getLogger(JSMapperServlet.class.getCanonicalName());

	private static final String UTF8NAME = "utf-8";

	private static final long serialVersionUID = -7377744771353464633L;

	private BeanFactory app;

	public static final Charset UTF8;
	private static final String JSON = "application/json;charset=" + UTF8NAME;
	private static final String JAVASCRIPT = "application/javascript;charset=" + UTF8NAME;
	static {
		UTF8 = Charset.forName(UTF8NAME);
	}

	public JSMapperServlet() {
		LOG.config("--- RESTful JavaScript to JPA ---");
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		app = (BeanFactory) config.getServletContext().getAttribute(ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		if (app == null) {
			throw new RuntimeException("App not loaded!");
		}
	}

	@Override
	protected void doPut(HttpServletRequest request, HttpServletResponse resp) throws ServletException, IOException {
		StringBuffer urls = request.getRequestURL();
		URL url = new URL(urls.toString());
		File f = new File(url.getFile());
		String filename = f.getName();
		ServletInputStream inputStream = request.getInputStream();
		byte[] copyToByteArray = CustomUtils.readBytes(inputStream);
		Map<String, String> headers = new LinkedHashMap<String, String>(0);
		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String n = (String) headerNames.nextElement();
			String v = request.getHeader(n);
			String nheader = request.getHeader(n + "-null");
			if (nheader != null) {
				v = null;
			}
			headers.put(n, v);
		}
		String result = "";
		try {
			Number id = getController().put(filename, headers, copyToByteArray, request);
			int index = getController().getIndexById(filename, id.intValue());
			resp.setContentType(JSON);
			String val = "{\"id\":" + id + ", \"no\":\"" + index + "\"}";
			resp.setContentLength(val.length());
			resp.getOutputStream().write(val.getBytes());
		} catch (Exception e) {
			LOG.log(Level.SEVERE, urls.toString(), e);
			result = "{\"error\":\"" + e.getMessage() + "\"}";
			resp.setContentLength(result.length());
			resp.setContentType(JSON);
			ServletOutputStream out = resp.getOutputStream();
			out.write(result.getBytes());
			out.close();
		}
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		StringBuffer urls = req.getRequestURL();
		URL url = new URL(urls.toString());
		File f = new File(url.getFile());
		JSMapperHandler bean = getHandler();
		switch (bean.calculateDeleteRequestType(f, req, url)) {
		case DELETE_ENTITY:
			try {
				getController().deleteEntity(f, req);
			} catch (RuntimeException e) {
				LOG.log(Level.WARNING, e.getMessage(), e);
				Throwable t = e;
				while (t != null) {
					if (t.getClass().getCanonicalName()
							.equals("org.hibernate.exception.ConstraintViolationException")) {
						resp.sendError(428);
					}
					t = t.getCause();
				}
			}
			break;
		default:
			break;
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		StringBuffer urls = request.getRequestURL();
		URL url = new URL(urls.toString());
		File f = new File(url.getFile());
		switch (getHandler().calculatePushRequestType(f, request, url)) {
		case UPDATE_FIELD:
			getController().updateSimpleFieldValue(f, request, url);
			break;
		case UPDATE_RELATION:
			String copyToString = new String(CustomUtils.readBytes(request.getInputStream()), UTF8);
			Integer newIndex = null;
			if (copyToString.length() > 0)
				newIndex = Integer.parseInt(copyToString);
			getController().updateRelation(f, newIndex, url, request);
			break;
		case ADD_N2M:
			String data = new String(CustomUtils.readBytes(request.getInputStream()), UTF8);
			getController().addN2M(f, data, request);
		default:
			break;
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		StringBuffer urls = req.getRequestURL();
		URL url = new URL(urls.toString());
		File f = new File(url.getFile());
		String filename = f.getName();
		JSMapperHandler bean = getHandler();
		GetRequestType calculateGetRequestType = bean.calculateGetRequestType(f, req, url);
		LOG.info("GET Request " + urls + " -> " + calculateGetRequestType);
		switch (calculateGetRequestType) {
		case INDEX_OF_ID: {
			String entity = f.getName();
			String json = getController().getIndexJSONById(entity, Integer.parseInt(req.getParameter("id")));
			resp.setContentLength(json.length());
			resp.setContentType(JSON);
			resp.getOutputStream().write(json.getBytes());
			break;
		}
		case COUNT:
			if (url.getFile().endsWith("/")) {
				for (DBModelTable dbModelTable : getModelHolder().getModel()) {
					if (dbModelTable.getName().equals(filename)) {
						resp.setContentType(JSON);
						getController().doGetCount(dbModelTable, resp);
						return;
					}
				}
			}
			break;
		case DETAILS:
			String entity = f.getParentFile().getName();
			String json = getController().getDetails(entity, Integer.parseInt(filename));
			byte[] bytes = json.getBytes(UTF8);
			resp.setContentLength(bytes.length);
			resp.setContentType(JSON);
			resp.getOutputStream().write(bytes);
			break;
		case JAVASCRIPT:
			String javascriptCode = bean.getJavascriptCode();
			resp.setContentLength(javascriptCode.length());
			resp.setContentType(JAVASCRIPT);
			resp.getOutputStream().write(javascriptCode.getBytes());
			break;
		case LAZY_BINARY_DATA_FIELD:
			resp.setContentType(JSON);
			bean.writeBinaryDataField(f, resp);
		default:
			break;
		}
	}

	private DBModelHolder getModelHolder() {
		return app.getBean(DBModelHolder.class);
	}

	private JSMapperHandler getHandler() {
		return app.getBean(JSMapperHandler.class);
	}

	private JSMapperController getController() {
		return app.getBean(JSMapperController.class);
	}
}