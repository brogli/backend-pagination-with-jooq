package ch.brogli.backendpagination.indexaudit;

import static ch.brogli.backendpagination.jooq.Tables.BOOK;
import static org.jooq.conf.ParamType.INLINED;

import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.jooq.tables.records.BookRecord;
import java.util.HashSet;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectSeekStepN;
import org.jooq.TableField;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs EXPLAIN (FORMAT JSON) against the same query shape as {@code BookRepository.fetchPage}. Keep
 * in sync when that method changes.
 */
final class PlanProbe {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DSLContext dsl;

    PlanProbe(DSLContext dsl) {
        this.dsl = dsl;
    }

    PlanReport explainForwardSeek(
            SortField sort,
            Direction dir,
            int size,
            @Nullable Object cursorValue,
            @Nullable Long cursorId,
            Condition where) {
        TableField<BookRecord, ?> sortCol = fieldFor(sort);
        boolean asc = dir == Direction.ASC;

        SelectSeekStepN<BookRecord> step =
                dsl.selectFrom(BOOK)
                        .where(where)
                        .orderBy(
                                asc ? sortCol.asc() : sortCol.desc(),
                                asc ? BOOK.ID.asc() : BOOK.ID.desc());

        var query =
                (cursorValue == null || cursorId == null)
                        ? step.limit(size)
                        : step.seek(cursorValue, cursorId).limit(size);

        return runExplain(query.getSQL(INLINED));
    }

    PlanReport explainReverseSeed(
            SortField sort,
            Direction dir,
            int size,
            Object cursorValue,
            long cursorId,
            Condition where) {
        TableField<BookRecord, ?> sortCol = fieldFor(sort);
        boolean asc = dir == Direction.ASC;

        var query =
                dsl.select(sortCol, BOOK.ID)
                        .from(BOOK)
                        .where(where)
                        .orderBy(
                                asc ? sortCol.desc() : sortCol.asc(),
                                asc ? BOOK.ID.desc() : BOOK.ID.asc())
                        .seek(cursorValue, cursorId)
                        .limit(size + 1);

        return runExplain(query.getSQL(INLINED));
    }

    private PlanReport runExplain(String sql) {
        String explainSql = "EXPLAIN (FORMAT JSON) " + sql;
        Record row = dsl.fetchOne(explainSql);
        if (row == null) {
            throw new IllegalStateException("EXPLAIN returned no rows for: " + sql);
        }
        // Postgres returns the JSON plan as a single text column.
        String json = String.valueOf(row.get(0));
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not parse EXPLAIN JSON: " + json, e);
        }
        // root is an array of one plan-wrapper object; the wrapper has a "Plan" key.
        JsonNode plan = root.get(0).get("Plan");
        Set<String> nodeTypes = new HashSet<>();
        Set<String> indexNames = new HashSet<>();
        collect(plan, nodeTypes, indexNames);
        return new PlanReport(sql, json, nodeTypes, indexNames);
    }

    private static void collect(JsonNode node, Set<String> nodeTypes, Set<String> indexNames) {
        JsonNode typeNode = node.get("Node Type");
        if (typeNode != null) {
            nodeTypes.add(typeNode.asString());
        }
        JsonNode indexNode = node.get("Index Name");
        if (indexNode != null) {
            indexNames.add(indexNode.asString());
        }
        JsonNode children = node.get("Plans");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                collect(child, nodeTypes, indexNames);
            }
        }
    }

    private static TableField<BookRecord, ?> fieldFor(SortField sort) {
        return switch (sort) {
            case TITLE -> BOOK.TITLE;
            case AUTHOR -> BOOK.AUTHOR;
            case PRICE -> BOOK.PRICE;
            case RATING -> BOOK.RATING;
            case PUBLISHED_AT -> BOOK.PUBLISHED_AT;
        };
    }

    record PlanReport(String sql, String rawJson, Set<String> nodeTypes, Set<String> indexNames) {

        boolean hasSeqScan() {
            return nodeTypes.contains("Seq Scan");
        }

        boolean hasIndexScan() {
            return nodeTypes.contains("Index Scan")
                    || nodeTypes.contains("Index Only Scan")
                    || nodeTypes.contains("Bitmap Index Scan");
        }
    }
}
