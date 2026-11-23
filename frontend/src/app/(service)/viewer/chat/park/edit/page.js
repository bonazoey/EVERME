import ChatContent from "../../../../chat/ChatContent";
export default function Page() {
  return (
    <ChatContent
      viewer={true}
      owner={"박정호"}
      level={"L1 일반"}
      variant={"edit"}
    />
  );
}
