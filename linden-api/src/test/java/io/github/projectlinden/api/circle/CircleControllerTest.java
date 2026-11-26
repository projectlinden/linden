package io.github.projectlinden.api.circle;

import io.github.projectlinden.api.security.SecurityConfig;
import io.github.projectlinden.core.circle.*;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CircleController.class)
@Import(SecurityConfig.class)
class CircleControllerTest {

    private static final String CIRCLES_URL = "/api/v1/circles";
    private static final UUID USER_ID = UUID.fromString("8b2d4f60-1a3c-4e5f-9b7d-2c4e6f8a0b1d");
    private static final UUID CIRCLE_ID = UUID.fromString("3c1e7a52-8f4d-4b6a-9e21-5d7f0a8c4b13");
    private static final UUID OTHER_CIRCLE_ID = UUID.fromString("6a9d2e14-7c3b-4f85-a1d6-0e8b5c7f2a94");
    private static final UUID MEMBER_ID = UUID.fromString("d4f6a8b0-2c1e-4a3b-8d5f-7e9a1b3c5d70");
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

    @Test
    @DisplayName("list my circles → 200 with my circles, in exactly the order the service gives them")
    void list_returnsMyCirclesInTheServiceOrder() throws Exception {
        // given – the service decides the order (oldest first); the API must keep it
        given(circleService.findCirclesOf(USER_ID)).willReturn(List.of(
                new Circle(OTHER_CIRCLE_ID, PAYLOAD),
                new Circle(CIRCLE_ID, PAYLOAD)));

        // when + then
        mockMvc.perform(get(CIRCLES_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circles.length()").value(2))
                .andExpect(jsonPath("$.circles[0].id").value(OTHER_CIRCLE_ID.toString()))
                .andExpect(jsonPath("$.circles[1].id").value(CIRCLE_ID.toString()))
                .andExpect(jsonPath("$.circles[0].payload").value(PAYLOAD_BASE64));
    }

    @Test
    @DisplayName("no circles yet → 200 with an empty list, not an error")
    void list_withNoCircles_returnsAnEmptyList() throws Exception {
        // given
        given(circleService.findCirclesOf(USER_ID)).willReturn(List.of());

        // when + then
        mockMvc.perform(get(CIRCLES_URL).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circles").isArray())
                .andExpect(jsonPath("$.circles").isEmpty());
    }

    @Test
    @DisplayName("list without a token → 401")
    void list_withoutToken_returns401() throws Exception {
        // when + then
        mockMvc.perform(get(CIRCLES_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("get a circle as a member → 200 with its id and payload")
    void get_asMember_returnsTheCircle() throws Exception {
        // given
        given(circleService.getCircle(CIRCLE_ID, USER_ID)).willReturn(new Circle(CIRCLE_ID, PAYLOAD));

        // when + then
        mockMvc.perform(get(CIRCLES_URL + "/" + CIRCLE_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CIRCLE_ID.toString()))
                .andExpect(jsonPath("$.payload").value(PAYLOAD_BASE64));
    }

    @Test
    @DisplayName("not a member, or the circle does not exist → 404 circle_not_found – the same answer for both")
    void get_whenNotAMember_returns404() throws Exception {
        // given
        given(circleService.getCircle(CIRCLE_ID, USER_ID)).willThrow(new NotAMemberException(CIRCLE_ID));

        // when + then
        mockMvc.perform(get(CIRCLES_URL + "/" + CIRCLE_ID).with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("circle_not_found"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("an id that is not a UUID → 400 bad_request, not a server error")
    void get_withAnIdThatIsNotAUuid_returns400() throws Exception {
        // when + then
        mockMvc.perform(get(CIRCLES_URL + "/not-a-uuid").with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    @DisplayName("get a circle without a token → 401")
    void get_withoutToken_returns401() throws Exception {
        // when + then
        mockMvc.perform(get(CIRCLES_URL + "/" + CIRCLE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("leave a circle → 204; the service is asked to remove my own membership")
    void leave_asMember_returns204() throws Exception {
        // when + then
        mockMvc.perform(delete(CIRCLES_URL + "/" + CIRCLE_ID + "/members/me")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isNoContent());
        verify(circleService).leaveCircle(CIRCLE_ID, USER_ID);
    }

    @Test
    @DisplayName("leave a circle I am not in → 404 circle_not_found")
    void leave_whenNotAMember_returns404() throws Exception {
        // given
        willThrow(new NotAMemberException(CIRCLE_ID)).given(circleService).leaveCircle(CIRCLE_ID, USER_ID);

        // when + then
        mockMvc.perform(delete(CIRCLES_URL + "/" + CIRCLE_ID + "/members/me")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("circle_not_found"));
    }

    @Test
    @DisplayName("the owner removes a member → 204; the service gets circle, acting user and member")
    void remove_byOwner_returns204() throws Exception {
        // when + then
        mockMvc.perform(delete(CIRCLES_URL + "/" + CIRCLE_ID + "/members/" + MEMBER_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isNoContent());
        verify(circleService).removeMember(CIRCLE_ID, USER_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("a member who is not the owner removes someone → 403 not_circle_owner")
    void remove_byMemberWhoIsNotOwner_returns403() throws Exception {
        // given
        willThrow(new NotCircleOwnerException(CIRCLE_ID)).given(circleService).removeMember(CIRCLE_ID, USER_ID, MEMBER_ID);

        // when + then
        mockMvc.perform(delete(CIRCLES_URL + "/" + CIRCLE_ID + "/members/" + MEMBER_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("not_circle_owner"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("the person to remove is not a member → 404 member_not_found")
    void remove_whoIsNotAMember_returns404() throws Exception {
        // given
        willThrow(new MemberNotFoundException(CIRCLE_ID, MEMBER_ID)).given(circleService).removeMember(CIRCLE_ID, USER_ID, MEMBER_ID);

        // when + then
        mockMvc.perform(delete(CIRCLES_URL + "/" + CIRCLE_ID + "/members/" + MEMBER_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("member_not_found"));
    }

    @Test
    @DisplayName("the owner removes themselves → 409 use_leave_instead")
    void remove_yourself_returns409() throws Exception {
        // given
        willThrow(new CannotRemoveYourselfException(CIRCLE_ID)).given(circleService).removeMember(CIRCLE_ID, USER_ID, USER_ID);

        // when + then
        mockMvc.perform(delete(CIRCLES_URL + "/" + CIRCLE_ID + "/members/" + USER_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("use_leave_instead"));
    }
}
