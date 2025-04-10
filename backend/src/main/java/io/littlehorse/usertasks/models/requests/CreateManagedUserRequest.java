package io.littlehorse.usertasks.models.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.stream.Stream;

@Data
@Builder
@AllArgsConstructor
public class CreateManagedUserRequest {
    private String firstName;
    private String lastName;
    private String username;
    private String email;

    /**
     * As long as 1 of the properties has content other than NULL, empty or a whitespace-only value, this model is valid.
     * @return True is at least one field has content, otherwise False.
     */
    public boolean isValid() {
        return Stream.of(getFirstName(), getLastName(), getUsername(), getEmail())
                .anyMatch(StringUtils::isNotBlank);
    }
}
