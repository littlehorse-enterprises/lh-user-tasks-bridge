"use client";
import {
  adminAssignUserTask,
  adminGetUserGroups,
  adminGetUsers,
} from "@/app/[tenantId]/actions/admin";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  AdminTaskActionParams,
  IDPUserDTO,
  SimpleUserTaskRunDTO,
  UserDTO,
  UserGroupDTO,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";

export default function AssignUserTaskButton({
  userTask,
}: {
  userTask: SimpleUserTaskRunDTO;
}) {
  const tenantId = useParams().tenantId as string;
  const [users, setUsers] = useState<UserDTO[]>([]);
  const [selectedUser, setSelectedUser] = useState<UserDTO>();

  const [userGroups, setUserGroups] = useState<UserGroupDTO[]>([]);
  const [selectedUserGroup, setSelectedUserGroup] = useState<
    UserGroupDTO | undefined
  >(userTask.userGroup);

  useEffect(() => {
    // Get users from server
    adminGetUsers(tenantId, {}).then((data) => {
      setUsers(data.users);
    });

    // Get user groups from server
    adminGetUserGroups(tenantId).then((data) => {
      setUserGroups(data.groups);
    });
  }, [tenantId]);

  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="outline">Assign</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Assign UserTask</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div>
            <Label>Current Assignee</Label>
            <div>
              {userTask.user
                ? `User: ${userTask.user.username} (${userTask.user.id})`
                : "No user assigned"}
            </div>
            <div>
              {userTask.userGroup
                ? `Group: ${userTask.userGroup.name} (${userTask.userGroup.id})`
                : "No group assigned"}
            </div>
          </div>

          <div>
            <Label htmlFor="user">
              Assign User
              <span className="ml-1 text-sm text-muted-foreground">
                (optional)
              </span>
            </Label>
            <Select
              value={selectedUser?.id ?? ""}
              onValueChange={(value) => {
                const user = users.find((u) => u.id === value);
                setSelectedUser(user);
              }}
            >
              <SelectTrigger id="user">
                <SelectValue placeholder="Select user" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">None</SelectItem>
                {users.map((user) => (
                  <SelectItem key={user.id} value={user.id}>
                    {user.username}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div>
            <Label htmlFor="group">
              Assign Group
              <span className="ml-1 text-sm text-muted-foreground">
                (recommended)
              </span>
            </Label>
            <Select
              value={selectedUserGroup?.id ?? ""}
              onValueChange={(value) => {
                const group = userGroups.find((g) => g.id === value);
                setSelectedUserGroup(group || undefined);
              }}
            >
              <SelectTrigger id="group">
                <SelectValue placeholder="Select user group" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">None</SelectItem>
                {userGroups.map((group) => (
                  <SelectItem key={group.id} value={group.id}>
                    {group.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <DialogFooter>
          <DialogClose className={buttonVariants({ variant: "outline" })}>
            Close
          </DialogClose>

          <DialogClose
            className={buttonVariants()}
            onClick={async () => {
              const taskParams: AdminTaskActionParams = {
                wf_run_id: userTask.wfRunId,
                user_task_guid: userTask.id,
              };

              await adminAssignUserTask(tenantId, taskParams, {
                userId: selectedUser?.id,
                userGroup: selectedUserGroup?.id,
              });

              setSelectedUser(undefined);
              setSelectedUserGroup(undefined);
            }}
          >
            Assign Task
          </DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
