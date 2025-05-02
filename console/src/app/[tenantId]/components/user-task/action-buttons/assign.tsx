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
  SimpleUserTaskRunDTO,
  UserDTO,
  UserGroupDTO,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";

export default function AssignUserTaskButton({
  userTask,
}: {
  userTask: SimpleUserTaskRunDTO;
}) {
  const tenantId = useParams().tenantId as string;
  const [users, setUsers] = useState<UserDTO[]>([]);
  const [selectedUser, setSelectedUser] = useState<UserDTO | undefined>(
    userTask.user,
  );

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

  const [open, setOpen] = useState(false);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" onClick={() => setOpen(true)}>
          Assign
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Assign UserTask</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div>
            <Label>Current Assignee</Label>
            <div>
              <span className="text-muted-foreground">User:</span>{" "}
              {userTask.user ? (
                userTask.user.valid ? (
                  <>
                    {userTask.user.firstName} {userTask.user.lastName}
                  </>
                ) : (
                  <>
                    {userTask.user.id}{" "}
                    <span className="text-red-500">(NOT VALID USER)</span>
                  </>
                )
              ) : (
                "N/A"
              )}
            </div>
            <div>
              <span className="text-muted-foreground">Group:</span>{" "}
              {userTask.userGroup ? (
                userTask.userGroup.valid ? (
                  <>
                    {userTask.userGroup.name}
                  </>
                ) : (
                  <>
                    {userTask.userGroup.id}{" "}
                    <span className="text-red-500">(NOT VALID GROUP)</span>
                  </>
                )
              ) : (
                "N/A"
              )}
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
                <SelectItem value="none">Deselect User</SelectItem>
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
                <SelectItem value="none">Deselect Group</SelectItem>
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

          <Button
            onClick={async () => {
              if (!selectedUserGroup && !selectedUser) {
                toast.error("Please select a user or group");
                return;
              }

              if (selectedUser && !selectedUser.valid) {
                toast.error("Selected user is not valid");
                return;
              }

              if (selectedUserGroup && !selectedUserGroup.valid) {
                toast.error("Selected user group is not valid");
                return;
              }
              
              try {
                await adminAssignUserTask(
                  tenantId,
                  {
                    wf_run_id: userTask.wfRunId,
                    user_task_guid: userTask.id,
                  },
                  {
                    userId: selectedUser?.id,
                    userGroup: selectedUserGroup?.id,
                  },
                );
                toast.success("UserTask assigned successfully");
                setOpen(false);
              } catch (error) {
                toast.error("Error assigning UserTask");
              }
            }}
          >
            Assign Task
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
