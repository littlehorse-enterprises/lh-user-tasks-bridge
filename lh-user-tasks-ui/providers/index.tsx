"use client";
import { ReactNode } from "react";
import { SessionProvider } from "next-auth/react";
import ClientProvider from "./clientProvider";
interface GlobalProviderProps {
  children: ReactNode;
}
const GlobalProvider: React.FC<GlobalProviderProps> = ({ children }) => {
  return (
    <main>
      <SessionProvider>
        <ClientProvider>{children}</ClientProvider>
      </SessionProvider>
    </main>
  );
};

export default GlobalProvider;
