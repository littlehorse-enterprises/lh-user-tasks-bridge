package io.littlehorse.usertasks.models.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UpdateManagedUserRequest {
    private String firstName;
    private String lastName;
    private String username;

    @Email
    private String email;

    private boolean enabled;

    @NotNull
    public Map<String, Object> toMap() {
        Map<String, Object> mappedProperties = new HashMap<>();
        mappedProperties.put("firstName", getFirstName());
        mappedProperties.put("lastName", getLastName());
        mappedProperties.put("username", getUsername());
        mappedProperties.put("email", getEmail());
        mappedProperties.put("enabled", isEnabled());

        mappedProperties.entrySet().removeIf(entry -> Objects.isNull(entry.getValue()));

        return mappedProperties;
    }
}
