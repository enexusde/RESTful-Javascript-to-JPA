package de.e_nexus.web.jpa.js;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.transaction.Transactional;

import de.e_nexus.web.jpa.js.mod.ColType;
import de.e_nexus.web.jpa.js.mod.DBModelColumn;
import de.e_nexus.web.jpa.js.mod.DBModelHolder;
import de.e_nexus.web.jpa.js.mod.DBModelTable;

@Named
@Transactional
public class IndexFacade {

	@PersistenceContext
	private final EntityManager entityManager = null;

	@Inject
	private final DBModelHolder model = null;

	/**
	 * Get the index-value instead of the id.
	 * 
	 * @param o The id value of the instance, never <code>null</code>.
	 * @param c The abstraction of the id column, never <code>null</code>.
	 * @param t The table of the entity, never <code>null</code>.
	 * @return The index of the entity, never <code>null</code>.
	 */
	public Number getIndexById(Object o, DBModelColumn c, DBModelTable t) {
		Query query = entityManager
				.createQuery("SELECT COUNT(*) FROM " + t.getName() + " e WHERE e." + c.getName() + " < :o ")
				.setParameter("o", o);
		return (Number) query.getSingleResult();
	}

	public Object findId(int index, DBModelTable table) {
		Set<DBModelColumn> idCol = model.getIdColumns(table);
		return findId(index, table, idCol);
	}

	public Object findId(int index, DBModelTable table, Set<DBModelColumn> id) {
		Object entityId = findId(table.getEntityClass(), id, index);
		return entityId;
	}

	public Object findId(Class<?> entity, Set<DBModelColumn> idColumn, int index) {
		try {
			String querySelect = "SELECT ";
			String queryOrder = " ";
			boolean first = true;
			for (DBModelColumn dbModelColumn : idColumn) {
				if (!first) {
					querySelect += ", ";
					queryOrder += ", ";
				}
				first = false;
				if (dbModelColumn.getColtype() == ColType.ID_COMPOSED) {
					querySelect += "e.id." + dbModelColumn.getName() + ".id";
					queryOrder += "e.id." + dbModelColumn.getName() + ".id";
				} else {
					querySelect += "e." + dbModelColumn.getName() + ".id";
					queryOrder += "e." + dbModelColumn.getName() + ".id";
				}

			}
			String queryString = querySelect + " FROM " + entity.getCanonicalName() + " e ORDER BY " + queryOrder
					+ " ASC";
			System.out.println(queryString);
			Query query = entityManager.createQuery(queryString);
			return query.getResultList().get(index);
		} catch (RuntimeException e) {
			throw new RuntimeException(entity.getSimpleName() + " can not be found by #" + index
					+ " (this is the index-number, not the primary-key)!", e);
		}
	}
}
