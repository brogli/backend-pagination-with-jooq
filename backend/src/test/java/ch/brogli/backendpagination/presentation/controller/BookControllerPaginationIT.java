package ch.brogli.backendpagination.presentation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Objects;
import org.hamcrest.Matchers;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookControllerPaginationIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired DSLContext dsl;

    @BeforeEach
    void truncateBooks() {
        dsl.execute("TRUNCATE TABLE book RESTART IDENTITY");
    }

    @Test
    void exactPageMultiple_lastPageOmitsNextCursor_noTrailingEmptyPage() throws Exception {
        // 100 rows / size 10 → exactly 10 pages. Page 10 must omit nextCursor even though the
        // page is full — peek (limit + 1) makes end-of-data detectable without an extra round
        // trip. prevCursor is still set since we walked here.
        seedRowsTitleOrderedByIndex(100);

        String cursor = walkToLastPage(10);

        fetchPage("asc", 10, cursor)
                .andExpect(jsonPath("$.content", Matchers.hasSize(10)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.prevCursor").isString());
    }

    @Test
    void firstPageOmitsPrevCursor() throws Exception {
        seedRowsTitleOrderedByIndex(30);

        fetchPage("asc", 10, null)
                .andExpect(jsonPath("$.content", Matchers.hasSize(10)))
                .andExpect(jsonPath("$.prevCursor").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isString());
    }

    @Test
    void cursorReusedWithDifferentSort_returns400() throws Exception {
        seedRowsTitleOrderedByIndex(30);

        String cursor = readNextCursor(body(fetchPage("asc", 10, null)));

        mockMvc.perform(
                        get("/api/books")
                                .param("sort", "author")
                                .param("dir", "asc")
                                .param("size", "10")
                                .param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail", Matchers.containsString("different sort/direction")));
    }

    @Test
    void cursorReusedWithDifferentFilters_returns400() throws Exception {
        seedRowsTitleOrderedByIndex(30);

        String cursor = readNextCursor(body(fetchPage("asc", 10, null)));

        mockMvc.perform(
                        get("/api/books")
                                .param("sort", "title")
                                .param("dir", "asc")
                                .param("size", "10")
                                .param("inStock", "true")
                                .param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", Matchers.containsString("different filter set")));
    }

    @Test
    void cursorReusedWithSameFiltersInDifferentGenreOrder_isAccepted() throws Exception {
        seedRowsTitleOrderedByIndex(30);

        String firstPage = body(fetchPage("asc", 10, null, "Fantasy", "SciFi"));

        fetchPage("asc", 10, readNextCursor(firstPage), "SciFi", "Fantasy")
                .andExpect(jsonPath("$.content[0].title").value("T011"));
    }

    @Test
    void prevWithGenreFilter_reversesCorrectly() throws Exception {
        // Same shape as nextThenPrev_returnsFirstPageInForwardOrder_withoutPrevCursor, but with a
        // genre filter applied throughout: the filter fingerprint and the reverse seek both ride
        // along on the same cursor, so PREV must still land back on page 1 correctly.
        seedRowsTitleOrderedByIndex(30);

        String page1 = body(fetchPage("asc", 10, null, "Fantasy"));
        String page2 = body(fetchPage("asc", 10, readNextCursor(page1), "Fantasy"));

        fetchPage("asc", 10, readPrevCursor(page2), "Fantasy")
                .andExpect(jsonPath("$.content", Matchers.hasSize(10)))
                .andExpect(jsonPath("$.content[0].title").value("T001"))
                .andExpect(jsonPath("$.content[9].title").value("T010"))
                .andExpect(jsonPath("$.prevCursor").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isString());
    }

    @Test
    void nextThenPrev_returnsFirstPageInForwardOrder_withoutPrevCursor() throws Exception {
        // Page 1 is T001..T010. Walking NEXT then PREV must land on exactly page 1 again, rows
        // in forward order (the PREV seek runs reversed and is re-reversed before returning),
        // and prevCursor must be null because the extra lookahead row does not exist.
        seedRowsTitleOrderedByIndex(30);

        String page1 = body(fetchPage("asc", 10, null));
        String page2 = body(fetchPage("asc", 10, readNextCursor(page1)));

        fetchPage("asc", 10, readPrevCursor(page2))
                .andExpect(jsonPath("$.content", Matchers.hasSize(10)))
                .andExpect(jsonPath("$.content[0].title").value("T001"))
                .andExpect(jsonPath("$.content[9].title").value("T010"))
                .andExpect(jsonPath("$.prevCursor").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isString());
    }

    @Test
    void prevFromMiddlePage_keepsBothCursors_andNextLeadsBackToSamePage() throws Exception {
        // Page 3 is T021..T030. PREV from page 3 gives page 2 (T011..T020) with both cursors
        // set. Following that page's nextCursor must return page 3 again.
        seedRowsTitleOrderedByIndex(30);

        String page1 = body(fetchPage("asc", 10, null));
        String page2 = body(fetchPage("asc", 10, readNextCursor(page1)));
        String page3 = body(fetchPage("asc", 10, readNextCursor(page2)));

        String backToPage2 =
                body(
                        fetchPage("asc", 10, readPrevCursor(page3))
                                .andExpect(jsonPath("$.content[0].title").value("T011"))
                                .andExpect(jsonPath("$.content[9].title").value("T020"))
                                .andExpect(jsonPath("$.prevCursor").isString())
                                .andExpect(jsonPath("$.nextCursor").isString()));

        fetchPage("asc", 10, readNextCursor(backToPage2))
                .andExpect(jsonPath("$.content[0].title").value("T021"))
                .andExpect(jsonPath("$.content[9].title").value("T030"));
    }

    @Test
    void prevWithDescDirection_reversesCorrectly() throws Exception {
        // dir=desc: page 1 is T030..T021, page 2 is T020..T011. PREV from page 2 must give
        // T030..T021 again (still descending).
        seedRowsTitleOrderedByIndex(30);

        String page1 = body(fetchPage("desc", 10, null));
        String page2 = body(fetchPage("desc", 10, readNextCursor(page1)));

        fetchPage("desc", 10, readPrevCursor(page2))
                .andExpect(jsonPath("$.content", Matchers.hasSize(10)))
                .andExpect(jsonPath("$.content[0].title").value("T030"))
                .andExpect(jsonPath("$.content[9].title").value("T021"))
                .andExpect(jsonPath("$.prevCursor").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isString());
    }

    /**
     * Walks pages [1..n-1] to obtain the cursor that, when passed to the next request, returns page
     * n. Returns the cursor string for fetching the final page.
     */
    private String walkToLastPage(int pageSize) throws Exception {
        String cursor = null;
        // Walk to page 9; cursor returned points at page 10.
        for (int i = 1; i <= 9; i++) {
            cursor = readNextCursor(body(fetchPage("asc", pageSize, cursor)));
        }
        return cursor;
    }

    private static String readNextCursor(String body) {
        return Objects.requireNonNull(
                JsonPath.read(body, "$.nextCursor"), "nextCursor missing in " + body);
    }

    private static String readPrevCursor(String body) {
        return Objects.requireNonNull(
                JsonPath.read(body, "$.prevCursor"), "prevCursor missing in " + body);
    }

    private ResultActions fetchPage(String dir, int size, @Nullable String cursor, String... genres)
            throws Exception {
        var req =
                get("/api/books")
                        .param("sort", "title")
                        .param("dir", dir)
                        .param("size", Integer.toString(size));
        if (cursor != null) {
            req = req.param("cursor", cursor);
        }
        if (genres.length > 0) {
            req = req.param("genre", genres);
        }
        return mockMvc.perform(req).andExpect(status().isOk());
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private void seedRowsTitleOrderedByIndex(int n) {
        for (int i = 1; i <= n; i++) {
            String title = String.format("T%03d", i);
            dsl.execute(
                    "INSERT INTO book"
                            + " (title, author, genre, language, in_stock, rating, price, published_at)"
                            + " VALUES (?, 'A', 'Fantasy', 'English', true, 3.0, 9.99, DATE"
                            + " '2020-01-01')",
                    title);
        }
    }
}
