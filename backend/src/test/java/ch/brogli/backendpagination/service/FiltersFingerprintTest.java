package ch.brogli.backendpagination.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.brogli.backendpagination.api.model.Genre;
import ch.brogli.backendpagination.api.model.Language;
import ch.brogli.backendpagination.service.SearchBooksQuery.Filters;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FiltersFingerprintTest {

    private static final Filters EMPTY = new Filters(null, null, null, null, null, null, null);

    @Test
    void isStableAcrossInstances() {
        Filters a =
                new Filters(
                        List.of(Genre.FANTASY),
                        Language.ENGLISH,
                        true,
                        new BigDecimal("3.5"),
                        new BigDecimal("1.00"),
                        null,
                        LocalDate.of(2020, 1, 1));
        Filters b =
                new Filters(
                        List.of(Genre.FANTASY),
                        Language.ENGLISH,
                        true,
                        new BigDecimal("3.5"),
                        new BigDecimal("1.00"),
                        null,
                        LocalDate.of(2020, 1, 1));

        assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
    }

    @Test
    void isUrlSafeAndShort() {
        assertThat(EMPTY.fingerprint()).matches("[A-Za-z0-9_-]{11}");
    }

    @Test
    void genreOrderDoesNotMatter() {
        Filters ab =
                new Filters(
                        List.of(Genre.FANTASY, Genre.SCI_FI), null, null, null, null, null, null);
        Filters ba =
                new Filters(
                        List.of(Genre.SCI_FI, Genre.FANTASY), null, null, null, null, null, null);

        assertThat(ab.fingerprint()).isEqualTo(ba.fingerprint());
    }

    @Test
    void emptyGenreListEqualsNullGenre() {
        Filters empty = new Filters(List.of(), null, null, null, null, null, null);

        assertThat(empty.fingerprint()).isEqualTo(EMPTY.fingerprint());
    }

    @Test
    void duplicateGenresDoNotChangeTheFingerprint() {
        Filters duplicated =
                new Filters(
                        List.of(Genre.FANTASY, Genre.FANTASY), null, null, null, null, null, null);
        Filters single = new Filters(List.of(Genre.FANTASY), null, null, null, null, null, null);

        assertThat(duplicated.fingerprint()).isEqualTo(single.fingerprint());
    }

    @Test
    void fingerprintCoversEveryRecordComponent() {
        // When this fails you added a filter field: extend the canonical string in
        // fingerprint() and bump Cursor.CURRENT_VERSION.
        assertThat(Filters.class.getRecordComponents()).hasSize(7);
    }

    @Test
    void anyFilterChangeChangesTheFingerprint() {
        Filters language = new Filters(null, Language.GERMAN, null, null, null, null, null);
        Filters inStock = new Filters(null, null, false, null, null, null, null);
        Filters minRating = new Filters(null, null, null, new BigDecimal("4"), null, null, null);
        Filters priceMin = new Filters(null, null, null, null, new BigDecimal("4"), null, null);
        Filters priceMax = new Filters(null, null, null, null, null, new BigDecimal("4"), null);
        Filters published =
                new Filters(null, null, null, null, null, null, LocalDate.of(2020, 1, 1));

        assertThat(
                        List.of(
                                EMPTY.fingerprint(),
                                language.fingerprint(),
                                inStock.fingerprint(),
                                minRating.fingerprint(),
                                priceMin.fingerprint(),
                                priceMax.fingerprint(),
                                published.fingerprint()))
                .doesNotHaveDuplicates();
    }
}
