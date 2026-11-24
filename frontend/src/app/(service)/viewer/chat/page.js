import ChatContent from "../../owner/chat/ChatContent";
export default async function Page({ searchParams }) {
  const params = await searchParams;
  const conversationId =
    typeof params.conversation === "string" ? params.conversation : undefined;
  const hasTitle =
    typeof params.title === "string" && params.title.trim().length > 0;
  return (
    <ChatContent
      key={conversationId ?? (hasTitle ? params.title : "new")}
      conversationId={conversationId}
      initialEmpty={false}
      viewer={true}
      owner="김영희"
      level="L2 개인"
    />
  );
}
