"use client";
import {
  adminAssignUserTask,
  adminListUserGroups,
  adminListUsers,
} from "@/app/[tenantId]/actions/admin";
import { useTenantId } from "@/app/[tenantId]/layout";
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
  User,
  UserGroup,
  UserTask,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { useEffect, useState } from "react";
import { toast } from "sonner";

export default function AssignUserTaskButton({
  userTask,
}: {
  userTask: UserTask;
}) {
  const tenantId = useTenantId();
  const [users, setUsers] = useState<User[]>([]);
  const [selectedUser, setSelectedUser] = useState<User | undefined>(
    userTask.user,
  );

  const [userGroups, setUserGroups] = useState<UserGroup[]>([]);
  const [selectedUserGroup, setSelectedUserGroup] = useState<
    UserGroup | undefined
  >(userTask.userGroup);

  useEffect(() => {
    // TODO: data from server
    adminListUsers(tenantId)
      .then((data) => {
        if ("message" in data) {
          toast.error(data.message);
          return;
        }
        setUsers(data.users);
      })
      .catch((e) => {
        toast.error("Failed to get users");
        console.error(e);
      });

    adminListUserGroups(tenantId)
      .then((data) => {
        if ("message" in data) {
          toast.error(data.message);
          return;
        }
        setUserGroups(data.groups);
      })
      .catch((e) => {
        toast.error("Failed to get user groups");
        console.error(e);
      });
  }, [tenantId]);

  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="ghost">Assign</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            Assign <span className="font-mono">{userTask.userTaskDefName}</span>
          </DialogTitle>
        </DialogHeader>
        <div>
          <Label>User</Label>
          <Select
            onValueChange={(value) => {
              if (value === "N/A") return setSelectedUser(undefined);
              setSelectedUser(users.find((user) => user.id === value));
            }}
            value={selectedUser?.id}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select a user" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="N/A">Select a user</SelectItem>
              {selectedUser?.valid === false && (
                <SelectItem value={selectedUser.id}>
                  {selectedUser.firstName && selectedUser.lastName
                    ? `${selectedUser.firstName} ${selectedUser.lastName}`
                    : selectedUser.id}{" "}
                  <span className="text-red-500">INVALID USER</span>
                </SelectItem>
              )}
              {users.map((user) => (
                <SelectItem key={user.id} value={user.id}>
                  {user.firstName} {user.lastName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div>
          <Label>Group</Label>
          <Select
            onValueChange={(value) => {
              if (value === "N/A") return setSelectedUserGroup(undefined);
              setSelectedUserGroup(
                userGroups.find((userGroup) => userGroup.id === value),
              );
            }}
            value={selectedUserGroup?.id}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select a group" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="N/A">Select a group</SelectItem>
              {selectedUserGroup?.valid === false && (
                <SelectItem value={selectedUserGroup.id}>
                  {selectedUserGroup.name ?? selectedUserGroup.id}{" "}
                  <span className="text-red-500">INVALID GROUP</span>
                </SelectItem>
              )}
              {userGroups.map((userGroup) => (
                <SelectItem key={userGroup.id} value={userGroup.id}>
                  {userGroup.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <DialogFooter>
          <DialogClose className={buttonVariants({ variant: "outline" })}>
            Close
          </DialogClose>

          <DialogClose
            className={buttonVariants()}
            onClick={async () => {
              if (!selectedUser && !selectedUserGroup)
                return toast.warning(
                  "No user or group selected. Please select a user or group to assign the UserTask to.",
                );

              if (
                selectedUser?.id === userTask.user?.id &&
                selectedUserGroup?.id === userTask.userGroup?.id
              )
                return toast.warning(
                  "Please select a different user or group to assign the UserTask to.",
                );

              try {
                const response = await adminAssignUserTask(tenantId, userTask, {
                  userId: selectedUser?.id,
                  userGroupId: selectedUserGroup?.id,
                });
                setSelectedUser(undefined);
                setSelectedUserGroup(undefined);
                if (response && "message" in response)
                  return toast.error(response.message);

                toast.success("UserTask assigned successfully");
              } catch (error) {
                console.error(error);
                toast.error("Failed to assign UserTask");
              }
            }}
          >
            Assign Task
          </DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
