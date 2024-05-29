package io.littlehorse.usertasks.services;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.common.proto.SearchUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.UserTaskDefId;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.sdk.common.proto.UserTaskRunIdList;
import io.littlehorse.sdk.common.proto.UserTaskRunStatus;
import io.littlehorse.sdk.common.proto.WfRunId;
import io.littlehorse.usertasks.models.requests.UserTaskRequestFilter;
import io.littlehorse.usertasks.models.responses.SimpleUserTaskRunDTO;
import io.littlehorse.usertasks.models.responses.UserTaskRunListDTO;
import io.littlehorse.usertasks.util.UserTaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public final ZoneOffset UTC_ZONE = ZoneOffset.UTC;
    private final LittleHorseGrpc.LittleHorseBlockingStub lhClient = mock();

    private final UserTaskService userTaskService = new UserTaskService(lhClient);

    @Test
    void getMyTasks_shouldReturnEmptyOptionalWhenNoTasksAreFoundForAGivenUser() {
        var userId = UUID.randomUUID().toString();
        var listOfUserTasks = UserTaskRunIdList.newBuilder().build();

        when(lhClient.searchUserTaskRun(any(SearchUserTaskRunRequest.class))).thenReturn(listOfUserTasks);

        assertTrue(userTaskService.getMyTasks(userId, null, null, null).isEmpty());

        verify(lhClient).searchUserTaskRun(any(SearchUserTaskRunRequest.class));
    }

    @Test
    void getMyTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUser() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskRun2 = buildFakeUserTaskRun(userId, wfRunId);

        when(lhClient.searchUserTaskRun(any(SearchUserTaskRunRequest.class))).thenReturn(listOfUserTasks);
        when(lhClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getMyTasks(userId, null, null, null);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));

        verify(lhClient).searchUserTaskRun(any(SearchUserTaskRunRequest.class));
        verify(lhClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getMyTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndThereIsMoreThanOnePageOfResults() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();

        var foundUserTaskRunIdList1 = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));
        var foundUserTaskRunIdList2 = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks1 = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList1)
                .setBookmark(ByteString.empty())
                .build();

        var listOfUserTasks2 = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList2)
                .build();

        var userTaskRun1InFirstSearch = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskRun2InFirstSearch = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskRun1InSecondSearch = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskRun2InSecondSearch = buildFakeUserTaskRun(userId, wfRunId);

        when(lhClient.searchUserTaskRun(any(SearchUserTaskRunRequest.class))).thenReturn(listOfUserTasks1, listOfUserTasks2);
        when(lhClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1InFirstSearch, userTaskRun2InFirstSearch,
                userTaskRun1InSecondSearch, userTaskRun2InSecondSearch);

        Optional<UserTaskRunListDTO> result = userTaskService.getMyTasks(userId, null, null, null);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        int expectedNumberOfUserTaskRuns = 4;

        assertEquals(expectedNumberOfUserTaskRuns, actualUserTaskDTOs.size());
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));

        verify(lhClient, times(2)).searchUserTaskRun(any(SearchUserTaskRunRequest.class));
        verify(lhClient, times(4)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getMyTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndUserGroup() {
        var userId = UUID.randomUUID().toString();
        var myUserGroup = "the_jedi_order";
        var wfRunId = UUID.randomUUID().toString();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRunWithUserGroup(userId, wfRunId, myUserGroup);

        when(lhClient.searchUserTaskRun(any(SearchUserTaskRunRequest.class))).thenReturn(listOfUserTasks);
        when(lhClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1);

        Optional<UserTaskRunListDTO> result = userTaskService.getMyTasks(userId, myUserGroup, null, null);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasUserGroup(myUserGroup)));

        verify(lhClient).searchUserTaskRun(any(SearchUserTaskRunRequest.class));
        verify(lhClient).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getMyTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndScheduledTimeIsAfterEarliestStartDate() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();
        var fiveDaysAgo = LocalDateTime.now().minusDays(5);
        var additionalFilters = UserTaskRequestFilter.builder()
                .earliestStartDate(buildTimestamp(fiveDaysAgo))
                .build();
        var searchRequest = SearchUserTaskRunRequest.newBuilder()
                .setLimit(25)
                .setUserId(userId)
                .setEarliestStart(additionalFilters.getEarliestStartDate())
                .build();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, fiveDaysAgo.plusHours(1L));
        var userTaskRun2 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, fiveDaysAgo.plusDays(5L));

        when(lhClient.searchUserTaskRun(searchRequest)).thenReturn(listOfUserTasks);
        when(lhClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getMyTasks(userId, null, additionalFilters, null);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasScheduledTimeAfterEarliestStart(fiveDaysAgo)));

        verify(lhClient).searchUserTaskRun(searchRequest);
        verify(lhClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getMyTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndScheduledTimeIsBeforeLatestStartDate() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();
        var currentDate = LocalDateTime.now();
        var additionalFilters = UserTaskRequestFilter.builder()
                .latestStartDate(buildTimestamp(currentDate))
                .build();
        var searchRequest = SearchUserTaskRunRequest.newBuilder()
                .setLimit(25)
                .setUserId(userId)
                .setLatestStart(additionalFilters.getLatestStartDate())
                .build();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, currentDate.minusHours(1L));
        var userTaskRun2 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, currentDate.minusDays(5L));

        when(lhClient.searchUserTaskRun(searchRequest)).thenReturn(listOfUserTasks);
        when(lhClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getMyTasks(userId, null, additionalFilters, null);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasScheduledTimeBeforeLatestStart(currentDate)));

        verify(lhClient).searchUserTaskRun(searchRequest);
        verify(lhClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getMyTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndScheduledTimeIsBetweenEarliestAndLatestStartDate() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();
        var currentDate = LocalDateTime.now();
        var lastTenDays = currentDate.minusDays(10L);
        var additionalFilters = UserTaskRequestFilter.builder()
                .earliestStartDate(buildTimestamp(lastTenDays))
                .latestStartDate(buildTimestamp(currentDate))
                .build();
        var searchRequest = SearchUserTaskRunRequest.newBuilder()
                .setLimit(25)
                .setUserId(userId)
                .setEarliestStart(additionalFilters.getEarliestStartDate())
                .setLatestStart(additionalFilters.getLatestStartDate())
                .build();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, currentDate.minusHours(1L));
        var userTaskRun2 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, currentDate.minusDays(5L));
        var userTaskRun3 = buildFakeUserTaskRunWithCustomScheduledTime(userId, wfRunId, currentDate.minusDays(1L));

        when(lhClient.searchUserTaskRun(searchRequest)).thenReturn(listOfUserTasks);
        when(lhClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2, userTaskRun3);

        Optional<UserTaskRunListDTO> result = userTaskService.getMyTasks(userId, null, additionalFilters, null);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasScheduledTimeAfterEarliestStart(lastTenDays)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(hasScheduledTimeBeforeLatestStart(currentDate)));

        verify(lhClient).searchUserTaskRun(searchRequest);
        verify(lhClient, times(3)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getMyTasks_shouldThrowExceptionWhenEarliestStartDateIsNotBeforeLatestStartDate() {
        var userId = UUID.randomUUID().toString();
        var latestStartDate = LocalDateTime.now().minusDays(1L);
        var earliestStartDate = latestStartDate.plusMinutes(1L);
        var additionalFilters = UserTaskRequestFilter.builder()
                .earliestStartDate(buildTimestamp(earliestStartDate))
                .latestStartDate(buildTimestamp(latestStartDate))
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userTaskService.getMyTasks(userId, null, additionalFilters, null));

        var expectedExceptionMessage = "Wrong date range received";

        assertEquals(expectedExceptionMessage, exception.getMessage());

        verify(lhClient, never()).searchUserTaskRun(any());
        verify(lhClient, never()).getUserTaskRun(any());
    }

    @Test
    void getMyTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndStatus() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();
        var additionalFilters = UserTaskRequestFilter.builder()
                .status(UserTaskStatus.ASSIGNED)
                .build();
        var searchRequest = SearchUserTaskRunRequest.newBuilder()
                .setLimit(25)
                .setUserId(userId)
                .setStatus(UserTaskRunStatus.ASSIGNED)
                .build();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRun(userId, wfRunId);
        var userTaskRun2 = buildFakeUserTaskRun(userId, wfRunId);

        when(lhClient.searchUserTaskRun(searchRequest)).thenReturn(listOfUserTasks);
        when(lhClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getMyTasks(userId, null, additionalFilters, null);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));

        verify(lhClient).searchUserTaskRun(searchRequest);
        verify(lhClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
    }

    @Test
    void getMyTasks_shouldReturnUserTaskListWhenTasksAreFoundForAGivenUserAndTaskDefName() {
        var userId = UUID.randomUUID().toString();
        var wfRunId = UUID.randomUUID().toString();
        var type = "my-custom-task-def";
        var additionalFilters = UserTaskRequestFilter.builder()
                .type(type)
                .build();
        var searchRequest = SearchUserTaskRunRequest.newBuilder()
                .setLimit(25)
                .setUserId(userId)
                .setUserTaskDefName(type)
                .build();

        var foundUserTaskRunIdList = Set.of(buildFakeUserTaskRunId(wfRunId), buildFakeUserTaskRunId(wfRunId));

        var listOfUserTasks = UserTaskRunIdList.newBuilder()
                .addAllResults(foundUserTaskRunIdList)
                .build();

        var userTaskRun1 = buildFakeUserTaskRunWithCustomTaskDefName(userId, wfRunId, type);
        var userTaskRun2 = buildFakeUserTaskRunWithCustomTaskDefName(userId, wfRunId, type);

        when(lhClient.searchUserTaskRun(searchRequest)).thenReturn(listOfUserTasks);
        when(lhClient.getUserTaskRun(any(UserTaskRunId.class))).thenReturn(userTaskRun1, userTaskRun2);

        Optional<UserTaskRunListDTO> result = userTaskService.getMyTasks(userId, null, additionalFilters, null);

        assertTrue(result.isPresent());
        Set<SimpleUserTaskRunDTO> actualUserTaskDTOs = result.get().getUserTasks();

        assertTrue(actualUserTaskDTOs.stream().allMatch(hasMandatoryFieldsForAUser(userId)));
        assertTrue(actualUserTaskDTOs.stream().allMatch(dto -> dto.getUserTaskDefId().equalsIgnoreCase(type)));

        verify(lhClient).searchUserTaskRun(searchRequest);
        verify(lhClient, times(2)).getUserTaskRun(any(UserTaskRunId.class));
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

    private Timestamp buildTimestamp(LocalDateTime currentDate) {
        return Timestamp.newBuilder()
                .setSeconds(currentDate.toEpochSecond(UTC_ZONE))
                .setNanos(currentDate.getNano())
                .build();
    }

    private Predicate<SimpleUserTaskRunDTO> hasMandatoryFieldsForAUser(String userId) {
        return dto -> StringUtils.hasText(dto.getId()) && StringUtils.hasText(dto.getWfRunId())
                && StringUtils.hasText(dto.getUserTaskDefId())
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
}
