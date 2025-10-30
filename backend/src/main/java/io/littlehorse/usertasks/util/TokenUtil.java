package io.littlehorse.usertasks.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.NonNull;

/**
 * Helper class able to handle token-related functions
 */
public class TokenUtil {

    /**
     * Decodes an access token and returns a map with the claims obtained from the token
     *
     * @param accessToken Access token that will be decoded
     * @return A {@code java.util.Map} that contains the decoded claims
     * @throws JsonProcessingException
     */
    public static Map<String, Object> getTokenClaims(@NonNull String accessToken) throws JsonProcessingException {
        String[] chunks = accessToken.split("\\.");
        Base64.Decoder decoder = Base64.getUrlDecoder();

        // headers are not taken into consideration here, that is why we skip position 0 of the chunk array
        String payload = new String(decoder.decode(chunks[1]));

        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {};

        return new ObjectMapper().readValue(payload, typeRef);
    }
}
