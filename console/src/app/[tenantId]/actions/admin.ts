"use server";
import { clientWithErrorHandling } from "@/lib/client";
import {
  ListUserTaskDefNamesRequest,
  ListUserTasksRequest,
  UserTask,
  UserTaskResult,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";

export async function adminCancelUserTask(
  tenantId: string,
  userTask: UserTask,
) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.adminCancelUserTask(userTask),
  );
}

export async function adminAssignUserTask(
  tenantId: string,
  userTask: UserTask,
  assignment: { userId?: string; userGroupId?: string },
) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.adminAssignUserTask(userTask, assignment),
  );
}

export async function adminListUsers(tenantId: string) {
  return clientWithErrorHandling(tenantId, (client) => client.adminListUsers());
}

export async function adminListUserGroups(tenantId: string) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.adminListUserGroups(),
  );
}

export async function adminListUserTaskDefNames(
  tenantId: string,
  search: ListUserTaskDefNamesRequest,
) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.adminListUserTaskDefNames(search),
  );
}

export async function adminListUserTasks(
  tenantId: string,
  search: ListUserTasksRequest,
) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.adminListUserTasks(search),
  );
}

export async function adminGetUserTask(tenantId: string, userTask: UserTask) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.adminGetUserTask(userTask),
  );
}

export async function adminCompleteUserTask(
  tenantId: string,
  userTask: UserTask,
  values: UserTaskResult,
) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.adminCompleteUserTask(userTask, values),
  );
}
