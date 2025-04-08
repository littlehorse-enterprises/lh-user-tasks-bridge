"use client";

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
import GroupRepresentation from "keycloak-admin/lib/defs/groupRepresentation";
import UserRepresentation from "keycloak-admin/lib/defs/userRepresentation";
import { Edit, MoreHorizontal, Plus, Trash2, Users } from "lucide-react";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
  addUserToGroup,
  createGroup,
  deleteGroup,
  listGroupMembers,
  listGroups,
  listUsers,
  removeUserFromGroup,
  updateGroup,
} from "../actions/users-and-groups-management";

export default function GroupsManagement() {
  const tenantId = useParams().tenantId as string;
  const [groups, setGroups] = useState<GroupRepresentation[]>([]);
  const [isAddGroupDialogOpen, setIsAddGroupDialogOpen] = useState(false);
  const [isEditGroupDialogOpen, setIsEditGroupDialogOpen] = useState(false);
  const [isManageMembersDialogOpen, setIsManageMembersDialogOpen] =
    useState(false);
  const [newGroupName, setNewGroupName] = useState("");
  const [selectedGroup, setSelectedGroup] =
    useState<GroupRepresentation | null>(null);
  const [availableUsers, setAvailableUsers] = useState<UserRepresentation[]>(
    [],
  );
  const [groupMembers, setGroupMembers] = useState<UserRepresentation[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Fetch groups
  useEffect(() => {
    fetchGroups();
  }, []);

  const fetchGroups = async () => {
    setIsLoading(true);
    try {
      const result = await listGroups(tenantId);
      if (result.groups) {
        setGroups(result.groups);
      } else {
        toast.error(result.message || "Failed to load groups");
      }
    } catch (error) {
      console.error("Error fetching groups:", error);
      toast.error("Failed to load groups");
    } finally {
      setIsLoading(false);
    }
  };

  const handleCreateGroup = async () => {
    if (!newGroupName.trim()) {
      toast.error("Group name is required");
      return;
    }

    try {
      const result = await createGroup(tenantId, newGroupName);
      if (result.success) {
        toast.success(`Group "${newGroupName}" created successfully`);
        setNewGroupName("");
        setIsAddGroupDialogOpen(false);
        fetchGroups();
      } else {
        toast.error(result.message || "Failed to create group");
      }
    } catch (error) {
      console.error("Error creating group:", error);
      toast.error("Failed to create group");
    }
  };

  const handleUpdateGroup = async () => {
    if (!selectedGroup || !newGroupName.trim()) {
      toast.error("Group name is required");
      return;
    }

    if (!selectedGroup.id) {
      toast.error("Group ID is required");
      return;
    }

    try {
      const result = await updateGroup(tenantId, selectedGroup.id, {
        ...selectedGroup,
        name: newGroupName,
      });
      if (result.success) {
        toast.success(`Group updated to "${newGroupName}"`);
        setNewGroupName("");
        setSelectedGroup(null);
        setIsEditGroupDialogOpen(false);
        fetchGroups();
      } else {
        toast.error(result.message || "Failed to update group");
      }
    } catch (error) {
      console.error("Error updating group:", error);
      toast.error("Failed to update group");
    }
  };

  const handleDeleteGroup = async (group: GroupRepresentation) => {
    if (
      !confirm(`Are you sure you want to delete the group "${group.name}"?`)
    ) {
      return;
    }

    if (!group.id) {
      toast.error("Group ID is required");
      return;
    }

    try {
      const result = await deleteGroup(tenantId, group.id);
      if (result.success) {
        toast.success(`Group "${group.name}" deleted successfully`);
        fetchGroups();
      } else {
        toast.error(result.message || "Failed to delete group");
      }
    } catch (error) {
      console.error("Error deleting group:", error);
      toast.error("Failed to delete group");
    }
  };

  const openManageMembersDialog = async (group: GroupRepresentation) => {
    if (!group.id) {
      toast.error("Group ID is required");
      return;
    }

    setSelectedGroup(group);
    setIsManageMembersDialogOpen(true);
    await Promise.all([fetchGroupMembers(group.id), fetchAvailableUsers()]);
  };

  const fetchGroupMembers = async (groupId: string) => {
    try {
      const result = await listGroupMembers(tenantId, groupId);
      if (result.members) {
        setGroupMembers(result.members);
      } else {
        toast.error(result.message || "Failed to load group members");
      }
    } catch (error) {
      console.error("Error fetching group members:", error);
      toast.error("Failed to load group members");
    }
  };

  const fetchAvailableUsers = async () => {
    try {
      const result = await listUsers(tenantId);
      if (result.users) {
        setAvailableUsers(result.users);
      } else {
        toast.error(result.message || "Failed to load users");
      }
    } catch (error) {
      console.error("Error fetching users:", error);
      toast.error("Failed to load users");
    }
  };

  const handleAddUserToGroup = async (userId: string) => {
    if (!selectedGroup) return;

    if (!selectedGroup.id) return toast.error("Group ID is required");

    try {
      const result = await addUserToGroup(tenantId, userId, selectedGroup.id);
      if (result.success) {
        toast.success("User added to group");
        fetchGroupMembers(selectedGroup.id);
      } else {
        toast.error(result.message || "Failed to add user to group");
      }
    } catch (error) {
      console.error("Error adding user to group:", error);
      toast.error("Failed to add user to group");
    }
  };

  const handleRemoveUserFromGroup = async (userId: string) => {
    if (!selectedGroup) return;
    if (!selectedGroup.id) return toast.error("Group ID is required");

    try {
      const result = await removeUserFromGroup(
        tenantId,
        userId,
        selectedGroup.id,
      );
      if (result.success) {
        toast.success("User removed from group");
        fetchGroupMembers(selectedGroup.id);
      } else {
        toast.error(result.message || "Failed to remove user from group");
      }
    } catch (error) {
      console.error("Error removing user from group:", error);
      toast.error("Failed to remove user from group");
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h2 className="text-2xl font-bold">Groups</h2>
        <Dialog
          open={isAddGroupDialogOpen}
          onOpenChange={setIsAddGroupDialogOpen}
        >
          <DialogTrigger asChild>
            <Button>
              <Plus className="mr-2 h-4 w-4" /> Add Group
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Create New Group</DialogTitle>
            </DialogHeader>
            <div className="py-4">
              <Label htmlFor="groupName">Group Name</Label>
              <Input
                id="groupName"
                value={newGroupName}
                onChange={(e) => setNewGroupName(e.target.value)}
                className="mt-2"
              />
            </div>
            <DialogFooter>
              <Button
                variant="outline"
                onClick={() => setIsAddGroupDialogOpen(false)}
              >
                Cancel
              </Button>
              <Button onClick={handleCreateGroup}>Create Group</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      {groups.length === 0 ? (
        <div className="text-center py-10">
          <p className="text-lg text-muted-foreground">
            {isLoading ? "Loading groups..." : "No groups found"}
          </p>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ID</TableHead>
              <TableHead>Name</TableHead>
              <TableHead>Path</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {groups.map((group) => (
              <TableRow key={group.id}>
                <TableCell className="font-mono text-xs">{group.id}</TableCell>
                <TableCell>{group.name}</TableCell>
                <TableCell>{group.path}</TableCell>
                <TableCell className="text-right">
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon">
                        <MoreHorizontal className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem
                        onClick={() => {
                          setSelectedGroup(group);
                          setNewGroupName(group.name || "");
                          setIsEditGroupDialogOpen(true);
                        }}
                      >
                        <Edit className="h-4 w-4 mr-2" />
                        Edit
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        onClick={() => openManageMembersDialog(group)}
                      >
                        <Users className="h-4 w-4 mr-2" />
                        Manage Members
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        className="text-destructive"
                        onClick={() => handleDeleteGroup(group)}
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

      {/* Edit Group Dialog */}
      <Dialog
        open={isEditGroupDialogOpen}
        onOpenChange={setIsEditGroupDialogOpen}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit Group</DialogTitle>
          </DialogHeader>
          <div className="py-4">
            <Label htmlFor="editGroupName">Group Name</Label>
            <Input
              id="editGroupName"
              value={newGroupName}
              onChange={(e) => setNewGroupName(e.target.value)}
              className="mt-2"
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setIsEditGroupDialogOpen(false)}
            >
              Cancel
            </Button>
            <Button onClick={handleUpdateGroup}>Update Group</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Manage Members Dialog */}
      <Dialog
        open={isManageMembersDialogOpen}
        onOpenChange={setIsManageMembersDialogOpen}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              Manage Group Members: {selectedGroup?.name}
            </DialogTitle>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-4 py-4">
            <div>
              <h3 className="font-medium mb-2">Available Users</h3>
              <div className="border rounded-md h-80 overflow-y-auto">
                <Table>
                  <TableBody>
                    {availableUsers
                      .filter(
                        (user) =>
                          !groupMembers.some((member) => member.id === user.id),
                      )
                      .map((user) => (
                        <TableRow key={user.id}>
                          <TableCell>
                            {user.firstName} {user.lastName} ({user.username})
                          </TableCell>
                          <TableCell className="text-right">
                            <Button
                              size="sm"
                              onClick={() =>
                                user.id
                                  ? handleAddUserToGroup(user.id)
                                  : toast.error("User ID not found")
                              }
                            >
                              Add
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                  </TableBody>
                </Table>
              </div>
            </div>
            <div>
              <h3 className="font-medium mb-2">Group Members</h3>
              <div className="border rounded-md h-80 overflow-y-auto">
                <Table>
                  <TableBody>
                    {groupMembers.map((user) => (
                      <TableRow key={user.id}>
                        <TableCell>
                          {user.firstName} {user.lastName} ({user.username})
                        </TableCell>
                        <TableCell className="text-right">
                          <Button
                            size="sm"
                            variant="destructive"
                            onClick={() =>
                              user.id
                                ? handleRemoveUserFromGroup(user.id)
                                : toast.error("User ID not found")
                            }
                          >
                            Remove
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => setIsManageMembersDialogOpen(false)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
