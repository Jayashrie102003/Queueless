package com.queueless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.queueless.model.User;
import com.queueless.repository.QueueTokenRepository;
import com.queueless.repository.UserRepository;
import com.queueless.support.TestSupport;

@SpringBootTest
@ActiveProfiles({"test", "test-local"})
class ApiFlowTest {

    private static final String STAFF_EMAIL = "staff1@queueless.local";
    private static final String STAFF_PASSWORD = "Staff@123";

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

    // Logs in and returns the session that carries the login.
    private MockHttpSession login(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private MockHttpSession loginPatient(User patient) throws Exception {
        return login(patient.getEmail(), "secret123");
    }

    private MockHttpSession loginStaff() throws Exception {
        return login(STAFF_EMAIL, STAFF_PASSWORD);
    }

    // Queue 1 is General Medicine (10 minutes per patient), seeded at startup.
    private static final long QUEUE = 1L;

    @Test
    void joinGivesTheNextNumberAndAWaitEstimate() throws Exception {
        MockHttpSession first = loginPatient(TestSupport.createPatient(users, encoder, "First Joiner"));
        MockHttpSession second = loginPatient(TestSupport.createPatient(users, encoder, "Second Joiner"));

        mvc.perform(post("/api/queues/" + QUEUE + "/join").session(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenNumber").value(1))
                .andExpect(jsonPath("$.peopleAhead").value(0))
                .andExpect(jsonPath("$.estimatedWaitMinutes").value(0));

        mvc.perform(post("/api/queues/" + QUEUE + "/join").session(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenNumber").value(2))
                .andExpect(jsonPath("$.peopleAhead").value(1))
                .andExpect(jsonPath("$.estimatedWaitMinutes").value(10));
    }

    @Test
    void secondJoinToTheSameQueueIsRefusedWith409() throws Exception {
        MockHttpSession patient = loginPatient(TestSupport.createPatient(users, encoder, "Twice"));
        mvc.perform(post("/api/queues/" + QUEUE + "/join").session(patient)).andExpect(status().isOk());

        mvc.perform(post("/api/queues/" + QUEUE + "/join").session(patient))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("You already have an active token in this queue"));
    }

    @Test
    void patientCanLeaveTheirOwnWaitingToken() throws Exception {
        MockHttpSession patient = loginPatient(TestSupport.createPatient(users, encoder, "Leaver"));
        String body = mvc.perform(post("/api/queues/" + QUEUE + "/join").session(patient))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long tokenId = Long.parseLong(body.replaceAll("\\D*\"id\"\\D*(\\d+).*", "$1"));

        mvc.perform(post("/api/tokens/" + tokenId + "/leave").session(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    void anotherPatientCannotLeaveSomeoneElsesToken() throws Exception {
        MockHttpSession owner = loginPatient(TestSupport.createPatient(users, encoder, "Owner"));
        MockHttpSession intruder = loginPatient(TestSupport.createPatient(users, encoder, "Intruder"));
        String body = mvc.perform(post("/api/queues/" + QUEUE + "/join").session(owner))
                .andReturn().getResponse().getContentAsString();
        long tokenId = Long.parseLong(body.replaceAll("\\D*\"id\"\\D*(\\d+).*", "$1"));

        mvc.perform(post("/api/tokens/" + tokenId + "/leave").session(intruder))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Token not found"));
    }

    @Test
    void aCalledPatientCountsAsOneAheadOfTheNextPatient() throws Exception {
        MockHttpSession first = loginPatient(TestSupport.createPatient(users, encoder, "Being Served"));
        MockHttpSession second = loginPatient(TestSupport.createPatient(users, encoder, "Waiting One"));
        MockHttpSession third = loginPatient(TestSupport.createPatient(users, encoder, "Third"));
        MockHttpSession staff = loginStaff();

        mvc.perform(post("/api/queues/" + QUEUE + "/join").session(first)).andExpect(status().isOk());
        mvc.perform(post("/api/queues/" + QUEUE + "/join").session(second)).andExpect(status().isOk());
        mvc.perform(post("/api/staff/queues/" + QUEUE + "/call-next").session(staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALLED"));

        // Third joins: one WAITING ahead (second) plus one CALLED (first) = 2 people ahead.
        mvc.perform(post("/api/queues/" + QUEUE + "/join").session(third))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peopleAhead").value(2))
                .andExpect(jsonPath("$.estimatedWaitMinutes").value(20));
    }

    @Test
    void staffCanCallNextAndComplete() throws Exception {
        MockHttpSession patient = loginPatient(TestSupport.createPatient(users, encoder, "To Complete"));
        MockHttpSession staff = loginStaff();
        mvc.perform(post("/api/queues/" + QUEUE + "/join").session(patient)).andExpect(status().isOk());

        String called = mvc.perform(post("/api/staff/queues/" + QUEUE + "/call-next").session(staff))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long tokenId = Long.parseLong(called.replaceAll("\\D*\"id\"\\D*(\\d+).*", "$1"));

        mvc.perform(post("/api/staff/tokens/" + tokenId + "/complete").session(staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void publicDisplayShowsNumbersButNeverNames() throws Exception {
        String name = "Secret Name Patient";
        MockHttpSession patient = loginPatient(TestSupport.createPatient(users, encoder, name));
        mvc.perform(post("/api/queues/" + QUEUE + "/join").session(patient)).andExpect(status().isOk());

        mvc.perform(get("/api/display/queues/" + QUEUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting[0]").value(1))
                .andExpect(content().string(not(containsString(name))))
                .andExpect(content().string(not(containsString("@loadtest.local"))));
    }
}