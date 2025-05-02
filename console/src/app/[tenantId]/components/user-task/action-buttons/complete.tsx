"use client";
import {
    adminCompleteUserTask,
    adminGetUserTaskDetail,
} from "@/app/[tenantId]/actions/admin";
import {
    completeUserTask,
    getUserTaskDetail,
} from "@/app/[tenantId]/actions/user";
import { Button, buttonVariants } from "@/components/ui/button";
import {
    Dialog,
    DialogClose,
    DialogContent,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { ErrorResponse } from "@/lib/error-handling";
import {
    DetailedUserTaskRunDTO,
    SimpleUserTaskRunDTO,
    UserTaskFieldDTO,
    UserTaskFieldType,
    UserTaskVariableValue,
} from "@littlehorse-enterprises/user-tasks-bridge-api-client";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { ErrorHandler } from "../../error-handler";
import Loading from "../../loading";
import NotesTextArea from "../notes";

export default function CompleteUserTaskButton({
  userTask,
  admin = false,
  readOnly = false,
}: {
  userTask: SimpleUserTaskRunDTO;
  admin?: boolean;
  readOnly?: boolean;
}) {
  const [userTaskDetails, setUserTaskDetails] =
    useState<DetailedUserTaskRunDTO>();
  const [userTaskResult, setUserTaskResult] = useState<
    Record<string, UserTaskVariableValue>
  >({});
  const [error, setError] = useState<ErrorResponse>();
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const tenantId = useParams().tenantId as string;

  const fetchTaskDetails = async () => {
    setIsLoading(true);
    setError(undefined);
    
    const params = {
      wf_run_id: userTask.wfRunId,
      user_task_guid: userTask.id,
    };

    const response = await (admin
      ? adminGetUserTaskDetail(tenantId, params)
      : getUserTaskDetail(tenantId, params)
    );

    setIsLoading(false);

    if (response.error) {
      setError(response.error);
      return;
    }

    setUserTaskDetails(response.data);
    setUserTaskResult(response.data?.results || {});
  };

  useEffect(() => {
    fetchTaskDetails();
  }, [tenantId, userTask, admin]);

  const handleSubmit = async () => {
    if (!userTaskDetails) return;
    
    setIsSubmitting(true);
    
    const params = {
      wf_run_id: userTask.wfRunId,
      user_task_guid: userTask.id,
    };
    
    const response = await (admin
      ? adminCompleteUserTask(tenantId, params, userTaskResult)
      : completeUserTask(tenantId, params, {
          variableResults: userTaskResult,
        }));
    
    setIsSubmitting(false);
    
    if (response.error) {
      toast.error(`Failed to complete task: ${response.error.message}`);
      return;
    }
    
    toast.success("Task completed successfully");
    setUserTaskResult({});
  };

  const renderContent = () => {
    if (isLoading) {
      return <Loading />;
    }

    if (error) {
      return (
        <ErrorHandler
          error={error}
          onRetry={fetchTaskDetails}
          allowReturn={false}
          title="Error Loading Task Details"
        />
      );
    }

    if (!userTaskDetails) {
      return <div>No task details available</div>;
    }

    return (
      <>
        <div className="space-y-2">
          <Label>Notes:</Label>
          <NotesTextArea notes={userTaskDetails.notes} />
        </div>
        {!readOnly && (
          <h1 className="text-lg font-semibold text-center">
            Fill out the form
          </h1>
        )}
        {userTaskDetails.fields.map((field: UserTaskFieldDTO) => (
          <div key={field.name} className="space-y-2">
            <Label>
              {field.displayName}
              {field.required && (
                <span className="text-destructive">*</span>
              )}
            </Label>
            {field.type === "STRING" && (
              <Input
                name={field.name}
                placeholder={field.description}
                value={userTaskResult[field.name]?.value?.toString() || ""}
                readOnly={readOnly}
                onChange={(e) => {
                  const value = e.target.value;
                  setUserTaskResult({
                    ...userTaskResult,
                    [field.name]: { type: field.type, value: value },
                  });
                }}
              />
            )}
            {(field.type === "DOUBLE" || field.type === "INTEGER") && (
              <Input
                type="number"
                value={userTaskResult[field.name]?.value?.toString() || ""}
                readOnly={readOnly}
                onChange={(e) => {
                  const value =
                    field.type === "DOUBLE"
                      ? parseFloat(e.target.value)
                      : parseInt(e.target.value);
                  setUserTaskResult({
                    ...userTaskResult,
                    [field.name]: {
                      value,
                      type: field.type as UserTaskFieldType,
                    },
                  });
                }}
              />
            )}
            {field.type === "BOOLEAN" && (
              <Select
                value={userTaskResult[field.name]?.value?.toString() ?? ""}
                disabled={readOnly}
                onValueChange={(value) => {
                  setUserTaskResult({
                    ...userTaskResult,
                    [field.name]: {
                      value: value === "true",
                      type: field.type as UserTaskFieldType,
                    },
                  });
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select a value" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="true">True</SelectItem>
                  <SelectItem value="false">False</SelectItem>
                </SelectContent>
              </Select>
            )}
          </div>
        ))}
      </>
    );
  };

  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="default">
          {readOnly ? "View Results" : "Complete"}
        </Button>
      </DialogTrigger>
      <DialogContent className="gap-2">
        <DialogHeader>
          <DialogTitle>
            {readOnly ? "View Results for" : "Complete"}{" "}
            <span className="font-mono">{userTask.userTaskDefName}</span>
          </DialogTitle>
        </DialogHeader>

        {renderContent()}

        {!error && (
          <p className="text-sm text-muted-foreground">
            <span className="text-destructive">*</span> Required fields
          </p>
        )}

        <DialogFooter>
          <DialogClose className={buttonVariants({ variant: "outline" })}>
            Close
          </DialogClose>

          {!readOnly && !error && userTaskDetails && (
            <Button
              variant="default"
              onClick={handleSubmit}
              disabled={isSubmitting}
            >
              {isSubmitting ? "Submitting..." : "Complete"}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
