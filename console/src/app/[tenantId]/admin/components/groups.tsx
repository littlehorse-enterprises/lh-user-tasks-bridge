"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@littlehorse-enterprises/ui-library/button";
import { Checkbox } from "@littlehorse-enterprises/ui-library/checkbox";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@littlehorse-enterprises/ui-library/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@littlehorse-enterprises/ui-library/form";
import { Input } from "@littlehorse-enterprises/ui-library/input";
import { toast } from "@littlehorse-enterprises/ui-library/sonner";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@littlehorse-enterprises/ui-library/table";
import {
  IDPGroupDTO,
  IDPUserDTO,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { ChevronDown, ChevronUp } from "lucide-react";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import useSWR from "swr";
import * as z from "zod";
import {
  createGroup,
  deleteGroup,
  getGroups,
  updateGroup,
} from "../../actions/group-management";
import {
  addUserToGroup,
  getUsersFromIdP,
  removeUserFromGroup,
} from "../../actions/user-management";

const formSchema = z.object({
  name: z.string().min(2, {
    message: "Group name must be at least 2 characters.",
  }),
});

export default function GroupsManagement() {
  const tenantId = useParams().tenantId as string;

  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [isMembersDialogOpen, setIsMembersDialogOpen] = useState(false);
  const [isBulkDeleteDialogOpen, setIsBulkDeleteDialogOpen] = useState(false);
  const [selectedGroup, setSelectedGroup] = useState<IDPGroupDTO | null>(null);
  const [groupMembers, setGroupMembers] = useState<IDPUserDTO[]>([]);
  const [availableUsers, setAvailableUsers] = useState<IDPUserDTO[]>([]);
  const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
  const [selectAll, setSelectAll] = useState(false);
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 25;

  const {
    data: groupsData,
    error: groupsError,
    mutate: mutateGroups,
  } = useSWR([`groups-${tenantId}`, currentPage], async () => {
    const response = await getGroups(tenantId, {
      first_result: (currentPage - 1) * pageSize,
      max_results: pageSize + 1,
    });
    return response.data;
  });

  const {
    data: usersData,
    error: usersError,
    mutate: mutateUsers,
  } = useSWR(isMembersDialogOpen ? `users-${tenantId}` : null, async () => {
    const response = await getUsersFromIdP(tenantId, {});
    return response.data;
  });

  const allGroups = groupsData?.groups || [];
  const hasMoreGroups = allGroups.length > pageSize;
  const pagedGroups = allGroups.slice(0, pageSize);
  pagedGroups.sort((a: any, b: any) => {
    const nameA = (a.name || "").toLowerCase();
    const nameB = (b.name || "").toLowerCase();
    const cmp = nameA.localeCompare(nameB);
    return sortDirection === "asc" ? cmp : -cmp;
  });

  // Auto-bounce back if page is empty (but not on first page)
  useEffect(() => {
    if (currentPage > 1 && pagedGroups.length === 0) {
      setCurrentPage(currentPage - 1);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pagedGroups.length, currentPage]);

  const createForm = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: "",
    },
  });

  const editForm = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: "",
    },
  });

  useEffect(() => {
    if (isMembersDialogOpen && selectedGroup && usersData) {
      const allUsers = usersData.users || [];
      const members = allUsers.filter((user) =>
        user.groups?.some((group) => group.id === selectedGroup.id),
      );
      setGroupMembers(members);

      const available = allUsers.filter(
        (user) => !user.groups?.some((group) => group.id === selectedGroup.id),
      );
      setAvailableUsers(available);
    }
  }, [isMembersDialogOpen, selectedGroup, usersData]);

  async function handleCreateGroup(values: z.infer<typeof formSchema>) {
    try {
      const response = await createGroup(tenantId, {
        name: values.name,
      });

      if (response.error) {
        if (response.error.type === "CONFLICT") {
          toast.error(`Group "${values.name}" already exists.`);
        } else {
          toast.error(response.error.message || "Failed to create group.");
        }
        console.error("Error creating group:", response.error);
        return;
      }

      toast.success("Group was successfully created.");
      setIsCreateDialogOpen(false);
      createForm.reset();
      mutateGroups();
    } catch (error) {
      toast.error("Failed to create group.");
      console.error("Error creating group:", error);
    }
  }

  async function handleEditGroup(values: z.infer<typeof formSchema>) {
    if (!selectedGroup) return;

    try {
      const response = await updateGroup(
        tenantId,
        { group_id: selectedGroup.id },
        {
          name: values.name,
        },
      );

      if (response.error) {
        toast.error(response.error.message || "Failed to update group.");
        console.error("Error updating group:", response.error);
        return;
      }

      toast.success("Group was successfully updated.");
      setIsEditDialogOpen(false);
      editForm.reset();
      mutateGroups();
    } catch (error) {
      toast.error("Failed to update group.");
      console.error("Error updating group:", error);
    }
  }

  async function handleDeleteGroup(groupId: string) {
    if (!confirm("Are you sure you want to delete this group?")) return;

    try {
      const response = await deleteGroup(tenantId, { group_id: groupId });

      if (response.error) {
        toast.error(response.error.message || "Failed to delete group.");
        console.error("Error deleting group:", response.error);
        return;
      }

      toast.success("Group was successfully deleted.");
      mutateGroups();
    } catch (error) {
      toast.error("Failed to delete group.");
      console.error("Error deleting group:", error);
    }
  }

  async function handleAddUserToGroup(userId: string) {
    if (!selectedGroup) return;

    try {
      const response = await addUserToGroup(tenantId, {
        user_id: userId,
        group_id: selectedGroup.id || "",
      });

      if (response.error) {
        toast.error(response.error.message || "Failed to add user to group.");
        console.error("Error adding user to group:", response.error);
        return;
      }

      toast.success("User added to group successfully.");
      mutateUsers();
    } catch (error) {
      toast.error("Failed to add user to group.");
      console.error("Error adding user to group:", error);
    }
  }

  async function handleRemoveUserFromGroup(userId: string) {
    if (!selectedGroup) return;

    try {
      const response = await removeUserFromGroup(tenantId, {
        user_id: userId,
        group_id: selectedGroup.id || "",
      });

      if (response.error) {
        toast.error(
          response.error.message || "Failed to remove user from group.",
        );
        console.error("Error removing user from group:", response.error);
        return;
      }

      toast.success("User removed from group successfully.");
      mutateUsers();
    } catch (error) {
      toast.error("Failed to remove user from group.");
      console.error("Error removing user from group:", error);
    }
  }

  async function handleManageMembers(group: IDPGroupDTO) {
    setSelectedGroup(group);
    setIsMembersDialogOpen(true);
  }

  async function handleEditClick(group: IDPGroupDTO) {
    setSelectedGroup(group);
    editForm.reset({
      name: group.name || "",
    });
    setIsEditDialogOpen(true);
  }

  async function handleBulkDelete() {
    if (selectedGroups.length === 0) {
      toast.error("No groups selected");
      return;
    }

    if (
      !confirm(
        `Are you sure you want to delete ${selectedGroups.length} selected groups?`,
      )
    ) {
      return;
    }

    try {
      let successCount = 0;
      let errorCount = 0;

      // Delete each selected group
      for (const groupId of selectedGroups) {
        try {
          const response = await deleteGroup(tenantId, { group_id: groupId });
          if (response.error) {
            errorCount++;
            console.error(`Error deleting group ${groupId}:`, response.error);
          } else {
            successCount++;
          }
        } catch (error) {
          errorCount++;
          console.error(`Error deleting group ${groupId}:`, error);
        }
      }

      if (successCount > 0) {
        toast.success(`${successCount} groups were successfully deleted.`);
      }

      if (errorCount > 0) {
        toast.error(`Failed to delete ${errorCount} groups.`);
      }

      setSelectedGroups([]);
      mutateGroups();
    } catch (error) {
      toast.error("Failed to delete groups.");
      console.error("Error deleting groups:", error);
    }
  }

  useEffect(() => {
    if (selectAll) {
      setSelectedGroups(
        groupsData?.groups?.map((group) => group.id || "") || [],
      );
    } else if (selectedGroups.length === groupsData?.groups?.length) {
      setSelectedGroups([]);
    }
  }, [selectAll, groupsData]);

  function handleSort() {
    setSortDirection(sortDirection === "asc" ? "desc" : "asc");
  }

  const sortedGroups = pagedGroups;

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold">Groups</h2>
        <div className="flex space-x-2">
          {selectedGroups.length > 0 && (
            <Button
              variant="destructive"
              onClick={handleBulkDelete}
              className="space-x-1"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M3 6h18" />
                <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
                <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
              </svg>
              <span>Delete Selected ({selectedGroups.length})</span>
            </Button>
          )}

          <Dialog
            open={isCreateDialogOpen}
            onOpenChange={setIsCreateDialogOpen}
          >
            <DialogTrigger asChild>
              <Button className="bg-yellow-400 hover:bg-yellow-500 text-black">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="mr-2"
                >
                  <path d="M12 5v14M5 12h14" />
                </svg>
                Add Group
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Create New Group</DialogTitle>
              </DialogHeader>
              <Form {...createForm}>
                <form
                  onSubmit={createForm.handleSubmit(handleCreateGroup)}
                  className="space-y-4"
                >
                  <FormField
                    control={createForm.control}
                    name="name"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Group Name</FormLabel>
                        <FormControl>
                          <Input {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <DialogFooter className="gap-2">
                    <Button
                      variant="outline"
                      type="button"
                      onClick={() => setIsCreateDialogOpen(false)}
                    >
                      Cancel
                    </Button>
                    <Button
                      type="submit"
                      className="bg-yellow-400 hover:bg-yellow-500 text-black"
                    >
                      Create Group
                    </Button>
                  </DialogFooter>
                </form>
              </Form>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {groupsError ? (
        <div className="py-4 text-center text-red-500">
          Failed to load groups
        </div>
      ) : !groupsData ? (
        <div className="py-4 text-center">Loading groups...</div>
      ) : groupsData.groups.length === 0 ? (
        <div className="py-4 text-center">
          <h3 className="text-lg font-medium">No groups found</h3>
          <p className="text-muted-foreground">
            Create a new group to get started.
          </p>
        </div>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-12">
                  <Checkbox
                    checked={
                      selectAll ||
                      (selectedGroups.length > 0 &&
                        selectedGroups.length === groupsData.groups.length)
                    }
                    onCheckedChange={(checked) => {
                      setSelectAll(checked === true);
                    }}
                  />
                </TableHead>
                <TableHead>ID</TableHead>
                <TableHead
                  onClick={handleSort}
                  className="cursor-pointer select-none"
                >
                  Name
                  {sortDirection === "asc" ? (
                    <ChevronUp className="inline w-4 h-4" />
                  ) : (
                    <ChevronDown className="inline w-4 h-4" />
                  )}
                </TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sortedGroups.map((group) => (
                <TableRow
                  key={group.id}
                  className={
                    selectedGroups.includes(group.id || "") ? "bg-muted/50" : ""
                  }
                >
                  <TableCell>
                    <Checkbox
                      checked={selectedGroups.includes(group.id || "")}
                      onCheckedChange={(checked) => {
                        if (checked) {
                          setSelectedGroups([
                            ...selectedGroups,
                            group.id || "",
                          ]);
                        } else {
                          setSelectedGroups(
                            selectedGroups.filter((id) => id !== group.id),
                          );
                        }

                        // Update select all state
                        if (
                          checked &&
                          selectedGroups.length + 1 === groupsData.groups.length
                        ) {
                          setSelectAll(true);
                        } else if (!checked && selectAll) {
                          setSelectAll(false);
                        }
                      }}
                    />
                  </TableCell>
                  <TableCell className="font-mono text-sm">
                    {group.id}
                  </TableCell>
                  <TableCell>{group.name}</TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end space-x-2">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleEditClick(group)}
                        className="h-8 w-8"
                      >
                        <svg
                          xmlns="http://www.w3.org/2000/svg"
                          width="16"
                          height="16"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        >
                          <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                          <path d="m15 5 4 4" />
                        </svg>
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleManageMembers(group)}
                        className="h-8 w-8"
                      >
                        <svg
                          xmlns="http://www.w3.org/2000/svg"
                          width="16"
                          height="16"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        >
                          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                          <circle cx="9" cy="7" r="4"></circle>
                          <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                          <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                        </svg>
                      </Button>
                      <Button
                        variant="destructive"
                        size="icon"
                        onClick={() => handleDeleteGroup(group.id || "")}
                        className="h-8 w-8 bg-red-500 hover:bg-red-600"
                      >
                        <svg
                          xmlns="http://www.w3.org/2000/svg"
                          width="16"
                          height="16"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="white"
                          strokeWidth="2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        >
                          <path d="M3 6h18" />
                          <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
                          <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
                        </svg>
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <div className="flex items-center justify-between px-2 py-4">
            <div className="text-sm text-muted-foreground">
              Showing {(currentPage - 1) * pageSize + 1} to{" "}
              {(currentPage - 1) * pageSize + pagedGroups.length} of{" "}
              {hasMoreGroups
                ? "many"
                : (currentPage - 1) * pageSize + pagedGroups.length}{" "}
              groups
            </div>
            <div className="flex items-center space-x-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setCurrentPage((prev) => Math.max(1, prev - 1))}
                disabled={currentPage === 1}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setCurrentPage((prev) => prev + 1)}
                disabled={!hasMoreGroups}
              >
                Next
              </Button>
            </div>
          </div>
        </>
      )}

      <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit Group</DialogTitle>
          </DialogHeader>
          <div className="py-4">
            <div className="space-y-2">
              <label htmlFor="group-name" className="text-sm font-medium">
                Group Name
              </label>
              <input
                id="group-name"
                className="w-full px-3 py-2 border rounded-md"
                value={selectedGroup?.name || ""}
                onChange={(e) => {
                  if (selectedGroup) {
                    setSelectedGroup({
                      ...selectedGroup,
                      name: e.target.value,
                    });
                  }
                }}
              />
            </div>
          </div>
          <DialogFooter className="flex justify-end gap-2">
            <Button
              variant="outline"
              onClick={() => setIsEditDialogOpen(false)}
            >
              Cancel
            </Button>
            <Button
              className="bg-yellow-400 hover:bg-yellow-500 text-black"
              onClick={() => {
                if (selectedGroup) {
                  handleEditGroup({ name: selectedGroup.name || "" });
                }
              }}
            >
              Update Group
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isMembersDialogOpen} onOpenChange={setIsMembersDialogOpen}>
        <DialogContent className="sm:max-w-[800px]">
          <DialogHeader>
            <DialogTitle>
              Manage Group Members: {selectedGroup?.name}
            </DialogTitle>
          </DialogHeader>

          {usersError ? (
            <div className="py-4 text-center">Loading users...</div>
          ) : (
            <div className="grid grid-cols-2 gap-6">
              <div>
                <h3 className="text-lg font-medium mb-4">Available Users</h3>
                <div className="border rounded-md">
                  {availableUsers.length === 0 ? (
                    <div className="p-4 text-center text-muted-foreground">
                      No available users found
                    </div>
                  ) : (
                    <div className="space-y-2 p-2">
                      {availableUsers.map((user) => (
                        <div
                          key={user.id}
                          className="flex items-center justify-between bg-gray-50 p-2 rounded"
                        >
                          <span>
                            {user.firstName} {user.lastName} ({user.username})
                          </span>
                          <Button
                            size="sm"
                            onClick={() => handleAddUserToGroup(user.id)}
                            className="bg-yellow-400 hover:bg-yellow-500 text-black"
                          >
                            Add
                          </Button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div>
                <h3 className="text-lg font-medium mb-4">Group Members</h3>
                <div className="border rounded-md">
                  {groupMembers.length === 0 ? (
                    <div className="p-4 text-center text-muted-foreground">
                      No members in this group
                    </div>
                  ) : (
                    <div className="space-y-2 p-2">
                      {groupMembers.map((user) => (
                        <div
                          key={user.id}
                          className="flex items-center justify-between bg-gray-50 p-2 rounded"
                        >
                          <span>
                            {user.firstName} {user.lastName} ({user.username})
                          </span>
                          <Button
                            size="sm"
                            onClick={() => handleRemoveUserFromGroup(user.id)}
                            variant="destructive"
                          >
                            Remove
                          </Button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          <DialogFooter>
            <Button
              onClick={() => setIsMembersDialogOpen(false)}
              className="bg-yellow-400 hover:bg-yellow-500 text-black"
            >
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
