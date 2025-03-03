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
    <>
      <Header />
      <main className="px-4 md:px-8 lg:px-16 py-4">{children}</main>
      <Footer />
    </>
  );
}
