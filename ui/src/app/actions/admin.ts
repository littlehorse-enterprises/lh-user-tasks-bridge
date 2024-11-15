"use server";
import { clientWithErrorHandling } from "@/lib/client";
import {
  ListUserTaskDefNamesRequest,
  ListUserTasksRequest,
  UserTask,
  UserTaskResult,
} from "@littlehorse-enterprises/user-tasks-api-client";

export async function adminCancelUserTask(userTask: UserTask) {
  return clientWithErrorHandling((client) =>
    client.adminCancelUserTask(userTask),
  );
}

export async function adminAssignUserTask(
  userTask: UserTask,
  assignment: { userId?: string; userGroupId?: string },
) {
  return clientWithErrorHandling((client) =>
    client.adminAssignUserTask(userTask, assignment),
  );
}

export async function adminListUsers() {
  return clientWithErrorHandling((client) => client.adminListUsers());
}

export async function adminListUserGroups() {
  return clientWithErrorHandling((client) => client.adminListUserGroups());
}

export async function adminListUserTaskDefNames(
  search: ListUserTaskDefNamesRequest,
) {
  return clientWithErrorHandling((client) =>
    client.adminListUserTaskDefNames(search),
  );
}

export async function adminListUserTasks(search: ListUserTasksRequest) {
  return clientWithErrorHandling((client) => client.adminListUserTasks(search));
}

export async function adminGetUserTask(userTask: UserTask) {
  return clientWithErrorHandling((client) => client.adminGetUserTask(userTask));
}

export async function adminCompleteUserTask(
  userTask: UserTask,
  values: UserTaskResult,
) {
  return clientWithErrorHandling((client) =>
    client.adminCompleteUserTask(userTask, values),
  );
}
