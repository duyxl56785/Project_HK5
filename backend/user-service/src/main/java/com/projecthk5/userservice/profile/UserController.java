package com.projecthk5.userservice.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import com.projecthk5.security.JwtResourceServer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** OD-04 profile API plus the F02 address book; identity is always the verified JWT sub. */
@RestController
@RequestMapping("/api/users/me")
@Transactional
public class UserController {
    static final String PHONE = "^\\+[1-9][0-9]{1,14}$";
    private final Profiles profiles;
    private final Addresses addresses;

    public UserController(Profiles profiles, Addresses addresses) {
        this.profiles = profiles;
        this.addresses = addresses;
    }

    public record ProfileResponse(String userId, String displayName, String phone) {}
    public record AddressRequest(@NotBlank @Size(max = 100) String recipientName, @NotBlank @Pattern(regexp = PHONE) String phone,
                                 @NotBlank @Size(max = 255) String line, @NotBlank @Size(max = 100) String city, Boolean isDefault) {}
    public record AddressResponse(UUID id, String recipientName, String phone, String line, String city, boolean isDefault) {
        static AddressResponse of(UserAddress a) {
            return new AddressResponse(a.getId(), a.getRecipientName(), a.getPhone(), a.getLine(), a.getCity(), a.isDefault());
        }
    }

    @GetMapping
    public ProfileResponse get() {
        return view(profile(Actor.current().id()));
    }

    /** Only displayName/phone: omitted keeps the value, null clears it, unknown fields or {} are rejected. */
    @PatchMapping
    public ProfileResponse patch(@RequestBody JsonNode body) {
        if (body == null || !body.isObject() || body.isEmpty()) throw invalid();
        body.fieldNames().forEachRemaining(f -> { if (!Set.of("displayName", "phone").contains(f)) throw invalid(); });
        UserProfile p = profile(Actor.current().id());
        String name = body.has("displayName") ? text(body.get("displayName")) : p.getDisplayName();
        String phone = body.has("phone") ? text(body.get("phone")) : p.getPhone();
        if (name != null && (name.strip().isEmpty() || name.strip().length() > 100)) throw invalid();
        if (phone != null && !phone.matches(PHONE)) throw invalid();
        p.update(name == null ? null : name.strip(), phone);
        return view(p);
    }

    @GetMapping("/addresses")
    public List<AddressResponse> listAddresses() {
        return addresses.findByAccountId(Actor.current().id()).stream()
                .sorted(Comparator.comparing(UserAddress::isDefault).reversed())
                .map(AddressResponse::of).toList();
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse addAddress(@Valid @RequestBody AddressRequest req) {
        String me = Actor.current().id();
        profile(me);
        List<UserAddress> mine = addresses.findByAccountId(me);
        if (mine.size() >= 10) throw ApiException.conflict("USER_ADDRESS_LIMIT", "At most 10 addresses");
        UserAddress a = new UserAddress(me, req.recipientName(), req.phone(), req.line(), req.city());
        makeDefault(mine, a, mine.isEmpty() || Boolean.TRUE.equals(req.isDefault()));
        return AddressResponse.of(addresses.saveAndFlush(a));
    }

    @PutMapping("/addresses/{id}")
    public AddressResponse updateAddress(@PathVariable UUID id, @Valid @RequestBody AddressRequest req) {
        UserAddress a = owned(id);
        a.update(req.recipientName(), req.phone(), req.line(), req.city());
        if (Boolean.TRUE.equals(req.isDefault())) makeDefault(addresses.findByAccountId(a.getAccountId()), a, true);
        return AddressResponse.of(a);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@PathVariable UUID id) {
        addresses.delete(owned(id));
    }

    private void makeDefault(List<UserAddress> mine, UserAddress target, boolean makeDefault) {
        if (!makeDefault) return;
        mine.stream().filter(UserAddress::isDefault).forEach(x -> x.setDefault(false));
        addresses.flush();
        target.setDefault(true);
    }

    private UserAddress owned(UUID id) {
        UserAddress a = addresses.findById(id).orElse(null);
        if (a == null || !a.getAccountId().equals(Actor.current().id()))
            throw ApiException.notFound("USER_ADDRESS_NOT_FOUND", "Address not found");
        return a;
    }

    /** Profiles are provisioned lazily on first authenticated access. */
    private UserProfile profile(String accountId) {
        return profiles.findById(accountId).orElseGet(() -> profiles.saveAndFlush(new UserProfile(accountId)));
    }

    private static ProfileResponse view(UserProfile p) {
        return new ProfileResponse(p.getAccountId(), p.getDisplayName(), p.getPhone());
    }

    private static String text(JsonNode node) {
        if (node.isNull()) return null;
        if (!node.isTextual()) throw invalid();
        return node.asText();
    }

    private static ApiException invalid() {
        return ApiException.badRequest("VALIDATION_FAILED", "Invalid profile update");
    }

    @Configuration
    static class Security {
        @Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
        SecurityFilterChain userSecurity(HttpSecurity http) throws Exception {
            return JwtResourceServer.configure(http).authorizeHttpRequests(auth -> auth
                    .requestMatchers("/error").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()
                    .anyRequest().denyAll()).build();
        }
    }
}
