import ChatContent from "../../../chat/ChatContent";
export default function Page() {
  return (
    <ChatContent
      viewer={true}
      variant={"empty"}
      owner={"박정호"}
      level={"L1 일반"}
    />
  );
}
