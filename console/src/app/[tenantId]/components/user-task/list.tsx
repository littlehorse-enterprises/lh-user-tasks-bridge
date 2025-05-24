"use client";
import { adminGetAllTasks } from "@/app/[tenantId]/actions/admin";
import { getUserTasks } from "@/app/[tenantId]/actions/user";
import { DateRangePicker } from "@/components/ui/data-range-picker";
import { ErrorResponse, ErrorType } from "@/lib/error-handling";
import {
  Alert,
  AlertDescription,
  AlertTitle,
} from "@littlehorse-enterprises/ui-library/alert";
import { Button } from "@littlehorse-enterprises/ui-library/button";
import { Input } from "@littlehorse-enterprises/ui-library/input";
import { Label } from "@littlehorse-enterprises/ui-library/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@littlehorse-enterprises/ui-library/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@littlehorse-enterprises/ui-library/select";
import {
  UserGroupDTO,
  UserTaskRunListDTO,
  UserTaskStatus,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { AlertCircle, FilterIcon, RefreshCw, Search, X } from "lucide-react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useLayoutEffect, useState } from "react";
import useSWRInfinite from "swr/infinite";
import UserTask from "../../components/user-task";
import Loading from "../loading";
// Add a counter for generating unique keys
const getUniqueId = () => Date.now().toString();

type Query = {
  user_group_id?: UserGroupDTO["id"];
  status?: UserTaskStatus;
  earliest_start_date?: string;
  latest_start_date?: string;
};

export default function ListUserTasks({
  userGroups,
  userTaskDefName,
  initialData,
  claimable,
  initialError,
}: {
  userGroups: UserGroupDTO[];
  userTaskDefName?: string;
  initialData: UserTaskRunListDTO;
  claimable?: boolean;
  initialError?: ErrorResponse;
}) {
  const [query, setQuery] = useState<Query>({});
  const [search, setSearch] = useState("");
  const [limit] = useState(100);
  const [error, setError] = useState<ErrorResponse | undefined>(initialError);
  const tenantId = useParams().tenantId as string;
  const [mountId] = useState(getUniqueId());

  // Ensure userGroups is an array, even if undefined
  const safeUserGroups = Array.isArray(userGroups) ? userGroups : [];

  const router = useRouter();

  const searchParams = useSearchParams();
  useEffect(() => {
    const query = Object.fromEntries(
      new URLSearchParams(searchParams).entries(),
    ) as Query;
    setQuery(query);
  }, [searchParams]);

  useLayoutEffect(() => {
    const searchParams = new URLSearchParams();
    Object.entries(query)
      .filter(([, value]) => value !== undefined)
      .forEach(([key, value]) => searchParams.set(key, value.toString()));
    router.replace(
      `${window.location.pathname.split("?")[0]}?${searchParams.toString()}`,
    );
  }, [query, router]);

  const getKey = (
    pageIndex: number,
    previousPageData: UserTaskRunListDTO | null,
  ) => {
    if (claimable) return null;

    if (previousPageData && !previousPageData.bookmark) return null; // reached the end
    return [mountId, "userTask", query, limit, previousPageData?.bookmark];
  };

  const { data, setSize, isValidating, mutate } =
    useSWRInfinite<UserTaskRunListDTO>(
      getKey,
      async (key): Promise<UserTaskRunListDTO> => {
        setError(undefined);

        const [, , query, limit, bookmark] = key;
        const response = await (userTaskDefName
          ? adminGetAllTasks(tenantId, {
              ...query,
              limit,
              type: userTaskDefName,
              bookmark,
            })
          : getUserTasks(tenantId, {
              ...query,
              limit,
              bookmark,
            }));

        // Handle error
        if (response.error) {
          setError(response.error);
          return { userTasks: [], bookmark: undefined };
        }

        return response.data || { userTasks: [], bookmark: undefined };
      },
      {
        refreshInterval: 1000,
        revalidateOnFocus: true,
        revalidateOnReconnect: true,
        revalidateOnMount: true,
        revalidateIfStale: true,
        fallbackData: [initialData],
        dedupingInterval: 500,
        shouldRetryOnError: false,
      },
    );

  const fetchNextPage = () => {
    setSize((size) => size + 1);
  };

  const handleRetry = () => {
    setError(undefined);
    mutate();
  };

  const clearFilters = () => {
    setQuery({});
    setSearch("");
  };

  const hasActiveFilters = Object.keys(query).length > 0 || search.length > 0;

  const isPending = !data;
  if (isPending) return <Loading />;
  const hasNextPage = !!(data && data[data.length - 1]?.bookmark);

  const isFetchingNextPage = isValidating && hasNextPage;

  // Handle error display
  if (error) {
    return (
      <div className="space-y-6">
        {!userTaskDefName && !claimable && (
          <div className="space-y-2">
            <h2 className="text-2xl font-bold tracking-tight text-foreground">
              My Assigned UserTasks
            </h2>
            <p className="text-muted-foreground">
              UserTasks currently assigned to you
            </p>
          </div>
        )}

        <Alert variant="destructive" className="shadow-sm">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>
            {error.type === ErrorType.UNAUTHORIZED
              ? "Authentication Error"
              : error.type === ErrorType.FORBIDDEN
                ? "Permission Denied"
                : error.type === ErrorType.NETWORK
                  ? "Network Error"
                  : "Error Loading UserTasks"}
          </AlertTitle>
          <AlertDescription className="mt-2">{error.message}</AlertDescription>
          <Button
            onClick={handleRetry}
            variant="outline"
            size="sm"
            className="mt-4"
          >
            <RefreshCw className="mr-2 h-4 w-4" />
            Retry
          </Button>
        </Alert>
      </div>
    );
  }

  const allTasks = data.flatMap((page) => page.userTasks);
  const filteredTasks = allTasks
    .sort(
      (a, b) =>
        new Date(b.scheduledTime).getTime() -
        new Date(a.scheduledTime).getTime(),
    )
    .filter((userTask) => {
      if (!search) return true;
      return Object.values(userTask).some((value) => {
        return JSON.stringify(value)
          .toLowerCase()
          .includes(search.toLowerCase());
      });
    });

  return (
    <div className="space-y-6">
      {/* Header */}
      {!userTaskDefName && !claimable && (
        <div className="space-y-2">
          <h2 className="text-2xl font-bold tracking-tight text-foreground">
            My Assigned UserTasks
          </h2>
          <p className="text-muted-foreground">
            Tasks currently assigned to you
          </p>
        </div>
      )}

      {userTaskDefName && (
        <div className="space-y-2">
          <h2 className="text-2xl font-bold tracking-tight text-foreground">
            {userTaskDefName}
          </h2>
          <p className="text-muted-foreground">
            All UserTasks for this UserTaskDef
          </p>
        </div>
      )}

      {/* Search and Filters */}
      <div className="flex flex-col sm:flex-row gap-4">
        {/* Search */}
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search UserTasks..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-10 pr-10"
          />
          {search && (
            <button
              onClick={() => setSearch("")}
              className="absolute right-3 top-1/2 transform -translate-y-1/2 text-muted-foreground hover:text-foreground"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>

        {/* Filters */}
        <div className="flex gap-2">
          <Popover>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                className={`${hasActiveFilters ? "border-primary bg-primary/5" : ""}`}
              >
                <FilterIcon className="h-4 w-4 mr-2" />
                Filters
                {hasActiveFilters && (
                  <span className="ml-2 bg-primary text-primary-foreground rounded-full px-2 py-0.5 text-xs">
                    {Object.keys(query).length}
                  </span>
                )}
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-80" align="end">
              <div className="space-y-4">
                <div className="space-y-2">
                  <h4 className="font-medium text-sm">Filter UserTasks</h4>
                  <p className="text-xs text-muted-foreground">
                    Narrow down your UserTask list
                  </p>
                </div>

                <div className="space-y-4">
                  <div>
                    <Label className="text-sm font-medium">Date Range</Label>
                    <DateRangePicker
                      initialDateFrom={
                        query.earliest_start_date
                          ? new Date(query.earliest_start_date)
                          : undefined
                      }
                      initialDateTo={
                        query.latest_start_date
                          ? new Date(query.latest_start_date)
                          : undefined
                      }
                      onUpdate={(values) => {
                        setQuery({
                          ...query,
                          earliest_start_date: values.range.from.toISOString(),
                          latest_start_date: values.range.to?.toISOString(),
                        });
                      }}
                    />
                  </div>

                  <div>
                    <Label className="text-sm font-medium">Status</Label>
                    <Select
                      value={query.status ?? "ALL"}
                      onValueChange={(value) => {
                        setQuery({
                          ...query,
                          status:
                            value === "ALL"
                              ? undefined
                              : (value as UserTaskStatus),
                        });
                      }}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="ALL">All Statuses</SelectItem>
                        {userTaskDefName && (
                          <SelectItem value="UNASSIGNED">Available</SelectItem>
                        )}
                        <SelectItem value="ASSIGNED">In Progress</SelectItem>
                        <SelectItem value="DONE">Completed</SelectItem>
                        <SelectItem value="CANCELLED">Cancelled</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  {safeUserGroups.length > 0 && (
                    <div>
                      <Label className="text-sm font-medium">User Group</Label>
                      <Select
                        value={query.user_group_id ?? "ALL"}
                        onValueChange={(value) => {
                          setQuery({
                            ...query,
                            user_group_id: value === "ALL" ? undefined : value,
                          });
                        }}
                      >
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="ALL">All Groups</SelectItem>
                          {safeUserGroups.map((group) => (
                            <SelectItem key={group.id} value={group.id}>
                              {group.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  )}
                </div>

                {hasActiveFilters && (
                  <Button
                    variant="outline"
                    onClick={clearFilters}
                    className="w-full"
                  >
                    <X className="h-4 w-4 mr-2" />
                    Clear All Filters
                  </Button>
                )}
              </div>
            </PopoverContent>
          </Popover>
        </div>
      </div>

      {/* Results */}
      {filteredTasks.length > 0 ? (
        <div className="space-y-6">
          {/* Results count */}
          <div className="flex items-center justify-between">
            <p className="text-sm text-muted-foreground">
              Showing {filteredTasks.length} of {allTasks.length} UserTasks
              {search && ` matching "${search}"`}
            </p>
          </div>

          {/* Task Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
            {filteredTasks.map((userTask) => (
              <UserTask
                key={userTask.id}
                userTask={userTask}
                admin={!!userTaskDefName}
                claimable={claimable}
              />
            ))}
          </div>

          {/* Load More */}
          {hasNextPage && (
            <div className="flex justify-center pt-4">
              <Button
                variant="outline"
                onClick={fetchNextPage}
                disabled={isFetchingNextPage}
                className="min-w-32"
              >
                {isFetchingNextPage ? (
                  <>
                    <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                    Loading...
                  </>
                ) : (
                  "Load More UserTasks"
                )}
              </Button>
            </div>
          )}
        </div>
      ) : (
        <div className="text-center py-12">
          <div className="mx-auto w-24 h-24 bg-muted rounded-full flex items-center justify-center mb-4">
            <AlertCircle className="h-8 w-8 text-muted-foreground" />
          </div>
          <h3 className="text-lg font-medium text-foreground mb-2">
            No UserTasks found
          </h3>
          <p className="text-muted-foreground mb-4">
            {search || hasActiveFilters
              ? "Try adjusting your search or filters"
              : "There are no UserTasks to display"}
          </p>
          {hasActiveFilters && (
            <Button variant="outline" onClick={clearFilters}>
              <X className="h-4 w-4 mr-2" />
              Clear Filters
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
