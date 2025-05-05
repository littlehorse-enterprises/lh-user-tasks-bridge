"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ErrorResponse, ErrorType } from "@/lib/error-handling";
import {
  UserGroupDTO,
  UserTaskRunListDTO,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { AlertCircle, Loader2Icon, RefreshCw } from "lucide-react";
import { useParams } from "next/navigation";
import { useState } from "react";
import useSWRInfinite from "swr/infinite";
import { getClaimableTasks } from "../../actions/user";
import ListUserTasks from "./list";

export default function ListClaimableUserTasks({
  userGroups,
  initialData,
  initialErrors,
}: {
  userGroups: [UserGroupDTO, ...UserGroupDTO[]];
  initialData: UserTaskRunListDTO[];
  initialErrors?: (ErrorResponse | undefined)[];
}) {
  const tenantId = useParams().tenantId as string;
  const [activeGroup, setActiveGroup] = useState<string>(userGroups[0].id);
  const [errors, setErrors] = useState<Record<string, ErrorResponse>>({});

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Claimable User Tasks</h1>

      <Tabs
        defaultValue={userGroups[0].id}
        onValueChange={(value) => setActiveGroup(value)}
      >
        <div className="flex items-center gap-2 mb-2">
          <span className="font-medium text-sm">Groups:</span>
          <TabsList>
            {userGroups.map((userGroup) => (
              <TabsTrigger key={userGroup.id} value={userGroup.id}>
                {userGroup.name}
              </TabsTrigger>
            ))}
          </TabsList>
        </div>

        {userGroups.map((userGroup, index) => {
          const initialError = initialErrors?.[index];

          const getKey = (
            pageIndex: number,
            previousPageData: UserTaskRunListDTO | null,
          ) => {
            // Only fetch when tab is active
            if (activeGroup !== userGroup.id) return null;

            // Reached the end
            if (previousPageData && !previousPageData.bookmark) return null;

            // First page, no bookmark
            if (pageIndex === 0) return [tenantId, userGroup.id, undefined];

            // Add the bookmark from the previous page
            return [tenantId, userGroup.id, previousPageData?.bookmark];
          };

          const { data, size, setSize, isValidating, mutate } =
            useSWRInfinite<UserTaskRunListDTO>(
              getKey,
              async ([tenantId, userGroupId, bookmark]) => {
                // Clear error when fetching
                if (errors[userGroup.id]) {
                  setErrors((prev) => {
                    const newErrors = { ...prev };
                    delete newErrors[userGroup.id];
                    return newErrors;
                  });
                }

                const result = await getClaimableTasks(tenantId, {
                  user_group_id: userGroupId,
                  limit: 10,
                  bookmark: bookmark || undefined,
                });

                // Handle error
                if (result.error) {
                  setErrors((prev) => ({
                    ...prev,
                    [userGroup.id]: result.error as ErrorResponse,
                  }));
                  return { userTasks: [], bookmark: undefined };
                }

                return result.data || { userTasks: [], bookmark: undefined };
              },
              {
                refreshInterval: 1000,
                revalidateOnFocus: true,
                revalidateOnReconnect: true,
                fallbackData: [initialData[index]],
              },
            );

          if (!data && isValidating)
            return (
              <TabsContent key={userGroup.id} value={userGroup.id}>
                <div className="flex justify-center items-center h-40">
                  <Loader2Icon className="h-8 w-8 animate-spin text-muted-foreground" />
                </div>
              </TabsContent>
            );

          const groupError = errors[userGroup.id] || initialError;

          if (groupError) {
            return (
              <TabsContent key={userGroup.id} value={userGroup.id}>
                <Alert variant="destructive" className="mt-4">
                  <AlertCircle className="h-4 w-4" />
                  <AlertTitle>
                    {groupError.type === ErrorType.UNAUTHORIZED
                      ? "Authentication Error"
                      : groupError.type === ErrorType.FORBIDDEN
                        ? "Permission Denied"
                        : groupError.type === ErrorType.NETWORK
                          ? "Network Error"
                          : "Error Loading Claimable Tasks"}
                  </AlertTitle>
                  <AlertDescription>{groupError.message}</AlertDescription>
                  <Button
                    onClick={() => mutate()}
                    variant="outline"
                    size="sm"
                    className="mt-2"
                  >
                    <RefreshCw className="mr-2 h-4 w-4" /> Retry
                  </Button>
                </Alert>
              </TabsContent>
            );
          }

          const hasNextPage = data && data[data.length - 1]?.bookmark != null;
          const isFetchingNextPage = isValidating && data && data.length > 0;

          return (
            <TabsContent key={userGroup.id} value={userGroup.id}>
              <ListUserTasks
                userGroups={userGroups}
                initialData={
                  data ? data[0] : { userTasks: [], bookmark: undefined }
                }
                claimable
              />

              {hasNextPage && (
                <div className="flex justify-center mt-4">
                  <Button
                    onClick={() => setSize(size + 1)}
                    variant="outline"
                    disabled={isFetchingNextPage}
                  >
                    {isFetchingNextPage ? "Loading more..." : "Load more"}
                  </Button>
                </div>
              )}
            </TabsContent>
          );
        })}
      </Tabs>
    </div>
  );
}
