package com.projecthk5.userservice.profile;

import com.projecthk5.security.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** F02 against the real schema: at most one default address, and only the owner reaches an address. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("migration")
@EnabledIfEnvironmentVariable(named = "TV1_DATABASE_TESTS", matches = "true")
class AddressBookDatabaseTest {
    @Autowired
    private UserController controller;

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void firstAddressBecomesDefaultAndANewDefaultReplacesIt() {
        actAs(account());
        var first = add(false);
        assertTrue(first.isDefault());
        assertFalse(add(false).isDefault());
        var third = add(true);

        List<UserController.AddressResponse> all = controller.listAddresses();
        assertEquals(3, all.size());
        assertEquals(List.of(third.id()), all.stream().filter(UserController.AddressResponse::isDefault)
                .map(UserController.AddressResponse::id).toList());
        assertEquals(third.id(), all.get(0).id());
    }

    @Test
    void updateCanMoveTheDefaultWithoutHittingTheUniqueIndex() {
        actAs(account());
        var first = add(false);
        var second = add(false);
        controller.updateAddress(second.id(), request(true));

        var byId = controller.listAddresses();
        assertTrue(byId.stream().filter(a -> a.id().equals(second.id())).findFirst().orElseThrow().isDefault());
        assertFalse(byId.stream().filter(a -> a.id().equals(first.id())).findFirst().orElseThrow().isDefault());
    }

    @Test
    void anotherAccountCannotSeeChangeOrDeleteAnAddress() {
        actAs(account());
        var owned = add(false);

        actAs(account());
        assertTrue(controller.listAddresses().isEmpty());
        assertNotFound(() -> controller.updateAddress(owned.id(), request(true)));
        assertNotFound(() -> controller.deleteAddress(owned.id()));
    }

    @Test
    void addressBookStopsAtTenEntries() {
        actAs(account());
        for (int i = 0; i < 10; i++) add(false);
        var limit = assertThrows(ApiException.class, () -> add(false));
        assertEquals(HttpStatus.CONFLICT, limit.status());
    }

    private UserController.AddressResponse add(boolean isDefault) {
        return controller.addAddress(request(isDefault));
    }

    private static UserController.AddressRequest request(boolean isDefault) {
        return new UserController.AddressRequest("An", "+84901234567", "1 Le Loi", "Ho Chi Minh", isDefault);
    }

    private static void assertNotFound(org.junit.jupiter.api.function.Executable call) {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, call).status());
    }

    private static String account() {
        return UUID.randomUUID().toString();
    }

    private static void actAs(String account) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(account).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(), account));
    }
}
