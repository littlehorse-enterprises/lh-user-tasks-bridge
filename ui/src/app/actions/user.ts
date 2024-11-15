"use server";
import { clientWithErrorHandling } from "@/lib/client";
import {
  ListUserTasksRequest,
  UserTask,
  UserTaskResult,
} from "@littlehorse-enterprises/user-tasks-api-client";

export async function claimUserTask(userTask: UserTask) {
  return clientWithErrorHandling((client) => client.claimUserTask(userTask));
}

export async function cancelUserTask(userTask: UserTask) {
  return clientWithErrorHandling((client) => client.cancelUserTask(userTask));
}

export async function listUserTasks(
  search: Omit<ListUserTasksRequest, "type">,
) {
  return clientWithErrorHandling((client) => client.listUserTasks(search));
}

export async function listUserGroups() {
  return clientWithErrorHandling((client) => client.listUserGroups());
}

export async function getUserTask(userTask: UserTask) {
  return clientWithErrorHandling((client) => client.getUserTask(userTask));
}

export async function completeUserTask(
  userTask: UserTask,
  values: UserTaskResult,
) {
  return clientWithErrorHandling((client) =>
    client.completeUserTask(userTask, values),
  );
}
