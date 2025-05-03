"use client";

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
import {
  IDPGroupDTO,
  IDPUserDTO,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { Eye, EyeOff } from "lucide-react";
import { useSession } from "next-auth/react";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
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

const bulkCreateSchema = z.object({
  users: z.string().min(1, {
    message: "Please provide at least one user to create",
  }),
});

const formSchema = z.object({
  username: z.string().min(2, {
    message: "Username must be at least 2 characters.",
  }),
  password: z.string().min(4, {
    message: "Password must be at least 4 characters.",
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

  const [users, setUsers] = useState<IDPUserDTO[]>([]);
  const [groups, setGroups] = useState<IDPGroupDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isGroupsLoading, setIsGroupsLoading] = useState(true);
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isBulkCreateDialogOpen, setIsBulkCreateDialogOpen] = useState(false);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [isGroupsDialogOpen, setIsGroupsDialogOpen] = useState(false);
  const [isBulkGroupsDialogOpen, setIsBulkGroupsDialogOpen] = useState(false);
  const [isPasswordResetDialogOpen, setIsPasswordResetDialogOpen] =
    useState(false);
  const [selectedUser, setSelectedUser] = useState<IDPUserDTO | null>(null);
  const [userGroups, setUserGroups] = useState<string[]>([]);
  const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
  const [isAccountEnabled, setIsAccountEnabled] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
  const [selectedUsers, setSelectedUsers] = useState<string[]>([]);
  const [selectAll, setSelectAll] = useState(false);
  const [bulkCreateResults, setBulkCreateResults] = useState<{
    success: number;
    errors: { username: string; error: string }[];
  }>({
    success: 0,
    errors: [],
  });
  const [isBulkCreateLoading, setIsBulkCreateLoading] = useState(false);

  const bulkCreateForm = useForm<z.infer<typeof bulkCreateSchema>>({
    resolver: zodResolver(bulkCreateSchema),
    defaultValues: {
      users: "",
    },
  });

  const createForm = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      username: "",
      password: "",
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

  useEffect(() => {
    loadUsers();
    loadGroups();
  }, [tenantId]);

  async function loadUsers() {
    setIsLoading(true);
    try {
      const response = await getUsersFromIdP(tenantId, {});
      // Sort users by last name (case-insensitive)
      const sortedUsers = [...(response.data?.users || [])].sort((a, b) => {
        const lastNameA = (a.lastName || "").toLowerCase();
        const lastNameB = (b.lastName || "").toLowerCase();
        return lastNameA.localeCompare(lastNameB);
      });
      setUsers(sortedUsers);
    } catch (error) {
      toast.error("Failed to load users from identity provider.");
      console.error("Error loading users:", error);
    } finally {
      setIsLoading(false);
    }
  }

  async function loadGroups() {
    setIsGroupsLoading(true);
    try {
      const response = await getGroups(tenantId, {});
      // Sort groups by name (case-insensitive)
      const sortedGroups = [...(response.data?.groups || [])].sort((a, b) => {
        const nameA = (a.name || "").toLowerCase();
        const nameB = (b.name || "").toLowerCase();
        return nameA.localeCompare(nameB);
      });
      setGroups(sortedGroups);
    } catch (error) {
      toast.error("Failed to load groups.");
      console.error("Error loading groups:", error);
    } finally {
      setIsGroupsLoading(false);
    }
  }

  async function handleBulkCreateUsers(
    values: z.infer<typeof bulkCreateSchema>,
  ) {
    setIsBulkCreateLoading(true);
    setBulkCreateResults({ success: 0, errors: [] });

    try {
      const userLines = values.users
        .split("\n")
        .filter((line) => line.trim() !== "");
      let successCount = 0;
      const errors: { username: string; error: string }[] = [];

      for (const userLine of userLines) {
        try {
          // Expected format: username,email,firstName,lastName,password
          const [username, email, firstName, lastName, password] = userLine
            .split(",")
            .map((s) => s.trim());

          if (!username || !email || !firstName || !lastName) {
            errors.push({
              username: username || "Unknown",
              error:
                "All fields (username, email, firstName, lastName) are required",
            });
            continue;
          }

          const response = await createUser(tenantId, {
            username,
            email,
            firstName,
            lastName,
          });

          if (response.error) {
            errors.push({
              username: username,
              error: response.error.message || "Unknown error",
            });
            continue;
          }

          // Set password separately
          if (password) {
            const passwordResponse = await upsertPassword(
              tenantId,
              { user_id: username },
              { value: password, temporary: true },
            );

            if (passwordResponse.error) {
              errors.push({
                username: username,
                error: `User created but password setting failed: ${passwordResponse.error.message || "Unknown error"}`,
              });
              continue;
            }
          } else {
            errors.push({
              username: username,
              error:
                "Password is required. User created but no password was set.",
            });
            continue;
          }

          successCount++;
        } catch (error: any) {
          const userInfo = userLine.split(",")[0] || "Unknown";
          errors.push({
            username: userInfo,
            error: error.message || "Unknown error",
          });
        }
      }

      setBulkCreateResults({
        success: successCount,
        errors,
      });

      if (successCount > 0) {
        toast.success(`Successfully created ${successCount} users`);
        loadUsers();
      }
    } catch (error) {
      console.error("Error in bulk user creation:", error);
      toast.error("Failed to process bulk user creation");
    } finally {
      setIsBulkCreateLoading(false);
    }
  }

  async function handleCreateUser(values: z.infer<typeof formSchema>) {
    try {
      // First create the user without password
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

      // Set password separately
      const passwordResponse = await upsertPassword(
        tenantId,
        { user_id: values.username },
        { value: values.password, temporary: true },
      );

      if (passwordResponse.error) {
        toast.error(
          `User created but password setting failed: ${passwordResponse.error.message || "Unknown error"}`,
        );
        console.error("Error setting password:", passwordResponse.error);
        return;
      }

      toast.success("User was successfully created.");
      setIsCreateDialogOpen(false);
      createForm.reset();
      loadUsers();
    } catch (error) {
      console.error("Detailed error creating user:", error);
      toast.error("Failed to create user. See console for details.");
    }
  }

  async function handleEditUser(values: z.infer<typeof editFormSchema>) {
    if (!selectedUser) return;

    try {
      // Create update object without the enabled property
      const updateData = {
        username: values.username,
        email: values.email,
        firstName: values.firstName,
        lastName: values.lastName,
      };

      const updateResponse = await updateUser(
        tenantId,
        { user_id: selectedUser.username },
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
      loadUsers();
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
        { user_id: selectedUser.username },
        { value: values.password, temporary: true },
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
      loadUsers();
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
        setIsAccountEnabled(true);
        editForm.reset({
          username: userResponse.data.username || "",
          email: userResponse.data.email || "",
          firstName: userResponse.data.firstName || "",
          lastName: userResponse.data.lastName || "",
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
    setUserGroups(user.groups?.map((g) => g.id) || []);
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
        // Add user to group
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
        // Remove user from group
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
      loadUsers();
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

      // Delete each selected user
      for (const userId of selectedUsers) {
        const response = await deleteUser(tenantId, { user_id: userId });
        if (response.error) {
          errorCount++;
          console.error(`Error deleting user ${userId}:`, response.error);
        } else {
          successCount++;
        }
      }

      if (errorCount > 0) {
        toast.error(
          `Failed to delete ${errorCount} of ${selectedUsers.length} users.`,
        );
      }

      if (successCount > 0) {
        toast.success(`Successfully deleted ${successCount} users.`);
        setSelectedUsers([]);
        loadUsers();
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
      loadUsers();
    } catch (error) {
      toast.error("Failed to add users to groups.");
      console.error("Error adding users to groups:", error);
    }
  }

  useEffect(() => {
    if (selectAll) {
      setSelectedUsers(users.map((user) => user.id));
    } else if (selectedUsers.length === users.length) {
      setSelectedUsers([]);
    }
  }, [selectAll]);

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
            open={isBulkCreateDialogOpen}
            onOpenChange={setIsBulkCreateDialogOpen}
          >
            <DialogTrigger asChild>
              <Button variant="outline" className="space-x-1">
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
                </svg>
                <span>Bulk Create</span>
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-[600px]">
              <DialogHeader>
                <DialogTitle>Bulk Create Users</DialogTitle>
              </DialogHeader>
              <Form {...bulkCreateForm}>
                <form
                  onSubmit={bulkCreateForm.handleSubmit(handleBulkCreateUsers)}
                  className="space-y-4"
                >
                  <div className="text-sm text-muted-foreground mb-2">
                    Enter one user per line in the format:{" "}
                    <span className="font-mono">
                      username,email,firstName,lastName,password
                    </span>
                    <br />
                    All fields are required. Passwords will be set as temporary
                    and users will need to change them on first login.
                  </div>
                  <FormField
                    control={bulkCreateForm.control}
                    name="users"
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <textarea
                            className="w-full h-60 p-2 border rounded-md font-mono text-sm"
                            placeholder="user1,user1@example.com,First,Last,password123&#10;user2,user2@example.com,First,Last,password123"
                            {...field}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  {bulkCreateResults.errors.length > 0 && (
                    <div className="border border-red-200 bg-red-50 p-3 rounded-md text-sm">
                      <div className="font-medium text-red-800 mb-2">
                        Failed to create {bulkCreateResults.errors.length}{" "}
                        users:
                      </div>
                      <div className="max-h-40 overflow-y-auto">
                        {bulkCreateResults.errors.map((error, i) => (
                          <div key={i} className="flex mb-1">
                            <span className="font-mono mr-2">
                              {error.username}:
                            </span>
                            <span>{error.error}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {bulkCreateResults.success > 0 && (
                    <div className="border border-green-200 bg-green-50 p-3 rounded-md">
                      <div className="font-medium text-green-800">
                        Successfully created {bulkCreateResults.success} users
                      </div>
                    </div>
                  )}

                  <DialogFooter className="gap-2">
                    <Button
                      variant="outline"
                      type="button"
                      onClick={() => setIsBulkCreateDialogOpen(false)}
                    >
                      Close
                    </Button>
                    <Button
                      type="submit"
                      className="bg-yellow-400 hover:bg-yellow-500 text-black"
                      disabled={isBulkCreateLoading}
                    >
                      {isBulkCreateLoading
                        ? "Creating Users..."
                        : "Create Users"}
                    </Button>
                  </DialogFooter>
                </form>
              </Form>
            </DialogContent>
          </Dialog>

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
                  <FormField
                    control={createForm.control}
                    name="password"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Password*</FormLabel>
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
                    Users will be required to change their password on first
                    login.
                  </div>
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

      {isLoading ? (
        <div className="py-4 text-center">Loading users...</div>
      ) : users.length === 0 ? (
        <div className="py-4 text-center">
          <h3 className="text-lg font-medium">No users found</h3>
          <p className="text-muted-foreground">
            Create a new user to get started.
          </p>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-12">
                <Checkbox
                  checked={
                    selectAll ||
                    (selectedUsers.length > 0 &&
                      selectedUsers.length === users.length)
                  }
                  onCheckedChange={(checked) => {
                    setSelectAll(checked === true);
                  }}
                />
              </TableHead>
              <TableHead>ID</TableHead>
              <TableHead>Username</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Name</TableHead>
              <TableHead>Admin</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((user) => (
              <TableRow
                key={user.username}
                className={selectedUsers.includes(user.id) ? "bg-muted/50" : ""}
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
                        selectedUsers.length + 1 === users.length
                      ) {
                        setSelectAll(true);
                      } else if (!checked && selectAll) {
                        setSelectAll(false);
                      }
                    }}
                  />
                </TableCell>
                <TableCell>{user.id}</TableCell>
                <TableCell>{user.username}</TableCell>
                <TableCell>{user.email}</TableCell>
                <TableCell>{`${user.firstName || ""} ${user.lastName || ""}`}</TableCell>
                <TableCell>
                  <Switch
                    checked={
                      user.realmRoles?.includes("lh-user-tasks-admin") || false
                    }
                    onCheckedChange={() => toggleAdminRole(user)}
                    disabled={user.id === currentUserId}
                  />
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end space-x-2">
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleEditClick(user.id)}
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
                        className="lucide lucide-pencil"
                      >
                        <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                        <path d="m15 5 4 4" />
                      </svg>
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleResetPasswordClick(user)}
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
                        <rect
                          x="3"
                          y="11"
                          width="18"
                          height="11"
                          rx="2"
                          ry="2"
                        ></rect>
                        <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                      </svg>
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleManageGroups(user)}
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
                      onClick={() => handleDeleteUser(user.id)}
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
                        className="lucide lucide-trash"
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
                      <Input {...field} readOnly />
                    </FormControl>
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
          {isGroupsLoading ? (
            <div className="py-4 text-center">Loading groups...</div>
          ) : groups.length === 0 ? (
            <div className="py-4 text-center">
              <p className="text-muted-foreground">No groups available.</p>
            </div>
          ) : (
            <div className="py-4">
              <div className="space-y-2 max-h-[400px] overflow-y-auto pr-2">
                {groups.map((group) => {
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
          {isGroupsLoading ? (
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
                {groups.map((group) => (
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
