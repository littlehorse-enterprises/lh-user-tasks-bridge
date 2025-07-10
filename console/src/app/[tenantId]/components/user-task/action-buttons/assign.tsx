"use client";
import {
  adminAssignUserTask,
  adminGetUserGroups,
} from "@/app/[tenantId]/actions/admin";
import { getUsersFromIdP } from "@/app/[tenantId]/actions/user-management";
import {
  Button,
  buttonVariants,
} from "@littlehorse-enterprises/ui-library/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@littlehorse-enterprises/ui-library/dialog";
import { Label } from "@littlehorse-enterprises/ui-library/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@littlehorse-enterprises/ui-library/select";
import { toast } from "@littlehorse-enterprises/ui-library/sonner";
import {
  IDPGroupDTO,
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

  // Data states
  const [allUsers, setAllUsers] = useState<IDPUserDTO[]>([]);
  const [allGroups, setAllGroups] = useState<IDPGroupDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // UI states
  const [open, setOpen] = useState(false);

  // Selection states
  const [selectedUserId, setSelectedUserId] = useState<string | undefined>(
    undefined,
  );
  const [selectedGroupId, setSelectedGroupId] = useState<string | undefined>(
    undefined,
  );
  const [firstSelection, setFirstSelection] = useState<
    "none" | "user" | "group"
  >("none");

  // Filtered options
  const [availableUsers, setAvailableUsers] = useState<IDPUserDTO[]>([]);
  const [availableGroups, setAvailableGroups] = useState<IDPGroupDTO[]>([]);

  // Load initial data
  useEffect(() => {
    async function loadData() {
      setIsLoading(true);
      try {
        // Load users and groups in parallel
        const [usersResponse, groupsResponse] = await Promise.all([
          getUsersFromIdP(tenantId, {}),
          adminGetUserGroups(tenantId),
        ]);

        const users = usersResponse.data?.users || [];
        const groups = groupsResponse.data?.groups || [];

        setAllUsers(users);
        setAllGroups(groups);

        // Initialize available lists with all options
        setAvailableUsers(users);
        setAvailableGroups(groups);

        // Set initial selection based on current task assignments
        if (userTask.user) {
          setSelectedUserId(userTask.user.id);
          setFirstSelection("user");
        }

        if (userTask.userGroup) {
          if (!userTask.userGroup.valid) {
            setSelectedGroupId(undefined);
            setFirstSelection("user");
          } else {
            setSelectedGroupId(userTask.userGroup.id);
            if (!userTask.user) {
              setFirstSelection("group");
            }
          }
        }
      } catch (error) {
        console.error("Error loading users or groups:", error);
        toast.error("Failed to load users or groups");
      } finally {
        setIsLoading(false);
      }
    }

    if (open) {
      loadData();
    }
  }, [tenantId, userTask, open]);

  // Update available groups when user is selected
  useEffect(() => {
    if (!selectedUserId || firstSelection !== "user" || allUsers.length === 0) {
      return;
    }

    const selectedUser = allUsers.find((u) => u.id === selectedUserId);
    if (!selectedUser) {
      setAvailableGroups(allGroups);
      return;
    }

    // Filter groups to only include those the user belongs to
    const userGroupIds = (selectedUser.groups || []).map((g) => g.id);
    const filteredGroups = allGroups.filter((group) =>
      userGroupIds.includes(group.id),
    );

    console.log(
      `User ${selectedUser.username} belongs to ${userGroupIds.length} groups`,
    );
    console.log(`Filtered to ${filteredGroups.length} available groups`);

    setAvailableGroups(filteredGroups.length > 0 ? filteredGroups : allGroups);

    // If current selected group is not valid for this user, deselect it
    if (selectedGroupId && !userGroupIds.includes(selectedGroupId)) {
      setSelectedGroupId(undefined);
    }
  }, [selectedUserId, allUsers, allGroups, firstSelection]);

  // Update available users when group is selected
  useEffect(() => {
    if (
      !selectedGroupId ||
      firstSelection !== "group" ||
      allGroups.length === 0
    ) {
      return;
    }

    console.log(
      `Filtering users for group ${selectedGroupId}, first selection: ${firstSelection}`,
    );

    // Filter users to only include those who belong to the selected group
    const usersInGroup = allUsers.filter((user) => {
      const isInGroup = (user.groups || []).some(
        (g) => g.id === selectedGroupId,
      );
      if (isInGroup) {
        console.log(`User ${user.username} is in group ${selectedGroupId}`);
      }
      return isInGroup;
    });

    console.log(
      `Found ${usersInGroup.length} users in group ${selectedGroupId}`,
    );

    setAvailableUsers(usersInGroup.length > 0 ? usersInGroup : allUsers);

    // If current selected user is not valid for this group, deselect it
    if (selectedUserId) {
      const isUserInGroup = usersInGroup.some((u) => u.id === selectedUserId);
      if (!isUserInGroup) {
        console.log(
          `Deselecting user ${selectedUserId} as they're not in group ${selectedGroupId}`,
        );
        setSelectedUserId(undefined);
      }
    }
  }, [selectedGroupId, allUsers, allGroups, firstSelection, selectedUserId]);

  // Reset filters if first selection is cleared
  useEffect(() => {
    if (firstSelection === "none") {
      setAvailableUsers(allUsers);
      setAvailableGroups(allGroups);
    }
  }, [firstSelection, allUsers, allGroups]);

  // Helper function to get selected user as UserDTO
  const getSelectedUserDTO = (): UserDTO | undefined => {
    if (!selectedUserId) return undefined;

    const fullUser = allUsers.find((u) => u.id === selectedUserId);
    if (!fullUser) return undefined;

    return {
      id: fullUser.id,
      email: fullUser.email,
      username: fullUser.username,
      firstName: fullUser.firstName,
      lastName: fullUser.lastName,
      valid: true,
    };
  };

  // Helper function to get selected group as UserGroupDTO
  const getSelectedGroupDTO = (): UserGroupDTO | undefined => {
    if (!selectedGroupId) return undefined;

    const group = allGroups.find((g) => g.id === selectedGroupId);
    if (!group) return undefined;

    return {
      id: group.id,
      name: group.name,
      valid: true,
    };
  };

  const handleUserChange = (value: string) => {
    console.log(`User selection changed to: ${value}`);

    if (value === "none") {
      setSelectedUserId(undefined);

      // If user was the first selection and is now deselected, reset selection order
      if (firstSelection === "user") {
        console.log("Resetting first selection from 'user' to 'none'");
        setFirstSelection("none");
      }
    } else {
      setSelectedUserId(value);

      // If this is the first selection, set selection order to 'user'
      if (firstSelection === "none") {
        console.log("Setting first selection to 'user'");
        setFirstSelection("user");
      }
    }
  };

  const handleGroupChange = (value: string) => {
    console.log(`Group selection changed to: ${value}`);

    if (value === "none") {
      setSelectedGroupId(undefined);

      // If group was the first selection and is now deselected, reset selection order
      if (firstSelection === "group") {
        console.log("Resetting first selection from 'group' to 'none'");
        setFirstSelection("none");
      }
    } else {
      setSelectedGroupId(value);

      // If this is the first selection, set selection order to 'group'
      if (firstSelection === "none") {
        console.log("Setting first selection to 'group'");
        setFirstSelection("group");
      }
    }
  };

  const handleAssignTask = async () => {
    const selectedUser = getSelectedUserDTO();
    const selectedGroup = getSelectedGroupDTO();

    if (!selectedGroup && !selectedUser) {
      toast.error("Please select a user or group");
      return;
    }

    if (selectedUser && !selectedUser.valid) {
      toast.error("Selected user is not valid");
      return;
    }

    if (selectedGroup && !selectedGroup.valid) {
      toast.error("Selected user group is not valid");
      return;
    }

    try {
      const response = await adminAssignUserTask(
        tenantId,
        {
          wf_run_id: userTask.wfRunId,
          user_task_guid: userTask.id,
        },
        {
          userId: selectedUser?.id,
          userGroup: selectedGroup?.id,
        },
      );

      if (response.error) {
        toast.error(response.error.message || "Error assigning UserTask");
        console.error("Error assigning UserTask:", response.error);
        return;
      }

      toast.success("UserTask assigned successfully");
      setOpen(false);
    } catch (error) {
      toast.error("Error assigning UserTask");
      console.error("Error assigning UserTask:", error);
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" className="w-full">
          Assign
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Assign UserTask</DialogTitle>
        </DialogHeader>

        {isLoading ? (
          <div className="py-4 text-center">Loading...</div>
        ) : (
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
                    <>{userTask.userGroup.name}</>
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
                {firstSelection === "group" &&
                  availableUsers.length < allUsers.length && (
                    <span className="ml-1 text-sm text-blue-500">
                      (filtered by group)
                    </span>
                  )}
              </Label>
              <Select
                value={selectedUserId || ""}
                onValueChange={handleUserChange}
                disabled={isLoading}
              >
                <SelectTrigger id="user">
                  <SelectValue placeholder="Select user" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">Deselect User</SelectItem>
                  {availableUsers.map((user) => (
                    <SelectItem key={user.id} value={user.id}>
                      {user.username || `${user.firstName} ${user.lastName}`}
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
                {firstSelection === "user" &&
                  availableGroups.length < allGroups.length && (
                    <span className="ml-1 text-sm text-blue-500">
                      (filtered by user)
                    </span>
                  )}
              </Label>
              <Select
                value={selectedGroupId || ""}
                onValueChange={handleGroupChange}
                disabled={isLoading}
              >
                <SelectTrigger id="group">
                  <SelectValue placeholder="Select user group" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">Deselect Group</SelectItem>
                  {availableGroups.map((group) => (
                    <SelectItem key={group.id} value={group.id}>
                      {group.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {firstSelection !== "none" && (
              <div className="text-sm text-muted-foreground">
                <p>
                  {firstSelection === "user" ? (
                    <>
                      You selected a user first. Groups are filtered to only
                      show groups this user belongs to.
                    </>
                  ) : (
                    <>
                      You selected a group first. Users are filtered to only
                      show users who belong to this group.
                    </>
                  )}
                </p>
              </div>
            )}
          </div>
        )}

        <DialogFooter>
          <DialogClose className={buttonVariants({ variant: "outline" })}>
            Close
          </DialogClose>

          <Button onClick={handleAssignTask} disabled={isLoading}>
            Assign Task
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
