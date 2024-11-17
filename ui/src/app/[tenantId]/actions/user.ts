"use server";
import { clientWithErrorHandling } from "@/lib/client";
import {
  ListUserTasksRequest,
  UserTask,
  UserTaskResult,
} from "@littlehorse-enterprises/user-tasks-api-client";

export async function claimUserTask(tenantId: string, userTask: UserTask) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.claimUserTask(userTask),
  );
}

export async function cancelUserTask(tenantId: string, userTask: UserTask) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.cancelUserTask(userTask),
  );
}

export async function listUserTasks(
  tenantId: string,
  search: Omit<ListUserTasksRequest, "type">,
) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.listUserTasks(search),
  );
}

export async function listUserGroups(tenantId: string) {
  return clientWithErrorHandling(tenantId, (client) => client.listUserGroups());
}

export async function getUserTask(tenantId: string, userTask: UserTask) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.getUserTask(userTask),
  );
}

export async function completeUserTask(
  tenantId: string,
  userTask: UserTask,
  values: UserTaskResult,
) {
  return clientWithErrorHandling(tenantId, (client) =>
    client.completeUserTask(userTask, values),
  );
}
