package io.github.projectlinden.api.circle;

import io.github.projectlinden.api.security.SecurityConfig;
import io.github.projectlinden.core.circle.Circle;
import io.github.projectlinden.core.circle.CircleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Base64;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CircleController.class)
@Import(SecurityConfig.class)
class CircleControllerTest {

    private static final String CIRCLES_URL = "/api/v1/circles";
    private static final UUID USER_ID = UUID.fromString("8b2d4f60-1a3c-4e5f-9b7d-2c4e6f8a0b1d");
    private static final UUID CIRCLE_ID = UUID.fromString("3c1e7a52-8f4d-4b6a-9e21-5d7f0a8c4b13");
    private static final byte[] PAYLOAD = {0, 70, 97, 109};
    private static final String PAYLOAD_BASE64 = "AEZhbQ==";   // the same 4 bytes as Base64 text

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CircleService circleService;

    @Test
    @DisplayName("create a circle → 201, Location points to it, the body holds its id and payload")
    void create_withValidPayload_returns201WithLocationAndBody() throws Exception {
        // given
        // noinspection DataFlowIssue
        given(circleService.createCircle(eq(USER_ID), aryEq(PAYLOAD))).willReturn(new Circle(CIRCLE_ID, PAYLOAD));

        // when + then
        mockMvc.perform(createRequest("{\"payload\": \"" + PAYLOAD_BASE64 + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(LOCATION, endsWith(CIRCLES_URL + "/" + CIRCLE_ID)))
                .andExpect(jsonPath("$.id").value(CIRCLE_ID.toString()))
                .andExpect(jsonPath("$.payload").value(PAYLOAD_BASE64));
    }

    @Test
    @DisplayName("empty payload → 400 bad_request naming the field; the service is never called")
    void create_withEmptyPayload_returns400NamingTheField() throws Exception {
        // when + then
        mockMvc.perform(createRequest("{\"payload\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.errors[0].field").value("payload"));
        verify(circleService, never()).createCircle(any(), any());
    }

    @Test
    @DisplayName("payload one byte over the limit → 400; the service is never called")
    void create_withPayloadOverTheLimit_returns400() throws Exception {
        // given
        String tooLarge = Base64.getEncoder().encodeToString(new byte[Circle.MAX_PAYLOAD_BYTES + 1]);

        // when + then
        mockMvc.perform(createRequest("{\"payload\": \"" + tooLarge + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("payload"));
        verify(circleService, never()).createCircle(any(), any());
    }

    @Test
    @DisplayName("no payload field → 400 naming the field")
    void create_withoutPayload_returns400() throws Exception {
        // when + then
        mockMvc.perform(createRequest("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("payload"));
    }

    @Test
    @DisplayName("payload that is not Base64 → 400 bad_request, not a server error")
    void create_withInvalidBase64_returns400() throws Exception {
        // when + then
        mockMvc.perform(createRequest("{\"payload\": \"this is not base64!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    @DisplayName("no token → 401")
    void create_withoutToken_returns401() throws Exception {
        // when + then
        mockMvc.perform(post(CIRCLES_URL).contentType(MediaType.APPLICATION_JSON).content("{\"payload\": \"" + PAYLOAD_BASE64 + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder createRequest(String json) {
        return post(CIRCLES_URL)
                .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }
}
