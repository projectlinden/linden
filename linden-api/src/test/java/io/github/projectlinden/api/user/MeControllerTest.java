package io.github.projectlinden.api.user;

import io.github.projectlinden.api.security.SecurityConfig;
import io.github.projectlinden.core.user.AppUser;
import io.github.projectlinden.core.user.AppUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeController.class) // only this controller – other controllers' dependencies stay out
@Import(SecurityConfig.class)
class MeControllerTest {

    private static final String ME_URL = "/api/v1/me";
    private static final UUID USER_ID = UUID.fromString("5e3f2c9a-1b7d-4c8e-9f60-2a4b6c8d0e1f");

    /**
     * Appears in every fake database error below – it must never reach the client.
     */
    private static final String INTERNAL = "db-internal:5432";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    AppUserService appUserService;

    @Test
    @DisplayName("no token → 401")
    void me_withoutToken_returns401() throws Exception {
        // when + then
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("no token → no session is created (stateless API)")
    void me_withoutToken_createsNoSession() throws Exception {
        // when + then
        mockMvc.perform(get(ME_URL))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    @DisplayName("valid token → the user is fetched or created through the service, 200 with its id")
    void me_withValidToken_getsOrCreatesTheUser() throws Exception {
        // given
        AppUser user = mock(AppUser.class); // AppUser has no public constructor – rows come only from the database
        given(user.getId()).willReturn(USER_ID);
        given(appUserService.getOrCreate(USER_ID)).willReturn(user);

        // when + then
        mockMvc.perform(get(ME_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
        verify(appUserService).getOrCreate(USER_ID);
    }

    /**
     * What "the database can't be reached" looks like by the time it reaches the API.
     */
    static Stream<RuntimeException> databaseUnavailable() {
        return Stream.of(
                // A stopped Postgres: the @Transactional service can't even start its transaction
                new CannotCreateTransactionException("Could not open JPA EntityManager for transaction",
                        new IllegalStateException("Connection to " + INTERNAL + " refused")),
                new DataAccessResourceFailureException("Connection to " + INTERNAL + " was lost"),
                new QueryTimeoutException("Query on " + INTERNAL + " timed out"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("databaseUnavailable")
    @DisplayName("database unreachable → 503 problem details with code service_unavailable, without internals")
    void me_whenDatabaseUnavailable_returns503(RuntimeException databaseFailure) throws Exception {
        // given
        given(appUserService.getOrCreate(USER_ID)).willThrow(databaseFailure);

        // when
        MockHttpServletResponse response = mockMvc.perform(get(ME_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                // then
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("service_unavailable"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andReturn().getResponse();

        assertThat(response.getContentAsString()).as("response body").doesNotContain(INTERNAL);
    }

    @Test
    @DisplayName("other database error (a bug, not an outage) → still 500 internal_error, without internals")
    void me_whenDatabaseRejectsData_returns500() throws Exception {
        // given
        given(appUserService.getOrCreate(USER_ID)).willThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"app_user_pkey\" on " + INTERNAL));

        // when
        MockHttpServletResponse response = mockMvc.perform(get(ME_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                // then
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andReturn().getResponse();

        assertThat(response.getContentAsString()).as("response body").doesNotContain(INTERNAL);
    }
}
