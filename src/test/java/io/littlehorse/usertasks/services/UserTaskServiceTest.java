package io.littlehorse.usertasks.services;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.littlehorse.sdk.common.proto.AssignUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.CancelUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.CompleteUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.SearchUserTaskDefRequest;
import io.littlehorse.sdk.common.proto.SearchUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.UserTaskDef;
import io.littlehorse.sdk.common.proto.UserTaskDefId;
import io.littlehorse.sdk.common.proto.UserTaskDefIdList;
import io.littlehorse.sdk.common.proto.UserTaskEvent;
import io.littlehorse.sdk.common.proto.UserTaskField;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.sdk.common.proto.UserTaskRunIdList;
import io.littlehorse.sdk.common.proto.UserTaskRunStatus;
import io.littlehorse.sdk.common.proto.VariableType;
import io.littlehorse.sdk.common.proto.VariableValue;
import io.littlehorse.sdk.common.proto.WfRunId;
import io.littlehorse.usertasks.exceptions.CustomUnauthorizedException;
import io.littlehorse.usertasks.exceptions.NotFoundException;
import io.littlehorse.usertasks.models.common.UserTaskVariableValue;
import io.littlehorse.usertasks.models.requests.AssignmentRequest;
import io.littlehorse.usertasks.models.requests.CompleteUserTaskRequest;
import io.littlehorse.usertasks.models.requests.UserTaskRequestFilter;
import io.littlehorse.usertasks.models.responses.SimpleUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.UserTaskDefListDTO;
import io.littlehorse.usertasks.models.responses.UserTaskFieldDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import io.littlehorse.usertasks.util.DateUtil;
import io.littlehorse.usertasks.util.enums.UserTaskFieldType;
import io.littlehorse.usertasks.util.enums.UserTaskStatus;
import org.apache.tomcat.util.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTaskServiceTest {
    private static final int RESULTS_LIMIT = 10;
    private final ZoneOffset UTC_ZONE = ZoneOffset.UTC;
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient = mock();
    private final LittleHorseGrpc.LittleHorseBlockingStub lhTenantClient = mock();
    private final String tenantId = "my-tenant-id";

    private final UserTaskService userTaskService = new UserTaskService(lhClient);

    @BeforeEach
    void init() {
        when(lhClient.withCallCredentials(any())).thenReturn(lhTenantClient);
    }

    @Test
    void getTasks_shouldThrowNullPointerExceptionWhenTenantIdParamIsNull() {
        assertThrows(NullPointerException.class,
                () -> userTaskService.getTasks(null, null, null, null, 1, null, false));
    }

    @Test
    void getTasks_shouldThrowIllegalArgumentExceptionWhenUserIsNotAdminAndUserIdIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userTaskService.getTasks(tenantId, null, null, null, 1, null, false));

        var expectedExceptionMessage = "Cannot search UserTask without specifying a proper UserId";

        assertEquals(expectedExceptionMessage, exception.getMessage());
    }

    @Test
    void getTasks_shouldReturnEmptyOptionalWhenNoTasksAreFoundForAGivenUser() {
        var userId = UUID.randomUUID().toString();
        var listOfUserTasks = UserTaskRunIdList.newBuilder().build();

        when(lhTenantClient.searchUserTaskRun(any(SearchUserTaskRunRequest.class))).thenReturn(listOfUserTasks);

        assertTrue(userTaskService.getTasks(tenantId, userId, null, null,
                RESULTS_LIMIT, null, false).isEmpty());

        verify(lhTenantClient).searchUserTaskRun(any(SearchUserTaskRunRequest.class));
    }

    @Test
    void getTasks_shouldReturnEmptyOptionalWhenNoTasksAreFoundForAGivenAdminUser() {
        var userId = UUID.randomUUID().toString();
        var listOfUserTasks = UserTaskRunIdList.newBuilder().build();

        when(lhTenantClient.searchUserTaskRun(any(SearchUserTaskRunRequest.class))).thenReturn(listOfUserTasks);

        assertTrue(userTaskService.getTasks(tenantId, userId, null, null,
                RESULTS_LIMIT, null, true).isEmpty());

        verify(lhTenantClient).searchUserTaskRun(any(SearchUserTaskRunRequest.class));
    }

    @Test
    void getTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUser() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskRun2 = buildFakeUserTaskRun(userId, wfRunId);

        when(lhTenantClient.searchUserTaskRun(any(SearchUserTaskRunRequest.class))).thenReturn(listOfUserTasks);
        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getTasks(tenantId, userId, null, null,
                RESULTS_LIMIT, null, false);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));

        verify(lhTenantClient).searchUserTaskRun(any(SearchUserTaskRunRequest.class));
        verify(lhTenantClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenAdminUser() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskRun2 = buildFakeUserTaskRun(userId, wfRunId);

        when(lhTenantClient.searchUserTaskRun(any(SearchUserTaskRunRequest.class))).thenReturn(listOfUserTasks);
        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getTasks(tenantId, userId, null, null,
                RESULTS_LIMIT, null, true);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));

        verify(lhTenantClient).searchUserTaskRun(any(SearchUserTaskRunRequest.class));
        verify(lhTenantClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndUserGroup() {
        var userId = UUID.randomUUID().toString();
        var myUserGroup = "the_jedi_order";
        var wfRunId = UUID.randomUUID().toString();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRunWithUserGroup(userId, wfRunId, myUserGroup);

        when(lhTenantClient.searchUserTaskRun(any(SearchUserTaskRunRequest.class))).thenReturn(listOfUserTasks);
        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1);

        Optional<UserTaskRunListDTO> result = userTaskService.getTasks(tenantId, userId, myUserGroup, null,
                RESULTS_LIMIT, null, false);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasUserGroup(myUserGroup)));

        verify(lhTenantClient).searchUserTaskRun(any(SearchUserTaskRunRequest.class));
        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndScheduledTimeIsAfterEarliestStartDate() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();
        var fiveDaysAgo = LocalDateTime.now().minusDays(5);
        var additionalFilters = UserTaskRequestFilter.builder()
                .earliestStartDate(buildTimestamp(fiveDaysAgo))
                .build();
        var searchRequest = SearchUserTaskRunRequest.newBuilder()
                .setLimit(RESULTS_LIMIT)
                .setUserId(userId)
                .setEarliestStart(additionalFilters.getEarliestStartDate())
                .build();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, fiveDaysAgo.plusHours(1L));
        var userTaskRun2 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, fiveDaysAgo.plusDays(5L));

        when(lhTenantClient.searchUserTaskRun(searchRequest)).thenReturn(listOfUserTasks);
        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getTasks(tenantId, userId, null, additionalFilters,
                RESULTS_LIMIT, null, false);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasScheduledTimeAfterEarliestStart(fiveDaysAgo)));

        verify(lhTenantClient).searchUserTaskRun(searchRequest);
        verify(lhTenantClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndScheduledTimeIsBeforeLatestStartDate() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();
        var currentDate = LocalDateTime.now();
        var additionalFilters = UserTaskRequestFilter.builder()
                .latestStartDate(buildTimestamp(currentDate))
                .build();
        var searchRequest = SearchUserTaskRunRequest.newBuilder()
                .setLimit(RESULTS_LIMIT)
                .setUserId(userId)
                .setLatestStart(additionalFilters.getLatestStartDate())
                .build();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, currentDate.minusHours(1L));
        var userTaskRun2 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, currentDate.minusDays(5L));

        when(lhTenantClient.searchUserTaskRun(searchRequest)).thenReturn(listOfUserTasks);
        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getTasks(tenantId, userId, null, additionalFilters,
                RESULTS_LIMIT, null, false);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasScheduledTimeBeforeLatestStart(currentDate)));

        verify(lhTenantClient).searchUserTaskRun(searchRequest);
        verify(lhTenantClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndScheduledTimeIsBetweenEarliestAndLatestStartDate() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();
        var currentDate = LocalDateTime.now();
        var lastTenDays = currentDate.minusDays(10L);
        var additionalFilters = UserTaskRequestFilter.builder()
                .earliestStartDate(buildTimestamp(lastTenDays))
                .latestStartDate(buildTimestamp(currentDate))
                .build();
        var searchRequest = SearchUserTaskRunRequest.newBuilder()
                .setLimit(RESULTS_LIMIT)
                .setUserId(userId)
                .setEarliestStart(additionalFilters.getEarliestStartDate())
                .setLatestStart(additionalFilters.getLatestStartDate())
                .build();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId),
                buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, currentDate.minusHours(1L));
        var userTaskRun2 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, currentDate.minusDays(5L));
        var userTaskRun3 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, currentDate.minusDays(1L));

        when(lhTenantClient.searchUserTaskRun(searchRequest)).thenReturn(listOfUserTasks);
        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2, userTaskRun3);

        Optional<UserTaskRunListDTO> result = userTaskService.getTasks(tenantId, userId, null, additionalFilters,
                RESULTS_LIMIT, null, false);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasScheduledTimeAfterEarliestStart(lastTenDays)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasScheduledTimeBeforeLatestStart(currentDate)));

        verify(lhTenantClient).searchUserTaskRun(searchRequest);
        verify(lhTenantClient, times(3)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getTasks_shouldThrowExceptionWhenEarliestStartDateIsNotBeforeLatestStartDate() {
        var userId = UUID.randomUUID().toString();
        var latestStartDate = LocalDateTime.now().minusDays(1L);
        var earliestStartDate = latestStartDate.plusMinutes(1L);
        var additionalFilters = UserTaskRequestFilter.builder()
                .earliestStartDate(buildTimestamp(earliestStartDate))
                .latestStartDate(buildTimestamp(latestStartDate))
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userTaskService.getTasks(tenantId, userId, null, additionalFilters, RESULTS_LIMIT, null, false));

        var expectedExceptionMessage = "Wrong date range received";

        assertEquals(expectedExceptionMessage, exception.getMessage());

        verify(lhClient, never()).searchUserTaskRun(any());
        verify(lhClient, never()).getUserTaskRun(any());
    }

    @Test
    void getTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndStatus() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();
        var additionalFilters = UserTaskRequestFilter.builder()
                .status(UserTaskStatus.ASSIGNED)
                .build();
        var searchRequest = SearchUserTaskRunRequest.newBuilder()
                .setLimit(RESULTS_LIMIT)
                .setUserId(userId)
                .setStatus(UserTaskRunStatus.ASSIGNED)
                .build();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskRun2 = buildFakeUserTaskRun(userId, wfRunId);

        when(lhTenantClient.searchUserTaskRun(searchRequest)).thenReturn(listOfUserTasks);
        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getTasks(tenantId, userId, null, additionalFilters,
                RESULTS_LIMIT, null, false);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));

        verify(lhTenantClient).searchUserTaskRun(searchRequest);
        verify(lhTenantClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndTaskDefName() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();
        var type = "my-custom-task-def";
        var additionalFilters = UserTaskRequestFilter.builder()
                .type(type)
                .build();
        var searchRequest = SearchUserTaskRunRequest.newBuilder()
                .setLimit(RESULTS_LIMIT)
                .setUserId(userId)
                .setUserTaskDefName(type)
                .build();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRunWithCustomTaskDefName(userId, wfRunId, type);
        var userTaskRun2 = buildFakeUserTaskRunWithCustomTaskDefName(userId, wfRunId, type);

        when(lhTenantClient.searchUserTaskRun(searchRequest)).thenReturn(listOfUserTasks);
        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getTasks(tenantId, userId, null, additionalFilters,
                RESULTS_LIMIT, null, false);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(dto -> dto.getUserTaskDefName().equalsIgnoreCase(type)));

        verify(lhTenantClient).searchUserTaskRun(searchRequest);
        verify(lhTenantClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndTaskDefNameAndStatusAndDateRange() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();
        var type = "my-custom-task-def";
        var earliestDate = LocalDateTime.now().minusDays(5L);
        var latestDate = earliestDate.plusDays(5L);
        var additionalFilters = UserTaskRequestFilter.builder()
                .type(type)
                .status(UserTaskStatus.ASSIGNED)
                .earliestStartDate(DateUtil.localDateTimeToTimestamp(earliestDate))
                .latestStartDate(DateUtil.localDateTimeToTimestamp(latestDate))
                .build();
        var searchRequest = SearchUserTaskRunRequest.newBuilder()
                .setLimit(RESULTS_LIMIT)
                .setUserId(userId)
                .setUserTaskDefName(type)
                .setStatus(UserTaskRunStatus.ASSIGNED)
                .setEarliestStart(DateUtil.localDateTimeToTimestamp(earliestDate))
                .setLatestStart(DateUtil.localDateTimeToTimestamp(latestDate))
                .build();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRunWithCustomTaskDefNameAndCustomDateRange(userId, wfRunId, type, earliestDate.plusHours(4L));
        var userTaskRun2 = buildFakeUserTaskRunWithCustomTaskDefNameAndCustomDateRange(userId, wfRunId, type, latestDate.minusHours(4L));

        when(lhTenantClient.searchUserTaskRun(searchRequest)).thenReturn(listOfUserTasks);
        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getTasks(tenantId, userId, null, additionalFilters,
                RESULTS_LIMIT, null, false);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(dto -> dto.getUserTaskDefName().equalsIgnoreCase(type)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasScheduledTimeAfterEarliestStart(earliestDate)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasScheduledTimeBeforeLatestStart(latestDate)));

        verify(lhTenantClient).searchUserTaskRun(searchRequest);
        verify(lhTenantClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getUserTaskDetails_shouldThrowNullPointerExceptionWhenWfRunIdParamIsNull() {
        var nonExistingUserTaskGuid = buildStringGuid();

        assertThrows(NullPointerException.class,
                () -> userTaskService.getUserTaskDetails(null, nonExistingUserTaskGuid, tenantId, "user-id", null, false));

        verify(lhTenantClient, never()).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldThrowNullPointerExceptionWhenUserTaskGuidParamIsNull() {
        var existingWfRunId = "some-existing-wf-run-id";

        assertThrows(NullPointerException.class,
                () -> userTaskService.getUserTaskDetails(existingWfRunId, null, tenantId, "user-id",
                        null, false));

        verify(lhTenantClient, never()).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldThrowNullPointerExceptionWhenTenantIdParamIsNull() {
        var existingWfRunId = "some-existing-wf-run-id";
        var existingUserTaskGuid = buildStringGuid();

        assertThrows(NullPointerException.class,
                () -> userTaskService.getUserTaskDetails(existingWfRunId, existingUserTaskGuid, null, "user-id",
                        null, false));

        verify(lhTenantClient, never()).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldThrowNotFoundExceptionWhenNoUserTaskRunIsFoundGivenAWfRunIdAndUserTaskRunGuid() {
        var nonExistingWfRunId = "some-fake-wf-run-id";
        var nonExistingUserTaskGuid = buildStringGuid();
        var expectedExceptionMessage = "Could not find UserTaskRun!";

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userTaskService.getUserTaskDetails(nonExistingWfRunId, nonExistingUserTaskGuid, tenantId, "user-id", null, false));

        assertEquals(expectedExceptionMessage, exception.getMessage());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldThrowNotFoundExceptionWhenNoUserTaskDefIsFoundForGivenUserTaskDefId() {
        var userId = UUID.randomUUID().toString();
        var existingWfRunId = "some-existing-wf-run-id";
        var existingUserTaskGuid = buildStringGuid();
        var expectedExceptionMessage = "Could not find associated UserTaskDef!";

        var foundUserTaskRun = buildFakeUserTaskRun(userId, existingWfRunId);

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(foundUserTaskRun);

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userTaskService.getUserTaskDetails(existingWfRunId, existingUserTaskGuid, tenantId, userId, null, false));

        assertEquals(expectedExceptionMessage, exception.getMessage());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldThrowCustomUnauthorizedExceptionWhenUserIdIsBlankSpaceOnly() {
        var userId = " ";
        var existingWfRunId = "some-existing-wf-run-id";
        var existingUserTaskGuid = buildStringGuid();
        var expectedExceptionMessage = "Unable to read provided user information";

        var foundUserTaskRun = buildFakeUserTaskRun(UUID.randomUUID().toString(), existingWfRunId);

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(foundUserTaskRun);

        CustomUnauthorizedException exception = assertThrows(CustomUnauthorizedException.class,
                () -> userTaskService.getUserTaskDetails(existingWfRunId, existingUserTaskGuid, tenantId, userId, null, false));

        assertEquals(expectedExceptionMessage, exception.getMessage());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldThrowCustomUnauthorizedExceptionWhenUserGroupAndUserIdAreNotRelatedToFoundUserTaskRun() {
        var userId = UUID.randomUUID().toString();
        var existingWfRunId = "some-existing-wf-run-id";
        var existingUserTaskGuid = buildStringGuid();
        var expectedExceptionMessage = "Current user/userGroup is forbidden from accessing this UserTask information";

        var foundUserTaskRun = buildFakeUserTaskRun(userId, existingWfRunId).toBuilder()
                .setUserId("some-different-user-id")
                .setUserGroup("some-pretty-cool-user-group")
                .build();

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(foundUserTaskRun);

        CustomUnauthorizedException exception = assertThrows(CustomUnauthorizedException.class,
                () -> userTaskService.getUserTaskDetails(existingWfRunId, existingUserTaskGuid, tenantId, userId, null, false));

        assertEquals(expectedExceptionMessage, exception.getMessage());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldThrowCustomUnauthorizedExceptionWhenUserIdIsNotAssignedToFoundUserTaskRun() {
        var userId = UUID.randomUUID().toString();
        var existingWfRunId = "some-existing-wf-run-id";
        var existingUserTaskGuid = buildStringGuid();
        var expectedExceptionMessage = "Current user is forbidden from accessing this UserTask information";

        var foundUserTaskRun = buildFakeUserTaskRun(userId, existingWfRunId).toBuilder()
                .setUserId("some-different-user-id")
                .build();

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(foundUserTaskRun);

        CustomUnauthorizedException exception = assertThrows(CustomUnauthorizedException.class,
                () -> userTaskService.getUserTaskDetails(existingWfRunId, existingUserTaskGuid, tenantId, userId, null, false));

        assertEquals(expectedExceptionMessage, exception.getMessage());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldReturnDetailedUserTaskRunDTOWhenFoundForGivenWfRunIdAndUserTaskRunGuid() {
        var userId = UUID.randomUUID().toString();
        var existingWfRunId = "some-existing-wf-run-id";
        var existingUserTaskGuid = buildStringGuid();

        var foundUserTaskRun = buildFakeUserTaskRun(userId, existingWfRunId)
                .toBuilder()
                .setId(UserTaskRunId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder()
                                .setId(existingWfRunId)
                                .build())
                        .setUserTaskGuid(existingUserTaskGuid)
                        .build())
                .build();
        var foundUserTaskDef = buildFakeUserTaskDef(foundUserTaskRun.getUserTaskDefId().getName());

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(foundUserTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(foundUserTaskDef);

        var result = userTaskService.getUserTaskDetails(existingWfRunId, existingUserTaskGuid, tenantId, userId, null, false);

        assertTrue(result.isPresent());

        var foundUserTaskRunDTO = result.get();

        assertEquals(existingWfRunId, foundUserTaskRunDTO.getWfRunId());
        assertEquals(existingUserTaskGuid, foundUserTaskRunDTO.getId());
        assertFalse(foundUserTaskRunDTO.getFields().isEmpty());
        assertTrue(foundUserTaskRunDTO.getFields().stream().allMatch(hasMandatoryFieldsForUserTaskField()));
        assertNull(foundUserTaskRunDTO.getEvents());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldReturnDetailedUserTaskRunDTOWhenFoundForGivenWfRunIdAndUserTaskRunGuidAsAnAdminUser() {
        var userId = UUID.randomUUID().toString();
        var existingWfRunId = "some-existing-wf-run-id";
        var existingUserTaskGuid = buildStringGuid();

        var foundUserTaskRun = buildFakeUserTaskRun(userId, existingWfRunId)
                .toBuilder()
                .setId(UserTaskRunId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder()
                                .setId(existingWfRunId)
                                .build())
                        .setUserTaskGuid(existingUserTaskGuid)
                        .build())
                .addEvents(UserTaskEvent.newBuilder()
                        .setAssigned(UserTaskEvent.UTEAssigned.newBuilder()
                                .setNewUserId(userId)
                                .build())
                        .setTime(DateUtil.localDateTimeToTimestamp(LocalDateTime.now().minusHours(1L)))
                        .build())
                .build();
        var foundUserTaskDef = buildFakeUserTaskDef(foundUserTaskRun.getUserTaskDefId().getName());

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(foundUserTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(foundUserTaskDef);

        var result = userTaskService.getUserTaskDetails(existingWfRunId, existingUserTaskGuid, tenantId, userId, null, true);

        assertTrue(result.isPresent());

        var foundUserTaskRunDTO = result.get();

        assertEquals(existingWfRunId, foundUserTaskRunDTO.getWfRunId());
        assertEquals(existingUserTaskGuid, foundUserTaskRunDTO.getId());
        assertFalse(foundUserTaskRunDTO.getFields().isEmpty());
        assertTrue(foundUserTaskRunDTO.getFields().stream().allMatch(hasMandatoryFieldsForUserTaskField()));
        assertFalse(foundUserTaskRunDTO.getEvents().isEmpty());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldReturnDetailedUserTaskRunDTOWhenFoundForGivenWfRunIdAndUserTaskRunGuidAfterCompletion() {
        var userId = UUID.randomUUID().toString();
        var existingWfRunId = "some-existing-wf-run-id";
        var existingUserTaskGuid = buildStringGuid();

        var foundUserTaskRun = buildFakeUserTaskRun(userId, existingWfRunId)
                .toBuilder()
                .setId(UserTaskRunId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder()
                                .setId(existingWfRunId)
                                .build())
                        .setUserTaskGuid(existingUserTaskGuid)
                        .build())
                .setStatus(UserTaskRunStatus.DONE)
                .build();
        var foundUserTaskDef = buildFakeUserTaskDef(foundUserTaskRun.getUserTaskDefId().getName());
        var expectedResultsKeys = Set.of("Requested by", "Request", "Approved");
        Map<String, VariableValue> expectedResultValues = Map.of(
                "Requested by", VariableValue.newBuilder().setStr("Pedro").build(),
                "Request", VariableValue.newBuilder().setStr("Something Pedro's needing").build(),
                "Approved", VariableValue.newBuilder().setBool(true).build()
        );

        foundUserTaskRun = foundUserTaskRun.toBuilder()
                .putAllResults(expectedResultValues)
                .build();

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(foundUserTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(foundUserTaskDef);

        var result = userTaskService.getUserTaskDetails(existingWfRunId, existingUserTaskGuid, tenantId, userId, null, false);

        assertTrue(result.isPresent());

        var foundUserTaskRunDTO = result.get();

        assertEquals(existingWfRunId, foundUserTaskRunDTO.getWfRunId());
        assertEquals(existingUserTaskGuid, foundUserTaskRunDTO.getId());
        assertFalse(foundUserTaskRunDTO.getFields().isEmpty());
        assertTrue(foundUserTaskRunDTO.getFields().stream().allMatch(hasMandatoryFieldsForUserTaskField()));
        assertNull(foundUserTaskRunDTO.getEvents());
        assertFalse(foundUserTaskRunDTO.getResults().isEmpty());
        assertTrue(foundUserTaskRunDTO.getResults().keySet().containsAll(expectedResultsKeys));

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldReturnDetailedUserTaskRunDTOWhenFoundForGivenWfRunIdAndUserTaskRunGuidWithMatchingUserGroup() {
        var userId = UUID.randomUUID().toString();
        var userGroup = "my-user-group";
        var existingWfRunId = "some-existing-wf-run-id";
        var existingUserTaskGuid = buildStringGuid();

        var foundUserTaskRun = buildFakeUserTaskRun(userId, existingWfRunId)
                .toBuilder()
                .clearUserId()
                .setId(UserTaskRunId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder()
                                .setId(existingWfRunId)
                                .build())
                        .setUserTaskGuid(existingUserTaskGuid)
                        .build())
                .setUserGroup(userGroup)
                .build();
        var foundUserTaskDef = buildFakeUserTaskDef(foundUserTaskRun.getUserTaskDefId().getName());

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(foundUserTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(foundUserTaskDef);

        var result = userTaskService.getUserTaskDetails(existingWfRunId, existingUserTaskGuid, tenantId, userId, userGroup, false);

        assertTrue(result.isPresent());

        var foundUserTaskRunDTO = result.get();

        assertEquals(existingWfRunId, foundUserTaskRunDTO.getWfRunId());
        assertEquals(existingUserTaskGuid, foundUserTaskRunDTO.getId());
        assertFalse(foundUserTaskRunDTO.getFields().isEmpty());
        assertNull(foundUserTaskRunDTO.getEvents());
        assertFalse(StringUtils.hasText(foundUserTaskRunDTO.getUserId()));
        assertTrue(foundUserTaskRunDTO.getFields().stream().allMatch(hasMandatoryFieldsForUserTaskField()));

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void getUserTaskDetails_shouldReturnDetailedUserTaskRunDTOWhenFoundForGivenWfRunIdAndUserTaskRunGuidWithMatchingUserIdButNoMatchingUserGroup() {
        var userId = UUID.randomUUID().toString();
        var setUserGroup = "my-user-group";
        var requestUserGroup = "my-cool-user-group";
        var existingWfRunId = "some-existing-wf-run-id";
        var existingUserTaskGuid = buildStringGuid();

        var foundUserTaskRun = buildFakeUserTaskRun(userId, existingWfRunId)
                .toBuilder()
                .setId(UserTaskRunId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder()
                                .setId(existingWfRunId)
                                .build())
                        .setUserTaskGuid(existingUserTaskGuid)
                        .build())
                .setUserGroup(setUserGroup)
                .build();
        var foundUserTaskDef = buildFakeUserTaskDef(foundUserTaskRun.getUserTaskDefId().getName());

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(foundUserTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(foundUserTaskDef);

        var result = userTaskService.getUserTaskDetails(existingWfRunId, existingUserTaskGuid, tenantId, userId, requestUserGroup, false);

        assertTrue(result.isPresent());

        var foundUserTaskRunDTO = result.get();

        assertEquals(existingWfRunId, foundUserTaskRunDTO.getWfRunId());
        assertEquals(existingUserTaskGuid, foundUserTaskRunDTO.getId());
        assertFalse(foundUserTaskRunDTO.getFields().isEmpty());
        assertNull(foundUserTaskRunDTO.getEvents());
        assertEquals(userId, foundUserTaskRunDTO.getUserId());
        assertTrue(foundUserTaskRunDTO.getFields().stream().allMatch(hasMandatoryFieldsForUserTaskField()));

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
    }

    @Test
    void completeUserTask_shouldThrowNullPointerExceptionWhenRequestParamIsNull() {
        var userId = "my-user-id";
        assertThrows(NullPointerException.class, () -> userTaskService.completeUserTask(userId, null, tenantId, false));

        verify(lhTenantClient, never()).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).getUserTaskDef(any(UserTaskDefId.class));
        verify(lhTenantClient, never()).completeUserTaskRun(any(CompleteUserTaskRunRequest.class));
    }

    @Test
    void completeUserTask_shouldThrowNullPointerExceptionWhenTenantIdParamIsNull() {
        var userId = "my-user-id";
        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();
        var request = CompleteUserTaskRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskRunGuid)
                .results(Map.of(
                                "string-field", UserTaskVariableValue.builder()
                                        .value("some-value")
                                        .type(UserTaskFieldType.STRING)
                                        .build(),
                                "integer-field", UserTaskVariableValue.builder()
                                        .value(1)
                                        .type(UserTaskFieldType.INTEGER)
                                        .build()
                        )
                ).build();

        assertThrows(NullPointerException.class, () -> userTaskService.completeUserTask(userId, request, null, false));

        verify(lhTenantClient, never()).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).getUserTaskDef(any(UserTaskDefId.class));
        verify(lhTenantClient, never()).completeUserTaskRun(any(CompleteUserTaskRunRequest.class));
    }

    @Test
    void completeUserTask_shouldSucceedWhenServerDoesNotThrowAnException() {
        var userId = "my-user-id";
        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();
        var request = CompleteUserTaskRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskRunGuid)
                .results(Map.of(
                                "string-field", UserTaskVariableValue.builder()
                                        .value("some-value")
                                        .type(UserTaskFieldType.STRING)
                                        .build(),
                                "integer-field", UserTaskVariableValue.builder()
                                        .value(1)
                                        .type(UserTaskFieldType.INTEGER)
                                        .build()
                        )
                ).build();

        var userTaskRun = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskDef = buildFakeUserTaskDef(userTaskRun.getUserTaskDefId().getName());

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(userTaskDef);
        when(lhTenantClient.completeUserTaskRun(any(CompleteUserTaskRunRequest.class))).thenReturn(Empty.getDefaultInstance());

        userTaskService.completeUserTask(userId, request, tenantId, false);

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
        verify(lhTenantClient).completeUserTaskRun(any(CompleteUserTaskRunRequest.class));
    }

    @Test
    void completeUserTask_shouldSucceedForAdminUserWhenServerDoesNotThrowAnException() {
        var userId = "my-admin-user-id";
        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();
        var request = CompleteUserTaskRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskRunGuid)
                .results(Map.of(
                                "string-field", UserTaskVariableValue.builder()
                                        .value("some-value")
                                        .type(UserTaskFieldType.STRING)
                                        .build(),
                                "integer-field", UserTaskVariableValue.builder()
                                        .value(1)
                                        .type(UserTaskFieldType.INTEGER)
                                        .build()
                        )
                ).build();

        var userTaskRun = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskDef = buildFakeUserTaskDef(userTaskRun.getUserTaskDefId().getName());

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(userTaskDef);
        when(lhTenantClient.completeUserTaskRun(any(CompleteUserTaskRunRequest.class))).thenReturn(Empty.getDefaultInstance());

        userTaskService.completeUserTask(userId, request, tenantId, true);

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
        verify(lhTenantClient).completeUserTaskRun(any(CompleteUserTaskRunRequest.class));
    }

    @Test
    void completeUserTask_shouldThrowACustomUnauthorizedExceptionWhenUserTaskRunIsNotAssignedToRequestUserId() {
        var userId = "my-user-id";
        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();
        var request = CompleteUserTaskRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskRunGuid)
                .results(Map.of())
                .build();

        var doneUserTaskRun = buildFakeUserTaskRun(userId, wfRunId).toBuilder()
                .setUserId("some-other-user-id")
                .build();
        var userTaskDef = buildFakeUserTaskDef(doneUserTaskRun.getUserTaskDefId().getName());

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(doneUserTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(userTaskDef);
        when(lhTenantClient.completeUserTaskRun(any(CompleteUserTaskRunRequest.class))).thenReturn(Empty.getDefaultInstance());

        CustomUnauthorizedException thrownException = assertThrows(CustomUnauthorizedException.class,
                () -> userTaskService.completeUserTask(userId, request, tenantId, false));

        var expectedErrorMessage = "Current user is forbidden from accessing this UserTask information";

        assertEquals(expectedErrorMessage, thrownException.getMessage());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).getUserTaskDef(any(UserTaskDefId.class));
        verify(lhTenantClient, never()).completeUserTaskRun(any(CompleteUserTaskRunRequest.class));
    }

    @Test
    void completeUserTask_shouldThrowAForbiddenExceptionWhenUserTaskRunHasStatusDone() {
        var userId = "my-user-id";
        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();
        var request = CompleteUserTaskRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskRunGuid)
                .results(Map.of())
                .build();

        var doneUserTaskRun = buildFakeUserTaskRun(userId, wfRunId).toBuilder()
                .setStatus(UserTaskRunStatus.DONE)
                .build();
        var userTaskDef = buildFakeUserTaskDef(doneUserTaskRun.getUserTaskDefId().getName());

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(doneUserTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(userTaskDef);
        when(lhTenantClient.completeUserTaskRun(any(CompleteUserTaskRunRequest.class))).thenReturn(Empty.getDefaultInstance());

        ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                () -> userTaskService.completeUserTask(userId, request, tenantId, false));

        int expectedHttpErrorCode = HttpStatus.FORBIDDEN.value();
        var expectedErrorMessage = "The UserTask you are trying to complete is already DONE or CANCELLED";

        assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
        assertEquals(expectedErrorMessage, thrownException.getReason());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
        verify(lhTenantClient, never()).completeUserTaskRun(any(CompleteUserTaskRunRequest.class));
    }

    @Test
    void completeUserTask_shouldThrowAForbiddenExceptionWhenUserTaskRunHasStatusCancelled() {
        var userId = "my-user-id";
        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();
        var request = CompleteUserTaskRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskRunGuid)
                .results(Map.of())
                .build();

        var cancelledUserTaskRun = buildFakeUserTaskRun(userId, wfRunId).toBuilder()
                .setStatus(UserTaskRunStatus.CANCELLED)
                .build();
        var userTaskDef = buildFakeUserTaskDef(cancelledUserTaskRun.getUserTaskDefId().getName());

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(cancelledUserTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(userTaskDef);
        when(lhTenantClient.completeUserTaskRun(any(CompleteUserTaskRunRequest.class))).thenReturn(Empty.getDefaultInstance());

        ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                () -> userTaskService.completeUserTask(userId, request, tenantId, false));

        int expectedHttpErrorCode = HttpStatus.FORBIDDEN.value();
        var expectedErrorMessage = "The UserTask you are trying to complete is already DONE or CANCELLED";

        assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
        assertEquals(expectedErrorMessage, thrownException.getReason());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
        verify(lhTenantClient, never()).completeUserTaskRun(any(CompleteUserTaskRunRequest.class));
    }

    @Test
    void completeUserTask_shouldThrowABadRequestExceptionWhenServerThrowsExceptionRelatedToAnArgument() {
        var userId = "my-user-id";
        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();
        var request = CompleteUserTaskRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskRunGuid)
                .results(Map.of(
                        "string-field", UserTaskVariableValue.builder()
                                .value("some-value")
                                .type(UserTaskFieldType.STRING)
                                .build(),
                        "integer-field", UserTaskVariableValue.builder()
                                .value(1)
                                .type(UserTaskFieldType.INTEGER)
                                .build(),
                        "not-defined-field", UserTaskVariableValue.builder()
                                .value(true)
                                .type(UserTaskFieldType.BOOLEAN)
                                .build()
                ))
                .build();

        var userTaskRun = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskDef = buildFakeUserTaskDef(userTaskRun.getUserTaskDefId().getName());

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(userTaskDef);
        when(lhTenantClient.completeUserTaskRun(any(CompleteUserTaskRunRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.INVALID_ARGUMENT));

        ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                () -> userTaskService.completeUserTask(userId, request, tenantId, false));

        int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();

        assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
        assertEquals("INVALID_ARGUMENT", thrownException.getReason());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
        verify(lhTenantClient).completeUserTaskRun(any(CompleteUserTaskRunRequest.class));
    }

    @Test
    void completeUserTask_shouldThrowABadRequestExceptionWhenServerThrowsUnhandledException() {
        var userId = "my-user-id";
        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();
        var request = CompleteUserTaskRequest.builder()
                .wfRunId(wfRunId)
                .userTaskRunGuid(userTaskRunGuid)
                .results(Map.of(
                        "string-field", UserTaskVariableValue.builder()
                                .value("some-value")
                                .type(UserTaskFieldType.STRING)
                                .build(),
                        "integer-field", UserTaskVariableValue.builder()
                                .value(1)
                                .type(UserTaskFieldType.INTEGER)
                                .build(),
                        "not-defined-field", UserTaskVariableValue.builder()
                                .value(true)
                                .type(UserTaskFieldType.BOOLEAN)
                                .build()
                ))
                .build();

        var userTaskRun = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskDef = buildFakeUserTaskDef(userTaskRun.getUserTaskDefId().getName());

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun);
        when(lhTenantClient.getUserTaskDef(any(UserTaskDefId.class))).thenReturn(userTaskDef);
        when(lhTenantClient.completeUserTaskRun(any(CompleteUserTaskRunRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.ABORTED));

        assertThrows(StatusRuntimeException.class,
                () -> userTaskService.completeUserTask(userId, request, tenantId, false));

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).getUserTaskDef(any(UserTaskDefId.class));
        verify(lhTenantClient).completeUserTaskRun(any(CompleteUserTaskRunRequest.class));
    }

    @Test
    void getUserTasksDef_shouldThrowNotFoundExceptionIfNoUserTaskDefIsFound() {
        var tenantId = "some-tenant-id";
        var requestLimit = 10;
        UserTaskDefIdList userTaskDefIdList = UserTaskDefIdList.newBuilder()
                .addAllResults(Collections.emptyList())
                .build();
        var expectedException = "No UserTaskDefs were found for given tenant";

        when(lhTenantClient.searchUserTaskDef(any(SearchUserTaskDefRequest.class))).thenReturn(userTaskDefIdList);

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userTaskService.getAllUserTasksDef(tenantId, requestLimit, null));

        assertEquals(expectedException, exception.getMessage());

        verify(lhTenantClient).searchUserTaskDef(any(SearchUserTaskDefRequest.class));
    }

    @Test
    void getUserTasksDef_shouldThrowNullPointerExceptionIfNoTenantIdParamIsPassedIn() {
        var requestLimit = 10;

        assertThrows(NullPointerException.class, () -> userTaskService.getAllUserTasksDef(null, requestLimit, null));

        verify(lhTenantClient, never()).searchUserTaskDef(any(SearchUserTaskDefRequest.class));
    }

    @Test
    void getUserTasksDef_shouldReturnSetOfUserTaskDefNamesIfFound() {
        var tenantId = "some-tenant-id";
        var requestLimit = 10;
        var expectedQuantityOfUserTaskDefs = 3;


        UserTaskDefId userTaskDefId1 = UserTaskDefId.newBuilder()
                .setName("userTaskDefId1")
                .build();
        UserTaskDefId userTaskDefId2 = UserTaskDefId.newBuilder()
                .setName("userTaskDefId2")
                .build();
        UserTaskDefId userTaskDefId3 = UserTaskDefId.newBuilder()
                .setName("userTaskDefId3")
                .build();

        UserTaskDefIdList userTaskDefIdList = UserTaskDefIdList.newBuilder()
                .addAllResults(List.of(userTaskDefId1, userTaskDefId2, userTaskDefId3))
                .build();

        when(lhTenantClient.searchUserTaskDef(any(SearchUserTaskDefRequest.class))).thenReturn(userTaskDefIdList);

        UserTaskDefListDTO result = userTaskService.getAllUserTasksDef(tenantId, requestLimit, null);

        assertFalse(result.getUserTaskDefNames().isEmpty());
        assertEquals(expectedQuantityOfUserTaskDefs, result.getUserTaskDefNames().size());

        verify(lhTenantClient).searchUserTaskDef(any(SearchUserTaskDefRequest.class));
    }

    @Test
    void getUserTasksDef_shouldReturnSetOfUserTaskDefNamesIfFoundWithBookmark() {
        var tenantId = "some-tenant-id";
        var requestLimit = 10;
        var expectedQuantityOfUserTaskDefs = 1;
        byte[] requestBookmark = Base64.decodeBase64("ChkIABIVEhMxMC9pdC1yZXF1ZXN0LzAwMDAw");
        byte[] responseBookmark = Base64.decodeBase64("GQjvv70SFRITMTAvaXQtcmVxdWVzdDQvMDAwMDA=");


        UserTaskDefId userTaskDefId1 = UserTaskDefId.newBuilder()
                .setName("userTaskDefId1")
                .build();

        UserTaskDefIdList searchResult = UserTaskDefIdList.newBuilder()
                .addAllResults(List.of(userTaskDefId1))
                .setBookmark(ByteString.copyFrom(responseBookmark))
                .build();

        SearchUserTaskDefRequest searchRequest = SearchUserTaskDefRequest.newBuilder()
                .setLimit(requestLimit)
                .setBookmark(ByteString.copyFrom(requestBookmark))
                .build();

        when(lhTenantClient.searchUserTaskDef(searchRequest)).thenReturn(searchResult);

        UserTaskDefListDTO result = userTaskService.getAllUserTasksDef(tenantId, requestLimit, requestBookmark);

        assertFalse(result.getUserTaskDefNames().isEmpty());
        assertEquals(expectedQuantityOfUserTaskDefs, result.getUserTaskDefNames().size());

        verify(lhTenantClient).searchUserTaskDef(searchRequest);
    }

    @Test
    void assignUserTask_shouldThrowNullPointerExceptionWhenRequestBodyIsNull() {
        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();

        assertThrows(NullPointerException.class,
                () -> userTaskService.assignUserTask(null, wfRunId, userTaskRunGuid, tenantId));
    }

    @Test
    void assignUserTask_shouldThrowNullPointerExceptionWhenWfRunIdIsNull() {
        var request = AssignmentRequest.builder()
                .userId("some-user-id")
                .build();

        var userTaskRunGuid = buildStringGuid();

        assertThrows(NullPointerException.class,
                () -> userTaskService.assignUserTask(request, null, userTaskRunGuid, tenantId));
    }

    @Test
    void assignUserTask_shouldThrowNullPointerExceptionWhenUserTaskRunGuidIsNull() {
        var request = AssignmentRequest.builder()
                .userId("some-user-id")
                .build();

        var wfRunId = buildStringGuid();

        assertThrows(NullPointerException.class,
                () -> userTaskService.assignUserTask(request, wfRunId, null, tenantId));
    }

    @Test
    void assignUserTask_shouldThrowNullPointerExceptionWhenTenantIdIsNull() {
        var request = AssignmentRequest.builder()
                .userId("some-user-id")
                .build();

        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();

        assertThrows(NullPointerException.class,
                () -> userTaskService.assignUserTask(request, wfRunId, userTaskRunGuid, null));
    }

    @Test
    void assignUserTask_shouldThrowResponseStatusExceptionAsBadRequestWhenUserIdAndUserGroupAreEmpty() {
        var request = AssignmentRequest.builder()
                .userId("")
                .userGroup("")
                .build();

        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();

        ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                () -> userTaskService.assignUserTask(request, wfRunId, userTaskRunGuid, tenantId));

        String expectedExceptionMessage = "No valid arguments were received to complete reassignment.";

        assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.getBody().getStatus());
        assertEquals(expectedExceptionMessage, thrownException.getReason());

        verify(lhTenantClient, never()).assignUserTaskRun(any());
    }

    @Test
    void assignUserTask_shouldSucceedWhenUserTaskRunIsAssignedToAUserAndServerDoesNotThrowAnyException() {
        var assignedUserId = "some-user-id";
        var request = AssignmentRequest.builder()
                .userId(assignedUserId)
                .build();

        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();

        when(lhTenantClient.assignUserTaskRun(any(AssignUserTaskRunRequest.class))).thenReturn(Empty.getDefaultInstance());

        userTaskService.assignUserTask(request, wfRunId, userTaskRunGuid, tenantId);
        ArgumentCaptor<AssignUserTaskRunRequest> serverRequest = ArgumentCaptor.forClass(AssignUserTaskRunRequest.class);

        verify(lhTenantClient).assignUserTaskRun(serverRequest.capture());

        AssignUserTaskRunRequest actualServerRequest = serverRequest.getValue();

        assertTrue(actualServerRequest.hasUserId());
        assertEquals(assignedUserId, actualServerRequest.getUserId());
        assertFalse(actualServerRequest.hasUserGroup());
    }

    @Test
    void assignUserTask_shouldSucceedWhenUserTaskRunIsAssignedToAUserGroupAndServerDoesNotThrowAnyException() {
        var assignedUserGroup = "some-user-group";
        var request = AssignmentRequest.builder()
                .userGroup(assignedUserGroup)
                .build();

        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();

        when(lhTenantClient.assignUserTaskRun(any(AssignUserTaskRunRequest.class))).thenReturn(Empty.getDefaultInstance());

        userTaskService.assignUserTask(request, wfRunId, userTaskRunGuid, tenantId);
        ArgumentCaptor<AssignUserTaskRunRequest> serverRequest = ArgumentCaptor.forClass(AssignUserTaskRunRequest.class);

        verify(lhTenantClient).assignUserTaskRun(serverRequest.capture());

        AssignUserTaskRunRequest actualServerRequest = serverRequest.getValue();

        assertFalse(actualServerRequest.hasUserId());
        assertTrue(actualServerRequest.hasUserGroup());
        assertEquals(assignedUserGroup, actualServerRequest.getUserGroup());
    }

    @Test
    void assignUserTask_shouldSucceedWhenUserTaskRunIsAssignedToAUserIdAndUserGroupAndServerDoesNotThrowAnyException() {
        var assignedUserId = "some-user-id";
        var assignedUserGroup = "some-user-group";
        var request = AssignmentRequest.builder()
                .userId(assignedUserId)
                .userGroup(assignedUserGroup)
                .build();

        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();

        when(lhTenantClient.assignUserTaskRun(any(AssignUserTaskRunRequest.class))).thenReturn(Empty.getDefaultInstance());

        userTaskService.assignUserTask(request, wfRunId, userTaskRunGuid, tenantId);
        ArgumentCaptor<AssignUserTaskRunRequest> serverRequest = ArgumentCaptor.forClass(AssignUserTaskRunRequest.class);

        verify(lhTenantClient).assignUserTaskRun(serverRequest.capture());

        AssignUserTaskRunRequest actualServerRequest = serverRequest.getValue();

        assertTrue(actualServerRequest.hasUserId());
        assertTrue(actualServerRequest.hasUserGroup());
        assertEquals(assignedUserGroup, actualServerRequest.getUserGroup());
    }

    @Test
    void assignUserTask_shouldThrowResponseStatusExceptionAsBadRequestWhenServerThrowsExceptionRelatedToAnArgument() {
        var wrongUserGroup = "2938-wjas";
        var request = AssignmentRequest.builder()
                .userId(wrongUserGroup)
                .build();

        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();

        when(lhTenantClient.assignUserTaskRun(any(AssignUserTaskRunRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.INVALID_ARGUMENT));

        ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                () -> userTaskService.assignUserTask(request, wfRunId, userTaskRunGuid, tenantId));

        int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();

        assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
        assertEquals("INVALID_ARGUMENT", thrownException.getReason());

        verify(lhTenantClient).assignUserTaskRun(any(AssignUserTaskRunRequest.class));
    }

    @Test
    void assignUserTask_shouldThrowResponseStatusExceptionAsPreconditionFailedWhenUserTaskRunIsInATerminalStatus() {
        var userGroup = "my-user-group";
        var request = AssignmentRequest.builder()
                .userId(userGroup)
                .build();

        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();

        when(lhTenantClient.assignUserTaskRun(any(AssignUserTaskRunRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.FAILED_PRECONDITION));

        ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                () -> userTaskService.assignUserTask(request, wfRunId, userTaskRunGuid, tenantId));

        int expectedHttpErrorCode = HttpStatus.PRECONDITION_FAILED.value();

        assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
        assertEquals("FAILED_PRECONDITION", thrownException.getReason());

        verify(lhTenantClient).assignUserTaskRun(any(AssignUserTaskRunRequest.class));
    }

    @Test
    void assignUserTask_shouldThrowExceptionAsIsWhenServerThrowsUnhandledException() {
        var userGroup = "my-user-group";
        var request = AssignmentRequest.builder()
                .userId(userGroup)
                .build();

        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();

        when(lhTenantClient.assignUserTaskRun(any(AssignUserTaskRunRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.ABORTED));

        assertThrows(StatusRuntimeException.class,
                () -> userTaskService.assignUserTask(request, wfRunId, userTaskRunGuid, tenantId));

        verify(lhTenantClient).assignUserTaskRun(any(AssignUserTaskRunRequest.class));
    }

    @Test
    void assignUserTask_shouldThrowExceptionWhenServerThrowsUnhandledException() {
        var assignedUserGroup = "some-user-group";
        var request = AssignmentRequest.builder()
                .userId(assignedUserGroup)
                .build();

        var wfRunId = buildStringGuid();
        var userTaskRunGuid = buildStringGuid();
        var expectedErrorMessage = "Something went wrong while assigning UserTaskRun";

        when(lhTenantClient.assignUserTaskRun(any(AssignUserTaskRunRequest.class)))
                .thenThrow(new RuntimeException(expectedErrorMessage));

        RuntimeException thrownException = assertThrows(RuntimeException.class,
                () -> userTaskService.assignUserTask(request, wfRunId, userTaskRunGuid, tenantId));

        assertEquals(expectedErrorMessage, thrownException.getMessage());

        verify(lhTenantClient).assignUserTaskRun(any(AssignUserTaskRunRequest.class));
    }

    @Test
    void cancelUserTask_shouldThrowNullPointerExceptionWhenWfRunIdIsNull() {
        var someUserTaskRunGuid = buildStringGuid();

        assertThrows(NullPointerException.class,
                () -> userTaskService.cancelUserTask(null, someUserTaskRunGuid, tenantId, null));
    }

    @Test
    void cancelUserTask_shouldThrowNullPointerExceptionWhenUserTaskRunGuidIsNull() {
        var someWfRunId = buildStringGuid();

        assertThrows(NullPointerException.class,
                () -> userTaskService.cancelUserTask(someWfRunId, null, tenantId, null));
    }

    @Test
    void cancelUserTask_shouldThrowNullPointerExceptionWhenTenantIdIsNull() {
        var someUserTaskRunGuid = buildStringGuid();
        var someWfRunId = buildStringGuid();

        assertThrows(NullPointerException.class,
                () -> userTaskService.cancelUserTask(someWfRunId, someUserTaskRunGuid, null, null));
    }

    @Test
    void cancelUserTask_shouldThrowResponseStatusExceptionAsBadRequestWhenServerThrowsExceptionRelatedToAnArgument() {
        var someUserTaskRunGuid = buildStringGuid();
        var someWfRunId = buildStringGuid();

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class)))
                .thenThrow(new StatusRuntimeException(Status.INVALID_ARGUMENT));

        ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                () -> userTaskService.cancelUserTask(someWfRunId, someUserTaskRunGuid, tenantId, null));

        int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();

        assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
        assertEquals("INVALID_ARGUMENT", thrownException.getReason());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).cancelUserTaskRun(any(CancelUserTaskRunRequest.class));
    }

    @Test
    void cancelUserTask_shouldThrowResponseStatusExceptionAsPreconditionFailedWhenServerThrowsExceptionRelatedToAFailedPrecondition() {
        var someUserTaskRunGuid = buildStringGuid();
        var someWfRunId = buildStringGuid();

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class)))
                .thenThrow(new StatusRuntimeException(Status.FAILED_PRECONDITION));

        ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                () -> userTaskService.cancelUserTask(someWfRunId, someUserTaskRunGuid, tenantId, null));

        int expectedHttpErrorCode = HttpStatus.PRECONDITION_FAILED.value();

        assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
        assertEquals("FAILED_PRECONDITION", thrownException.getReason());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).cancelUserTaskRun(any(CancelUserTaskRunRequest.class));
    }

    @Test
    void cancelUserTask_shouldThrowExceptionAsIsWhenServerThrowsUnhandledException() {
        var someUserTaskRunGuid = buildStringGuid();
        var someWfRunId = buildStringGuid();

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenThrow(new StatusRuntimeException(Status.ABORTED));

        assertThrows(StatusRuntimeException.class,
                () -> userTaskService.cancelUserTask(someWfRunId, someUserTaskRunGuid, tenantId, null));

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).cancelUserTaskRun(any(CancelUserTaskRunRequest.class));
    }

    @Test
    void cancelUserTask_shouldThrowResponseStatusExceptionAsForbiddenWhenUserTaskRunIsAlreadyInATerminatedStatus() {
        var someUserTaskRunGuid = buildStringGuid();
        var someWfRunId = buildStringGuid();
        var someUserId = buildStringGuid();

        UserTaskRun sampleUserTaskRun = buildFakeUserTaskRun(someUserId, someWfRunId)
                .toBuilder().setStatus(UserTaskRunStatus.CANCELLED).build();

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(sampleUserTaskRun);

        ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                () -> userTaskService.cancelUserTask(someWfRunId, someUserTaskRunGuid, tenantId, null));

        var expectedExceptionMessage = "The UserTask you are trying to cancel is already DONE or CANCELLED";

        assertEquals(HttpStatus.FORBIDDEN.value(), thrownException.getBody().getStatus());
        assertEquals(expectedExceptionMessage, thrownException.getReason());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).cancelUserTaskRun(any(CancelUserTaskRunRequest.class));
    }

    @Test
    void cancelUserTask_shouldThrowResponseStatusExceptionAsForbiddenWhenUserTaskRunIsNotFound() {
        var someUserTaskRunGuid = buildStringGuid();
        var someWfRunId = buildStringGuid();

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(null);

        NotFoundException thrownException = assertThrows(NotFoundException.class,
                () -> userTaskService.cancelUserTask(someWfRunId, someUserTaskRunGuid, tenantId, null));

        var expectedExceptionMessage = "Could not find UserTaskRun!";

        assertEquals(expectedExceptionMessage, thrownException.getMessage());

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient, never()).cancelUserTaskRun(any(CancelUserTaskRunRequest.class));
    }

    @Test
    void cancelUserTask_shouldSucceedWhenUserTaskRunIsNotInATerminatedStatusAndServerDoesNotThrowAnError() {
        var someUserTaskRunGuid = buildStringGuid();
        var someWfRunId = buildStringGuid();
        var someUserId = buildStringGuid();

        UserTaskRun sampleUserTaskRun = buildFakeUserTaskRun(someUserId, someWfRunId);

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(sampleUserTaskRun);
        when(lhTenantClient.cancelUserTaskRun(any(CancelUserTaskRunRequest.class))).thenReturn(Empty.getDefaultInstance());

        userTaskService.cancelUserTask(someWfRunId, someUserTaskRunGuid, tenantId, null);

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).cancelUserTaskRun(any(CancelUserTaskRunRequest.class));
    }

    @Test
    void cancelUserTask_shouldSucceedForNonAdminUserWhenUserTaskRunIsNotInATerminatedStatusAndServerDoesNotThrowAnError() {
        var someUserTaskRunGuid = buildStringGuid();
        var someWfRunId = buildStringGuid();
        var someUserId = buildStringGuid();

        UserTaskRun sampleUserTaskRun = buildFakeUserTaskRun(someUserId, someWfRunId);

        when(lhTenantClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(sampleUserTaskRun);
        when(lhTenantClient.cancelUserTaskRun(any(CancelUserTaskRunRequest.class))).thenReturn(Empty.getDefaultInstance());

        userTaskService.cancelUserTask(someWfRunId, someUserTaskRunGuid, tenantId, someUserId);

        verify(lhTenantClient).getUserTaskRun(any(UserTaskRunId.class));
        verify(lhTenantClient).cancelUserTaskRun(any(CancelUserTaskRunRequest.class));
    }

    private static String buildStringGuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private UserTaskRunId buildFakeUserTaskRunId(String wfRunId) {
        return UserTaskRunId.newBuilder()
                .setUserTaskGuid(UUID.randomUUID().toString())
                .setWfRunId(WfRunId.newBuilder()
                        .setId(wfRunId)
                        .build())
                .build();
    }

    private UserTaskRun buildFakeUserTaskRun(String userId, String wfRunId) {
        return UserTaskRun.newBuilder()
                .setId(UserTaskRunId.newBuilder()
                        .setUserTaskGuid(UUID.randomUUID().toString())
                        .setWfRunId(WfRunId.newBuilder()
                                .setId(wfRunId)
                                .build())
                        .build())
                .setUserTaskDefId(UserTaskDefId.newBuilder()
                        .setName(UUID.randomUUID().toString())
                        .build())
                .setUserId(userId)
                .setStatus(UserTaskRunStatus.ASSIGNED)
                .setScheduledTime(buildTimestamp(LocalDateTime.now()))
                .build();
    }

    private UserTaskRun buildFakeUserTaskRunWithUserGroup(String userId, String wfRunId, String userGroup) {
        var userTaskRun = buildFakeUserTaskRun(userId, wfRunId);

        return UserTaskRun.newBuilder(userTaskRun)
                .setUserGroup(userGroup)
                .build();
    }

    private UserTaskRun buildFakeUserTaskRunWithCustomScheduledTime(String userId, String wfRunId,
                                                                    LocalDateTime customScheduledTime) {
        var userTaskRun = buildFakeUserTaskRun(userId, wfRunId);
        return UserTaskRun.newBuilder(userTaskRun)
                .setScheduledTime(buildTimestamp(customScheduledTime))
                .build();
    }

    private UserTaskRun buildFakeUserTaskRunWithCustomTaskDefName(String userId, String wfRunId, String taskDefName) {
        var userTaskRun = buildFakeUserTaskRun(userId, wfRunId);
        return UserTaskRun.newBuilder(userTaskRun)
                .setUserTaskDefId(UserTaskDefId.newBuilder()
                        .setName(taskDefName)
                        .build())
                .build();
    }

    private UserTaskRun buildFakeUserTaskRunWithCustomTaskDefNameAndCustomDateRange(String userId, String wfRunId,
                                                                                    String taskDefName,
                                                                                    LocalDateTime scheduledTime) {
        var userTaskRun = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, scheduledTime);
        return UserTaskRun.newBuilder(userTaskRun)
                .setUserTaskDefId(UserTaskDefId.newBuilder()
                        .setName(taskDefName)
                        .build())
                .build();
    }

    private Timestamp buildTimestamp(LocalDateTime currentDate) {
        return Timestamp.newBuilder()
                .setSeconds(currentDate.toEpochSecond(UTC_ZONE))
                .setNanos(currentDate.getNano())
                .build();
    }

    private UserTaskDef buildFakeUserTaskDef(String userTaskDefId) {
        return UserTaskDef.newBuilder()
                .setName(userTaskDefId)
                .addFields(buildFakeUserTaskField(VariableType.STR, "Requested by"))
                .addFields(buildFakeUserTaskField(VariableType.STR, "Request"))
                .addFields(buildFakeUserTaskField(VariableType.BOOL, "Approved"))
                .build();
    }

    private UserTaskField buildFakeUserTaskField(VariableType type, String fieldName) {
        return UserTaskField.newBuilder()
                .setName(fieldName)
                .setDisplayName(fieldName)
                .setDescription("Random description")
                .setType(type)
                .setRequired(true)
                .build();
    }

    private Predicate<SimpleUserTaskRunDTO> hasMandatoryFieldsForAUser(String userId) {
        return dto -> StringUtils.hasText(dto.getId()) && StringUtils.hasText(dto.getWfRunId())
                && StringUtils.hasText(dto.getUserTaskDefName())
                && StringUtils.hasText(dto.getUserId()) && dto.getUserId().equalsIgnoreCase(userId)
                && Objects.nonNull(dto.getStatus()) && dto.getStatus() == UserTaskStatus.ASSIGNED
                && Objects.nonNull(dto.getScheduledTime());
    }

    private Predicate<SimpleUserTaskRunDTO> hasUserGroup(String userGroup) {
        return dto -> StringUtils.hasText(dto.getUserGroup()) && dto.getUserGroup().equalsIgnoreCase(userGroup);
    }

    private Predicate<SimpleUserTaskRunDTO> hasScheduledTimeAfterEarliestStart(LocalDateTime earliestStartDate) {
        return dto -> Objects.nonNull(dto.getScheduledTime()) && dto.getScheduledTime().isAfter(earliestStartDate);
    }

    private Predicate<SimpleUserTaskRunDTO> hasScheduledTimeBeforeLatestStart(LocalDateTime latestStartDate) {
        return dto -> Objects.nonNull(dto.getScheduledTime()) && dto.getScheduledTime().isBefore(latestStartDate);
    }

    private Predicate<UserTaskFieldDTO> hasMandatoryFieldsForUserTaskField() {
        return userTaskFieldDTO -> StringUtils.hasText(userTaskFieldDTO.getName())
                && StringUtils.hasText(userTaskFieldDTO.getDescription())
                && StringUtils.hasText(userTaskFieldDTO.getDisplayName())
                && Objects.nonNull(userTaskFieldDTO.getType())
                && !userTaskFieldDTO.getType().equals(UserTaskFieldType.UNRECOGNIZED);
    }
}
