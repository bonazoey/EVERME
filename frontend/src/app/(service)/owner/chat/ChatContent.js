"use client";

import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { getInitialConversations, useChatSessions } from "@/app/context/ChatSessions";
import ChatInput from "./ChatInput";
import EvermeMascot from "@/components/EvermeMascot";
import Button from "@/components/Button";
import Badge from "@/components/Badge";
import Icon from "@/components/Icon";
import Field from "@/components/Field";
import Modal from "@/components/Modal";
const viewerQuestionSets = [
  [
    "평소에 가장 좋아하는 음식은 무엇인가요?",
    "오래 기억에 남은 순간은 언제인가요?",
    "살면서 가장 중요하게 여겨 온 것은 무엇인가요?",
  ],
  [
    "쉬는 날에는 주로 무엇을 하시나요?",
    "어릴 적 가장 좋아했던 놀이는 무엇인가요?",
    "힘들 때 위로가 됐던 말이 있나요?",
  ],
  [
    "언젠가 다시 가보고 싶은 곳은 어디인가요?",
    "오랫동안 즐겨 온 취미는 무엇인가요?",
    "소중한 사람에게 전하고 싶은 말이 있나요?",
  ],
];

const topicSets = [
  [
    "오늘 가장 기억에 남는 순간은?",
    "어릴 때 자주 가던 곳은?",
    "요즘 문득 떠오르는 사람은?",
  ],
  [
    "최근에 무엇 때문에 웃었나요?",
    "다시 가보고 싶은 곳은?",
    "힘이 됐던 한마디가 있나요?",
  ],
  [
    "오래 간직해 온 물건이 있나요?",
    "마음이 편해지는 장소는?",
    "오늘의 작은 행복은 뭐였나요?",
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
  const messageListRef = useRef(null);
  const [showJump, setShowJump] = useState(false);
  const sentCount = sentTurns.length;
  const updateScrollPosition = useCallback((list) => {
    setShowJump(list.scrollHeight - list.clientHeight - list.scrollTop > 4);
  }, []);

  useEffect(() => {
    const list = messageListRef.current;
    if (!empty && list) list.scrollTop = list.scrollHeight;
  }, [conversationId, empty, sentCount]);

  useEffect(() => {
    const list = messageListRef.current;
    if (empty || !list) return;
    const observer = new ResizeObserver(() => updateScrollPosition(list));
    observer.observe(list);
    if (list.firstElementChild) observer.observe(list.firstElementChild);
    return () => observer.disconnect();
  }, [empty, updateScrollPosition]);

  function jumpToLatest() {
    const list = messageListRef.current;
    if (!list) return;
    list.focus({ preventScroll: true });
    list.scrollTop = list.scrollHeight;
    updateScrollPosition(list);
  }
  const closeModal = useCallback(() => setModal(null), [setModal]);
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
  const questionSets = viewer ? viewerQuestionSets : topicSets;
  return (
    <>
      <div
        data-chat-scroll={empty ? "" : undefined}
        className={`flex h-full min-h-0 flex-col ${empty ? "overflow-y-auto overscroll-contain" : ""}`}
      >
        {viewer && (
          <header className="shrink-0 p-[22px_28px] [border-bottom:1px_solid_var(--line)] [&_h2]:mb-3 [&_p]:text-[14px] [&_p]:mb-0 [&_h2_.ui-badge]:ml-3 [&_h2_.ui-badge]:[vertical-align:middle] [@media(max-width:_760px)]:p-5 [@media(max-width:_760px)]:[&_h2]:text-[18px]">
            <h2>
              <>
                {owner}님의 AI 대변인 <Badge>{level}</Badge>
              </>
            </h2>
            <p className="ui-muted text-muted">
              {`AI가 ${owner}님을 대신해 답변합니다.`}
            </p>
          </header>
        )}
        <div
          ref={messageListRef}
          data-chat-scroll={empty ? undefined : ""}
          role={empty ? undefined : "region"}
          aria-label={empty ? undefined : "대화 내용"}
          tabIndex={empty ? undefined : 0}
          onScroll={empty ? undefined : (event) => updateScrollPosition(event.currentTarget)}
          className={`px-7 pt-6 [@media(max-width:_760px)]:px-5 [@media(max-width:_760px)]:pt-5 ${empty ? "mt-auto shrink-0 pb-1" : "min-h-0 flex-1 overflow-y-auto overscroll-contain pb-6 focus-visible:-outline-offset-2 [@media(max-width:_760px)]:pb-5 [@media(min-width:_1600px)]:pt-8"}`}
        >
          {empty ? (
            <div className="mx-auto w-full max-w-200 text-center">
              <span
                className="mx-auto mb-5 inline-flex size-24 items-center justify-center rounded-full bg-green-light p-3 [@media(max-width:_760px)]:mb-3 [@media(max-width:_760px)]:size-18"
                aria-hidden="true"
              >
                <EvermeMascot animated={false} thinking />
              </span>
              <h1 className="mb-2 text-[clamp(22px,3vw,34px)] font-bold leading-tight tracking-tight text-balance">
                {viewer
                  ? `${owner}님에 대해 무엇이 궁금하세요?`
                  : "오늘은 어떤 이야기를 나눠볼까요?"}
              </h1>
              <p className="m-0 text-muted">
                {viewer
                  ? "AI 대변인이 공유된 이야기와 기억을 바탕으로 답해요."
                  : "오늘의 일상부터 오래 간직한 기억까지, 편하게 이야기해 주세요."}
              </p>
                <div className="mx-auto mt-5 flex max-w-200 flex-col items-center gap-2">
                  <div className="flex flex-wrap justify-center gap-2">
                    {questionSets[topicSet].map((topic) => (
                      <button
                        key={topic}
                        type="button"
                        className="min-h-11 rounded-full border border-line bg-surface px-4 py-2 text-[14px] text-green hover:bg-green-light"
                        onClick={() => send(topic)}
                      >
                        {topic}
                      </button>
                    ))}
                  </div>
                  <button
                    type="button"
                    className="inline-flex min-h-11 items-center gap-2 rounded-lg px-2 text-[13px] font-semibold text-muted hover:text-green hover:underline underline-offset-4"
                    onClick={() =>
                      setTopicSet((current) => (current + 1) % questionSets.length)
                    }
                  >
                    <Icon name="refresh-single" size={15} />
                    다른 질문 보기
                  </button>
                </div>
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
                    className="group/message relative flex flex-col items-start gap-3 pl-16 leading-[1.8] break-words [overflow-wrap:anywhere] [&_p]:m-0 [@media(max-width:_760px)]:pl-12"
                    aria-label="AI 답변"
                  >
                    {index === turns.length - 1 && (
                      <div className="absolute left-0 top-5 size-14 rounded-full bg-green-light p-0.5 [@media(max-width:_760px)]:size-10">
                        <EvermeMascot animated={false} />
                      </div>
                    )}
                    <span className="block text-[12px] font-medium text-muted">
                      {viewer ? `${owner}님의 AI 대변인` : "EVERME"}
                    </span>
                    <div data-answer className="relative max-w-full space-y-4 rounded-[20px] rounded-tl-sm bg-surface px-5 py-3 before:absolute before:-left-1.5 before:top-3 before:size-3 before:rotate-45 before:rounded-[2px] before:bg-surface">
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
                    <div className="-mt-2 flex items-center gap-1 text-muted opacity-0 pointer-events-none group-hover/message:opacity-100 group-hover/message:pointer-events-auto group-focus-within/message:opacity-100 group-focus-within/message:pointer-events-auto [@media(hover:none)]:opacity-100 [@media(hover:none)]:pointer-events-auto">
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
        <div className={`relative shrink-0 bg-white p-[8px_28px_16px] [&_>_small]:block [&_>_small]:text-center [@media(max-width:_760px)]:p-[8px_20px_20px] [@media(max-width:_760px)]:[&_>_small]:text-[10px] ${empty ? "mb-auto" : ""}`}>
          {!empty && showJump && (
            <button
              type="button"
              className="absolute bottom-full left-1/2 z-10 mb-2 flex size-11 -translate-x-1/2 cursor-pointer items-center justify-center rounded-full bg-white text-green shadow-[0_4px_16px_#17212b22] hover:bg-green-light"
              aria-label="최신 메시지로 이동"
              title="최신 메시지로 이동"
              onClick={jumpToLatest}
            >
              <span className="rotate-180">
                <Icon name="chevron-up" />
              </span>
            </button>
          )}
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
