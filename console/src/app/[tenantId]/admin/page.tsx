import { ClipboardList, UserCheck, Users } from "lucide-react";
import Link from "next/link";
import { adminGetAllUserTaskDefs, adminGetUserGroups } from "../actions/admin";
import { getUsersFromIdP } from "../actions/user-management";
import AdminTabs from "./components/admin-tabs";

export default async function AdminPage({
  params,
  searchParams,
}: {
  params: { tenantId: string };
  searchParams: { tab?: string };
}) {
  const tenantId = params.tenantId;
  const currentTab = searchParams.tab || "tasks";

  // Fetch all data for both statistics and tabs in parallel
  const [userTaskDefsResult, userGroupsResult, usersResult] = await Promise.all(
    [
      adminGetAllUserTaskDefs(tenantId, { limit: 1000 }), // Increased limit to get all UserTaskDefs
      adminGetUserGroups(tenantId),
      getUsersFromIdP(tenantId, { max_results: 1000 }), // Increased limit to get all users
    ],
  );

  // Calculate statistics
  const taskDefsCount = userTaskDefsResult.data?.userTaskDefNames?.length || 0;
  const userGroupsCount = userGroupsResult.data?.groups?.length || 0;
  const activeUsersCount =
    usersResult.data?.users?.filter((user: any) => user.enabled)?.length || 0;

  return (
    <div className="max-w-7xl mx-auto space-y-8">
      {/* Header Section */}
      <div className="space-y-4">
        <div className="space-y-2">
          <h1 className="text-3xl font-bold tracking-tight text-foreground">
            Admin Dashboard
          </h1>
          <p className="text-muted-foreground">
            Manage UserTasks, users, and groups for your organization
          </p>
        </div>

        {/* Quick Stats */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <Link href="?tab=tasks">
            <div className="bg-card border border-border rounded-lg p-6 shadow-sm h-full">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-primary/10 rounded-lg">
                  <ClipboardList className="h-5 w-5 text-primary" />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">
                    UserTaskDefs
                  </p>
                  <p className="text-2xl font-bold text-foreground">
                    {userTaskDefsResult.error ? (
                      <span className="text-destructive text-sm">Error</span>
                    ) : (
                      taskDefsCount
                    )}
                  </p>
                </div>
              </div>
            </div>
          </Link>
          <Link href="?tab=users">
            <div className="bg-card border border-border rounded-lg p-6 shadow-sm h-full">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-green-500/10 rounded-lg">
                  <UserCheck className="h-5 w-5 text-green-600" />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">
                    Active Users
                  </p>
                  <p className="text-2xl font-bold text-foreground">
                    {usersResult.error ? (
                      <span className="text-destructive text-sm">Error</span>
                    ) : (
                      activeUsersCount
                    )}
                  </p>
                </div>
              </div>
            </div>
          </Link>
          <Link href="?tab=groups">
            <div className="bg-card border border-border rounded-lg p-6 shadow-sm h-full">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-blue-500/10 rounded-lg">
                  <Users className="h-5 w-5 text-blue-600" />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">
                    User Groups
                  </p>
                  <p className="text-2xl font-bold text-foreground">
                    {userGroupsResult.error ? (
                      <span className="text-destructive text-sm">Error</span>
                    ) : (
                      userGroupsCount
                    )}
                  </p>
                </div>
              </div>
            </div>
          </Link>
        </div>

        {/* Error Messages */}
        {(userTaskDefsResult.error ||
          userGroupsResult.error ||
          usersResult.error) && (
          <div className="bg-destructive/10 border border-destructive/20 rounded-lg p-4">
            <p className="text-sm text-destructive">
              Some statistics could not be loaded. Please check your permissions
              and try again.
            </p>
          </div>
        )}
      </div>

      {/* Admin Tabs */}
      <div className="bg-card border border-border rounded-lg shadow-sm">
        <AdminTabs
          currentTab={currentTab}
          tenantId={tenantId}
          userTaskDefs={userTaskDefsResult.data?.userTaskDefNames || []}
          users={usersResult.data?.users || []}
          groups={userGroupsResult.data?.groups || []}
          hasDataErrors={{
            userTaskDefs: !!userTaskDefsResult.error,
            users: !!usersResult.error,
            groups: !!userGroupsResult.error,
          }}
        />
      </div>
    </div>
  );
}
