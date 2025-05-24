import Footer from "./components/footer";
import Header from "./components/header";

export default function Layout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: { tenantId: string };
}) {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="flex-1 px-4 md:px-8 lg:px-16 py-8">{children}</main>
      <Footer />
    </div>
  );
}
