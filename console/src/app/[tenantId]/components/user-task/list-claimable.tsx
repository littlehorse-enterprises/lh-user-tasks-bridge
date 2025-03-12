"use client";

import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  ListUserTasksResponse,
  UserGroup,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { Loader2Icon } from "lucide-react";
import { useParams } from "next/navigation";
import useSWRInfinite from "swr/infinite";
import { listClaimableUserTasks } from "../../actions/user";
import ListUserTasks from "./list";

export default function ListClaimableUserTasks({
  userGroups,
  initialData,
}: {
  userGroups: [UserGroup, ...UserGroup[]];
  initialData: ListUserTasksResponse[];
}) {
  const tenantId = useParams().tenantId as string;
  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">Claimable User Tasks</h1>

      <Tabs defaultValue={userGroups[0].id}>
        <TabsList>
          {userGroups.map((userGroup) => (
            <TabsTrigger key={userGroup.id} value={userGroup.id}>
              {userGroup.name}
            </TabsTrigger>
          ))}
        </TabsList>

        {userGroups.map((userGroup) => {
          const getKey = (
            pageIndex: number,
            previousPageData: ListUserTasksResponse | null,
          ) => {
            // Reached the end
            if (previousPageData && !previousPageData.bookmark) return null;

            // First page, no bookmark
            if (pageIndex === 0) return [tenantId, userGroup.id, undefined];

            // Add the bookmark from the previous page
            return [tenantId, userGroup.id, previousPageData?.bookmark];
          };

          const { data, error, size, setSize, isValidating } =
            useSWRInfinite<ListUserTasksResponse>(
              getKey,
              async ([tenantId, userGroupId, bookmark]) => {
                const data = await listClaimableUserTasks(tenantId, {
                  user_group_id: userGroupId,
                  limit: 10,
                  bookmark: bookmark || undefined,
                });
                console.log(data);
                if ("message" in data) {
                  throw new Error(data.message);
                }
                return data;
              },
              {
                refreshInterval: 1000,
                revalidateOnFocus: true,
                revalidateOnReconnect: true,
                fallbackData: initialData,
              },
            );

          if (!data && isValidating)
            return (
              <Loader2Icon className="size-4 animate-spin" key={userGroup.id} />
            );
          if (error)
            return (
              <div key={userGroup.id}>Error loading tasks: {error.message}</div>
            );

          const hasNextPage = data && data[data.length - 1]?.bookmark != null;
          const isFetchingNextPage = isValidating && data && data.length > 0;

          return (
            <TabsContent key={userGroup.id} value={userGroup.id}>
              <ListUserTasks
                userGroups={userGroups}
                initialData={
                  data
                    ? (data[0] as ListUserTasksResponse)
                    : { userTasks: [], bookmark: null }
                }
                claimable
              />

              {hasNextPage && (
                <div className="flex justify-center mt-4">
                  <Button
                    onClick={() => setSize(size + 1)}
                    className="w-fit"
                    loading={isFetchingNextPage}
                  >
                    Load More
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
