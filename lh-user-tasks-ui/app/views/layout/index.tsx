import React, { ReactNode } from "react";
import { Header } from "./header";
import { Footer } from "./footer";
interface LayoutProps {
  children: ReactNode;
}

export const Layout = ({ children }: LayoutProps) => {
  return (
    <>
      <Header />
      {children}
      <Footer />
    </>
  );
};
