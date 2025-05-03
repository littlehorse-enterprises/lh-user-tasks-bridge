"use client";

import {
  adminGetAllTasks,
  adminGetAllUserTasksDef,
} from "@/app/[tenantId]/actions/admin";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { UserTaskStatus } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { AlertCircle, RefreshCw, WrenchIcon } from "lucide-react";
import { useSession } from "next-auth/react";
import Link from "next/link";
import useSWR from "swr";

interface UserTaskDefCount {
  name: string;
  unassignedCount: number;
  assignedToMeCount: number;
}

interface UserTaskDefsProps {
  tenantId: string;
}

export default function UserTaskDefs({ tenantId }: UserTaskDefsProps) {
  const { data: session } = useSession();
  const userId = session?.user?.id;

  // Fetch user task definitions with SWR
  const {
    data: userTaskDefData,
    error: userTaskDefError,
    mutate: refreshUserTaskDefs,
  } = useSWR(
    ["userTaskDefs", tenantId],
    async () => {
      const result = await adminGetAllUserTasksDef(tenantId, {
        limit: 999,
      });

      if (result.error) {
        throw result.error;
      }

      return result.data;
    },
    {
      refreshInterval: 5000, // Refresh every 5 seconds
      revalidateOnFocus: true,
      revalidateOnReconnect: true,
    },
  );

  // Fetch task counts for each definition
  const {
    data: taskCounts,
    error: taskCountsError,
    mutate: refreshTaskCounts,
  } = useSWR(
    userTaskDefData?.userTaskDefNames
      ? ["taskCounts", tenantId, userTaskDefData.userTaskDefNames, userId]
      : null,
    async () => {
      if (!userTaskDefData || !userTaskDefData.userTaskDefNames) {
        return [];
      }

      const countPromises = userTaskDefData.userTaskDefNames.map(
        async (name) => {
          // Get unassigned tasks count - limit to 99
          const unassignedResult = await adminGetAllTasks(tenantId, {
            type: name,
            status: UserTaskStatus.UNASSIGNED,
            limit: 99,
          });

          // Get tasks assigned to current user - limit to 99
          const assignedToMeResult = userId
            ? await adminGetAllTasks(tenantId, {
                type: name,
                status: UserTaskStatus.ASSIGNED,
                user_id: userId,
                limit: 99,
              })
            : { data: { userTasks: [] } };

          // Handle error responses
          const unassignedCount = unassignedResult.data?.userTasks?.length || 0;
          const assignedToMeCount =
            assignedToMeResult.data?.userTasks?.length || 0;

          return {
            name,
            unassignedCount,
            assignedToMeCount,
          };
        },
      );

      return Promise.all(countPromises);
    },
    {
      refreshInterval: 5000, // Refresh every 5 seconds
      revalidateOnFocus: true,
      revalidateOnReconnect: true,
    },
  );

  // Handle refresh
  const handleRefresh = () => {
    refreshUserTaskDefs();
    refreshTaskCounts();
  };

  // Handle errors
  if (userTaskDefError) {
    return (
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertTitle>Error loading task definitions</AlertTitle>
        <AlertDescription>{userTaskDefError.message}</AlertDescription>
        <Button
          onClick={handleRefresh}
          variant="outline"
          size="sm"
          className="mt-2"
        >
          <RefreshCw className="mr-2 h-4 w-4" /> Retry
        </Button>
      </Alert>
    );
  }

  if (taskCountsError) {
    return (
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertTitle>Error loading task counts</AlertTitle>
        <AlertDescription>{taskCountsError.message}</AlertDescription>
        <Button
          onClick={handleRefresh}
          variant="outline"
          size="sm"
          className="mt-2"
        >
          <RefreshCw className="mr-2 h-4 w-4" /> Retry
        </Button>
      </Alert>
    );
  }

  // Loading state or empty state
  if (!userTaskDefData?.userTaskDefNames || !taskCounts) {
    return (
      <div className="flex justify-center items-center p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-4 w-32 bg-gray-200 rounded"></div>
          <div className="grid grid-cols-3 gap-4">
            <div className="h-20 bg-gray-200 rounded"></div>
            <div className="h-20 bg-gray-200 rounded"></div>
            <div className="h-20 bg-gray-200 rounded"></div>
          </div>
        </div>
      </div>
    );
  }

  // No data state
  if (userTaskDefData.userTaskDefNames.length === 0) {
    return (
      <div>
        <h1 className="text-center text-2xl font-bold text-foreground">
          No UserTask Definitions Found
        </h1>
        <p className="text-center text-muted-foreground">
          You currently have no UserTask Definitions registered.
        </p>
      </div>
    );
  }

  return (
    <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
      {userTaskDefData.userTaskDefNames.map((name) => {
        const counts = taskCounts.find((count) => count.name === name) || {
          unassignedCount: 0,
          assignedToMeCount: 0,
        };

        return (
          <Link key={name} href={`/${tenantId}/admin/${name}`}>
            <div className="rounded-lg border bg-card p-5 hover:bg-accent/50 transition-colors cursor-pointer h-full">
              <div className="flex items-center gap-4">
                <WrenchIcon className="size-10 text-primary bg-primary/25 p-2 rounded-full" />
                <h3 className="text-lg font-medium text-card-foreground">
                  {name}
                </h3>
              </div>

              <div className="mt-4 flex gap-3 text-sm">
                {counts.unassignedCount > 0 ? (
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-accent text-accent-foreground">
                    {counts.unassignedCount >= 99
                      ? "99+"
                      : counts.unassignedCount}{" "}
                    unassigned
                  </span>
                ) : null}

                {counts.assignedToMeCount > 0 ? (
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-primary text-primary-foreground">
                    {counts.assignedToMeCount >= 99
                      ? "99+"
                      : counts.assignedToMeCount}{" "}
                    assigned to me
                  </span>
                ) : null}
              </div>
            </div>
          </Link>
        );
      })}
    </div>
  );
}
