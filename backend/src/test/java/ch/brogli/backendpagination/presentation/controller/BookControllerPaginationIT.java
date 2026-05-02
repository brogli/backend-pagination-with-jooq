package ch.brogli.backendpagination.presentation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
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

        mockMvc.perform(
                        get("/api/books")
                                .param("sort", "title")
                                .param("dir", "asc")
                                .param("size", "10")
                                .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", Matchers.hasSize(10)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.prevCursor").isString());
    }

    @Test
    void firstPageOmitsPrevCursor() throws Exception {
        seedRowsTitleOrderedByIndex(30);

        mockMvc.perform(
                        get("/api/books")
                                .param("sort", "title")
                                .param("dir", "asc")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", Matchers.hasSize(10)))
                .andExpect(jsonPath("$.prevCursor").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isString());
    }

    @Test
    void cursorReusedWithDifferentSort_returns400() throws Exception {
        seedRowsTitleOrderedByIndex(30);

        String cursor = readNextCursor(fetchFirstPageBody(10));

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

    private String fetchFirstPageBody(int pageSize) throws Exception {
        return mockMvc.perform(
                        get("/api/books")
                                .param("sort", "title")
                                .param("dir", "asc")
                                .param("size", Integer.toString(pageSize)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /**
     * Walks pages [1..n-1] to obtain the cursor that, when passed to the next request, returns page
     * n. Returns the cursor string for fetching the final page.
     */
    private String walkToLastPage(int pageSize) throws Exception {
        String cursor = null;
        // Walk to page 9; cursor returned points at page 10.
        for (int i = 1; i <= 9; i++) {
            var req =
                    get("/api/books")
                            .param("sort", "title")
                            .param("dir", "asc")
                            .param("size", Integer.toString(pageSize));
            if (cursor != null) {
                req = req.param("cursor", cursor);
            }
            String body =
                    mockMvc.perform(req)
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            cursor = readNextCursor(body);
        }
        return cursor;
    }

    private static String readNextCursor(String body) {
        return JsonPath.read(body, "$.nextCursor");
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
