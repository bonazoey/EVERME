import ChatContent from "../../../chat/ChatContent";
export default async function Page({ searchParams }) {
  const { conversation } = await searchParams;
  return (
    <ChatContent
      key={typeof conversation === "string" ? conversation : "new"}
      viewer
      variant="empty"
    />
  );
}
