import { cn } from "@/lib/utils";
import { Textarea } from "@littlehorse-enterprises/ui-library/textarea";

export default function NotesTextArea({ notes }: { notes?: string }) {
  return (
    <Textarea
      className={cn("resize-none h-24", !notes && "text-destructive")}
      defaultValue={notes || "No notes are available."}
      readOnly
    />
  );
}
