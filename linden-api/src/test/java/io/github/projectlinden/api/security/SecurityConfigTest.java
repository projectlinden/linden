package io.github.projectlinden.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The security door: a token is only accepted if its {@code sub} is a strict UUID, because that is our user id.
 *
 * <p>These tests send a real {@code Authorization} header, so the whole Spring Security chain runs. Only the
 * signature check is replaced: the mocked {@link JwtDecoder} returns the token we want to test.
 */
@WebMvcTest(controllers = SecurityConfigTest.SubjectController.class)
@Import({SecurityConfig.class, SecurityConfigTest.SubjectController.class})
class SecurityConfigTest {

    private static final String SUBJECT_URL = "/test/subject";
    private static final String TOKEN = "test-token";

    /**
     * A test-only endpoint that echoes the token's sub.
     */
    @RestController
    static class SubjectController {

        @GetMapping(SUBJECT_URL)
        String subject(@AuthenticationPrincipal Jwt token) {
            return token.getSubject();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    @DisplayName("sub is a UUID → 200 (control: proves this test's token path works, so a 401 below means a rejection)")
    void uuidSubject_isAccepted() throws Exception {
        // given
        String subject = "5e3f2c9a-1b7d-4c8e-9f60-2a4b6c8d0e1f";
        given(jwtDecoder.decode(TOKEN)).willReturn(tokenWithSubject(subject));

        // when + then
        mockMvc.perform(get(SUBJECT_URL).header(AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(subject));
    }

    @ParameterizedTest(name = "sub \"{0}\"")
    @ValueSource(strings = {
            "anna",                                   // a username
            "f:ldap-provider:anna",                   // Keycloak's format for users from a federated directory
            "1-2-3-4-5",                              // UUID.fromString accepts this – it would become 00000001-0002-…
            "5E3F2C9A-1B7D-4C8E-9F60-2A4B6C8D0E1F"    // uppercase: the same UUID as another sub string
    })
    @DisplayName("sub is not a strict lowercase UUID → 401 invalid_token")
    void nonUuidSubject_isRejected(String subject) throws Exception {
        // given
        given(jwtDecoder.decode(TOKEN)).willReturn(tokenWithSubject(subject));

        // when + then
        mockMvc.perform(get(SUBJECT_URL).header(AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    @Test
    @DisplayName("token without sub → 401 invalid_token")
    void missingSubject_isRejected() throws Exception {
        // given – e.g. the realm lost its "basic" client scope
        Jwt withoutSubject = Jwt.withTokenValue(TOKEN).header("alg", "none").claim("scope", "openid").build();
        given(jwtDecoder.decode(TOKEN)).willReturn(withoutSubject);

        // when + then
        mockMvc.perform(get(SUBJECT_URL).header(AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    private static Jwt tokenWithSubject(String subject) {
        return Jwt.withTokenValue(TOKEN).header("alg", "none").subject(subject).build();
    }
}
