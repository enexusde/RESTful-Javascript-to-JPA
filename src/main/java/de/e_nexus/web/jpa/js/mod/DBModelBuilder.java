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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.sql.Blob;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.logging.Logger;

import jakarta.persistence.EntityManager;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

public class DBModelBuilder {
	private static final Logger LOG = Logger.getLogger(DBModelBuilder.class.getCanonicalName());
	private EntityManager entityManager;

	public void setEntityManager(final EntityManager myEntityManager) {
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
						PluralAttribute<?, ?, ?> pluralPersistentAttribute = (PluralAttribute<?, ?, ?>) attribute;
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
				if (ct == ColType.ID_COMPOSED) {

					BeanInfo info;
					try {
						info = Introspector.getBeanInfo(attribute.getJavaType());
					} catch (IntrospectionException e) {
						throw new RuntimeException(e);
					}
					for (PropertyDescriptor prop : info.getPropertyDescriptors()) {
						if (prop.getReadMethod().getDeclaringClass() == attribute.getJavaType()) {
							DBModelColumn c = new DBModelColumn(prop.getName(), ct, prop.getPropertyType(), null);
							t.add(c);
						}

					}
				} else {
					DBModelColumn c = new DBModelColumn(attribute.getName(), ct, javaType, oppositePropery);
					t.add(c);
				}
			}
		}
		return sm;
	}

	private ColType calculateType(final Attribute<?, ?> attribute) {
		PersistentAttributeType at = attribute.getPersistentAttributeType();
		if (at == PersistentAttributeType.BASIC) {
			if (attribute instanceof SingularAttribute) {
				SingularAttribute<?, ?> sa = (SingularAttribute<?, ?>) attribute;
				Class<?> jt = sa.getJavaType();
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
					if (jt == Blob.class) {
						return ColType.OPTIONAL_BODY_DATA_BLOB;
					}
					if (Timestamp.class == jt) {
						return ColType.OPTIONAL_TIMESTAMP;
					}
					if (Date.class == jt) {
						return ColType.OPTIONAL_DATE;
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
				if (Timestamp.class == jt) {
					return ColType.REQUIRED_TIMESTAMP;
				}
				if (Date.class == jt) {
					return ColType.REQUIRED_DATE;
				}
				if (byte[].class == jt || Byte[].class == jt) {
					return ColType.REQUIRED_BODY_DATA;
				}
				if (Blob.class == jt) {
					return ColType.REQUIRED_BODY_DATA_BLOB;
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
		} else if (at == PersistentAttributeType.ONE_TO_ONE) {
			if (attribute instanceof SingularAttribute<?, ?>) {
				SingularAttribute<?, ?> singular = (SingularAttribute<?, ?>) attribute;
				boolean owningSide = false;
				if (singular.getJavaMember() instanceof Method) {
					Method method = (Method) singular.getJavaMember();
					Annotation[] annotations = method.getAnnotations();
					for (Annotation annotation : annotations) {
						if (annotation instanceof OneToOne) {
							OneToOne oneToOne = (OneToOne) annotation;
							if (!"".equals(oneToOne.mappedBy())) {
								owningSide = true;
							}
						}
					}
				}
				return owningSide ? ColType.ONE_TO_ONE_OWNING_SIDE : ColType.ONE_TO_ONE_NON_OWNING_SIDE;
			}
		} else if (at == PersistentAttributeType.EMBEDDED) {
			return ColType.ID_COMPOSED;
		}
		LOG.severe("Can not find type: " + at);
		return null;
	}

}