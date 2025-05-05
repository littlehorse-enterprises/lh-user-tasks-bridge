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
      : getUserTaskDetail(tenantId, params));

    setIsLoading(false);

    if (response.error) {
      setError(response.error);
      return;
    }

    // Clean up the results data to ensure all values have valid types
    const cleanedResults: Record<string, UserTaskVariableValue> = {};
    const results = response.data?.results || {};

    // Loop through all result values
    for (const fieldName in results) {
      const fieldValue = results[fieldName];

      // Only include fields with a defined type
      if (fieldValue && fieldValue.type) {
        cleanedResults[fieldName] = fieldValue;
      } else if (
        fieldValue &&
        fieldValue.value !== undefined &&
        fieldValue.value !== null
      ) {
        // Find the field definition to get its type
        const field = response.data?.fields.find((f) => f.name === fieldName);
        let inferredType = field?.type || UserTaskFieldType.STRING;

        // Fallback to STRING if no type is found
        if (!inferredType) {
          console.warn(
            `No type found for field ${fieldName} in results, defaulting to STRING`,
          );
          inferredType = UserTaskFieldType.STRING;
        }

        cleanedResults[fieldName] = {
          value: fieldValue.value,
          type: inferredType,
        };
      }
    }

    setUserTaskDetails(response.data);
    setUserTaskResult(cleanedResults);
  };

  useEffect(() => {
    fetchTaskDetails();
  }, [tenantId, userTask, admin]);

  // Check for fields with missing types
  useEffect(() => {
    if (userTaskDetails?.fields) {
      const fieldsWithMissingTypes = userTaskDetails.fields.filter(
        (f) => !f.type,
      );
      if (fieldsWithMissingTypes.length > 0) {
        toast.warning(
          `Some fields have missing types: ${fieldsWithMissingTypes.map((f) => f.name).join(", ")}`,
          {
            duration: 5000,
          },
        );
        console.warn("Fields with missing types:", fieldsWithMissingTypes);
      }
    }
  }, [userTaskDetails]);

  const handleSubmit = async () => {
    if (!userTaskDetails) return;

    setIsSubmitting(true);

    const params = {
      wf_run_id: userTask.wfRunId,
      user_task_guid: userTask.id,
    };

    // Clean the data by ensuring all variable values have a defined type
    const cleanedResult: Record<string, UserTaskVariableValue> = {};

    // Loop through all form fields
    for (const fieldName in userTaskResult) {
      const fieldValue = userTaskResult[fieldName];

      // Only include fields with a defined type
      if (fieldValue && fieldValue.type) {
        cleanedResult[fieldName] = fieldValue;
      } else if (
        fieldValue &&
        fieldValue.value !== undefined &&
        fieldValue.value !== null
      ) {
        // If we have a value but no type, try to infer a safe type
        const field = userTaskDetails.fields.find((f) => f.name === fieldName);
        let inferredType = field?.type || UserTaskFieldType.STRING;

        // Last resort fallback - always use STRING if nothing else works
        if (!inferredType) {
          console.warn(
            `No type found for field ${fieldName}, defaulting to STRING`,
          );
          inferredType = UserTaskFieldType.STRING;
        }

        cleanedResult[fieldName] = {
          value: fieldValue.value,
          type: inferredType,
        };
      }
    }

    console.log("Submitting cleaned form data:", cleanedResult);

    const response = await (admin
      ? adminCompleteUserTask(tenantId, params, cleanedResult)
      : completeUserTask(tenantId, params, cleanedResult));

    setIsSubmitting(false);

    if (response.error) {
      toast.error(
        `Failed to complete task: ${response.error.message || "Unknown error"}`,
      );
      console.error("Error response:", response.error);
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
              {field.required && <span className="text-destructive">*</span>}
            </Label>
            {field.type && field.type === "STRING" && (
              <Input
                name={field.name}
                placeholder={field.description}
                value={userTaskResult[field.name]?.value?.toString() || ""}
                readOnly={readOnly}
                onChange={(e) => {
                  const value = e.target.value;
                  setUserTaskResult({
                    ...userTaskResult,
                    [field.name]: {
                      type: field.type || UserTaskFieldType.STRING,
                      value: value,
                    },
                  });
                }}
              />
            )}
            {field.type &&
              (field.type === "DOUBLE" || field.type === "INTEGER") && (
                <Input
                  type="number"
                  value={userTaskResult[field.name]?.value?.toString() || ""}
                  readOnly={readOnly}
                  onChange={(e) => {
                    const value = Number(e.target.value);
                    setUserTaskResult({
                      ...userTaskResult,
                      [field.name]: {
                        value,
                        type: field.type || UserTaskFieldType.DOUBLE,
                      },
                    });
                  }}
                />
              )}
            {field.type && field.type === "BOOLEAN" && (
              <Select
                value={userTaskResult[field.name]?.value?.toString() ?? ""}
                disabled={readOnly}
                onValueChange={(value) => {
                  setUserTaskResult({
                    ...userTaskResult,
                    [field.name]: {
                      value: value === "true",
                      type: field.type || UserTaskFieldType.BOOLEAN,
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
            {!field.type && (
              <div className="p-2 text-sm bg-yellow-50 text-yellow-800 rounded border border-yellow-200">
                Unknown field type
              </div>
            )}
          </div>
        ))}
      </>
    );
  };

  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="default" className="w-full">
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
