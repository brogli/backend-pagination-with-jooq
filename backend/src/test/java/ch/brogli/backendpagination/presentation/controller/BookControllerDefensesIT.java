package ch.brogli.backendpagination.presentation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookControllerDefensesIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;

    @Test
    void searchBooks_priceMinGreaterThanPriceMax_returns400ProblemDetail() throws Exception {
        mockMvc.perform(
                        get("/api/books")
                                .param("sort", "price")
                                .param("dir", "asc")
                                .param("size", "25")
                                .param("priceMin", "50")
                                .param("priceMax", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", Matchers.containsString("priceMin")))
                .andExpect(jsonPath("$.detail", Matchers.containsString("priceMax")));
    }

    @ParameterizedTest
    @CsvSource({"title, 500, size", "title, abc, size", "isbn, 25, sort"})
    void invalidRequestParameter_returns400ProblemDetail(
            String sort, String size, String expectedDetail) throws Exception {
        mockMvc.perform(
                        get("/api/books")
                                .param("sort", sort)
                                .param("dir", "asc")
                                .param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", Matchers.containsString(expectedDetail)));
    }

    @Test
    void malformedCursor_returns400ProblemDetailWithDetail() throws Exception {
        mockMvc.perform(
                        get("/api/books")
                                .param("sort", "title")
                                .param("dir", "asc")
                                .param("size", "25")
                                .param("cursor", "!!!"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", Matchers.containsString("malformed")));
    }
}
