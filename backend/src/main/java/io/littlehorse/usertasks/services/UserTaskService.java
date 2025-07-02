package io.littlehorse.usertasks.services;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.littlehorse.sdk.common.proto.*;
import io.littlehorse.usertasks.exceptions.CustomUnauthorizedException;
import io.littlehorse.usertasks.exceptions.NotFoundException;
import io.littlehorse.usertasks.models.common.UserTaskVariableValue;
import io.littlehorse.usertasks.models.requests.AssignmentRequest;
import io.littlehorse.usertasks.models.requests.CompleteUserTaskRequest;
import io.littlehorse.usertasks.models.requests.StandardPagination;
import io.littlehorse.usertasks.models.requests.UserTaskRequestFilter;
import io.littlehorse.usertasks.models.requests.comment_requests.DeleteCommentRequest;
import io.littlehorse.usertasks.models.requests.comment_requests.EditCommentRequest;
import io.littlehorse.usertasks.models.requests.comment_requests.PutCommentRequest;
import io.littlehorse.usertasks.models.responses.AuditEventDTO;
import io.littlehorse.usertasks.models.responses.DetailedUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.SimpleUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.UserTaskDefListDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import io.littlehorse.usertasks.util.enums.UserTaskFieldType;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

import static io.littlehorse.usertasks.util.DateUtil.isDateRangeValid;

@Service
@Slf4j
public class UserTaskService {
    private final Map<String, LittleHorseGrpc.LittleHorseBlockingStub> lhClients;
    private final Set<UserTaskRunStatus> TERMINAL_STATUSES = Set.of(UserTaskRunStatus.CANCELLED, UserTaskRunStatus.DONE);

    UserTaskService(Map<String, LittleHorseGrpc.LittleHorseBlockingStub> lhClients) {
        this.lhClients = lhClients;
    }

    @NonNull
    public UserTaskRunListDTO getTasks(@NonNull String tenantId, String userId, String userGroup, UserTaskRequestFilter additionalFilters,
                                                 int limit, byte[] bookmark, boolean isAdminRequest) {
        if (!isAdminRequest && !StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("Cannot search UserTask without specifying a proper UserId");
        }

        var pagination = StandardPagination.builder()
                .bookmark(bookmark)
                .limit(limit)
                .build();

        SearchUserTaskRunRequest searchRequest = buildSearchUserTaskRunRequest(userId, userGroup, additionalFilters, pagination);

        LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);

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

        return response;
    }

    public Optional<DetailedUserTaskRunDTO> getUserTaskDetails(@NonNull String wfRunId, @NonNull String userTaskRunGuid,
                                                               @NonNull String tenantId, String userId, String userGroup,
                                                               boolean isAdminRequest) {
        UserTaskRunId getUserTaskRunRequest = buildUserTaskRunId(wfRunId, userTaskRunGuid);

        LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);

        UserTaskRun userTaskRunResult = tenantClient.getUserTaskRun(getUserTaskRunRequest);

        if (!Objects.nonNull(userTaskRunResult)) {
            throw new NotFoundException("Could not find UserTaskRun!");
        }

        if (!isAdminRequest) {
            validateIfUserIsAllowedToSeeUserTask(userId, userGroup, userTaskRunResult);
        }

        UserTaskDef userTaskDefResult = tenantClient.getUserTaskDef(userTaskRunResult.getUserTaskDefId());

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

                LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);

                validateMandatoryStringFields(request, userTaskDetails.get().getUserTaskDefName(), tenantClient);

                CompleteUserTaskRunRequest serverRequest = request.toServerRequest(userId);

                tenantClient.completeUserTaskRun(serverRequest);

                log.atInfo()
                        .setMessage("UserTaskRun with wfRunId: {}, guid: {} was successfully completed")
                        .addArgument(request.getWfRunId())
                        .addArgument(request.getUserTaskRunGuid())
                        .log();
            }
        } catch (StatusRuntimeException e) {
            log.atError()
                    .setMessage("Something went wrong in LH Kernel with completion process for UserTaskRun with " +
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
    public AuditEventDTO comment(PutCommentRequest request, String userId, String tenantId, boolean isAdminRequest){

        PutUserTaskRunCommentRequest serverRequest =  request.toServer(userId);
        try {
            LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);

            UserTaskRun userTaskRun = tenantClient.putUserTaskRunComment(serverRequest);
                List<UserTaskEvent> serverEvents = userTaskRun.getEventsList().stream()
            .filter(event -> event.hasCommentAdded())
        .filter(event -> userId.equals(event.getCommentAdded().getUserId()))
        .toList();

            AuditEventDTO eventDTO = AuditEventDTO.fromUserTaskEvent(serverEvents.getLast());
        
            return eventDTO;
        }
        catch(StatusRuntimeException e){

            if(e.getStatus().getCode() == Status.Code.NOT_FOUND)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");

            if(e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);

            throw e;
        }
        catch(Exception e){
            log.error("Unexpected error during comment creation", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", e);
        }
        
    }

    public AuditEventDTO editComment(EditCommentRequest request, String userId, String tenantId, boolean isAdminRequest){

        EditUserTaskRunCommentRequest serverRequest =  request.toServer(userId);
        try {
            LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);

            UserTaskRun userTaskRun = tenantClient.editUserTaskRunComment(serverRequest);
            UserTaskEvent serverEvent = userTaskRun.getEventsList().getLast();
            AuditEventDTO eventDTO = AuditEventDTO.fromUserTaskEvent(serverEvent);

            return eventDTO;
        } catch (StatusRuntimeException e) {

            if (e.getStatus().getCode() == Status.Code.FAILED_PRECONDITION)
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);

            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);

            if (e.getStatus().getCode() == Status.Code.NOT_FOUND){
                String message = e.getMessage();
                if(message.contains("comment"))
                   throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment does not exits");
                else 
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "UserTask does not exist");
        }
            throw e;
            }catch (Exception e) {
            log.error("Unexpected while editing comment", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", e);
        }
    }

    public AuditEventDTO deleteComment(DeleteCommentRequest request, String userId, String tenantId,
            boolean isAdminRequest) {

        DeleteUserTaskRunCommentRequest serverRequest = request.toServer(userId);
        try {
            LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);
                            
            UserTaskRun userTaskRun = tenantClient.deleteUserTaskRunComment(serverRequest);
            UserTaskEvent serverEvent = userTaskRun.getEventsList().getLast();
            AuditEventDTO eventDTO = AuditEventDTO.fromUserTaskEvent(serverEvent);

            return eventDTO;
        } catch (StatusRuntimeException e) {

            if (e.getStatus().getCode() == Status.Code.FAILED_PRECONDITION)
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);

            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);

            if (e.getStatus().getCode() == Status.Code.NOT_FOUND){
                String message = e.getMessage();
                if(message.contains("comment"))
                   throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment does not exist");
                else
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find userTask");
                }

            throw e;
        } catch (Exception e) {
            log.error("Unexpected error deleting comment", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", e);
        }
    }

    public List<AuditEventDTO> getComment(String wfRunId, String userTaskRunGuid, String tenantId,
            String userIdFromToken) {

        UserTaskRunId getUserTaskRunRequest = buildUserTaskRunId(wfRunId, userTaskRunGuid);

        LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);
        try{
            UserTaskRun userTaskRun = tenantClient.getUserTaskRun(getUserTaskRunRequest);

            HashMap<Integer, AuditEventDTO> commentIdToLatestEvent = new HashMap<>();
            userTaskRun.getEventsList().forEach(event -> {
                if (event.hasCommentAdded())
                    commentIdToLatestEvent.put(event.getCommentAdded().getUserCommentId(),
                            AuditEventDTO.fromUserTaskEvent(event));
                if (event.hasCommentEdited())
                    commentIdToLatestEvent.put(event.getCommentEdited().getUserCommentId(),
                            AuditEventDTO.fromUserTaskEvent(event));
                if (event.hasCommentDeleted())
                    commentIdToLatestEvent.put(event.getCommentDeleted().getUserCommentId(),
                            AuditEventDTO.fromUserTaskEvent(event));
        });
            return new ArrayList<>(commentIdToLatestEvent.values());
        }
        catch(StatusRuntimeException sre){

            if (sre.getStatus().getCode() == Status.Code.FAILED_PRECONDITION)
                throw new ResponseStatusException(HttpStatus.CONFLICT, sre.getMessage(), sre);

            if (sre.getStatus().getCode() == Status.Code.INVALID_ARGUMENT)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, sre.getMessage(), sre);

            if (sre.getStatus().getCode() == Status.Code.NOT_FOUND)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");

            throw sre;
        } catch (Exception e) {
            log.error("Unexpected error fetching comments", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", e);
        }
    }


    public UserTaskDefListDTO getAllUserTasksDef(@NonNull String tenantId, int limit, byte[] bookmark) {
        LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);

        SearchUserTaskDefRequest.Builder searchRequest = SearchUserTaskDefRequest.newBuilder()
                .setLimit(limit);

        if (Objects.nonNull(bookmark)) {
            searchRequest.setBookmark(ByteString.copyFrom(bookmark));
        }

        UserTaskDefIdList searchResults = tenantClient.searchUserTaskDef(searchRequest.build());

        if (searchResults.getResultsList().isEmpty()) {
            return UserTaskDefListDTO.builder().userTaskDefNames(Set.of()).build();
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

            LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);

            UserTaskRunId userTaskRunId = buildUserTaskRunId(wfRunId, userTaskRunGuid);

            AssignUserTaskRunRequest.Builder requestBuilder = AssignUserTaskRunRequest.newBuilder()
                    .setUserTaskRunId(userTaskRunId)
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
                    .addArgument(StringUtils.hasText(requestBody.getUserId())
                            ? requestBody.getUserId()
                            : requestBody.getUserGroup())
                    .log();
        } catch (StatusRuntimeException e) {
            log.atError()
                    .setMessage("Something went wrong in LH Kernel with assignment process for UserTaskRun with " +
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
                    .setMessage("Assignment of UserTaskRun with wfRunId: {} and guid: {} failed.")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();

            throw e;
        }
    }

    public void cancelUserTask(@NonNull String wfRunId, @NonNull String userTaskRunGuid, @NonNull String tenantId) {
        try {
            log.atInfo()
                    .setMessage("Cancelling UserTaskRun with wfRunId: {} and userTaskGuid: {} as Admin.")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();

            LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);

            UserTaskRunId userTaskRunId = UserTaskRunId.newBuilder()
                    .setUserTaskGuid(userTaskRunGuid)
                    .setWfRunId(WfRunId.newBuilder()
                            .setId(wfRunId)
                            .build())
                    .build();

            UserTaskRun userTaskRun = tenantClient.getUserTaskRun(userTaskRunId);

            boolean isAlreadyTerminated;

            if (Objects.nonNull(userTaskRun)) {
                isAlreadyTerminated = isUserTaskTerminated(userTaskRun.getStatus());
            } else {
                throw new NotFoundException("Could not find UserTaskRun!");
            }

            if (isAlreadyTerminated) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "The UserTask you are trying to cancel is already DONE or CANCELLED");
            }

            CancelUserTaskRunRequest requestBuilder = CancelUserTaskRunRequest.newBuilder()
                    .setUserTaskRunId(UserTaskRunId.newBuilder()
                            .setWfRunId(WfRunId.newBuilder()
                                    .setId(wfRunId)
                                    .build())
                            .setUserTaskGuid(userTaskRunGuid)
                            .build())
                    .build();

            tenantClient.cancelUserTaskRun(requestBuilder);

            log.atInfo()
                    .setMessage("UserTaskRun with wfRunId: {} and guid: {} was successfully cancelled as Admin.")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();
        } catch (StatusRuntimeException e) {
            log.atError()
                    .setMessage("Something went wrong in LH Kernel with cancellation process for UserTaskRun with " +
                            "wfRunId: {} and guid: {} as Admin.")
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
                    .setMessage("Cancellation of UserTaskRun with wfRunId: {} and guid: {} as Admin failed.")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();

            throw e;
        }
    }

    public void cancelUserTaskForNonAdmin(@NonNull String wfRunId, @NonNull String userTaskRunGuid, @NonNull String tenantId,
                                          @NonNull String userId) {
        try {
            log.atInfo()
                    .setMessage("Cancelling UserTaskRun with wfRunId: {} and userTaskGuid: {}")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();

            LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);

            UserTaskRunId userTaskRunId = UserTaskRunId.newBuilder()
                    .setUserTaskGuid(userTaskRunGuid)
                    .setWfRunId(WfRunId.newBuilder()
                            .setId(wfRunId)
                            .build())
                    .build();

            UserTaskRun userTaskRun = tenantClient.getUserTaskRun(userTaskRunId);

            boolean isAlreadyTerminated;

            if (Objects.nonNull(userTaskRun)) {
                isAlreadyTerminated = isUserTaskTerminated(userTaskRun.getStatus());
            } else {
                throw new NotFoundException("Could not find UserTaskRun!");
            }

            if (isAlreadyTerminated) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "The UserTask you are trying to cancel is already DONE or CANCELLED");
            }

            validateIfUserIsAllowedToSeeUserTask(userId, null, userTaskRun);

            CancelUserTaskRunRequest requestBuilder = CancelUserTaskRunRequest.newBuilder()
                    .setUserTaskRunId(UserTaskRunId.newBuilder()
                            .setWfRunId(WfRunId.newBuilder()
                                    .setId(wfRunId)
                                    .build())
                            .setUserTaskGuid(userTaskRunGuid)
                            .build())
                    .build();

            tenantClient.cancelUserTaskRun(requestBuilder);

            log.atInfo()
                    .setMessage("UserTaskRun with wfRunId: {} and guid: {} was successfully cancelled.")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();
        } catch (StatusRuntimeException e) {
            log.atError()
                    .setMessage("Something went wrong in LH Kernel with cancellation process for UserTaskRun with " +
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
        } catch (CustomUnauthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (Exception e) {
            log.atError()
                    .setMessage("Cancellation of UserTaskRun with wfRunId: {} and guid: {} failed")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();

            throw e;
        }
    }

    public void claimUserTask(@NonNull String userId, @Nullable Set<String> userGroups, @NonNull String wfRunId, @NonNull String userTaskRunGuid,
                              @NonNull String tenantId, boolean isAdminClaim) {
        try {
            log.atInfo()
                    .setMessage("Claiming UserTaskRun with wfRunId: {} and userTaskGuid: {}")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();

            LittleHorseGrpc.LittleHorseBlockingStub tenantClient = getTenantLHClient(tenantId);

            UserTaskRunId userTaskRunId = buildUserTaskRunId(wfRunId, userTaskRunGuid);

            UserTaskRun userTaskRun = tenantClient.getUserTaskRun(userTaskRunId);

            boolean isUserTaskClaimable = isUserTaskClaimable(isAdminClaim, userTaskRun, userGroups);

            if (isUserTaskClaimable) {
                AssignUserTaskRunRequest requestBuilder = AssignUserTaskRunRequest.newBuilder()
                        .setUserId(userId)
                        .setUserTaskRunId(userTaskRunId)
                        .build();

                if (StringUtils.hasText(userTaskRun.getUserGroup())) {
                    requestBuilder = requestBuilder.toBuilder()
                            .setUserGroup(userTaskRun.getUserGroup())
                            .build();
                }

                if (isAdminClaim) {
                    requestBuilder = requestBuilder.toBuilder()
                            .setOverrideClaim(true)
                            .build();
                }

                tenantClient.assignUserTaskRun(requestBuilder);

                log.atInfo()
                        .setMessage("UserTaskRun with wfRunId: {} and guid: {} was successfully claimed.")
                        .addArgument(wfRunId)
                        .addArgument(userTaskRunGuid)
                        .log();
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "UserTaskRun cannot be claimed!");
            }
        } catch (StatusRuntimeException e) {
            log.atError()
                    .setMessage("Something went wrong in LH Kernel with claiming process for UserTaskRun with " +
                            "wfRunId: {} and guid: {} ")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }

            if (e.getStatus().getCode() == Status.Code.FAILED_PRECONDITION) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
            }

            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new NotFoundException("Could not find UserTaskRun!");
            }

            throw e;
        } catch (Exception e) {
            log.atError()
                    .setMessage("Claim of UserTaskRun with wfRunId: {} and guid: {} failed.")
                    .addArgument(wfRunId)
                    .addArgument(userTaskRunGuid)
                    .log();

            throw e;
        }
    }

    private LittleHorseGrpc.LittleHorseBlockingStub getTenantLHClient(String tenantId) {
        Optional<LittleHorseGrpc.LittleHorseBlockingStub> optionalTenantClient = Optional.ofNullable(lhClients.get(tenantId));

        return optionalTenantClient.orElseThrow(() -> new SecurityException("Could not find a matching configured tenant"));
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

            //This enforces that given the UNASSIGNED status and a userGroup, no userId is required
            if (builder.getStatus() == UserTaskRunStatus.UNASSIGNED && Objects.nonNull(additionalFilters.getStatus())
                    && builder.hasUserGroup()) {
                builder.clearUserId();
            }
        }
    }

    private void validateIfUserIsAllowedToSeeUserTask(String userId, String userGroup, @NonNull UserTaskRun userTaskRun) {
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

    private void validateMandatoryStringFields(CompleteUserTaskRequest request, String userTaskDefName, LittleHorseGrpc.LittleHorseBlockingStub tenantClient) {
        UserTaskDefId userTaskDefId = UserTaskDefId.newBuilder()
                .setName(userTaskDefName)
                .build();

        UserTaskDef userTaskDef = tenantClient.getUserTaskDef(userTaskDefId);
        List<String> mandatoryStringFieldsNames = getMandatoryStringFieldsNames(userTaskDef);

        if (!CollectionUtils.isEmpty(mandatoryStringFieldsNames)) {
            Map<String, UserTaskVariableValue> onlyStringVariableValues = getStringVariableValues(request);

            if (!CollectionUtils.isEmpty(onlyStringVariableValues)) {
                boolean stringFieldsHaveValidInputs = haveStringFieldsValidInputs(onlyStringVariableValues, mandatoryStringFieldsNames);

                if (!stringFieldsHaveValidInputs) {
                    String joinedFieldNames = String.join(", ", mandatoryStringFieldsNames);
                    String errorMessage = String.format("Mandatory Field(s): %s contain invalid inputs (null or whitespace-only values)", joinedFieldNames);

                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
                }
            }
        }
    }

    private boolean isUserTaskTerminated(UserTaskRunStatus currentStatus) {
        return TERMINAL_STATUSES.contains(currentStatus);
    }

    private UserTaskRunId buildUserTaskRunId(String wfRunId, String userTaskRunGuid) {
        return UserTaskRunId.newBuilder()
                .setUserTaskGuid(userTaskRunGuid)
                .setWfRunId(WfRunId.newBuilder()
                        .setId(wfRunId)
                        .build())
                .build();
    }

    private boolean isUserTaskClaimable(boolean isAdminClaim, UserTaskRun userTaskRun, Set<String> userGroups) {
        if (isAdminClaim) {
            return !isUserTaskTerminated(userTaskRun.getStatus());
        } else {
            return isClaimableAsNonAdminUser(userTaskRun, userGroups);
        }
    }

    private boolean isClaimableAsNonAdminUser(UserTaskRun userTaskRun, Set<String> userGroups) {
        return userTaskRun.getStatus().equals(UserTaskRunStatus.UNASSIGNED)
                && !CollectionUtils.isEmpty(userGroups)
                && userGroups.contains(userTaskRun.getUserGroup().trim());
    }

    private List<String> getMandatoryStringFieldsNames(UserTaskDef userTaskDef) {
        return userTaskDef.getFieldsList().stream()
                .filter(userTaskField -> userTaskField.getType().equals(VariableType.STR) && userTaskField.getRequired())
                .map(UserTaskField::getName)
                .toList();
    }

    private Map<String, UserTaskVariableValue> getStringVariableValues(CompleteUserTaskRequest request) {
        return request.getResults().entrySet().stream()
                .filter(entry -> entry.getValue().getType().equals(UserTaskFieldType.STRING))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean haveStringFieldsValidInputs(Map<String, UserTaskVariableValue> stringVariableValues,
                                                List<String> mandatoryStringFieldsNames) {
        return stringVariableValues.entrySet().stream()
                .filter(entry -> mandatoryStringFieldsNames.contains(entry.getKey()))
                .allMatch(entry -> StringUtils.hasText((String) entry.getValue().getValue()));
    }

 
}
