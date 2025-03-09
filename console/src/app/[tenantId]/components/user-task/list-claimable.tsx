"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  ListUserTasksResponse,
  UserGroup,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { Loader2Icon } from "lucide-react";
import { useParams } from "next/navigation";
import useSWR from "swr";
import { listClaimableUserTasks } from "../../actions/user";
import ListUserTasks from "./list";

export default function ListClaimableUserTasks({
  userGroups,
}: {
  userGroups: [UserGroup, ...UserGroup[]];
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
          const { data, error, isLoading } = useSWR(
            [`claimable-tasks-${userGroup.id}`, tenantId, userGroup.id],
            async ([_, tenantId, userGroupId]) => {
              console.log(
                "fetching claimable tasks for user group",
                userGroupId,
              );
              const data = await listClaimableUserTasks(tenantId, {
                user_group_id: userGroupId,
                limit: 10,
              });
              console.log(data);
              if ("message" in data) {
                throw new Error(data.message);
              }
              return data;
            },
          );

          if (isLoading)
            return (
              <Loader2Icon className="size-4 animate-spin" key={userGroup.id} />
            );
          if (error)
            return (
              <div key={userGroup.id}>Error loading tasks: {error.message}</div>
            );

          return (
            <TabsContent key={userGroup.id} value={userGroup.id}>
              <ListUserTasks
                userGroups={userGroups}
                initialData={data as ListUserTasksResponse}
              />
            </TabsContent>
          );
        })}
      </Tabs>
    </div>
  );
}
