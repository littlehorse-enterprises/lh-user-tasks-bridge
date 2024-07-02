package io.littlehorse.usertasks.services;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.littlehorse.sdk.common.auth.TenantMetadataProvider;
import io.littlehorse.sdk.common.proto.AssignUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.CompleteUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.SearchUserTaskDefRequest;
import io.littlehorse.sdk.common.proto.SearchUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.UserTaskDefId;
import io.littlehorse.sdk.common.proto.UserTaskDefIdList;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.sdk.common.proto.UserTaskRunIdList;
import io.littlehorse.sdk.common.proto.UserTaskRunStatus;
import io.littlehorse.sdk.common.proto.WfRunId;
import io.littlehorse.usertasks.exceptions.CustomUnauthorizedException;
import io.littlehorse.usertasks.exceptions.NotFoundException;
import io.littlehorse.usertasks.models.requests.AssignmentRequest;
import io.littlehorse.usertasks.models.requests.CompleteUserTaskRequest;
import io.littlehorse.usertasks.models.requests.StandardPagination;
import io.littlehorse.usertasks.models.requests.UserTaskRequestFilter;
import io.littlehorse.usertasks.models.responses.AuditEventDTO;
import io.littlehorse.usertasks.models.responses.DetailedUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.SimpleUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.UserTaskDefListDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.littlehorse.usertasks.util.DateUtil.isDateRangeValid;

@Service
@Slf4j
public class UserTaskService {
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient;

    UserTaskService(LittleHorseGrpc.LittleHorseBlockingStub lhClient) {
        this.lhClient = lhClient;
    }

    public Optional<UserTaskRunListDTO> getTasks(@NonNull String tenantId, String userId, String userGroup, UserTaskRequestFilter additionalFilters,
                                                 int limit, byte[] bookmark, boolean isAdminRequest) {
        if (!isAdminRequest && !StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("Cannot search UserTask without specifying a proper UserId");
        }

        var pagination = StandardPagination.builder()
                .bookmark(bookmark)
                .limit(limit)
                .build();

        SearchUserTaskRunRequest searchRequest;
        searchRequest = buildSearchUserTaskRunRequest(userId, userGroup, additionalFilters, pagination);

        LittleHorseGrpc.LittleHorseBlockingStub tenantClient = lhClient.withCallCredentials(new TenantMetadataProvider(tenantId));

        UserTaskRunIdList searchResults = tenantClient.searchUserTaskRun(searchRequest);
        List<UserTaskRunId> resultsIdList = searchResults.getResultsList();
        var setOfUserTasks = new HashSet<SimpleUserTaskRunDTO>();
        var response = UserTaskRunListDTO.builder()
                .userTasks(setOfUserTasks)
                .build();

        if (!resultsIdList.isEmpty()) {
            resultsIdList.forEach(userTaskRunId -> {
                UserTaskRun userTaskRun = tenantClient.getUserTaskRun(userTaskRunId);
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

    public Optional<DetailedUserTaskRunDTO> getUserTaskDetails(@NonNull String wfRunId, @NonNull String userTaskRunGuid,
                                                               @NonNull String tenantId, String userId, String userGroup,
                                                               boolean isAdminRequest) {
        var getUserTaskRunRequest = UserTaskRunId.newBuilder()
                .setWfRunId(WfRunId.newBuilder()
                        .setId(wfRunId)
                        .build())
                .setUserTaskGuid(userTaskRunGuid)
                .build();

        LittleHorseGrpc.LittleHorseBlockingStub tenantClient = lhClient.withCallCredentials(new TenantMetadataProvider(tenantId));

        var userTaskRunResult = tenantClient.getUserTaskRun(getUserTaskRunRequest);

        if (!Objects.nonNull(userTaskRunResult)) {
            throw new NotFoundException("Could not find UserTaskRun!");
        }

        if (!isAdminRequest) {
            validateIfUserIsAllowedToSeeUserTask(userId, userGroup, userTaskRunResult);
        }

        var userTaskDefResult = tenantClient.getUserTaskDef(userTaskRunResult.getUserTaskDefId());

        if (!Objects.nonNull(userTaskDefResult)) {
            throw new NotFoundException("Could not find associated UserTaskDef!");
        }

        var resultDto = DetailedUserTaskRunDTO.fromUserTaskRun(userTaskRunResult, userTaskDefResult);

        if (isAdminRequest) {
            Set<AuditEventDTO> events = new HashSet<>();

            userTaskRunResult.getEventsList().forEach(serverEvent -> {
                AuditEventDTO event = AuditEventDTO.fromUserTaskEvent(serverEvent);
                events.add(event);
            });

            resultDto.setEvents(events);
        }

        return Optional.of(resultDto);
    }

    public void completeUserTask(@NonNull String userId, @NonNull CompleteUserTaskRequest request, @NonNull String tenantId,
                                 boolean isAdminRequest) {
        try {
            log.info("Completing UserTaskRun");

            Optional<DetailedUserTaskRunDTO> userTaskDetails = getUserTaskDetails(request.getWfRunId(),
                    request.getUserTaskRunGuid(), tenantId, userId, null, isAdminRequest);//TODO: UserGroup param must be added here later on

            if (userTaskDetails.isPresent()) {
                if (isUserTaskTerminated(userTaskDetails.get().getStatus().toServerStatus())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "The UserTask you are trying to complete is already DONE or CANCELLED");
                }
            }

            CompleteUserTaskRunRequest serverRequest = request.toServerRequest(userId);

            LittleHorseGrpc.LittleHorseBlockingStub tenantClient = lhClient.withCallCredentials(new TenantMetadataProvider(tenantId));

            tenantClient.completeUserTaskRun(serverRequest);

            log.atInfo()
                    .setMessage("UserTaskRun with wfRunId: {}, guid: {} was successfully completed")
                    .addArgument(request.getWfRunId())
                    .addArgument(request.getUserTaskRunGuid())
                    .log();
        } catch (StatusRuntimeException e) {
            log.atError()
                    .setMessage("Something went wrong in LH Server with completion process for UserTaskRun with with " +
                            "wfRunId: {} and guid: {} ")
                    .addArgument(request.getWfRunId())
                    .addArgument(request.getUserTaskRunGuid())
                    .log();

            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }

            throw e;
        } catch (Exception e) {
            log.atError()
                    .setMessage("Completion of UserTaskRun with wfRunId: {}, guid: {} failed")
                    .addArgument(request.getWfRunId())
                    .addArgument(request.getUserTaskRunGuid())
                    .log();
            throw e;
        }
    }

    public UserTaskDefListDTO getAllUserTasksDef(@NonNull String tenantId, int limit, byte[] bookmark) {
        LittleHorseGrpc.LittleHorseBlockingStub tenantClient = lhClient.withCallCredentials(new TenantMetadataProvider(tenantId));

        SearchUserTaskDefRequest.Builder searchRequest = SearchUserTaskDefRequest.newBuilder()
                .setLimit(limit);

        if (Objects.nonNull(bookmark)) {
            searchRequest.setBookmark(ByteString.copyFrom(bookmark));
        }

        UserTaskDefIdList searchResults = tenantClient.searchUserTaskDef(searchRequest.build());

        if (searchResults.getResultsList().isEmpty()) {
            throw new NotFoundException("No UserTaskDefs were found for given tenant");
        }

        Set<String> setOfUserTaskDefNames = searchResults.getResultsList().stream()
                .map(UserTaskDefId::getName)
                .collect(Collectors.toSet());

        return UserTaskDefListDTO.builder()
                .userTaskDefNames(setOfUserTaskDefNames)
                .bookmark(searchResults.hasBookmark()
                        ? Base64.encodeBase64String(searchResults.getBookmark().toByteArray())
                        : null)
                .build();
    }

    public void assignUserTask(@NonNull AssignmentRequest requestBody, @NonNull String wfRunId,
                               @NonNull String userTaskRunGuid, @NonNull String tenantId) {
        try {
            log.atInfo()
                    .setMessage("Assigning UserTaskRun with wfRunId: {} and userTaskGuid: {}")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();

            if (!StringUtils.hasText(requestBody.getUserId()) && !StringUtils.hasText(requestBody.getUserGroup())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid arguments were received to complete reassignment.");
            }

            LittleHorseGrpc.LittleHorseBlockingStub tenantClient = lhClient.withCallCredentials(new TenantMetadataProvider(tenantId));

            AssignUserTaskRunRequest.Builder requestBuilder = AssignUserTaskRunRequest.newBuilder()
                    .setUserTaskRunId(UserTaskRunId.newBuilder()
                            .setWfRunId(WfRunId.newBuilder()
                                    .setId(wfRunId)
                                    .build())
                            .setUserTaskGuid(userTaskRunGuid)
                            .build())
                    .setOverrideClaim(true);

            if (StringUtils.hasText(requestBody.getUserId())) {
                requestBuilder.setUserId(requestBody.getUserId());
            }

            if (StringUtils.hasText(requestBody.getUserGroup())) {
                requestBuilder.setUserGroup(requestBody.getUserGroup());
            }

            tenantClient.assignUserTaskRun(requestBuilder.build());

            log.atInfo()
                    .setMessage("UserTaskRun with wfRunId: {} and guid: {} was successfully assigned to {}")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .addArgument(requestBody.getUserId())
                    .log();
        } catch (StatusRuntimeException e) {
            log.atError()
                    .setMessage("Something went wrong in LH Server with assignment process for UserTaskRun with with " +
                            "wfRunId: {} and guid: {} ")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }

            if (e.getStatus().getCode() == Status.Code.FAILED_PRECONDITION) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, e.getMessage(), e);
            }

            throw e;
        } catch (Exception e) {
            log.atError()
                    .setMessage("Assignment of UserTaskRun with wfRunId: {} and guid: {} failed")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();

            throw e;
        }
    }

    private SearchUserTaskRunRequest buildSearchUserTaskRunRequest(String userId, String userGroup,
                                                                   UserTaskRequestFilter additionalFilters,
                                                                   @NonNull StandardPagination pagination) {
        var builder = SearchUserTaskRunRequest.newBuilder();

        if (StringUtils.hasText(userId)) {
            builder.setUserId(userId);
        }

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

    private void addAdditionalFilters(UserTaskRequestFilter additionalFilters, SearchUserTaskRunRequest.Builder builder) {
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

    private void validateIfUserIsAllowedToSeeUserTask(@NonNull String userId, String userGroup, @NonNull UserTaskRun userTaskRun) {
        if (!StringUtils.hasText(userId)) {
            throw new CustomUnauthorizedException("Unable to read provided user information");
        }

        if (userTaskRun.hasUserGroup()) {
            var hasNoMatchingUserGroup = !userTaskRun.getUserGroup().equalsIgnoreCase(userGroup);
            var hasNoMatchingUserId = userTaskRun.hasUserId() && !userTaskRun.getUserId().equalsIgnoreCase(userId);

            if (hasNoMatchingUserGroup && hasNoMatchingUserId) {
                throw new CustomUnauthorizedException("Current user/userGroup is forbidden from accessing this UserTask information");
            }
        }

        if (userTaskRun.hasUserId()) {
            var hasNoMatchingUserId = !userTaskRun.getUserId().equalsIgnoreCase(userId);

            if (hasNoMatchingUserId) {
                throw new CustomUnauthorizedException("Current user is forbidden from accessing this UserTask information");
            }
        }
    }

    private boolean isUserTaskTerminated(UserTaskRunStatus currentStatus) {
        var blockingStatuses = Set.of(UserTaskRunStatus.DONE, UserTaskRunStatus.CANCELLED);
        return blockingStatuses.contains(currentStatus);
    }
}
