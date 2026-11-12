package dev.ngb.sampletenant.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.application.user.command.RegisterUserUseCase;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Creates users and calls the API with a real bearer session, the way SessionAuthenticationFilter reads it. */
public final class HttpWorld {

    public static final String PASSWORD = "perm-pass-1";

    private final MockMvc mvc;
    private final RegisterUserUseCase registerUser;
    private final JsonMapper json = JsonMapper.builder().build();

    public HttpWorld(MockMvc mvc, RegisterUserUseCase registerUser) {
        this.mvc = mvc;
        this.registerUser = registerUser;
    }

    public String adminToken() {
        return token(Role.ADMIN);
    }

    /** Registers an account with the given role through the use case, then logs in over HTTP. */
    public String token(Role role) {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        registerUser.execute(new RegisterUserUseCase.Command(email, role.name(), PASSWORD, role));
        return login(email, PASSWORD);
    }

    /** Registers a customer over the public endpoint, which is what triggers the customer mapping. */
    public Account registerViaApi() {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        MvcResult result = call(HttpMethod.POST, null, "/api/auth/register", Map.of(
                "email", email, "displayName", "Customer", "password", PASSWORD));
        requireStatus(result, 201);
        return new Account(body(result).get("id").asString(), email, login(email, PASSWORD));
    }

    public String login(String email, String password) {
        MvcResult result = call(HttpMethod.POST, null, "/api/auth/login", Map.of("email", email, "password", password));
        requireStatus(result, 200);
        return body(result).get("token").asString();
    }

    public record Account(String id, String email, String token) {}

    public MvcResult call(HttpMethod method, String token, String path, Object payload) {
        try {
            MockHttpServletRequestBuilder builder = request(method, path);
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            if (payload != null) {
                builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
            }
            return mvc.perform(builder).andReturn();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public MvcResult callRaw(HttpMethod method, String token, String path, String body, Map<String, String> headers) {
        try {
            MockHttpServletRequestBuilder builder = request(method, path).contentType(MediaType.APPLICATION_JSON).content(body);
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            headers.forEach(builder::header);
            return mvc.perform(builder).andReturn();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public int status(HttpMethod method, String token, String path, Object payload) {
        return call(method, token, path, payload).getResponse().getStatus();
    }

    public JsonNode body(MvcResult result) {
        requireSuccess(result);
        return parse(result);
    }

    public JsonNode parse(MvcResult result) {
        try {
            String content = result.getResponse().getContentAsString();
            return content.isBlank() ? json.nullNode() : json.readTree(content);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String text(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void requireStatus(MvcResult result, int expected) {
        int actual = result.getResponse().getStatus();
        if (actual != expected) {
            throw new AssertionError("expected status " + expected + " but was " + actual + ": " + text(result));
        }
    }

    private void requireSuccess(MvcResult result) {
        int status = result.getResponse().getStatus();
        if (status >= 300) {
            throw new IllegalStateException("unexpected status " + status + ": " + text(result));
        }
    }

    /** Tiny map builder that accepts mixed value types without unchecked generics. */
    public static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
