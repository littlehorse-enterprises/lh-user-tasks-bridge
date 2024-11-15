import { RefreshCcw } from "lucide-react";

export default function Loading() {
  return (
    <div className="flex justify-center items-center h-full">
      <RefreshCcw className="animate-spin" />
    </div>
  );
}
