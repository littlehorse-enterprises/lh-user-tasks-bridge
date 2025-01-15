"use client";

import { createContext, useContext } from "react";
import Footer from "./components/footer";
import Header from "./components/header";

const TenantContext = createContext<string>("default");

export function useTenantId() {
  return useContext(TenantContext);
}

export default function Layout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: { tenantId: string };
}) {
  return (
    <TenantContext.Provider value={params.tenantId}>
      <Header />
      <main className="px-4 md:px-8 lg:px-16 py-4">{children}</main>
      <Footer />
    </TenantContext.Provider>
  );
}
