import { redirect } from "next/navigation";
import { auth } from "../api/auth/[...nextauth]/authOptions";

export default async function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const session = await auth();
  if (!session) return redirect("/api/auth/signout");

  if (!session.roles.includes("lh-user-tasks-admin")) redirect("/");

  return children;
}
