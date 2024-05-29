package io.littlehorse.usertasks.services;

import com.google.protobuf.ByteString;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.SearchUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.usertasks.models.requests.StandardPagination;
import io.littlehorse.usertasks.models.requests.UserTaskRequestFilter;
import io.littlehorse.usertasks.models.responses.SimpleUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static io.littlehorse.usertasks.util.DateUtil.isDateRangeValid;

@Service
public class UserTaskService {
    private static final Logger log = LoggerFactory.getLogger(UserTaskService.class);
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient;

    UserTaskService(LittleHorseGrpc.LittleHorseBlockingStub lhClient) {
        this.lhClient = lhClient;
    }

    public Optional<UserTaskRunListDTO> getMyTasks(@NonNull String userId, @Nullable String userGroup,
                                                   @Nullable UserTaskRequestFilter additionalFilters, @Nullable ByteString bookmark) {
        var pagination = StandardPagination.builder()
                .bookmark(bookmark)
                .limit(25)
                .build();

        var searchRequest = buildSearchUserTaskRunRequest(userId, userGroup, additionalFilters, pagination).get();
        var searchResults = lhClient.searchUserTaskRun(searchRequest);
        var resultsIdList = searchResults.getResultsList();
        var resultsHaveBookmark = searchResults.hasBookmark();
        var setOfUserTasks = new HashSet<SimpleUserTaskRunDTO>();
        var response = UserTaskRunListDTO.builder()
                .userTasks(setOfUserTasks)
                .build();

        if (!resultsIdList.isEmpty()) {
            resultsIdList.forEach(userTaskRunId -> {
                UserTaskRun userTaskRun = lhClient.getUserTaskRun(userTaskRunId);
                setOfUserTasks.add(SimpleUserTaskRunDTO.fromUserTaskRun(userTaskRun));
            });

            response.setUserTasks(setOfUserTasks);
        }

        while (resultsHaveBookmark) {
            Optional<UserTaskRunListDTO> subsequentUserTasks = getMyTasks(userId, userGroup, additionalFilters, searchResults.getBookmark());

            subsequentUserTasks.ifPresent(dto -> response.getUserTasks().addAll(dto.getUserTasks()));
            resultsHaveBookmark = false;
        }

        return response.getUserTasks().isEmpty()
                ? Optional.empty()
                : Optional.of(response);
    }

    private Supplier<SearchUserTaskRunRequest> buildSearchUserTaskRunRequest(@NonNull String userId, @Nullable String userGroup,
                                                                             @Nullable UserTaskRequestFilter additionalFilters,
                                                                             @NonNull StandardPagination pagination) {
        return () -> {
            var builder = SearchUserTaskRunRequest.newBuilder();
            builder.setUserId(userId);

            if (StringUtils.hasText(userGroup)) {
                builder.setUserGroup(userGroup);
            }

            if (Objects.nonNull(pagination.getBookmark())) {
                builder.setBookmark(pagination.getBookmark());
            }

            builder.setLimit(pagination.getLimit());
            addAdditionalFilters(additionalFilters, builder);

            return builder.build();
        };
    }

    private void addAdditionalFilters(@Nullable UserTaskRequestFilter additionalFilters, SearchUserTaskRunRequest.Builder builder) {
        if (Objects.nonNull(additionalFilters)) {
            if (Objects.nonNull(additionalFilters.getEarliestStartDate())) {
                builder.setEarliestStart(additionalFilters.getEarliestStartDate());
            }

            if (Objects.nonNull(additionalFilters.getLatestStartDate())) {
                builder.setLatestStart(additionalFilters.getLatestStartDate());
            }

            if (Objects.nonNull(additionalFilters.getStatus())) {
                builder.setStatus(additionalFilters.getStatus().toServerStatus());
            }

            if (Objects.nonNull(additionalFilters.getType())) {
                builder.setUserTaskDefName(additionalFilters.getType());
            }

            if (Objects.nonNull(additionalFilters.getEarliestStartDate()) && Objects.nonNull(additionalFilters.getLatestStartDate())
                    && !isDateRangeValid(builder.getEarliestStart(), builder.getLatestStart())) {
                throw new IllegalArgumentException("Wrong date range received");
            }
        }
    }
}
