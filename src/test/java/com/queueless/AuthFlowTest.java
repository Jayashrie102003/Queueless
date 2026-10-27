package com.queueless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.queueless.model.Role;
import com.queueless.model.User;
import com.queueless.repository.QueueTokenRepository;
import com.queueless.repository.UserRepository;
import com.queueless.support.TestSupport;

@SpringBootTest
@ActiveProfiles({"test", "test-local"})
class AuthFlowTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private QueueTokenRepository tokens;
    @Autowired
    private PasswordEncoder encoder;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        TestSupport.resetPatientsAndTokens(tokens, users);
    }

    @Test
    void registrationAlwaysCreatesAPatient_evenWhenRoleIsSentAsStaff() throws Exception {
        String email = "sneaky-" + System.nanoTime() + "@loadtest.local";
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sneaky\",\"email\":\"" + email
                                + "\",\"password\":\"secret123\",\"role\":\"STAFF\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"));

        User saved = users.findByEmail(email).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.PATIENT);
    }

    @Test
    void passwordIsStoredAsBCryptHash_notPlainText() throws Exception {
        String email = "hash-" + System.nanoTime() + "@loadtest.local";
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hash Test\",\"email\":\"" + email
                                + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());

        User saved = users.findByEmail(email).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(saved.getPasswordHash()).startsWith("$2");
    }

    @Test
    void staffEndpointRefusesAnonymousUsersWith401() throws Exception {
        mvc.perform(get("/api/staff/queues/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffEndpointRefusesPatientsWith403() throws Exception {
        User patient = TestSupport.createPatient(users, encoder, "Role Check");
        var session = new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/api/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + patient.getEmail() + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/staff/queues/1").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordIsRefusedWith401() throws Exception {
        User patient = TestSupport.createPatient(users, encoder, "Wrong Password");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + patient.getEmail() + "\",\"password\":\"wrong123\"}"))
                .andExpect(status().isUnauthorized());
    }
}