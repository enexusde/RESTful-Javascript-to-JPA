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
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.e_nexus.web.jpa.js.mod.DBModelTable;

public interface JSMapperController {
	String getDetails(String entity, int index);

	void updateSimpleFieldValue(File f, HttpServletRequest req, URL url);

	Number put(String entityName, Map<String, String> headers, byte[] ba);

	void doGetCount(DBModelTable dbModelTable, HttpServletResponse resp) throws IOException;

	void deleteEntity(File f);

	void updateRelation(File f, Integer newIndex, URL url);

	String getIndexJSONById(String entity, int id);

	/**
	 * Returns the index of the id. If tuple does not exists this returns
	 * <code>-1000</code>
	 * 
	 * @param entity The entity to look for, never <code>null</code>.
	 * @param id     The primary key to look for.
	 * @return The index or -1000.
	 */
	int getIndexById(String entity, int id);

	void addN2M(File f, String data);
}
