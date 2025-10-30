package io.littlehorse.usertasks.models.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StandardPagination {
    private int limit;

    @Nullable
    private byte[] bookmark;
}
