"use client";
import {
  adminCompleteUserTask,
  adminGetUserTask,
} from "@/app/[tenantId]/actions/admin";
import { completeUserTask, getUserTask } from "@/app/[tenantId]/actions/user";
import { useTenantId } from "@/app/[tenantId]/layout";
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
import {
  GetUserTaskResponse,
  UserTask,
  UserTaskResult,
} from "@littlehorse-enterprises/sso-workflow-bridge-api-client";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import Loading from "../../loading";
import NotesTextArea from "../notes";

export default function CompleteUserTaskButton({
  userTask,
  admin = false,
  readOnly = false,
}: {
  userTask: UserTask;
  admin?: boolean;
  readOnly?: boolean;
}) {
  const [userTaskDetails, setUserTaskDetails] = useState<GetUserTaskResponse>();
  const [userTaskResult, setUserTaskResult] = useState<UserTaskResult>({});
  const tenantId = useTenantId();

  useEffect(() => {
    (admin
      ? adminGetUserTask(tenantId, userTask)
      : getUserTask(tenantId, userTask)
    )
      .then((res) => {
        if ("message" in res) {
          toast.error(res.message);
          return;
        }
        setUserTaskDetails(res);
        setUserTaskResult(res.results);
      })
      .catch((err) => {
        toast.error("Failed to get UserTask");
        console.error(err);
      });
  }, [tenantId, userTask, admin]);

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

        {userTaskDetails ? (
          <>
            <div className="space-y-2">
              <Label>Notes:</Label>
              <NotesTextArea notes={userTaskDetails.notes} />
            </div>
            <h1 className="text-lg font-semibold text-center">
              Fill out the form
            </h1>
            {userTaskDetails.fields.map((field) => (
              <div key={field.name} className="space-y-2">
                <Label>
                  {field.displayName}
                  {field.required && <span className="text-red-500">*</span>}
                </Label>
                {field.type === "STRING" && (
                  <Input
                    name={field.name}
                    placeholder={field.description}
                    value={userTaskResult[field.name]?.value?.toString()}
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
                    value={userTaskResult[field.name]?.value?.toString()}
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
                          type: field.type,
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
                          type: field.type,
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
        ) : (
          <Loading />
        )}

        <p className="text-sm text-gray-500">
          <span className="text-red-500">*</span> Required fields
        </p>

        <DialogFooter>
          <DialogClose className={buttonVariants({ variant: "outline" })}>
            Close
          </DialogClose>

          {!readOnly && (
            <DialogClose asChild>
              <Button
                variant="default"
                onClick={async () => {
                  if (!userTaskDetails) return;
                  if (
                    userTaskDetails.fields.filter((field) => field.required)
                      .length >
                    Object.keys(userTaskResult).filter((oneResult) =>
                      userTaskDetails.fields.find(
                        (field) => field.name === oneResult && field.required,
                      ),
                    ).length
                  )
                    return toast.warning("All required fields must be filled.");
                  try {
                    const response = admin
                      ? await adminCompleteUserTask(
                          tenantId,
                          userTask,
                          userTaskResult,
                        )
                      : await completeUserTask(
                          tenantId,
                          userTask,
                          userTaskResult,
                        );
                    setUserTaskResult({});
                    if (response && "message" in response) {
                      toast.error(response.message);
                      return;
                    }
                    toast.success("UserTask completed");
                  } catch (error) {
                    console.error(error);
                    toast.error("Failed to complete UserTask");
                  }
                }}
              >
                Complete
              </Button>
            </DialogClose>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
