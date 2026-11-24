"use client";

import { Fragment, useCallback, useState } from "react";
import { useRouter } from "next/navigation";
import { getInitialConversations, useChatSessions } from "@/app/context/ChatSessions";
import ChatInput from "./ChatInput";
import EvermeMascot from "@/components/EvermeMascot";
import Button from "@/components/Button";
import Badge from "@/components/Badge";
import Icon from "@/components/Icon";
import Field from "@/components/Field";
import Modal from "@/components/Modal";
const topicSets = [
  [
    "오늘 하루 이야기",
    "좋아하는 음식",
    "소중한 사람들",
    "기억에 남는 여행",
    "어릴 적 추억",
    "나의 취미",
    "요즘 드는 생각",
    "나의 가치관",
    "미래의 나에게",
  ],
  [
    "최근 웃었던 순간",
    "나만의 휴식법",
    "기억에 남는 선물",
    "다시 가고 싶은 곳",
    "좋아하는 계절",
    "힘이 된 한마디",
    "배워보고 싶은 것",
    "나의 작은 습관",
    "고마운 사람에게",
  ],
  [
    "설레었던 첫 만남",
    "위로가 되는 노래",
    "오래 간직한 물건",
    "주말에 하고픈 일",
    "나의 어린 시절 꿈",
    "함께한 기념일",
    "새로 도전한 일",
    "편안해지는 장소",
    "오늘의 작은 행복",
  ],
];
export default function ChatContent({
  viewer = false,
  conversationId,
  initialEmpty = false,
  owner = "김영희",
  level = "L2 개인",
}) {
  const base = viewer
    ? owner === "박정호"
      ? "/viewer/chat/park"
      : "/viewer/chat"
    : "/owner/chat";
  const router = useRouter();
  const mode = viewer ? "viewer" : "user";
  const { conversations, completeConversation } = useChatSessions();
  const conversation = (
    conversations[mode] ?? getInitialConversations(mode)
  ).find((item) => item.id === conversationId);
  const [modal, setModal] = useState(null);
  const [topicSet, setTopicSet] = useState(0);
  const [message, setMessage] = useState(conversation?.message ?? "");
  const [correction, setCorrection] = useState("");
  const [correctingTurn, setCorrectingTurn] = useState(0);
  const [editedTurns, setEditedTurns] = useState({});
  const [sentTurns, setSentTurns] = useState([]);
  const [deletedMessages, setDeletedMessages] = useState([]);
  const [answerFeedback, setAnswerFeedback] = useState({});
  const empty = !message && (conversationId ? !conversation : initialEmpty);
  const closeModal = useCallback(() => setModal(null), []);
  const currentMessage =
    message ||
    (viewer
      ? "어머니는 어떤 음식을 좋아하시나요?"
      : "오늘 저녁에는 가족들과 함께 국수를 먹었어.");
  const exampleTurns = viewer ? null : conversation?.turns;
  const turns = [
    ...(exampleTurns ?? (sentTurns.length ? [] : [{ user: currentMessage }])),
    ...sentTurns,
  ];
  function send(text) {
    setMessage(text);
    setSentTurns((previous) => [
      ...previous,
      {
        user: text,
        assistant:
          "이야기해 주셔서 고마워요. 그 순간 어떤 마음이 들었는지 조금 더 들려주시겠어요?",
      },
    ]);
    if (empty) {
      const session = completeConversation(mode, text, conversationId, base);
      router.replace(base + "?conversation=" + encodeURIComponent(session.id));
    }
  }
  return (
    <>
      <div className="min-h-dvh flex flex-col [@media(max-width:_760px)]:min-h-[calc(100dvh_-_205px)]">
        {viewer && (
          <header className="p-[22px_28px] [border-bottom:1px_solid_var(--line)] [&_h2]:mb-3 [&_p]:text-[14px] [&_p]:mb-0 [&_h2_.ui-badge]:ml-3 [&_h2_.ui-badge]:[vertical-align:middle] [@media(max-width:_760px)]:p-5 [@media(max-width:_760px)]:[&_h2]:text-[18px]">
            <h2>
              <>
                {owner}님의 AI 클론 <Badge>{level}</Badge>
              </>
            </h2>
            <p className="ui-muted text-muted">
              {`실제 ${owner}님이 아닌 AI 클론과 대화 중입니다.`}
            </p>
          </header>
        )}
        <div
          className={`p-[24px_28px] flex-1 min-h-97.5 [@media(min-width:_1600px)]:pt-8 [@media(max-width:_760px)]:p-5 [@media(max-width:_760px)]:min-h-75 ${empty ? "flex items-center justify-center [&_.ui-chatcontent-empty-chat]:m-0 [&_.ui-chatcontent-empty-chat]:w-full" : ""}`}
        >
          {empty ? (
            <div className="ui-chatcontent-empty-chat max-w-200 m-[64px_auto] text-center [&_.ui-avatar]:mb-6 [&_h1]:text-[26px] [@media(max-width:_760px)]:m-[32px_auto] [@media(max-width:_760px)]:[&_h1]:text-[22px]">
              <span
                className="ui-avatar inline-flex w-24 items-center justify-center"
                role="img"
                aria-label="위를 바라보며 생각하는 에버미 마스코트"
              >
                <EvermeMascot animated={false} thinking />
              </span>
              <h1>오늘은 어떤 이야기를 나눠볼까요?</h1>
              <p className="ui-muted text-muted mb-0">
                소소한 일상부터 오래 간직한 기억까지, 편하게 이야기해 주세요.
              </p>
              <p className="ui-muted text-muted">
                EVERME는 당신의 이야기를 기억하고, 당신의 생각을 다른 사람에게 이야기 해줄 수 있어요.
              </p>
              <div
                className={`gap-3 mt-8 [&_.ui-button]:rounded-full ${viewer ? "flex flex-wrap justify-center" : "grid grid-cols-10 [&_.ui-button]:col-span-2 [&_.ui-button]:min-w-0 [&_.ui-button]:px-2 [&_.ui-button:nth-child(6)]:col-start-2 [@media(max-width:_760px)]:grid-cols-2 [@media(max-width:_760px)]:[&_.ui-button]:col-span-1 [@media(max-width:_760px)]:[&_.ui-button:nth-child(6)]:col-start-auto"}`}
              >
                {(viewer
                  ? ["오늘 하루 이야기", "좋아하는 음식", "소중한 사람들"]
                  : topicSets[topicSet]
                ).map((t) => (
                  <Button key={t} variant="secondary" onClick={() => send(t)}>
                    {t}
                  </Button>
                ))}
              </div>
              {!viewer && (
                <button
                  type="button"
                  className="mt-5 inline-flex min-h-11.5 items-center justify-center gap-2 rounded-full border-0 bg-green px-5 py-2.5 text-[15px] font-semibold text-white cursor-pointer hover:bg-[color-mix(in_srgb,var(--green)_85%,black)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green"
                  onClick={() =>
                    setTopicSet((current) => (current + 1) % topicSets.length)
                  }
                >
                  <Icon name="refresh-single" size={18} />
                  새로운 이야기 거리
                </button>
              )}
            </div>
          ) : (
            <div className="mx-auto w-full max-w-200 space-y-8 py-4">
              {turns.map((turn, index) => (
                <Fragment key={index}>
                  {!deletedMessages.includes(index) && (
                    <article
                      className="group/message ml-auto flex w-fit max-w-[85%] flex-col items-end gap-1 [@media(max-width:_760px)]:max-w-[95%]"
                      aria-label="내 메시지"
                    >
                      <p className="m-0 rounded-[20px] bg-green-light px-5 py-3 leading-[1.8] whitespace-pre-wrap break-words [overflow-wrap:anywhere]">
                        {editedTurns[index] ?? turn.user}
                      </p>
                      {!viewer && (
                        <div className="flex items-center gap-1 text-muted opacity-0 pointer-events-none group-hover/message:opacity-100 group-hover/message:pointer-events-auto group-focus-within/message:opacity-100 group-focus-within/message:pointer-events-auto [@media(hover:none)]:opacity-100 [@media(hover:none)]:pointer-events-auto">
                          <button
                            type="button"
                            className="inline-flex size-8 items-center justify-center rounded-lg hover:bg-green-light hover:text-green focus-visible:outline-2 focus-visible:outline-green"
                            onClick={() => {
                              setCorrectingTurn(index);
                              setCorrection(editedTurns[index] ?? turn.user);
                              setModal("correct");
                            }}
                            aria-label="발언 정정"
                            title="발언 정정"
                          >
                            <Icon name="edit" size={17} />
                          </button>
                          <button
                            type="button"
                            className="inline-flex size-8 items-center justify-center rounded-lg hover:bg-green-light hover:text-green focus-visible:outline-2 focus-visible:outline-green"
                            aria-label="메시지 삭제"
                            title="메시지 삭제"
                            onClick={() =>
                              setDeletedMessages((previous) => [
                                ...previous,
                                index,
                              ])
                            }
                          >
                            <Icon name="trash" size={17} />
                          </button>
                        </div>
                      )}
                    </article>
                  )}
                  <article
                    className={`group/message flex flex-col gap-4 leading-[1.8] break-words [overflow-wrap:anywhere] [&_p]:m-0 ${!viewer ? "pl-11 [@media(max-width:_760px)]:pl-6" : ""}`}
                    aria-label="AI 답변"
                  >
                    <span className="relative block text-[12px] font-medium text-muted">
                      {!viewer && index === turns.length - 1 && (
                        <span className="absolute -left-11 top-1/2 w-9 -translate-y-1/2 [@media(max-width:_760px)]:-left-6 [@media(max-width:_760px)]:w-5">
                          <EvermeMascot />
                        </span>
                      )}
                      {viewer ? `${owner} AI 클론` : "EVERME"}
                    </span>
                    <div data-answer className="space-y-4">
                      {viewer ? (
                        <>
                          <p>
                            <Badge>기록된 사실</Badge>
                          </p>
                          <p>
                            국수를 좋아하고, 가족과 함께 먹는 시간을 소중히
                            여긴다고 기록되어 있어요.
                          </p>
                          <p>
                            <Badge tone="blue">AI 추론</Badge>
                          </p>
                          <p>담백한 음식을 선호하실 가능성이 있어요.</p>
                          <button
                            type="button"
                            className="block text-[12px] text-muted hover:text-green"
                            onClick={() => setModal("evidence")}
                          >
                            응답 근거 보기 →
                          </button>
                          <small className="block text-muted">
                            AI 추론은 현재의 생각이나 확정된 의사가 아닙니다.
                          </small>
                        </>
                      ) : (
                        <>
                          <p>
                            {turn.assistant ??
                              "가족과 함께한 따뜻한 시간이었겠어요. 오늘 저녁에서 가장 기억에 남는 순간은 무엇인가요?"}
                          </p>
                        </>
                      )}
                    </div>
                    <div className="-mt-3 flex items-center gap-1 text-muted opacity-0 pointer-events-none group-hover/message:opacity-100 group-hover/message:pointer-events-auto group-focus-within/message:opacity-100 group-focus-within/message:pointer-events-auto [@media(hover:none)]:opacity-100 [@media(hover:none)]:pointer-events-auto">
                      <button
                        type="button"
                        className="inline-flex size-8 items-center justify-center rounded-lg hover:bg-green-light hover:text-green focus-visible:outline-2 focus-visible:outline-green"
                        aria-label="답변 복사"
                        title="답변 복사"
                        onClick={async (event) => {
                          const answer = event.currentTarget
                            .closest("article")
                            .querySelector("[data-answer]");
                          const text = Array.from(
                            answer.querySelectorAll("p, small"),
                            (element) => element.innerText,
                          ).join("\n\n");
                          try {
                            await navigator.clipboard.writeText(text);
                            setAnswerFeedback((previous) => ({
                              ...previous,
                              [index]: "복사했어요.",
                            }));
                          } catch {
                            setAnswerFeedback((previous) => ({
                              ...previous,
                              [index]: "복사하지 못했어요. 다시 시도해 주세요.",
                            }));
                          }
                        }}
                      >
                        <Icon name="copy" size={17} />
                      </button>
                      <button
                        type="button"
                        className="inline-flex size-8 items-center justify-center rounded-lg hover:bg-green-light hover:text-green focus-visible:outline-2 focus-visible:outline-green"
                        aria-label="답변 새로고침"
                        title="답변 새로고침"
                        onClick={(event) => {
                          const answer = event.currentTarget
                            .closest("article")
                            .querySelector("[data-answer]");
                          if (
                            !window.matchMedia(
                              "(prefers-reduced-motion: reduce)",
                            ).matches
                          ) {
                            answer.animate([{ opacity: 0.3 }, { opacity: 1 }], {
                              duration: 400,
                              easing: "ease-out",
                            });
                          }
                          setAnswerFeedback((previous) => ({
                            ...previous,
                            [index]: "예시 답변을 다시 표시했어요.",
                          }));
                        }}
                      >
                        <Icon name="refresh" size={17} />
                      </button>
                      <span role="status" className="ml-2 text-[12px]">
                        {answerFeedback[index]}
                      </span>
                    </div>
                  </article>
                </Fragment>
              ))}
            </div>
          )}
        </div>
        <div className="p-[0_28px_16px] [&_>_small]:block [&_>_small]:text-center [@media(max-width:_760px)]:p-[0_20px_20px] [@media(max-width:_760px)]:[&_>_small]:text-[10px]">
          <ChatInput onSend={send} />
          <small className="ui-muted text-muted">
            {viewer
              ? "AI 답변은 참고용이며, 본인의 의사결정이나 동의를 대신하지 않습니다."
              : "AI가 잘못 이해할 수 있어요. 나의 발언은 언제든 정정할 수 있어요."}
          </small>
        </div>
      </div>
      {modal === "correct" && (
        <Modal title="발언 정정" onClose={closeModal}>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (!correction.trim()) return;
              setEditedTurns((previous) => ({
                ...previous,
                [correctingTurn]: correction.trim(),
              }));
              closeModal();
            }}
          >
            <Field
              label="정정할 발언"
              value={correction}
              onChange={(event) => setCorrection(event.target.value)}
              required
            />
            <div className="flex gap-3 mt-9 [&_.ui-button]:flex-1">
              <Button variant="secondary" onClick={closeModal}>
                취소
              </Button>
              <Button type="submit">저장</Button>
            </div>
          </form>
        </Modal>
      )}
      {modal === "evidence" && (
        <Modal title="응답의 근거" onClose={closeModal} drawer>
          <Badge>{level}</Badge>
          <p className="ui-muted text-muted">
            허용된 {level.split(" ")[0]} 이하의 원본만 표시해요.
          </p>
          {[
            [
              "기록된 사실",
              "국수를 좋아하고 가족과의 식사 시간을 소중히 여긴다고 기록되어 있어요.",
            ],
            [
              "AI 추론",
              "담백한 음식을 선호할 가능성이 있어요. 현재의 생각으로 확정할 수는 없어요.",
            ],
            [
              "판단 근거",
              "음식 선호와 가족에 관한 대화 기록을 함께 참고했어요.",
            ],
            ["불확실성", "최근의 음식 선호를 직접 확인한 기록이 부족해요."],
            [
              "원본 출처 · L1 일반",
              "11월 20일 · 좋아하는 음식 이야기\n“나는 국수가 좋아. 담백해서 자주 먹어.”",
            ],
          ].map(([t, d]) => (
            <section
              className="[border-bottom:1px_solid_var(--line)] p-[8px_0_16px] whitespace-pre-line [&_p]:text-[14px] [&_p]:text-muted"
              key={t}
            >
              <h3>{t}</h3>
              <p>{d}</p>
            </section>
          ))}
        </Modal>
      )}
    </>
  );
}
