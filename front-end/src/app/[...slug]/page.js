import { notFound } from "next/navigation";
import { screens } from "@/lib/screens";
import AuthPage from "@/components/pages/AuthPage";
import ChatPage from "@/components/pages/ChatPage";
import ClonePage from "@/components/pages/ClonePage";
import MembersPage from "@/components/pages/MembersPage";
import AccessHistoryPage from "@/components/pages/AccessHistoryPage";
import ClonesPage from "@/components/pages/ClonesPage";
import InvitationsPage from "@/components/pages/InvitationsPage";
import SettingsPage from "@/components/pages/SettingsPage";
const components = {
  AuthPage,
  ChatPage,
  ClonePage,
  MembersPage,
  AccessHistoryPage,
  ClonesPage,
  InvitationsPage,
  SettingsPage,
};
export function generateStaticParams() {
  return screens
    .filter((s) => s.path !== "/")
    .map((s) => ({ slug: s.path.slice(1).split("/") }));
}
export default async function Page({ params }) {
  const { slug } = await params;
  const screen = screens.find((s) => s.path === "/" + slug.join("/"));
  if (!screen) notFound();
  const Component = components[screen.component];
  return <Component {...screen.props} />;
}
