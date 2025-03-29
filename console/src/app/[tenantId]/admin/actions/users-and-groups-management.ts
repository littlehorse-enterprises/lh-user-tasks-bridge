"use server";

import KcAdminClient from "keycloak-admin";
import GroupRepresentation from "keycloak-admin/lib/defs/groupRepresentation";
import UserRepresentation from "keycloak-admin/lib/defs/userRepresentation";

// Initialize the Keycloak Admin Client
async function getKeycloakAdminClient(tenantId: string) {
  const issuerUrl = process.env.AUTH_KEYCLOAK_ISSUER || "";
  // Extract the base URL (without '/realms/realmName')
  const baseUrlMatch = issuerUrl.match(/(.*?)\/realms\/[^/]+$/);
  const baseUrl = baseUrlMatch && baseUrlMatch[1] ? baseUrlMatch[1] : issuerUrl;

  const kcAdminClient = new KcAdminClient({
    baseUrl,
    realmName: tenantId,
  });

  await kcAdminClient.auth({
    clientId: process.env.AUTH_KEYCLOAK_ID as string,
    clientSecret: process.env.AUTH_KEYCLOAK_SECRET as string,
    grantType: "client_credentials",
  });

  return kcAdminClient;
}

// Users API
export async function listUsers(tenantId: string, search?: string) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    // Get all users matching the search query
    const users = await kcAdminClient.users.find({
      search: search || undefined,
      max: 100,
    });

    // Fetch role information for each user
    const enrichedUsers = await Promise.all(
      users.map(async (user) => {
        if (!user.id) return user;

        try {
          // Get realm role mappings for the user
          const roleMappings = await kcAdminClient.users.listRealmRoleMappings({
            id: user.id,
          });

          // Add realmRoles array to user object
          return {
            ...user,
            realmRoles: roleMappings
              .map((role) => role.name)
              .filter((role) => role !== undefined),
          };
        } catch (error) {
          console.error(`Error fetching roles for user ${user.id}:`, error);
          return user;
        }
      }),
    );

    return { users: enrichedUsers };
  } catch (error: any) {
    console.error("Error listing users:", error);
    return { message: error.message || "Failed to list users" };
  }
}

export async function createUser(
  tenantId: string,
  userData: UserRepresentation & { password?: string },
) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    const userPayload: UserRepresentation = {
      username: userData.username,
      email: userData.email,
      firstName: userData.firstName,
      lastName: userData.lastName,
      enabled: userData.enabled,
      emailVerified: true,
    };

    // Create the user
    await kcAdminClient.users.create(userPayload);

    // Set password if provided
    if (userData.password) {
      // Find the newly created user by username
      const users = await kcAdminClient.users.find({
        username: userData.username,
        exact: "true",
      });
      if (users.length === 0) {
        throw new Error("Created user not found");
      }

      const userId = users[0].id!;
      await kcAdminClient.users.resetPassword({
        id: userId,
        credential: {
          temporary: false,
          type: "password",
          value: userData.password,
        },
      });
    }

    return { success: true };
  } catch (error: any) {
    console.error("Error creating user:", error);
    return { success: false, message: error.message };
  }
}

export async function updateUser(
  tenantId: string,
  userData: UserRepresentation & { id: string },
) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    const userPayload: UserRepresentation = {
      username: userData.username,
      email: userData.email,
      firstName: userData.firstName,
      lastName: userData.lastName,
      enabled: userData.enabled,
    };

    await kcAdminClient.users.update({ id: userData.id }, userPayload);

    return { success: true };
  } catch (error: any) {
    console.error("Error updating user:", error);
    return { success: false, message: error.message };
  }
}

export async function resetUserPassword(
  tenantId: string,
  userId: string,
  password: string,
  temporary: boolean = false,
) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    await kcAdminClient.users.resetPassword({
      id: userId,
      credential: {
        temporary: temporary,
        type: "password",
        value: password,
      },
    });

    return { success: true };
  } catch (error: any) {
    console.error("Error resetting password:", error);
    return { success: false, message: error.message };
  }
}

export async function deleteUser(tenantId: string, userId: string) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    await kcAdminClient.users.del({
      id: userId,
    });

    return { success: true };
  } catch (error: any) {
    console.error("Error deleting user:", error);
    return { success: false, message: error.message };
  }
}

// Groups API
export async function listGroups(tenantId: string) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    const groups = await kcAdminClient.groups.find();

    return { success: true, groups };
  } catch (error: any) {
    console.error("Error listing groups:", error);
    return { success: false, message: error.message };
  }
}

export async function createGroup(tenantId: string, groupName: string) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    await kcAdminClient.groups.create({
      name: groupName,
    });

    return { success: true };
  } catch (error: any) {
    console.error("Error creating group:", error);
    return { success: false, message: error.message };
  }
}

export async function updateGroup(
  tenantId: string,
  groupId: string,
  groupData: GroupRepresentation,
) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    await kcAdminClient.groups.update({ id: groupId }, groupData);

    return { success: true };
  } catch (error: any) {
    console.error("Error updating group:", error);
    return { success: false, message: error.message };
  }
}

export async function deleteGroup(tenantId: string, groupId: string) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    await kcAdminClient.groups.del({
      id: groupId,
    });

    return { success: true };
  } catch (error: any) {
    console.error("Error deleting group:", error);
    return { success: false, message: error.message };
  }
}

export async function listGroupMembers(tenantId: string, groupId: string) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    const members = await kcAdminClient.groups.listMembers({
      id: groupId,
    });

    return { success: true, members };
  } catch (error: any) {
    console.error("Error listing group members:", error);
    return { success: false, message: error.message };
  }
}

export async function addUserToGroup(
  tenantId: string,
  userId: string,
  groupId: string,
) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    await kcAdminClient.users.addToGroup({
      id: userId,
      groupId: groupId,
    });

    return { success: true };
  } catch (error: any) {
    console.error("Error adding user to group:", error);
    return { success: false, message: error.message };
  }
}

export async function removeUserFromGroup(
  tenantId: string,
  userId: string,
  groupId: string,
) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    await kcAdminClient.users.delFromGroup({
      id: userId,
      groupId: groupId,
    });

    return { success: true };
  } catch (error: any) {
    console.error("Error removing user from group:", error);
    return { success: false, message: error.message };
  }
}

export async function toggleUserAdminRole(
  tenantId: string,
  userId: string,
  isAdmin: boolean,
) {
  try {
    const kcAdminClient = await getKeycloakAdminClient(tenantId);

    const adminRole = "lh-user-tasks-admin";

    // Get the role representation
    const roleRep = await kcAdminClient.roles.findOneByName({
      name: adminRole,
    });

    if (!roleRep.id || !roleRep.name) {
      return { success: false, message: "Admin role not found" };
    }

    if (isAdmin) {
      // Add admin role
      await kcAdminClient.users.addRealmRoleMappings({
        id: userId,
        roles: [
          {
            id: roleRep.id!,
            name: roleRep.name,
          },
        ],
      });
    } else {
      // Remove admin role
      await kcAdminClient.users.delRealmRoleMappings({
        id: userId,
        roles: [
          {
            id: roleRep.id!,
            name: roleRep.name,
          },
        ],
      });
    }

    return { success: true };
  } catch (error) {
    console.error("Error toggling admin role:", error);
    return { success: false, message: "Failed to update user role" };
  }
}
