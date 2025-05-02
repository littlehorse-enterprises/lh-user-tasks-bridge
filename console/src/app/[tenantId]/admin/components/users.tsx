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
	IDPUserDTO
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { Eye, EyeOff } from "lucide-react";
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

const formSchema = z.object({
	username: z.string().min(2, {
		message: "Username must be at least 2 characters.",
	}),
	password: z.string().min(6, {
		message: "Password must be at least 6 characters.",
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
	password: z.string().optional(),
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

export default function UsersManagement() {
	const tenantId = useParams().tenantId as string;
	
	const [users, setUsers] = useState<IDPUserDTO[]>([]);
	const [groups, setGroups] = useState<IDPGroupDTO[]>([]);
	const [isLoading, setIsLoading] = useState(true);
	const [isGroupsLoading, setIsGroupsLoading] = useState(true);
	const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
	const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
	const [isGroupsDialogOpen, setIsGroupsDialogOpen] = useState(false);
	const [selectedUser, setSelectedUser] = useState<IDPUserDTO | null>(null);
	const [userGroups, setUserGroups] = useState<string[]>([]);
	const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
	const [isAccountEnabled, setIsAccountEnabled] = useState(true);
	const [showPassword, setShowPassword] = useState(false);

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
			password: "",
			email: "",
			firstName: "",
			lastName: "",
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
			setUsers(response.users || []);
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
			setGroups(response.groups || []);
		} catch (error) {
			toast.error("Failed to load groups.");
			console.error("Error loading groups:", error);
		} finally {
			setIsGroupsLoading(false);
		}
	}

	async function handleCreateUser(values: z.infer<typeof formSchema>) {
		try {
			const response = await createUser(tenantId, {
				username: values.username,
				email: values.email,
				firstName: values.firstName,
				lastName: values.lastName,
				password: values.password,
				tempPassword: true,
				enabled: true,
			});
			
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
			
			// Note: Since enabled property doesn't exist on IDPUserDTO type,
			// we might need a different API call to toggle enabled status, or
			// this property might be sent differently in the actual implementation
			
			await updateUser(
				tenantId,
				{ user_id: selectedUser.username },
				updateData
			);

			if (values.password && values.password.length > 0) {
				await upsertPassword(
					tenantId,
					{ user_id: values.username },
					{ value: values.password }
				);
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

	async function handleDeleteUser(userId: string) {
		if (!confirm("Are you sure you want to delete this user?")) {
			return;
		}
		
		try {
			await deleteUser(tenantId, { user_id: userId });
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
			if (userResponse) {
				setSelectedUser(userResponse);
				setIsAccountEnabled(true);
				editForm.reset({
					username: userResponse.username || "",
					password: "",  // Don't set password for security reasons
					email: userResponse.email || "",
					firstName: userResponse.firstName || "",
					lastName: userResponse.lastName || "",
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

	async function handleManageGroups(user: IDPUserDTO) {
		setSelectedUser(user);
		setUserGroups(user.groups?.map(g => g.id) || []);
		setSelectedGroups(user.groups?.map(g => g.id) || []);
		setIsGroupsDialogOpen(true);
	}

	async function handleToggleGroup(userId: string, groupId: string, isActive: boolean) {
		try {
			if (isActive) {
				// Add user to group
				await addUserToGroup(tenantId, { 
					user_id: userId, 
					group_id: groupId 
				});
				toast.success("User added to group successfully.");
			} else {
				// Remove user from group
				await removeUserFromGroup(tenantId, { 
					user_id: userId, 
					group_id: groupId 
				});
				toast.success("User removed from group successfully.");
			}
			
			// Update local state
			if (isActive) {
				setSelectedGroups(prev => [...prev, groupId]);
			} else {
				setSelectedGroups(prev => prev.filter(id => id !== groupId));
			}
		} catch (error) {
			toast.error(isActive ? "Failed to add user to group." : "Failed to remove user from group.");
			console.error("Error updating group membership:", error);
		}
	}

	async function toggleAdminRole(user: IDPUserDTO) {
		const hasAdminRole = user.realmRoles?.includes('lh-user-tasks-admin') || false;
		
		try {
			if (hasAdminRole) {
				await removeAdminRole(tenantId, { user_id: user.id });
				toast.success("Admin role removed successfully.");
			} else {
				await assignAdminRole(tenantId, { user_id: user.id });
				toast.success("Admin role assigned successfully.");
			}
			loadUsers();
		} catch (error) {
			toast.error("Failed to update admin role.");
			console.error("Error updating admin role:", error);
		}
	}

	return (
		<div className="space-y-4">
			<div className="flex justify-between items-center">
				<h2 className="text-xl font-semibold">Users Management</h2>
				<Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
					<DialogTrigger asChild>
						<Button>Add User</Button>
					</DialogTrigger>
					<DialogContent>
						<DialogHeader>
							<DialogTitle>Create New User</DialogTitle>
						</DialogHeader>
						<Form {...createForm}>
							<form onSubmit={createForm.handleSubmit(handleCreateUser)} className="space-y-4">
								<div className="grid grid-cols-2 gap-4">
									<FormField
										control={createForm.control}
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
										control={createForm.control}
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
											<FormLabel>Password</FormLabel>
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
									Users will be required to change their password on first login.
								</div>
								<DialogFooter className="gap-2">
									<Button variant="outline" type="button" onClick={() => setIsCreateDialogOpen(false)}>
										Cancel
									</Button>
									<Button type="submit">Create User</Button>
								</DialogFooter>
							</form>
						</Form>
					</DialogContent>
				</Dialog>
			</div>

			{isLoading ? (
				<div className="py-4 text-center">Loading users...</div>
			) : users.length === 0 ? (
				<div className="py-4 text-center">
					<h3 className="text-lg font-medium">No users found</h3>
					<p className="text-muted-foreground">Create a new user to get started.</p>
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
							<TableHead className="text-right">Actions</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{users.map((user) => (
							<TableRow key={user.username}>
								<TableCell>{user.id}</TableCell>
								<TableCell>{user.username}</TableCell>
								<TableCell>{user.email}</TableCell>
								<TableCell>{`${user.firstName || ''} ${user.lastName || ''}`}</TableCell>
								<TableCell>
									<span className="inline-block py-1 px-2 text-xs rounded-md bg-green-100 text-green-800">
										Active
									</span>
								</TableCell>
								<TableCell className="text-right">
									<div className="flex justify-end space-x-2">
										<Button 
											variant="ghost" 
											size="icon"
											onClick={() => handleEditClick(user.id)}
											className="h-8 w-8"
										>
											<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-pencil">
												<path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
												<path d="m15 5 4 4" />
											</svg>
										</Button>
										<Button 
											variant="ghost" 
											size="icon"
											onClick={() => handleManageGroups(user)}
											className="h-8 w-8"
										>
											<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
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
											<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-trash">
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
						<form onSubmit={editForm.handleSubmit(handleEditUser)} className="space-y-4">
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
							<div className="flex items-center space-x-2">
								<Checkbox 
									id="account-enabled"
									checked={isAccountEnabled}
									onCheckedChange={(checked) => {
										setIsAccountEnabled(checked === true);
									}}
								/>
								<label
									htmlFor="account-enabled"
									className="text-sm font-medium leading-none"
								>
									Account Enabled
								</label>
							</div>
							<FormField
								control={editForm.control}
								name="password"
								render={({ field }) => (
									<FormItem>
										<FormLabel>Reset Password (leave blank to keep current)</FormLabel>
										<FormControl>
											<Input type="password" {...field} />
										</FormControl>
										<FormMessage />
									</FormItem>
								)}
							/>
							<DialogFooter className="gap-2">
								<Button variant="outline" type="button" onClick={() => setIsEditDialogOpen(false)}>
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
						<DialogTitle>Manage Groups for User: {selectedUser?.username}</DialogTitle>
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
										<div key={group.id} className="flex items-center justify-between border p-3 rounded-md">
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
															handleToggleGroup(selectedUser.id, group.id, checked);
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
		</div>
	);
}
