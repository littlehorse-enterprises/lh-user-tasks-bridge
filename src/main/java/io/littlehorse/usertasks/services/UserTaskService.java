package io.littlehorse.usertasks.services;

import com.google.protobuf.ByteString;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.SearchUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.sdk.common.proto.WfRunId;
import io.littlehorse.usertasks.exceptions.NotFoundException;
import io.littlehorse.usertasks.models.requests.StandardPagination;
import io.littlehorse.usertasks.models.requests.UserTaskRequestFilter;
import io.littlehorse.usertasks.models.responses.DetailedUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.SimpleUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

import static io.littlehorse.usertasks.util.DateUtil.isDateRangeValid;

@Service
public class UserTaskService {
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient;

    UserTaskService(LittleHorseGrpc.LittleHorseBlockingStub lhClient) {
        this.lhClient = lhClient;
    }

    public Optional<UserTaskRunListDTO> getMyTasks(@NonNull String userId, @Nullable String userGroup,
                                                   @Nullable UserTaskRequestFilter additionalFilters,
                                                   int limit,
                                                   @Nullable byte[] bookmark) {
        var pagination = StandardPagination.builder()
                .bookmark(bookmark)
                .limit(limit)
                .build();

        var searchRequest = buildSearchUserTaskRunRequest(userId, userGroup, additionalFilters, pagination);
        var searchResults = lhClient.searchUserTaskRun(searchRequest);
        var resultsIdList = searchResults.getResultsList();
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
            response.setBookmark(searchResults.hasBookmark()
                    ? Base64.encodeBase64String(searchResults.getBookmark().toByteArray())
                    : null);
        }

        return response.getUserTasks().isEmpty()
                ? Optional.empty()
                : Optional.of(response);
    }

    public Optional<DetailedUserTaskRunDTO> getUserTaskDetails(@NonNull String wfRunId, @NonNull String userTaskRunGuid) {
        var getUserTaskRunRequest = UserTaskRunId.newBuilder()
                .setWfRunId(WfRunId.newBuilder()
                        .setId(wfRunId)
                        .build())
                .setUserTaskGuid(userTaskRunGuid)
                .build();

        var userTaskRunResult = lhClient.getUserTaskRun(getUserTaskRunRequest);

        if (!Objects.nonNull(userTaskRunResult)) {
            throw new NotFoundException("Could not find UserTaskRun!");
        }

        var userTaskDefResult = lhClient.getUserTaskDef(userTaskRunResult.getUserTaskDefId());

        if (!Objects.nonNull(userTaskDefResult)) {
            throw new NotFoundException("Could not find associated UserTaskDef!");
        }

        var resultDto = DetailedUserTaskRunDTO.fromUserTaskRun(userTaskRunResult, userTaskDefResult);

        return Optional.of(resultDto);
    }

    private SearchUserTaskRunRequest buildSearchUserTaskRunRequest(@NonNull String userId, @Nullable String userGroup,
                                                                   @Nullable UserTaskRequestFilter additionalFilters,
                                                                   @NonNull StandardPagination pagination) {
        var builder = SearchUserTaskRunRequest.newBuilder();
        builder.setUserId(userId);

        if (StringUtils.hasText(userGroup)) {
            builder.setUserGroup(userGroup);
        }

        if (Objects.nonNull(pagination.getBookmark())) {
            builder.setBookmark(ByteString.copyFrom(pagination.getBookmark()));
        }

        builder.setLimit(pagination.getLimit());
        addAdditionalFilters(additionalFilters, builder);

        return builder.build();
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

            if (StringUtils.hasText(additionalFilters.getType())) {
                builder.setUserTaskDefName(additionalFilters.getType());
            }

            if (Objects.nonNull(additionalFilters.getEarliestStartDate()) && Objects.nonNull(additionalFilters.getLatestStartDate())
                    && !isDateRangeValid(builder.getEarliestStart(), builder.getLatestStart())) {
                //TODO: Map this to produce a BadRequest error response
                throw new IllegalArgumentException("Wrong date range received");
            }
        }
    }
}
