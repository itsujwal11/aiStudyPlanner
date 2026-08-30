package com.aasa.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.logging.Logger;

/**
 * Executes pgvector SQL inside its own transaction so a database that lacks the
 * extension cannot break the caller.
 *
 * <p><b>Why this exists.</b> {@code VectorSearchService} caught its own SQL
 * failures and returned an empty list, which looks fail-soft but is not: the
 * failing statement runs inside the caller's read-only transaction, so
 * PostgreSQL marks that transaction rollback-only. The catch swallows the
 * exception, the caller happily builds a response, and then the commit throws
 * {@code UnexpectedRollbackException: Transaction silently rolled back because
 * it has been marked as rollback-only}. A missing extension therefore surfaced
 * as an HTTP 500 from {@code /api/rag/ask} instead of a degraded answer.</p>
 *
 * <p>{@link Propagation#REQUIRES_NEW} suspends the caller's transaction and runs
 * the statement in a fresh one, so a failure rolls back only the inner
 * transaction and the caller survives to use the fallback path.</p>
 *
 * <p>This is a separate bean on purpose. Spring's {@code @Transactional} is
 * proxy-based, so a {@code REQUIRES_NEW} method called from inside the same
 * class would bypass the proxy and silently join the outer transaction again.</p>
 */
@Service
public class PgVectorSupport {

    private static final Logger logger = Logger.getLogger(PgVectorSupport.class.getName());

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Whether the connected database knows the {@code vector} type.
     *
     * <p>Probes {@code pg_type} rather than {@code pg_extension} because the
     * type is what {@code CAST(... AS vector)} actually needs. The query itself
     * is valid on any PostgreSQL, so a {@code false} here means "extension not
     * installed" rather than "query broken". Non-PostgreSQL databases (H2 in
     * tests) throw, which is also reported as unsupported.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isVectorTypeAvailable() {
        try {
            List<?> rows = entityManager
                    .createNativeQuery("SELECT 1 FROM pg_type WHERE typname = 'vector'")
                    .getResultList();
            return !rows.isEmpty();
        } catch (Exception e) {
            logger.info("Could not probe for the pgvector type (" + e.getMessage()
                    + ") - treating vector search as unavailable");
            return false;
        }
    }

    /**
     * Runs a pgvector similarity query.
     *
     * @return the raw rows, or {@code null} if the query failed. {@code null}
     *         rather than an empty list so the caller can tell "no matches"
     *         apart from "could not search" and pick the fallback accordingly.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Object[]> query(String sql, Long scopeValue, String vectorLiteral, int topK) {
        try {
            var query = entityManager.createNativeQuery(sql, Object[].class);
            query.setParameter(1, scopeValue);
            query.setParameter(2, vectorLiteral);
            query.setParameter(3, topK);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            return rows;
        } catch (Exception e) {
            logger.warning("pgvector query failed: " + e.getMessage());
            return null;
        }
    }
}
