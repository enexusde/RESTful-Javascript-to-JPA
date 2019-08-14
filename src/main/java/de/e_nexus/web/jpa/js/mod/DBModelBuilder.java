/**    ____  _____________________      __       __                  _____           _       __     __             _            
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Attribute.PersistentAttributeType;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;

public class DBModelBuilder {
	private static final Logger LOG = Logger.getLogger(DBModelBuilder.class.getCanonicalName());
	private EntityManager entityManager;

	public void setEntityManager(EntityManager myEntityManager) {
		entityManager = myEntityManager;
	}

	public DBSchemaModel build() {

		Metamodel metamodel = entityManager.getMetamodel();
		DBSchemaModel sm = new DBSchemaModel();
		for (EntityType<?> et : metamodel.getEntities()) {
			DBModelTable t = new DBModelTable(et.getBindableJavaType());
			if (!et.hasSingleIdAttribute()) {
				throw new RuntimeException("Single id class required!");
			}
			sm.add(t);
			for (Attribute<?, ?> attribute : et.getAttributes()) {
				ColType ct = calculateType(attribute);
				Class<?> javaType = attribute.getJavaType();
				String oppositePropery = null;
				if (Collection.class.isAssignableFrom(javaType)) {
					if (attribute instanceof PluralAttribute) {
						PluralAttribute pluralPersistentAttribute = (PluralAttribute) attribute;
						Member member = pluralPersistentAttribute.getJavaMember();
						if (member instanceof Method) {
							Method method = (Method) member;
							for (Annotation anno : method.getAnnotations()) {
								if (anno instanceof ManyToMany) {
									ManyToMany manyToMany = (ManyToMany) anno;
									if (manyToMany.mappedBy() == null) {
										throw new RuntimeException(
												"Many-to-many annotation must have a mappedBy-value!");
									}
									oppositePropery = manyToMany.mappedBy();
								}
							}
						}
						javaType = pluralPersistentAttribute.getElementType().getJavaType();
					}
				}
				DBModelColumn c = new DBModelColumn(attribute.getName(), ct, javaType, oppositePropery);
				t.add(c);
			}
		}
		return sm;
	}

	private ColType calculateType(Attribute<?, ?> attribute) {
		PersistentAttributeType at = attribute.getPersistentAttributeType();
		if (at == PersistentAttributeType.BASIC) {
			if (attribute instanceof SingularAttribute) {
				SingularAttribute sa = (SingularAttribute) attribute;
				Class jt = sa.getJavaType();
				if (sa.isOptional()) {
					if (String.class.isAssignableFrom(jt) || char.class == jt || Character.class == jt) {
						return ColType.OPTIONAL_STRING_OR_CHAR;
					}
					if (Number.class.isAssignableFrom(jt) || jt == long.class || jt == byte.class || jt == int.class
							|| jt == short.class || jt == float.class || jt == double.class) {
						return ColType.OPTIONAL_NUMBER;
					}
					if (Boolean.class == jt || boolean.class == jt) {
						return ColType.OPTIONAL_BOOLEAN;
					}
					if (byte[].class == jt || Byte[].class == jt) {
						return ColType.OPTIONAL_BODY_DATA;
					}

				}
				if (sa.isId()) {
					return ColType.ID;
				}
				if (sa.isVersion()) {
					return ColType.VERSION;
				}
				if (String.class.isAssignableFrom(jt) || char.class == jt || Character.class == jt) {
					return ColType.REQUIRED_STRING_OR_CHAR;
				}
				if (Number.class.isAssignableFrom(jt) || jt == long.class || jt == byte.class || jt == int.class
						|| jt == short.class || jt == float.class || jt == double.class) {
					return ColType.REQUIRED_NUMBER;
				}
				if (Boolean.class == jt || boolean.class == jt) {
					return ColType.REQUIRED_BOOLEAN;
				}
				if (byte[].class == jt || Byte[].class == jt) {
					return ColType.REQUIRED_BODY_DATA;
				}
				LOG.severe("Type " + jt + " is not mapped!");
			}
		} else if (at == PersistentAttributeType.ONE_TO_MANY) {
			if (attribute instanceof SetAttribute<?, ?>) {
				return ColType.ONE_TO_MANY;
			}
		} else if (at == PersistentAttributeType.MANY_TO_ONE) {
			if (attribute instanceof SingularAttribute<?, ?>) {
				SingularAttribute<?, ?> sa = (SingularAttribute<?, ?>) attribute;
				if (sa.isOptional()) {
					return ColType.OPTIONAL_MANY_TO_ONE;
				} else {
					return ColType.REQUIRED_MANY_TO_ONE;
				}
			}
		} else if (at == PersistentAttributeType.MANY_TO_MANY) {
			if (attribute instanceof SetAttribute<?, ?>) {

				SetAttribute<?, ?> setAttribute = (SetAttribute<?, ?>) attribute;
				boolean owningSide = false;
				if (setAttribute.getJavaMember() instanceof Method) {
					Method method = (Method) setAttribute.getJavaMember();
					Annotation[] annotations = method.getAnnotations();
					for (Annotation annotation : annotations) {
						if (annotation instanceof JoinTable) {
							owningSide = true;
						}
					}
				}
				return owningSide ? ColType.MANY_TO_MANY_OWNER : ColType.MANY_TO_MANY_NON_OWNER;
			}
		}
		return null;
	}

}