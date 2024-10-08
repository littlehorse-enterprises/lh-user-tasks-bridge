"use client";
import { ReactNode } from "react";
import { useSession } from "next-auth/react";
import { NEXT_AUTH_STATUS } from "@/types/enum";
import { useRouter } from "next/navigation";
import { LHContextProvider } from "@littlehorse-enterprises/user-tasks";
import Loader from "@/app/views/loader";

interface ClientProviderProps {
  children: ReactNode;
}

const ClientProvider: React.FC<ClientProviderProps> = ({ children }) => {
  const { status } = useSession();
  const router = useRouter();

  if (status === NEXT_AUTH_STATUS.LOADING) {
    return <Loader />;
  }
  if (status === NEXT_AUTH_STATUS.SUCCESS) {
    return <LHContextProvider>{children}</LHContextProvider>;
  } else {
    router.push("/api/auth/signin");
  }

  return <></>;
};

export default ClientProvider;
