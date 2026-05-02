package ch.brogli.backendpagination.indexaudit;

import static ch.brogli.backendpagination.indexaudit.IndexUsageIT.Verdict.ACCEPTABLE;
import static ch.brogli.backendpagination.indexaudit.IndexUsageIT.Verdict.FAST;
import static ch.brogli.backendpagination.jooq.Tables.BOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.noCondition;

import ch.brogli.backendpagination.BookPaginationApplication;
import ch.brogli.backendpagination.api.model.Direction;
import ch.brogli.backendpagination.api.model.Genre;
import ch.brogli.backendpagination.api.model.Language;
import ch.brogli.backendpagination.api.model.SortField;
import ch.brogli.backendpagination.indexaudit.PlanProbe.PlanReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Asserts every supported (sort × filter) combo runs as an Index Scan / Index Only Scan, never a
 * Seq Scan. Probe queries inline params via {@code ParamType.INLINED}; production queries use bind
 * params, so a regression there can hide from this suite — investigate the bind-param plan first if
 * a "FAST" combo seq-scans in prod.
 */
@SpringBootTest(classes = BookPaginationApplication.class)
@ActiveProfiles("index-audit")
@Testcontainers
class IndexUsageIT {

    @Container @ServiceConnection
    static final SeededPostgresContainer POSTGRES = SeededPostgresContainer.INSTANCE;

    @Autowired DSLContext dsl;

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("supportedCombos")
    @DisplayName("forward seek hits an index scan, not a seq scan")
    void forwardSeekHitsIndexScan(Combo combo) {
        PlanProbe probe = new PlanProbe(dsl);
        PlanReport plan =
                probe.explainForwardSeek(
                        combo.sort,
                        combo.dir,
                        25,
                        sampleCursorValue(combo.sort),
                        sampleCursorId(),
                        combo.where);

        assertVerdict(plan, combo);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("supportedCombos")
    @DisplayName("reverse-seed (prev) hits an index scan, not a seq scan")
    void reverseSeedHitsIndexScan(Combo combo) {
        PlanProbe probe = new PlanProbe(dsl);
        PlanReport plan =
                probe.explainReverseSeed(
                        combo.sort,
                        combo.dir,
                        25,
                        sampleCursorValue(combo.sort),
                        sampleCursorId(),
                        combo.where);

        assertVerdict(plan, combo);
    }

    @Test
    @DisplayName("partial index on (rating, id) WHERE in_stock=true does not displace composite")
    void partialInStockRatingIndexDoesNotDisplaceComposite() {
        PlanProbe probe = new PlanProbe(dsl);
        String partialIdx = "book_rating_id_in_stock_partial_idx_audit";

        PlanReport before =
                probe.explainForwardSeek(
                        SortField.RATING, Direction.DESC, 25, null, null, BOOK.IN_STOCK.eq(true));

        try {
            dsl.execute(
                    "CREATE INDEX " + partialIdx + " ON book (rating, id) WHERE in_stock = true");
            dsl.execute("ANALYZE book");

            PlanReport after =
                    probe.explainForwardSeek(
                            SortField.RATING,
                            Direction.DESC,
                            25,
                            null,
                            null,
                            BOOK.IN_STOCK.eq(true));

            assertThat(before.hasSeqScan()).isFalse();
            assertThat(after.hasSeqScan()).isFalse();
            assertThat(after.indexNames())
                    .as("planner stays with the existing composite — partial offers no win")
                    .doesNotContain(partialIdx);
        } finally {
            dsl.execute("DROP INDEX IF EXISTS " + partialIdx);
            dsl.execute("ANALYZE book");
        }
    }

    private static void assertVerdict(PlanReport plan, Combo c) {
        switch (c.verdict) {
            case FAST ->
                    assertThat(plan.hasIndexScan())
                            .as(
                                    "expected index scan; plan=%s; sql=%s",
                                    plan.nodeTypes(), plan.sql())
                            .isTrue();
            case ACCEPTABLE ->
                    assertThat(plan.hasSeqScan())
                            .as(
                                    "expected planner to avoid seq scan; plan=%s; sql=%s",
                                    plan.nodeTypes(), plan.sql())
                            .isFalse();
        }
    }

    private static Stream<Combo> supportedCombos() {
        return Stream.of(
                fast("title asc, no filter", SortField.TITLE, Direction.ASC, noCondition()),
                fast("author desc, no filter", SortField.AUTHOR, Direction.DESC, noCondition()),
                fast("price asc, no filter", SortField.PRICE, Direction.ASC, noCondition()),
                fast("rating desc, no filter", SortField.RATING, Direction.DESC, noCondition()),
                fast(
                        "publishedAt asc, no filter",
                        SortField.PUBLISHED_AT,
                        Direction.ASC,
                        noCondition()),
                fast(
                        "price asc, genre=Fantasy",
                        SortField.PRICE,
                        Direction.ASC,
                        BOOK.GENRE.eq(Genre.FANTASY.getValue())),
                fast(
                        "price desc, genre IN (Fantasy, SciFi)",
                        SortField.PRICE,
                        Direction.DESC,
                        BOOK.GENRE.in(Genre.FANTASY.getValue(), Genre.SCI_FI.getValue())),
                fast(
                        "rating desc, inStock=true",
                        SortField.RATING,
                        Direction.DESC,
                        BOOK.IN_STOCK.eq(true)),
                fast(
                        "rating asc, inStock=false",
                        SortField.RATING,
                        Direction.ASC,
                        BOOK.IN_STOCK.eq(false)),
                fast(
                        "publishedAt desc, language=English",
                        SortField.PUBLISHED_AT,
                        Direction.DESC,
                        BOOK.LANGUAGE.eq(Language.ENGLISH.getValue())),
                acceptable(
                        "price asc + minRating>=4.5",
                        SortField.PRICE,
                        Direction.ASC,
                        BOOK.RATING.ge(new BigDecimal("4.5"))),
                acceptable(
                        "publishedAt desc + priceMin>=180",
                        SortField.PUBLISHED_AT,
                        Direction.DESC,
                        BOOK.PRICE.ge(new BigDecimal("180"))),
                acceptable(
                        "title asc + publishedAfter=2025-01-01",
                        SortField.TITLE,
                        Direction.ASC,
                        BOOK.PUBLISHED_AT.ge(LocalDate.of(2025, 1, 1))),
                fast(
                        "price asc, genre=Mystery + inStock=true",
                        SortField.PRICE,
                        Direction.ASC,
                        BOOK.GENRE.eq(Genre.MYSTERY.getValue()).and(BOOK.IN_STOCK.eq(true))),
                acceptable(
                        "title asc + language=French (no aligned composite)",
                        SortField.TITLE,
                        Direction.ASC,
                        BOOK.LANGUAGE.eq(Language.FRENCH.getValue())));
    }

    private static Combo fast(String name, SortField s, Direction d, Condition w) {
        return new Combo(name, s, d, w, FAST);
    }

    private static Combo acceptable(String name, SortField s, Direction d, Condition w) {
        return new Combo(name, s, d, w, ACCEPTABLE);
    }

    private static Object sampleCursorValue(SortField sort) {
        return switch (sort) {
            case TITLE -> "M";
            case AUTHOR -> "M";
            case PRICE -> new BigDecimal("50.00");
            case RATING -> new BigDecimal("2.5");
            case PUBLISHED_AT -> LocalDate.of(2010, 1, 1);
        };
    }

    private static long sampleCursorId() {
        return 500_000L;
    }

    enum Verdict {
        FAST,
        ACCEPTABLE
    }

    record Combo(String name, SortField sort, Direction dir, Condition where, Verdict verdict) {
        @Override
        public String toString() {
            return verdict + " — " + name;
        }
    }
}
