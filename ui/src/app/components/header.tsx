"use client";
import logo from "@/../public/images/logo.png";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { LogOut } from "lucide-react";
import { signOut, useSession } from "next-auth/react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";

export default function Header() {
  const session = useSession();
  const pathname = usePathname();
  return (
    <>
      {session.data?.roles.includes("lh-user-tasks-admin") &&
        pathname == "/" && (
          <p className="text-sm text-destructive-foreground bg-destructive text-center py-2">
            Viewing as User
          </p>
        )}
      <header className="flex px-4 md:px-8 lg:px-16 py-4 bg-foreground/10">
        <div className="flex-1 flex items-center">
          <div>
            <Image src={logo} alt="Logo" width={100} height={50} priority />
          </div>
        </div>
        <div className="flex-1 flex justify-end gap-2 items-center">
          {session.data?.roles.includes("lh-user-tasks-admin") &&
            (pathname.startsWith("/admin") ? (
              <Button variant="ghost" asChild>
                <Link href="/">View As User</Link>
              </Button>
            ) : (
              <Button variant="destructive" asChild>
                <Link href="/admin">Back to Admin View</Link>
              </Button>
            ))}
          {session.status == "loading" && (
            <Skeleton className="size-10 rounded-full" />
          )}
          {session.status == "authenticated" && session.data.user?.name && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Avatar className="cursor-pointer">
                  <AvatarFallback>
                    {session.data.user.name.charAt(0).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
              </DropdownMenuTrigger>
              <DropdownMenuContent>
                <DropdownMenuLabel>{session.data.user.name}</DropdownMenuLabel>

                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onClick={() => {
                    signOut();
                  }}
                  className="cursor-pointer"
                >
                  <LogOut className="mr-2 h-4 w-4" />
                  <span>Log Out</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      </header>
    </>
  );
}
