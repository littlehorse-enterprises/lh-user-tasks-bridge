"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { zodResolver } from "@hookform/resolvers/zod";
import { IDPUserDTO } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import {
  ChevronDown,
  ChevronUp,
  Eye,
  EyeOff,
  Lock,
  MoreHorizontal,
  Pencil,
  Trash,
  Users,
} from "lucide-react";
import { useSession } from "next-auth/react";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import useSWR from "swr";
import * as z from "zod";
import { getGroups } from "../../actions/group-management";
import {
  addUserToGroup,
  assignAdminRole,
  createUser,
  deleteUser,
  getUserFromIdP,
  getUsersFromIdP,
  removeAdminRole,
  removeUserFromGroup,
  updateUser,
  upsertPassword,
} from "../../actions/user-management";

const createFormSchema = z.object({
  username: z.string().min(2, {
    message: "Username must be at least 2 characters.",
  }),
  email: z.string().email({
    message: "Please enter a valid email address.",
  }),
  firstName: z.string().min(2, {
    message: "First name must be at least 2 characters.",
  }),
  lastName: z.string().min(2, {
    message: "Last name must be at least 2 characters.",
  }),
});

const editFormSchema = z.object({
  username: z.string().min(2, {
    message: "Username must be at least 2 characters.",
  }),
  email: z.string().email({
    message: "Please enter a valid email address.",
  }),
  firstName: z.string().min(2, {
    message: "First name must be at least 2 characters.",
  }),
  lastName: z.string().min(2, {
    message: "Last name must be at least 2 characters.",
  }),
  enabled: z.boolean().optional(),
});

const passwordResetSchema = z.object({
  password: z.string().min(4, {
    message: "Password must be at least 4 characters.",
  }),
});

export default function UsersManagement() {
  const tenantId = useParams().tenantId as string;
  const { data: session } = useSession();
  const currentUserId = session?.user?.id;

  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [isGroupsDialogOpen, setIsGroupsDialogOpen] = useState(false);
  const [isBulkGroupsDialogOpen, setIsBulkGroupsDialogOpen] = useState(false);
  const [isPasswordResetDialogOpen, setIsPasswordResetDialogOpen] =
    useState(false);
  const [selectedUser, setSelectedUser] = useState<IDPUserDTO | null>(null);
  const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
  const [showPassword, setShowPassword] = useState(false);
  const [selectedUsers, setSelectedUsers] = useState<string[]>([]);
  const [selectAll, setSelectAll] = useState(false);
  const [sortColumn, setSortColumn] = useState<string>("name");
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 25;

  const {
    data: usersData,
    error: usersError,
    mutate: mutateUsers,
  } = useSWR([`users-${tenantId}`, currentPage], async () => {
    const response = await getUsersFromIdP(tenantId, {
      first_result: (currentPage - 1) * pageSize,
      max_results: pageSize + 1,
    });
    return response.data;
  });

  const { data: groupsData, error: groupsError } = useSWR(
    `groups-${tenantId}`,
    async () => {
      const response = await getGroups(tenantId, {
        max_results: 1000,
      });
      return response.data;
    },
  );

  const allUsers = usersData?.users || [];
  const hasMoreUsers = allUsers.length > pageSize;
  const pagedUsers = allUsers.slice(0, pageSize);
  pagedUsers.sort((a: any, b: any) => {
    let aValue: any = "";
    let bValue: any = "";
    switch (sortColumn) {
      case "username":
        aValue = a.username || "";
        bValue = b.username || "";
        break;
      case "email":
        aValue = a.email || "";
        bValue = b.email || "";
        break;
      case "name":
        aValue = `${a.firstName || ""} ${a.lastName || ""}`.trim();
        bValue = `${b.firstName || ""} ${b.lastName || ""}`.trim();
        break;
      case "enabled":
        aValue = a.enabled ? 1 : 0;
        bValue = b.enabled ? 1 : 0;
        break;
      case "admin":
        aValue = a.realmRoles?.includes("lh-user-tasks-admin") ? 1 : 0;
        bValue = b.realmRoles?.includes("lh-user-tasks-admin") ? 1 : 0;
        break;
      default:
        return 0;
    }
    if (typeof aValue === "string" && typeof bValue === "string") {
      const cmp = aValue.localeCompare(bValue);
      return sortDirection === "asc" ? cmp : -cmp;
    } else {
      return sortDirection === "asc"
        ? Number(aValue) - Number(bValue)
        : Number(bValue) - Number(aValue);
    }
  });

  // Auto-bounce back if page is empty (but not on first page)
  useEffect(() => {
    if (currentPage > 1 && pagedUsers.length === 0) {
      setCurrentPage(currentPage - 1);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pagedUsers.length, currentPage]);

  const groups = groupsData?.groups || [];

  const createForm = useForm<z.infer<typeof createFormSchema>>({
    resolver: zodResolver(createFormSchema),
    defaultValues: {
      username: "",
      email: "",
      firstName: "",
      lastName: "",
    },
  });

  const editForm = useForm<z.infer<typeof editFormSchema>>({
    resolver: zodResolver(editFormSchema),
    defaultValues: {
      username: "",
      email: "",
      firstName: "",
      lastName: "",
    },
  });

  const passwordResetForm = useForm<z.infer<typeof passwordResetSchema>>({
    resolver: zodResolver(passwordResetSchema),
    defaultValues: {
      password: "",
    },
  });

  async function handleCreateUser(values: z.infer<typeof createFormSchema>) {
    try {
      const response = await createUser(tenantId, {
        username: values.username,
        email: values.email,
        firstName: values.firstName,
        lastName: values.lastName,
      });

      if (response.error) {
        toast.error(response.error.message || "Failed to create user.");
        console.error("Error creating user:", response.error);
        return;
      }

      toast.success("User was successfully created.");
      setIsCreateDialogOpen(false);
      createForm.reset();
      mutateUsers();
    } catch (error) {
      console.error("Detailed error creating user:", error);
      toast.error("Failed to create user. See console for details.");
    }
  }

  async function handleEditUser(values: z.infer<typeof editFormSchema>) {
    if (!selectedUser) return;

    try {
      // Create update object
      const updateData = {
        email: values.email,
        firstName: values.firstName,
        lastName: values.lastName,
        enabled: values.enabled,
      };

      const updateResponse = await updateUser(
        tenantId,
        { user_id: selectedUser.id },
        updateData,
      );

      if (updateResponse.error) {
        toast.error(updateResponse.error.message || "Failed to update user.");
        console.error("Error updating user:", updateResponse.error);
        return;
      }

      toast.success("User was successfully updated.");
      setIsEditDialogOpen(false);
      editForm.reset();
      mutateUsers();
    } catch (error) {
      toast.error("Failed to update user.");
      console.error("Error updating user:", error);
    }
  }

  async function handleResetPassword(
    values: z.infer<typeof passwordResetSchema>,
  ) {
    if (!selectedUser) return;

    try {
      const passwordResponse = await upsertPassword(
        tenantId,
        { user_id: selectedUser.id },
        { password: values.password, temporary: true },
      );

      if (passwordResponse.error) {
        toast.error(
          passwordResponse.error.message || "Failed to reset password.",
        );
        console.error("Error resetting password:", passwordResponse.error);
        return;
      }

      toast.success("Password was successfully reset.");
      setIsPasswordResetDialogOpen(false);
      passwordResetForm.reset();
    } catch (error) {
      toast.error("Failed to reset password.");
      console.error("Error resetting password:", error);
    }
  }

  async function handleDeleteUser(userId: string) {
    if (!confirm("Are you sure you want to delete this user?")) {
      return;
    }

    try {
      const response = await deleteUser(tenantId, { user_id: userId });

      if (response.error) {
        toast.error(response.error.message || "Failed to delete user.");
        console.error("Error deleting user:", response.error);
        return;
      }

      toast.success("User was successfully deleted.");
      mutateUsers();
    } catch (error) {
      toast.error("Failed to delete user.");
      console.error("Error deleting user:", error);
    }
  }

  async function handleEditClick(userId: string) {
    try {
      const userResponse = await getUserFromIdP(tenantId, { user_id: userId });
      if (userResponse.data) {
        setSelectedUser(userResponse.data);
        editForm.reset({
          username: userResponse.data.username || "",
          email: userResponse.data.email || "",
          firstName: userResponse.data.firstName || "",
          lastName: userResponse.data.lastName || "",
          enabled: userResponse.data.enabled !== false,
        });
        setIsEditDialogOpen(true);
      } else {
        toast.error("User details not found.");
      }
    } catch (error) {
      toast.error("Failed to fetch user details.");
      console.error("Error fetching user:", error);
    }
  }

  function handleResetPasswordClick(user: IDPUserDTO) {
    setSelectedUser(user);
    passwordResetForm.reset();
    setIsPasswordResetDialogOpen(true);
  }

  async function handleManageGroups(user: IDPUserDTO) {
    setSelectedUser(user);
    setSelectedGroups(user.groups?.map((g) => g.id) || []);
    setIsGroupsDialogOpen(true);
  }

  async function handleToggleGroup(
    userId: string,
    groupId: string,
    isActive: boolean,
  ) {
    try {
      let response;
      if (isActive) {
        response = await addUserToGroup(tenantId, {
          user_id: userId,
          group_id: groupId,
        });

        if (response.error) {
          toast.error(response.error.message || "Failed to add user to group.");
          console.error("Error adding user to group:", response.error);
          return;
        }

        toast.success("User added to group successfully.");
      } else {
        response = await removeUserFromGroup(tenantId, {
          user_id: userId,
          group_id: groupId,
        });

        if (response.error) {
          toast.error(
            response.error.message || "Failed to remove user from group.",
          );
          console.error("Error removing user from group:", response.error);
          return;
        }

        toast.success("User removed from group successfully.");
      }
      mutateUsers();

      // Update local state
      if (isActive) {
        setSelectedGroups((prev) => [...prev, groupId]);
      } else {
        setSelectedGroups((prev) => prev.filter((id) => id !== groupId));
      }
    } catch (error) {
      toast.error(
        isActive
          ? "Failed to add user to group."
          : "Failed to remove user from group.",
      );
      console.error("Error updating group membership:", error);
    }
  }

  async function toggleAdminRole(user: IDPUserDTO) {
    const hasAdminRole =
      user.realmRoles?.includes("lh-user-tasks-admin") || false;

    try {
      let response;
      if (hasAdminRole) {
        response = await removeAdminRole(tenantId, { user_id: user.id });

        if (response.error) {
          toast.error(response.error.message || "Failed to remove admin role.");
          console.error("Error removing admin role:", response.error);
          return;
        }

        toast.success("Admin role removed successfully.");
      } else {
        response = await assignAdminRole(tenantId, { user_id: user.id });

        if (response.error) {
          toast.error(response.error.message || "Failed to assign admin role.");
          console.error("Error assigning admin role:", response.error);
          return;
        }

        toast.success("Admin role assigned successfully.");
      }
      mutateUsers();
    } catch (error) {
      toast.error("Failed to update admin role.");
      console.error("Error updating admin role:", error);
    }
  }

  async function handleBulkDelete() {
    if (selectedUsers.length === 0) {
      toast.error("No users selected");
      return;
    }

    if (
      !confirm(
        `Are you sure you want to delete ${selectedUsers.length} selected users?`,
      )
    ) {
      return;
    }

    try {
      let successCount = 0;
      let errorCount = 0;
      let failedUserId: string | null = null;

      // Map userId to username for error reporting
      const userIdToUsername: Record<string, string> = {};
      allUsers.forEach((user: any) => {
        userIdToUsername[user.id] = user.username;
      });

      // Delete each selected user
      for (const userId of selectedUsers) {
        const response = await deleteUser(tenantId, { user_id: userId });
        if (response.error) {
          errorCount++;
          if (!failedUserId) failedUserId = userId;
          console.error(`Error deleting user ${userId}:`, response.error);
        } else {
          successCount++;
        }
      }

      if (errorCount > 0 && failedUserId) {
        const failedUsername = userIdToUsername[failedUserId] || failedUserId;
        toast.error(
          `Delete the user "${failedUsername}" individually to find out more details!`,
        );
      }

      if (successCount > 0) {
        toast.success(`Successfully deleted ${successCount} users.`);
        setSelectedUsers([]);
        mutateUsers();
      }
    } catch (error) {
      toast.error("Failed to delete users.");
      console.error("Error deleting users:", error);
    }
  }

  async function handleBulkManageGroups() {
    if (selectedUsers.length === 0) {
      toast.error("No users selected");
      return;
    }

    // Reset selected groups when opening dialog
    setSelectedGroups([]);
    setIsBulkGroupsDialogOpen(true);
  }

  async function handleBulkAddToGroups() {
    if (selectedUsers.length === 0 || selectedGroups.length === 0) {
      toast.error("No users or groups selected");
      return;
    }

    try {
      let successCount = 0;
      let errorCount = 0;

      // For each selected user, add to each selected group
      for (const userId of selectedUsers) {
        for (const groupId of selectedGroups) {
          try {
            const response = await addUserToGroup(tenantId, {
              user_id: userId,
              group_id: groupId,
            });

            if (response.error) {
              errorCount++;
              console.error(
                `Error adding user ${userId} to group ${groupId}:`,
                response.error,
              );
            } else {
              successCount++;
            }
          } catch (error) {
            errorCount++;
            console.error(
              `Error adding user ${userId} to group ${groupId}:`,
              error,
            );
          }
        }
      }

      if (successCount > 0) {
        toast.success(
          `Added ${successCount} user-group associations successfully.`,
        );
      }

      if (errorCount > 0) {
        toast.error(`Failed to add ${errorCount} user-group associations.`);
      }

      setIsBulkGroupsDialogOpen(false);
      setSelectedGroups([]);
      mutateUsers();
    } catch (error) {
      toast.error("Failed to add users to groups.");
      console.error("Error adding users to groups:", error);
    }
  }

  useEffect(() => {
    if (selectAll) {
      setSelectedUsers(pagedUsers.map((user: any) => user.id));
    } else if (selectedUsers.length === pagedUsers.length) {
      setSelectedUsers([]);
    }
  }, [selectAll, pagedUsers, selectedUsers.length]);

  async function toggleUserEnabled(user: IDPUserDTO) {
    try {
      const updateData = {
        email: user.email,
        enabled: !user.enabled,
      };

      const updateResponse = await updateUser(
        tenantId,
        { user_id: user.id },
        updateData,
      );

      if (updateResponse.error) {
        toast.error(
          updateResponse.error.message || "Failed to update user status.",
        );
        console.error("Error updating user status:", updateResponse.error);
        return;
      }

      toast.success(
        `User ${user.enabled ? "disabled" : "enabled"} successfully.`,
      );
      mutateUsers();
    } catch (error) {
      toast.error("Failed to update user status.");
      console.error("Error updating user status:", error);
    }
  }

  function handleSort(column: string) {
    if (sortColumn === column) {
      setSortDirection(sortDirection === "asc" ? "desc" : "asc");
    } else {
      setSortColumn(column);
      setSortDirection("asc");
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold">Users Management</h2>
        <div className="flex space-x-2">
          {selectedUsers.length > 0 && (
            <>
              <Button
                variant="outline"
                onClick={handleBulkManageGroups}
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
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                  <circle cx="9" cy="7" r="4"></circle>
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                  <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                </svg>
                <span>Add to Groups</span>
              </Button>
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
                <span>Delete Selected ({selectedUsers.length})</span>
              </Button>
            </>
          )}

          <Dialog
            open={isCreateDialogOpen}
            onOpenChange={setIsCreateDialogOpen}
          >
            <DialogTrigger asChild>
              <Button>Add User</Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Create New User</DialogTitle>
              </DialogHeader>
              <Form {...createForm}>
                <form
                  onSubmit={createForm.handleSubmit(handleCreateUser)}
                  className="space-y-4"
                >
                  <div className="grid grid-cols-2 gap-4">
                    <FormField
                      control={createForm.control}
                      name="firstName"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>First Name*</FormLabel>
                          <FormControl>
                            <Input {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={createForm.control}
                      name="lastName"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Last Name*</FormLabel>
                          <FormControl>
                            <Input {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                  <FormField
                    control={createForm.control}
                    name="username"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Username*</FormLabel>
                        <FormControl>
                          <Input {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={createForm.control}
                    name="email"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Email*</FormLabel>
                        <FormControl>
                          <Input type="email" {...field} />
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
                    <Button type="submit">Create User</Button>
                  </DialogFooter>
                </form>
              </Form>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {usersError ? (
        <div className="py-4 text-center text-red-500">
          Failed to load users
        </div>
      ) : !usersData ? (
        <div className="py-4 text-center">Loading users...</div>
      ) : pagedUsers.length === 0 ? (
        <div className="py-4 text-center">
          <h3 className="text-lg font-medium">No users found</h3>
          <p className="text-muted-foreground">
            Create a new user to get started.
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
                      (selectedUsers.length > 0 &&
                        selectedUsers.length === pagedUsers.length)
                    }
                    onCheckedChange={(checked) => {
                      setSelectAll(checked === true);
                    }}
                  />
                </TableHead>
                <TableHead
                  onClick={() => handleSort("username")}
                  className="cursor-pointer select-none"
                >
                  Username
                  {sortColumn === "username" &&
                    (sortDirection === "asc" ? (
                      <ChevronUp className="inline w-4 h-4" />
                    ) : (
                      <ChevronDown className="inline w-4 h-4" />
                    ))}
                </TableHead>
                <TableHead
                  onClick={() => handleSort("email")}
                  className="cursor-pointer select-none"
                >
                  Email
                  {sortColumn === "email" &&
                    (sortDirection === "asc" ? (
                      <ChevronUp className="inline w-4 h-4" />
                    ) : (
                      <ChevronDown className="inline w-4 h-4" />
                    ))}
                </TableHead>
                <TableHead
                  onClick={() => handleSort("name")}
                  className="cursor-pointer select-none"
                >
                  Name
                  {sortColumn === "name" &&
                    (sortDirection === "asc" ? (
                      <ChevronUp className="inline w-4 h-4" />
                    ) : (
                      <ChevronDown className="inline w-4 h-4" />
                    ))}
                </TableHead>
                <TableHead
                  onClick={() => handleSort("enabled")}
                  className="cursor-pointer select-none"
                >
                  Enabled
                  {sortColumn === "enabled" &&
                    (sortDirection === "asc" ? (
                      <ChevronUp className="inline w-4 h-4" />
                    ) : (
                      <ChevronDown className="inline w-4 h-4" />
                    ))}
                </TableHead>
                <TableHead
                  onClick={() => handleSort("admin")}
                  className="cursor-pointer select-none"
                >
                  Admin
                  {sortColumn === "admin" &&
                    (sortDirection === "asc" ? (
                      <ChevronUp className="inline w-4 h-4" />
                    ) : (
                      <ChevronDown className="inline w-4 h-4" />
                    ))}
                </TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {pagedUsers.map((user: any) => (
                <TableRow
                  key={user.username}
                  className={
                    selectedUsers.includes(user.id) ? "bg-muted/50" : ""
                  }
                >
                  <TableCell>
                    <Checkbox
                      checked={selectedUsers.includes(user.id)}
                      onCheckedChange={(checked) => {
                        if (checked) {
                          setSelectedUsers([...selectedUsers, user.id]);
                        } else {
                          setSelectedUsers(
                            selectedUsers.filter((id) => id !== user.id),
                          );
                        }

                        // Update select all state
                        if (
                          checked &&
                          selectedUsers.length + 1 === pagedUsers.length
                        ) {
                          setSelectAll(true);
                        } else if (!checked && selectAll) {
                          setSelectAll(false);
                        }
                      }}
                    />
                  </TableCell>
                  <TableCell>{user.username}</TableCell>
                  <TableCell>{user.email}</TableCell>
                  <TableCell>{`${user.firstName || ""} ${user.lastName || ""}`}</TableCell>
                  <TableCell>
                    {user.enabled !== false ? (
                      <Badge
                        className={`bg-green-500 ${user.id !== currentUserId ? "hover:bg-green-600 cursor-pointer" : ""}`}
                        onClick={
                          user.id !== currentUserId
                            ? () => toggleUserEnabled(user)
                            : undefined
                        }
                      >
                        Enabled
                      </Badge>
                    ) : (
                      <Badge
                        variant="destructive"
                        className={
                          user.id !== currentUserId ? "cursor-pointer" : ""
                        }
                        onClick={
                          user.id !== currentUserId
                            ? () => toggleUserEnabled(user)
                            : undefined
                        }
                      >
                        Disabled
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    <Switch
                      checked={
                        user.realmRoles?.includes("lh-user-tasks-admin") ||
                        false
                      }
                      onCheckedChange={() => toggleAdminRole(user)}
                      disabled={user.id === currentUserId}
                    />
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end space-x-2">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-8 w-8"
                          >
                            <MoreHorizontal size={16} />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem
                            onClick={() => handleEditClick(user.id)}
                          >
                            <Pencil size={16} className="mr-2" />
                            Edit
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => handleResetPasswordClick(user)}
                          >
                            <Lock size={16} className="mr-2" />
                            Reset Password
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => handleManageGroups(user)}
                          >
                            <Users size={16} className="mr-2" />
                            Manage Groups
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => handleDeleteUser(user.id)}
                            className="text-red-600 focus:text-red-600 focus:bg-red-100"
                          >
                            <Trash size={16} className="mr-2" />
                            Delete
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <div className="flex items-center justify-between px-2 py-4">
            <div className="text-sm text-muted-foreground">
              Showing {(currentPage - 1) * pageSize + 1} to{" "}
              {(currentPage - 1) * pageSize + pagedUsers.length} of{" "}
              {hasMoreUsers
                ? "many"
                : (currentPage - 1) * pageSize + pagedUsers.length}{" "}
              users
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
                disabled={!hasMoreUsers}
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
            <DialogTitle>Edit User</DialogTitle>
          </DialogHeader>
          <Form {...editForm}>
            <form
              onSubmit={editForm.handleSubmit(handleEditUser)}
              className="space-y-4"
            >
              <div className="grid grid-cols-2 gap-4">
                <FormField
                  control={editForm.control}
                  name="firstName"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>First Name</FormLabel>
                      <FormControl>
                        <Input {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={editForm.control}
                  name="lastName"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Last Name</FormLabel>
                      <FormControl>
                        <Input {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <FormField
                control={editForm.control}
                name="username"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Username*</FormLabel>
                    <FormControl>
                      <Input {...field} disabled />
                    </FormControl>
                    <p className="text-xs text-muted-foreground mt-1">
                      Username cannot be changed after creation.
                    </p>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={editForm.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email*</FormLabel>
                    <FormControl>
                      <Input type="email" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={editForm.control}
                name="enabled"
                render={({ field }) => (
                  <FormItem className="flex flex-row items-center justify-between rounded-lg border p-3 shadow-sm">
                    <div className="space-y-0.5">
                      <FormLabel>Account Enabled</FormLabel>
                      <div className="text-sm text-muted-foreground">
                        Disable to temporarily block the user's access
                      </div>
                    </div>
                    <FormControl>
                      <Switch
                        checked={field.value}
                        onCheckedChange={field.onChange}
                        disabled={selectedUser?.id === currentUserId}
                      />
                    </FormControl>
                  </FormItem>
                )}
              />
              <DialogFooter className="gap-2">
                <Button
                  variant="outline"
                  type="button"
                  onClick={() => setIsEditDialogOpen(false)}
                >
                  Cancel
                </Button>
                <Button type="submit">Update User</Button>
              </DialogFooter>
            </form>
          </Form>
        </DialogContent>
      </Dialog>

      <Dialog open={isGroupsDialogOpen} onOpenChange={setIsGroupsDialogOpen}>
        <DialogContent className="sm:max-w-[600px]">
          <DialogHeader>
            <DialogTitle>
              Manage Groups for User: {selectedUser?.username}
            </DialogTitle>
          </DialogHeader>
          {groupsError ? (
            <div className="py-4 text-center text-red-500">
              Failed to load groups
            </div>
          ) : !groupsData ? (
            <div className="py-4 text-center">Loading groups...</div>
          ) : groups.length === 0 ? (
            <div className="py-4 text-center">
              <p className="text-muted-foreground">No groups available.</p>
            </div>
          ) : (
            <div className="py-4">
              <div className="space-y-2 max-h-[400px] overflow-y-auto pr-2">
                {groups
                  .slice()
                  .sort((a, b) => {
                    const nameA = (a.name || "").toLowerCase();
                    const nameB = (b.name || "").toLowerCase();
                    return nameA.localeCompare(nameB);
                  })
                  .map((group) => {
                    const isInGroup = selectedGroups.includes(group.id);
                    return (
                      <div
                        key={group.id}
                        className="flex items-center justify-between border p-3 rounded-md"
                      >
                        <div className="flex items-center space-x-2">
                          <label
                            htmlFor={`toggle-${group.id}`}
                            className="font-medium"
                          >
                            {group.name}
                          </label>
                        </div>
                        <div className="flex items-center space-x-3">
                          <Switch
                            id={`toggle-${group.id}`}
                            checked={isInGroup}
                            onCheckedChange={(checked) => {
                              if (selectedUser) {
                                handleToggleGroup(
                                  selectedUser.id,
                                  group.id,
                                  checked,
                                );
                              }
                            }}
                          />
                        </div>
                      </div>
                    );
                  })}
              </div>
              <DialogFooter className="mt-6">
                <Button
                  variant="outline"
                  onClick={() => setIsGroupsDialogOpen(false)}
                >
                  Close
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog
        open={isBulkGroupsDialogOpen}
        onOpenChange={setIsBulkGroupsDialogOpen}
      >
        <DialogContent className="sm:max-w-[600px]">
          <DialogHeader>
            <DialogTitle>
              Add {selectedUsers.length} Users to Groups
            </DialogTitle>
          </DialogHeader>
          {groupsError ? (
            <div className="py-4 text-center text-red-500">
              Failed to load groups
            </div>
          ) : !groupsData ? (
            <div className="py-4 text-center">Loading groups...</div>
          ) : groups.length === 0 ? (
            <div className="py-4 text-center">
              <p className="text-muted-foreground">No groups available.</p>
            </div>
          ) : (
            <div className="py-4">
              <div className="py-2">
                <p className="text-sm text-muted-foreground mb-4">
                  Select groups to add the selected users to:
                </p>
              </div>
              <div className="space-y-2 max-h-[400px] overflow-y-auto pr-2">
                {groups
                  .slice()
                  .sort((a, b) => {
                    const nameA = (a.name || "").toLowerCase();
                    const nameB = (b.name || "").toLowerCase();
                    return nameA.localeCompare(nameB);
                  })
                  .map((group) => (
                    <div
                      key={group.id}
                      className="flex items-center justify-between border p-3 rounded-md"
                    >
                      <label
                        htmlFor={`bulkgroup-${group.id}`}
                        className="font-medium cursor-pointer flex-1"
                      >
                        {group.name}
                      </label>
                      <Checkbox
                        id={`bulkgroup-${group.id}`}
                        checked={selectedGroups.includes(group.id)}
                        onCheckedChange={(checked) => {
                          if (checked) {
                            setSelectedGroups([...selectedGroups, group.id]);
                          } else {
                            setSelectedGroups(
                              selectedGroups.filter((id) => id !== group.id),
                            );
                          }
                        }}
                      />
                    </div>
                  ))}
              </div>
              <DialogFooter className="mt-6 gap-2">
                <Button
                  variant="outline"
                  onClick={() => setIsBulkGroupsDialogOpen(false)}
                >
                  Cancel
                </Button>
                <Button
                  onClick={handleBulkAddToGroups}
                  className="bg-yellow-400 hover:bg-yellow-500 text-black"
                  disabled={selectedGroups.length === 0}
                >
                  Add to Selected Groups
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog
        open={isPasswordResetDialogOpen}
        onOpenChange={setIsPasswordResetDialogOpen}
      >
        <DialogContent className="sm:max-w-[425px]">
          <DialogHeader>
            <DialogTitle>
              Reset Password for {selectedUser?.username}
            </DialogTitle>
          </DialogHeader>
          <Form {...passwordResetForm}>
            <form
              onSubmit={passwordResetForm.handleSubmit(handleResetPassword)}
              className="space-y-4"
            >
              <FormField
                control={passwordResetForm.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>New Password</FormLabel>
                    <div className="relative">
                      <FormControl>
                        <Input
                          type={showPassword ? "text" : "password"}
                          {...field}
                        />
                      </FormControl>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="absolute right-0 top-0 h-full"
                        onClick={() => setShowPassword(!showPassword)}
                      >
                        {showPassword ? (
                          <EyeOff className="h-4 w-4" />
                        ) : (
                          <Eye className="h-4 w-4" />
                        )}
                        <span className="sr-only">
                          {showPassword ? "Hide password" : "Show password"}
                        </span>
                      </Button>
                    </div>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <div className="text-sm text-muted-foreground">
                User will be required to change their password on next login.
              </div>
              <DialogFooter className="gap-2">
                <Button
                  variant="outline"
                  type="button"
                  onClick={() => setIsPasswordResetDialogOpen(false)}
                >
                  Cancel
                </Button>
                <Button type="submit">Reset Password</Button>
              </DialogFooter>
            </form>
          </Form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
