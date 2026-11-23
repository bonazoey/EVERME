import ChatContent from "./ChatContent";
export default async function Page({ searchParams }) {
  const { title } = await searchParams;
  const hasConversation = typeof title === "string" && title.trim().length > 0;
  return (
    <ChatContent
      variant={hasConversation ? "" : "empty"}
      title={hasConversation ? title : undefined}
    />
  );
}
