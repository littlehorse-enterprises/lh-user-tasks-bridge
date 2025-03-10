"use client";

import { useTenantId } from "@/app/[tenantId]/layout";
import { Button } from "@/components/ui/button";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import UserRepresentation from "keycloak-admin/lib/defs/userRepresentation";
import {
  Edit,
  MoreHorizontal,
  Plus,
  Search,
  Trash2,
  Users,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import {
  createUser,
  deleteUser,
  listUsers,
  resetUserPassword,
  toggleUserAdminRole,
  updateUser,
} from "../actions/users-groups-management";

// Add a custom hook for debouncing values
function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(timer);
    };
  }, [value, delay]);

  return debouncedValue;
}

interface KeycloakUser {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  emailVerified: boolean;
  createdTimestamp: number;
}

const cacheDuration = 60 * 1000; // 1 minute in milliseconds

export default function UsersManagement() {
  const tenantId = useTenantId();
  const [users, setUsers] = useState<UserRepresentation[]>([]);
  const [isAddUserDialogOpen, setIsAddUserDialogOpen] = useState(false);
  const [isEditUserDialogOpen, setIsEditUserDialogOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const debouncedSearchQuery = useDebounce(searchQuery, 100);
  const searchInputRef = useRef<HTMLInputElement>(null);

  // Add these refs for caching
  const cacheRef = useRef<{
    query: string;
    timestamp: number;
    users: UserRepresentation[];
  } | null>(null);

  // Form state for new/edit user
  const [userData, setUserData] = useState({
    username: "",
    email: "",
    firstName: "",
    lastName: "",
    enabled: true,
    password: "",
    confirmPassword: "",
    temporaryPassword: true,
  });
  const [selectedUser, setSelectedUser] = useState<UserRepresentation | null>(
    null,
  );

  // Fetch users
  useEffect(() => {
    fetchUsers();
  }, [debouncedSearchQuery]);

  // Add this useEffect to maintain focus
  useEffect(() => {
    // Only attempt to restore focus if loading has completed
    if (!isLoading) {
      searchInputRef.current?.focus();
    }
  }, [isLoading]);

  const fetchUsers = async (forceFresh = false) => {
    // If forceFresh is true, skip the cache check
    const now = Date.now();
    if (
      !forceFresh &&
      cacheRef.current &&
      cacheRef.current.query === debouncedSearchQuery &&
      now - cacheRef.current.timestamp < cacheDuration
    ) {
      // Use cached result but only if it's different from current state
      // This prevents unnecessary re-renders
      if (JSON.stringify(cacheRef.current.users) !== JSON.stringify(users)) {
        setUsers(cacheRef.current.users);
      }
      return;
    }

    setIsLoading(true);
    try {
      const result = await listUsers(tenantId, debouncedSearchQuery);
      if (result.users) {
        // Only update state if the data has actually changed
        if (JSON.stringify(result.users) !== JSON.stringify(users)) {
          setUsers(result.users);
        }

        // Cache the result with timestamp
        cacheRef.current = {
          query: debouncedSearchQuery,
          timestamp: now,
          users: result.users,
        };
      } else {
        toast.error(result.message || "Failed to load users");
      }
    } catch (error) {
      console.error("Error fetching users:", error);
      toast.error("Failed to load users");
    } finally {
      setIsLoading(false);
    }
  };

  const handleCreateUser = async () => {
    // Validate form
    if (!userData.username || !userData.email) {
      toast.error("Username and email are required");
      return;
    }

    if (userData.password !== userData.confirmPassword) {
      toast.error("Passwords do not match");
      return;
    }

    try {
      const result = await createUser(tenantId, {
        username: userData.username,
        email: userData.email,
        firstName: userData.firstName,
        lastName: userData.lastName,
        enabled: userData.enabled,
        ...(userData.password
          ? {
              password: userData.password,
              temporaryPassword: userData.temporaryPassword,
            }
          : {}),
      });

      if (result.success) {
        toast.success(`User "${userData.username}" created successfully`);
        resetForm();
        setIsAddUserDialogOpen(false);
        // Force a fresh fetch, bypassing the cache
        fetchUsers(true);
      } else {
        toast.error(result.message || "Failed to create user");
      }
    } catch (error) {
      console.error("Error creating user:", error);
      toast.error("Failed to create user");
    }
  };

  const handleUpdateUser = async () => {
    if (!selectedUser?.id) return;

    try {
      // Update user details
      const updateResult = await updateUser(tenantId, {
        id: selectedUser.id,
        username: userData.username,
        email: userData.email,
        firstName: userData.firstName,
        lastName: userData.lastName,
        enabled: userData.enabled,
      });

      if (!updateResult.success) {
        toast.error(updateResult.message || "Failed to update user");
        return;
      }

      // Update password if provided
      if (userData.password) {
        if (userData.password !== userData.confirmPassword) {
          toast.error("Passwords do not match");
          return;
        }

        const passwordResult = await resetUserPassword(
          tenantId,
          selectedUser.id,
          userData.password,
        );

        if (!passwordResult.success) {
          toast.error(passwordResult.message || "Failed to update password");
          return;
        }
      }

      toast.success(`User "${userData.username}" updated successfully`);
      resetForm();
      setIsEditUserDialogOpen(false);
      // Force a fresh fetch, bypassing the cache
      fetchUsers(true);
    } catch (error) {
      console.error("Error updating user:", error);
      toast.error("Failed to update user");
    }
  };

  const handleDeleteUser = async (user: UserRepresentation) => {
    if (!user.id) return;
    if (!confirm(`Are you sure you want to delete user "${user.username}"?`)) {
      return;
    }

    try {
      const result = await deleteUser(tenantId, user.id);
      if (result.success) {
        toast.success(`User "${user.username}" deleted successfully`);
        // Force a fresh fetch, bypassing the cache
        fetchUsers(true);
      } else {
        toast.error(result.message || "Failed to delete user");
      }
    } catch (error) {
      console.error("Error deleting user:", error);
      toast.error("Failed to delete user");
    }
  };

  const resetForm = () => {
    setUserData({
      username: "",
      email: "",
      firstName: "",
      lastName: "",
      enabled: true,
      password: "",
      confirmPassword: "",
      temporaryPassword: true,
    });
    setSelectedUser(null);
  };

  const openEditDialog = (user: UserRepresentation) => {
    setSelectedUser(user);
    setUserData({
      username: user.username || "",
      email: user.email || "",
      firstName: user.firstName || "",
      lastName: user.lastName || "",
      enabled: user.enabled || false,
      password: "",
      confirmPassword: "",
      temporaryPassword: true,
    });
    setIsEditUserDialogOpen(true);
  };

  // Add this new function to handle role changes
  const handleRoleChange = async (
    user: UserRepresentation,
    isAdmin: boolean,
  ) => {
    if (!user.id) return;

    try {
      const result = await toggleUserAdminRole(tenantId, user.id, isAdmin);

      if (result.success) {
        toast.success(
          `User "${user.username}" ${isAdmin ? "is now an admin" : "is now a regular user"}`,
        );
        // Force a fresh fetch to update the UI
        fetchUsers(true);
      } else {
        toast.error(result.message || "Failed to update user role");
      }
    } catch (error) {
      console.error("Error updating user role:", error);
      toast.error("Failed to update user role");
    }
  };

  console.log(users);

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h2 className="text-2xl font-bold">Users</h2>
        <Dialog
          open={isAddUserDialogOpen}
          onOpenChange={setIsAddUserDialogOpen}
        >
          <DialogTrigger asChild>
            <Button>
              <Plus className="mr-2 h-4 w-4" /> Add User
            </Button>
          </DialogTrigger>
          <DialogContent className="max-w-md">
            <DialogHeader>
              <DialogTitle>Create New User</DialogTitle>
            </DialogHeader>
            <div className="grid gap-4 py-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <Label htmlFor="firstName">First Name</Label>
                  <Input
                    id="firstName"
                    value={userData.firstName}
                    onChange={(e) =>
                      setUserData({ ...userData, firstName: e.target.value })
                    }
                    className="mt-1"
                  />
                </div>
                <div>
                  <Label htmlFor="lastName">Last Name</Label>
                  <Input
                    id="lastName"
                    value={userData.lastName}
                    onChange={(e) =>
                      setUserData({ ...userData, lastName: e.target.value })
                    }
                    className="mt-1"
                  />
                </div>
              </div>
              <div>
                <Label htmlFor="username">Username*</Label>
                <Input
                  id="username"
                  value={userData.username}
                  onChange={(e) =>
                    setUserData({ ...userData, username: e.target.value })
                  }
                  className="mt-1"
                  required
                />
              </div>
              <div>
                <Label htmlFor="email">Email*</Label>
                <Input
                  id="email"
                  type="email"
                  value={userData.email}
                  onChange={(e) =>
                    setUserData({ ...userData, email: e.target.value })
                  }
                  className="mt-1"
                  required
                />
              </div>
              <div>
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  type="password"
                  value={userData.password}
                  onChange={(e) =>
                    setUserData({ ...userData, password: e.target.value })
                  }
                  className="mt-1"
                />
              </div>
              <div>
                <Label htmlFor="confirmPassword">Confirm Password</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  value={userData.confirmPassword}
                  onChange={(e) =>
                    setUserData({
                      ...userData,
                      confirmPassword: e.target.value,
                    })
                  }
                  className="mt-1"
                />
              </div>
              <div className="flex items-center gap-2">
                <input
                  id="temporaryPassword"
                  type="checkbox"
                  checked={userData.temporaryPassword}
                  onChange={(e) =>
                    setUserData({
                      ...userData,
                      temporaryPassword: e.target.checked,
                    })
                  }
                />
                <Label htmlFor="temporaryPassword">
                  Require password change on first login
                </Label>
              </div>
            </div>
            <DialogFooter>
              <Button
                variant="outline"
                onClick={() => {
                  resetForm();
                  setIsAddUserDialogOpen(false);
                }}
              >
                Cancel
              </Button>
              <Button onClick={handleCreateUser}>Create User</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      {/* Search */}
      <div className="relative flex-1">
        <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input
          ref={searchInputRef}
          placeholder="Search users by username..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="pl-8"
        />
      </div>

      {users.length === 0 ? (
        <div className="text-center py-10">
          <p className="text-lg text-muted-foreground">
            {isLoading ? "Loading users..." : "No users found"}
          </p>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ID</TableHead>
              <TableHead>Username</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Name</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Admin</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.id}>
                <TableCell className="font-mono text-xs">{user.id}</TableCell>
                <TableCell>{user.username}</TableCell>
                <TableCell>{user.email}</TableCell>
                <TableCell>
                  {user.firstName} {user.lastName}
                </TableCell>
                <TableCell>
                  <span
                    className={`px-2 py-1 rounded text-xs ${
                      user.enabled
                        ? "bg-green-500 text-white"
                        : "bg-destructive text-destructive-foreground"
                    }`}
                  >
                    {user.enabled ? "Active" : "Disabled"}
                  </span>
                </TableCell>
                <TableCell>
                  <span
                    className={`px-2 py-1 rounded text-xs ${
                      user.realmRoles?.includes("lh-user-tasks-admin")
                        ? "bg-primary text-primary-foreground"
                        : "bg-muted text-muted-foreground"
                    }`}
                  >
                    {user.realmRoles?.includes("lh-user-tasks-admin")
                      ? "Admin"
                      : "User"}
                  </span>
                </TableCell>
                <TableCell className="text-right">
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon">
                        <MoreHorizontal className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={() => openEditDialog(user)}>
                        <Edit className="h-4 w-4 mr-2" />
                        Edit
                      </DropdownMenuItem>
                      {!user.realmRoles?.includes("lh-user-tasks-admin") && (
                        <DropdownMenuItem
                          onClick={() => handleRoleChange(user, true)}
                        >
                          <Users className="h-4 w-4 mr-2" />
                          Set as Admin
                        </DropdownMenuItem>
                      )}
                      {user.realmRoles?.includes("lh-user-tasks-admin") && (
                        <DropdownMenuItem
                          onClick={() => handleRoleChange(user, false)}
                        >
                          <Users className="h-4 w-4 mr-2" />
                          Set as User
                        </DropdownMenuItem>
                      )}
                      <DropdownMenuItem
                        className="text-destructive"
                        onClick={() => handleDeleteUser(user)}
                      >
                        <Trash2 className="h-4 w-4 mr-2" />
                        Delete
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {/* Edit User Dialog */}
      <Dialog
        open={isEditUserDialogOpen}
        onOpenChange={setIsEditUserDialogOpen}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Edit User</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label htmlFor="editFirstName">First Name</Label>
                <Input
                  id="editFirstName"
                  value={userData.firstName}
                  onChange={(e) =>
                    setUserData({ ...userData, firstName: e.target.value })
                  }
                  className="mt-1"
                />
              </div>
              <div>
                <Label htmlFor="editLastName">Last Name</Label>
                <Input
                  id="editLastName"
                  value={userData.lastName}
                  onChange={(e) =>
                    setUserData({ ...userData, lastName: e.target.value })
                  }
                  className="mt-1"
                />
              </div>
            </div>
            <div>
              <Label htmlFor="editUsername">Username*</Label>
              <Input
                id="editUsername"
                value={userData.username}
                onChange={(e) =>
                  setUserData({ ...userData, username: e.target.value })
                }
                className="mt-1"
                required
              />
            </div>
            <div>
              <Label htmlFor="editEmail">Email*</Label>
              <Input
                id="editEmail"
                type="email"
                value={userData.email}
                onChange={(e) =>
                  setUserData({ ...userData, email: e.target.value })
                }
                className="mt-1"
                required
              />
            </div>
            <div className="flex items-center gap-2">
              <input
                id="editEnabled"
                type="checkbox"
                checked={userData.enabled}
                onChange={(e) =>
                  setUserData({ ...userData, enabled: e.target.checked })
                }
              />
              <Label htmlFor="editEnabled">Account Enabled</Label>
            </div>
            <div>
              <Label htmlFor="editPassword">
                Reset Password (leave blank to keep current)
              </Label>
              <Input
                id="editPassword"
                type="password"
                value={userData.password}
                onChange={(e) =>
                  setUserData({ ...userData, password: e.target.value })
                }
                className="mt-1"
              />
            </div>
            {userData.password && (
              <div>
                <Label htmlFor="editConfirmPassword">Confirm Password</Label>
                <Input
                  id="editConfirmPassword"
                  type="password"
                  value={userData.confirmPassword}
                  onChange={(e) =>
                    setUserData({
                      ...userData,
                      confirmPassword: e.target.value,
                    })
                  }
                  className="mt-1"
                />
              </div>
            )}
            {userData.password && (
              <div className="flex items-center gap-2 mt-2">
                <input
                  id="editTemporaryPassword"
                  type="checkbox"
                  checked={userData.temporaryPassword}
                  onChange={(e) =>
                    setUserData({
                      ...userData,
                      temporaryPassword: e.target.checked,
                    })
                  }
                />
                <Label htmlFor="editTemporaryPassword">
                  Require password change on first login
                </Label>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                resetForm();
                setIsEditUserDialogOpen(false);
              }}
            >
              Cancel
            </Button>
            <Button onClick={handleUpdateUser}>Update User</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
