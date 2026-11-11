package com.projecthk5.sellerservice.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.sellerservice.seller.service.SellerInputs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** HTTP boundary of the seller application PII, on PostgreSQL with synthetic values only. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("postgresql")
class SellerApplicationHttpPostgresIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private static final String ID_NUMBER = "001234567890";
    private static final String BANK_ACCOUNT = "0123456789012";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc http;
    @Autowired private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private String applicant;

    @BeforeEach
    void apply() throws Exception {
        jdbc.update("DELETE FROM seller_outbox");
        jdbc.update("DELETE FROM seller_status_history");
        jdbc.update("DELETE FROM stores");
        jdbc.update("DELETE FROM seller_profiles");
        applicant = "applicant-" + UUID.randomUUID();
        assertEquals(201, call(post("/api/sellers").contentType(MediaType.APPLICATION_JSON).content(body(SellerInputs.TERMS_VERSION)),
                applicant, "CUSTOMER").getResponse().getStatus());
    }

    @Test
    void theOwnerReadsBackTheFullApplicationAndTheRulesAcceptanceIsStored() throws Exception {
        JsonNode contact = json.readTree(call(get("/api/sellers/me"), applicant, "CUSTOMER").getResponse().getContentAsString())
                .get("contact");
        assertEquals(ID_NUMBER, contact.get("idNumber").asText());
        assertEquals(BANK_ACCOUNT, contact.get("bankAccountNumber").asText());
        assertEquals("HOUSEHOLD", contact.get("businessType").asText());
        Map<String, Object> row = jdbc.queryForMap("SELECT id_number, terms_version, terms_accepted_at FROM seller_profiles");
        assertEquals(ID_NUMBER, row.get("id_number"));
        assertEquals(SellerInputs.TERMS_VERSION, row.get("terms_version"));
        assertNotNull(row.get("terms_accepted_at"));
    }

    @Test
    void anotherAccountCannotReadTheApplication() throws Exception {
        MvcResult other = call(get("/api/sellers/me"), "someone-else", "CUSTOMER");
        assertEquals(404, other.getResponse().getStatus());
        assertFalse(other.getResponse().getContentAsString().contains(ID_NUMBER));
    }

    @Test
    void onlyAnAdminReachesTheListAndHistory() throws Exception {
        String id = jdbc.queryForObject("SELECT id::text FROM seller_profiles", String.class);
        for (String path : List.of("/api/admin/sellers", "/api/admin/sellers/" + id + "/history")) {
            for (String role : List.of("CUSTOMER", "SELLER")) {
                MvcResult denied = call(get(path), applicant, role);
                assertEquals(403, denied.getResponse().getStatus(), path + " as " + role);
                assertFalse(denied.getResponse().getContentAsString().contains(ID_NUMBER));
            }
            assertEquals(401, http.perform(get(path)).andReturn().getResponse().getStatus(), path + " anonymous");
        }
    }

    @Test
    void theAdminListNeverCarriesTheFullIdOrBankAccount() throws Exception {
        String list = call(get("/api/admin/sellers"), "admin-1", "ADMIN").getResponse().getContentAsString();
        JsonNode contact = json.readTree(list).get(0).get("contact");
        assertEquals("********7890", contact.get("idNumber").asText());
        assertEquals("*********9012", contact.get("bankAccountNumber").asText());
        assertFalse(list.contains(ID_NUMBER));
        assertFalse(list.contains(BANK_ACCOUNT));
    }

    @Test
    void anApplicationForOtherRulesIsRefusedWithoutEchoingIt() throws Exception {
        MvcResult refused = call(post("/api/sellers").contentType(MediaType.APPLICATION_JSON).content(body("2020-01-01")),
                "late-applicant", "CUSTOMER");
        assertEquals(400, refused.getResponse().getStatus());
        assertFalse(refused.getResponse().getContentAsString().contains(ID_NUMBER));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class));
    }

    private MvcResult call(MockHttpServletRequestBuilder request, String subject, String role) throws Exception {
        JwtRequestPostProcessor token = jwt().jwt(j -> j.subject(subject).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
        return http.perform(request.with(token)).andReturn();
    }

    private String body(String termsVersion) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("representativeName", "Nguoi Thu");
        body.put("phone", "0901234567");
        body.put("pickupAddress", "1 Duong Thu");
        body.put("businessType", "HOUSEHOLD");
        body.put("idNumber", ID_NUMBER);
        body.put("taxCode", "0312345678");
        body.put("email", "thu@example.test");
        body.put("bankName", "Ngan hang Thu");
        body.put("bankAccountNumber", BANK_ACCOUNT);
        body.put("bankAccountHolder", "NGUOI THU");
        body.put("termsVersion", termsVersion);
        return json.writeValueAsString(body);
    }
}
