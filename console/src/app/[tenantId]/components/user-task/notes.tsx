import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

export default function NotesTextArea({ notes }: { notes?: string }) {
  return (
    <Textarea
      className={cn("resize-none h-24", !notes && "text-destructive")}
      defaultValue={notes || "No notes are available."}
      readOnly
    />
  );
}
