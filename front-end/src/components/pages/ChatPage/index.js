import Link from "next/link";
import AppLayout from "@/components/layout/AppLayout";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Icon from "@/components/ui/Icon";
import Field from "@/components/ui/Field";
import Modal from "@/components/ui/Modal";
export default function ChatPage({
  viewer = false,
  variant = "",
  owner = "김영희",
  level = "L2 개인",
}) {
  const base = viewer
    ? owner === "박정호"
      ? "/viewer/chat/park"
      : "/viewer/chat"
    : "/chat";
  const empty = variant === "empty",
    voice = variant === "voice",
    evidence = variant === "evidence";
  return (
    <AppLayout
      active="chat"
      title={viewer ? "AI 대변인" : "AI 대화"}
      viewer={viewer}
      chat
      modeOpen={variant === "mode"}
    >
      <div className="chat-page">
        <header className="chat-header">
          <h2>
            {viewer ? (
              <>
                {owner}님의 AI 클론 <Badge>{level}</Badge>
              </>
            ) : empty ? (
              "새로운 이야기 시작하기"
            ) : (
              "가족과 함께한 저녁"
            )}
          </h2>
          <p className="muted">
            {viewer
              ? `실제 ${owner}님이 아닌 AI 클론과 대화 중입니다.`
              : "나의 이야기를 안전하게 기록하고 있어요."}
          </p>
        </header>
        <div className="chat-body">
          {empty ? (
            <div className="empty-chat">
              <span className="avatar large">
                <Icon name="chat" size={32} />
              </span>
              <h1>오늘은 어떤 이야기를 나눠볼까요?</h1>
              <p className="muted">
                소소한 일상부터 오래 간직한 기억까지, 편하게 이야기해 주세요.
              </p>
              <div className="suggestions">
                {["오늘 하루 이야기", "좋아하는 음식", "소중한 사람들"].map(
                  (t) => (
                    <Button key={t} variant="secondary" href={base}>
                      {t}
                    </Button>
                  ),
                )}
              </div>
            </div>
          ) : (
            <>
              <article className="message user-message">
                <strong>나</strong>
                <p>
                  {viewer
                    ? "어머니는 어떤 음식을 좋아하시나요?"
                    : "오늘 저녁에는 가족들과 함께 국수를 먹었어."}
                </p>
                {!viewer && (
                  <Link
                    className="message-edit"
                    href={base + "/edit"}
                    aria-label="발언 정정"
                  >
                    ✎
                  </Link>
                )}
              </article>
              <article className="message ai-message">
                <strong>{viewer ? "김영희 AI 클론" : "EVERME"}</strong>
                {variant === "error" ? (
                  <>
                    <p>응답을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
                    <Button href={base} variant="secondary">
                      다시 시도
                    </Button>
                  </>
                ) : variant === "streaming" ? (
                  <p>
                    가족과 함께한 따뜻한 시간이었겠어요.{" "}
                    <span className="stream-cursor">▍</span>
                  </p>
                ) : viewer ? (
                  <>
                    <p>
                      <Badge>기록된 사실</Badge>
                    </p>
                    <p>
                      국수를 좋아하고, 가족과 함께 먹는 시간을 소중히 여긴다고
                      기록되어 있어요.
                    </p>
                    <p>
                      <Badge tone="blue">AI 추론</Badge>
                    </p>
                    <p>담백한 음식을 선호하실 가능성이 있어요.</p>
                    <Link className="text-link" href="/viewer/chat/evidence">
                      응답 근거 보기 →
                    </Link>
                    <small>
                      AI 추론은 현재의 생각이나 확정된 의사가 아닙니다.
                    </small>
                  </>
                ) : (
                  <>
                    <p>
                      가족과 함께한 따뜻한 시간이었겠어요.
                      <br />
                      오늘 저녁에서 가장 기억에 남는 순간은 무엇인가요?
                    </p>
                    <small>대화가 저장되었어요</small>
                  </>
                )}
              </article>
            </>
          )}
        </div>
        {voice ? (
          <div className="voice-panel">
            <Badge>음성 대화 중</Badge>
            <div className="waveform" aria-label="음성 파형">
              {Array.from({ length: 25 }, (_, i) => (
                <i key={i} style={{ height: 16 + ((i * 17) % 52) }} />
              ))}
            </div>
            <h3>편하게 말씀해 주세요</h3>
            <p className="muted">듣고 있어요 · 나의 이야기를 이어가세요</p>
            <Button href={base} variant="secondary">
              텍스트로 전환
            </Button>
            <Button href={base}>음성 대화 종료</Button>
          </div>
        ) : (
          <div className="composer-wrap">
            <div className="composer">
              <textarea
                aria-label="대화 입력"
                placeholder="편하게 이야기해 주세요"
              />
              <div>
                <Link
                  className="mic-button"
                  href={base + "/voice"}
                  aria-label="음성 대화로 전환"
                >
                  <Icon name="mic" />
                </Link>
                <span className="muted">음성으로도 이야기할 수 있어요</span>
                <Link
                  className="send-button"
                  href={base}
                  aria-label="메시지 보내기"
                >
                  <Icon name="send" />
                </Link>
              </div>
            </div>
            <small className="muted">
              {viewer
                ? "AI 답변은 참고용이며, 본인의 의사결정이나 동의를 대신하지 않습니다."
                : "AI가 잘못 이해할 수 있어요. 나의 발언은 언제든 정정할 수 있어요."}
            </small>
          </div>
        )}
      </div>
      {["delete", "edit"].includes(variant) && (
        <Modal
          title={
            variant === "delete" ? "대화를 삭제하시겠어요?" : "대화 제목 수정"
          }
          closeHref={base}
        >
          {variant === "delete" ? (
            <p className="muted">삭제한 대화는 다시 확인할 수 없어요.</p>
          ) : (
            <Field label="대화 제목" defaultValue="가족과 함께한 저녁" />
          )}
          <div className="modal-actions">
            <Button variant="secondary" href={base}>
              취소
            </Button>
            <Button
              variant={variant === "delete" ? "danger" : "primary"}
              href={variant === "delete" ? base + "/empty" : base}
            >
              {variant === "delete" ? "삭제" : "저장"}
            </Button>
          </div>
        </Modal>
      )}
      {evidence && (
        <Modal title="응답의 근거" closeHref={base} drawer>
          <Badge>L2 개인</Badge>
          <p className="muted">허용된 L2 이하의 원본만 표시해요.</p>
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
            <section className="evidence-section" key={t}>
              <h3>{t}</h3>
              <p>{d}</p>
            </section>
          ))}
        </Modal>
      )}
    </AppLayout>
  );
}
