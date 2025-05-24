"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Badge } from "@littlehorse-enterprises/ui-library/badge";
import { Button } from "@littlehorse-enterprises/ui-library/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@littlehorse-enterprises/ui-library/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@littlehorse-enterprises/ui-library/dropdown-menu";
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
import { Switch } from "@littlehorse-enterprises/ui-library/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@littlehorse-enterprises/ui-library/table";
import { IDPUserDTO } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import {
  ChevronDown,
  ChevronUp,
  Eye,
  EyeOff,
  Lock,
  MoreHorizontal,
  Pencil,
  Plus,
  Trash,
  Users,
} from "lucide-react";
import { useSession } from "next-auth/react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
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

interface UsersManagementProps {
  tenantId: string;
  initialUsers: IDPUserDTO[];
  hasError: boolean;
}

export default function UsersManagement({
  tenantId,
  initialUsers,
  hasError,
}: UsersManagementProps) {
  const { data: session } = useSession();
  const currentUserId = session?.user?.id;

  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [isGroupsDialogOpen, setIsGroupsDialogOpen] = useState(false);
  const [isPasswordResetDialogOpen, setIsPasswordResetDialogOpen] =
    useState(false);
  const [selectedUser, setSelectedUser] = useState<IDPUserDTO | null>(null);
  const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
  const [showPassword, setShowPassword] = useState(false);
  const [sortColumn, setSortColumn] = useState<string>("name");
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 25;

  // Use SWR with fallback data for users - this allows real-time updates while using initial data
  const {
    data: usersData,
    error: usersError,
    mutate: mutateUsers,
  } = useSWR(
    [`users-${tenantId}`, currentPage],
    async () => {
      // Only fetch if we don't have initial data or need pagination
      if (hasError) throw new Error("Failed to load users");

      const response = await getUsersFromIdP(tenantId, {
        first_result: (currentPage - 1) * pageSize,
        max_results: pageSize + 1,
      });
      return response.data;
    },
    {
      fallbackData: { users: initialUsers },
      revalidateOnMount: false, // Don't refetch immediately if we have initial data
    },
  );

  const { data: groupsData, error: groupsError } = useSWR(
    `groups-${tenantId}`,
    async () => {
      const response = await getGroups(tenantId, {
        max_results: 1000,
      });
      return response.data;
    },
  );

  const allUsers = usersData?.users || initialUsers;
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
        <h2 className="text-xl font-semibold">Users</h2>
        <div className="flex space-x-2">
          <Dialog
            open={isCreateDialogOpen}
            onOpenChange={setIsCreateDialogOpen}
          >
            <DialogTrigger asChild>
              <Button>
                <Plus className="mr-2" size={16} />
                Add User
              </Button>
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
                <TableRow key={user.username}>
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
