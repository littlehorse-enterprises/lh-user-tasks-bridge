"use client";

import {
  adminGetAllTasks,
  adminGetAllUserTasksDef,
} from "@/app/[tenantId]/actions/admin";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { UserTaskStatus } from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import {
  InfiniteData,
  useInfiniteQuery,
  useQuery,
} from "@tanstack/react-query";
import {
  AlertCircle,
  ChevronDown,
  ClipboardList,
  RefreshCw,
} from "lucide-react";
import { useSession } from "next-auth/react";
import Link from "next/link";

interface UserTaskDefCount {
  name: string;
  unassignedCount: number;
  assignedToMeCount: number;
}

interface UserTaskDefsProps {
  tenantId: string;
}

// Define the type for UserTaskDefListDTO
interface UserTaskDefListDTO {
  userTaskDefNames: string[];
  bookmark: string | null;
}

export default function UserTaskDefs({ tenantId }: UserTaskDefsProps) {
  const { data: session } = useSession();
  const userId = session?.user?.id;

  // Fetch user task definitions with useInfiniteQuery
  const {
    data: userTaskDefData,
    error: userTaskDefError,
    isFetching: isUserTaskDefFetching,
    hasNextPage,
    fetchNextPage,
    refetch: refreshUserTaskDefs,
  } = useInfiniteQuery<
    UserTaskDefListDTO,
    Error,
    InfiniteData<UserTaskDefListDTO, string | undefined>,
    string[],
    string | undefined
  >({
    queryKey: ["userTaskDefs", tenantId],
    queryFn: async ({ pageParam }) => {
      const result = await adminGetAllUserTasksDef(tenantId, {
        limit: 100,
        bookmark: pageParam,
      });

      if (result.error) {
        throw result.error;
      }

      return result.data!;
    },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage?.bookmark || undefined,
    refetchInterval: 5000,
    refetchOnWindowFocus: true,
  });

  // Get all task definition names from all pages
  const allUserTaskDefNames =
    userTaskDefData?.pages.flatMap(
      (page: UserTaskDefListDTO) => page.userTaskDefNames || [],
    ) || [];

  // Fetch task counts for each definition
  const {
    data: taskCounts,
    error: taskCountsError,
    refetch: refreshTaskCounts,
  } = useQuery<UserTaskDefCount[]>({
    queryKey: ["taskCounts", tenantId, allUserTaskDefNames, userId],
    queryFn: async () => {
      if (!allUserTaskDefNames || allUserTaskDefNames.length === 0) {
        return [];
      }

      const countPromises = allUserTaskDefNames.map(async (name: string) => {
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
      });

      return Promise.all(countPromises);
    },
    enabled: allUserTaskDefNames.length > 0,
    refetchInterval: 5000,
    refetchOnWindowFocus: true,
  });

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
        <AlertDescription>
          {(userTaskDefError as Error).message}
        </AlertDescription>
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
        <AlertDescription>
          {(taskCountsError as Error).message}
        </AlertDescription>
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

  // Loading state
  if (!userTaskDefData?.pages || typeof taskCounts === "undefined") {
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

  // Empty state
  if (userTaskDefData.pages.length === 0) {
    return (
      <div className="flex justify-center items-center p-8">
        <span className="text-muted-foreground text-lg">
          You do not have any user task defs registered.
        </span>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        {allUserTaskDefNames.map((name: string) => {
          const counts = taskCounts.find(
            (count: UserTaskDefCount) => count.name === name,
          ) || {
            unassignedCount: 0,
            assignedToMeCount: 0,
          };

          return (
            <Link key={name} href={`/${tenantId}/admin/${name}`}>
              <div className="rounded-lg border bg-card p-5 hover:bg-accent/50 transition-colors cursor-pointer h-full">
                <div className="flex items-center gap-4">
                  <ClipboardList className="size-10 text-primary bg-primary/25 p-2 rounded-full" />
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

      {hasNextPage && (
        <div className="flex justify-center mt-4">
          <Button
            onClick={() => fetchNextPage()}
            variant="outline"
            disabled={isUserTaskDefFetching}
            className="flex items-center gap-2"
          >
            {isUserTaskDefFetching ? (
              <RefreshCw className="h-4 w-4 animate-spin" />
            ) : (
              <ChevronDown className="h-4 w-4" />
            )}
            Load More
          </Button>
        </div>
      )}
    </div>
  );
}
