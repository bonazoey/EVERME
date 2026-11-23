"use client";

import { createContext, useContext, useState } from "react";

const ChatSessionsContext = createContext(null);
export const initialConversations = [
  {
    id: "family",
    updatedAt: "2026-11-23T12:00:00+09:00",
    title: "가족과 함께한 저녁",
    turns: [
      {
        user: "오늘 저녁에는 가족들과 함께 국수를 먹었어.",
        assistant:
          "가족과 둘러앉아 먹는 국수라니 따뜻한 저녁이었겠어요. 어떤 이야기를 나누셨나요?",
      },
      {
        user: "딸이 회사에서 있었던 일을 얘기했는데, 다 같이 한참 웃었어.",
        assistant:
          "평범한 식탁이 웃음으로 가득해졌네요. 그때 유독 기억에 남는 장면이 있나요?",
      },
      {
        user: "남편이 딸 흉내를 내다가 국수를 떨어뜨린 거. 이런 저녁이 오래 이어졌으면 좋겠어.",
        assistant:
          "남편분의 장난과 딸의 웃음소리까지 떠오르는 장면이네요. 오늘은 ‘국수 한 그릇과 가족의 웃음으로 채운 저녁’으로 기억해 두면 좋겠어요.",
      },
    ],
  },
  {
    id: "food",
    updatedAt: "2026-11-22T12:00:00+09:00",
    title: "좋아하는 음식 이야기",
    turns: [
      {
        user: "나는 화려한 요리보다 엄마가 끓여주던 된장찌개가 좋아.",
        assistant:
          "익숙한 맛에 어머니와의 기억도 담겨 있겠어요. 어머니의 된장찌개에는 어떤 특징이 있었나요?",
      },
      {
        user: "애호박이 큼직하게 들어가고, 마지막에 청양고추를 조금 넣으셨어.",
        assistant:
          "달큰한 애호박에 살짝 매콤한 국물이었겠네요. 지금도 직접 끓여 드시나요?",
      },
      {
        user: "응. 주말 아침에 끓이면 어릴 때 집에 있는 기분이 들어.",
        assistant:
          "주말 아침의 된장찌개가 어린 시절로 이어지는 작은 습관이군요. 애호박과 청양고추, 그리고 집처럼 편안한 마음이 함께 담긴 음식이네요.",
      },
    ],
  },
  {
    id: "travel",
    updatedAt: "2026-11-20T12:00:00+09:00",
    title: "오래 기억하고 싶은 여행",
    turns: [
      {
        user: "몇 년 전에 혼자 갔던 제주 여행이 아직도 생각나.",
        assistant:
          "혼자 떠난 여행이라 더 선명한 순간들이 있겠어요. 가장 먼저 떠오르는 장소는 어디인가요?",
      },
      {
        user: "아침 일찍 걸었던 바닷가. 사람도 없고 파도 소리만 들렸어.",
        assistant:
          "조용한 바닷가를 온전히 혼자 누리셨네요. 그 길을 걸으며 어떤 생각을 하셨나요?",
      },
      {
        user: "아무것도 안 해도 괜찮다는 생각. 늘 바쁘게 살아야 하는 줄 알았거든.",
        assistant:
          "그 여행에서 잠시 멈춰도 괜찮다는 여유를 만나셨군요. 제주 바다의 파도 소리를 떠올리면 그때의 편안함도 다시 느껴볼 수 있겠어요.",
      },
    ],
  },
  {
    id: "values",
    updatedAt: "2026-11-18T12:00:00+09:00",
    title: "나에게 소중한 것들",
    turns: [
      {
        user: "요즘은 거창한 성공보다 내 사람들과 잘 지내는 게 더 중요해.",
        assistant:
          "가까운 사람들과의 관계가 더욱 소중하게 느껴지시는군요. 최근 그런 마음이 들었던 일이 있나요?",
      },
      {
        user: "오랜 친구가 별일 없이 전화해 줬는데 그게 참 고맙더라고.",
        assistant:
          "특별한 용건 없이도 안부를 나눌 수 있는 사이가 큰 힘이 됐겠어요. 친구분과는 어떤 이야기를 나누셨나요?",
      },
      {
        user: "그냥 날씨랑 밥 얘기. 나도 먼저 안부를 묻는 사람이 되고 싶어.",
        assistant:
          "소소한 안부를 주고받는 관계를 아끼시는 마음이 느껴져요. 이번 주에 떠오르는 친구 한 분께 먼저 연락해 보는 것도 좋겠네요.",
      },
    ],
  },
  {
    id: "childhood",
    updatedAt: "2026-11-10T12:00:00+09:00",
    title: "어릴 적 살던 동네",
    turns: [
      {
        user: "어릴 때 살던 동네에는 작은 문방구가 있었어.",
        assistant:
          "학교가 끝나면 들르던 곳이었나요? 그 문방구에서 가장 좋아했던 게 궁금해요.",
      },
      {
        user: "백 원짜리 뽑기. 친구들이랑 누가 좋은 걸 뽑나 내기했지.",
        assistant:
          "작은 뽑기 하나에도 기대가 가득했겠어요. 특별히 기억에 남는 상품도 있나요?",
      },
      {
        user: "한 번은 큰 지우개를 뽑았는데 아까워서 못 썼어.",
        assistant:
          "쓰지 않고 간직할 만큼 뿌듯한 선물이었네요. 문방구와 친구들, 커다란 지우개가 함께 떠오르는 추억이에요.",
      },
    ],
  },
  {
    id: "garden",
    updatedAt: "2026-11-05T12:00:00+09:00",
    title: "베란다에 찾아온 작은 봄",
    turns: [
      {
        user: "베란다 화분에 새잎이 났어. 한동안 안 자라서 걱정했는데.",
        assistant:
          "기다리던 새잎이라 더 반가우셨겠어요. 어떤 식물을 키우고 계세요?",
      },
      {
        user: "친구한테 받은 몬스테라야. 아침마다 들여다봐.",
        assistant:
          "친구가 준 식물을 살피는 시간이 아침의 작은 즐거움이 되었네요. 새잎은 어떤 모습인가요?",
      },
      {
        user: "아직 돌돌 말려 있어. 천천히 펴지는 걸 보는 게 재밌어.",
        assistant:
          "조금씩 달라지는 모습을 지켜보는 재미가 있군요. 오늘의 작은 기쁨은 ‘몬스테라의 새잎을 기다리는 아침’이네요.",
      },
    ],
  },
  {
    id: "music",
    updatedAt: "2026-10-28T12:00:00+09:00",
    title: "노래 한 곡에 담긴 기억",
    turns: [
      {
        user: "라디오에서 대학 때 즐겨 듣던 노래가 나왔어.",
        assistant:
          "익숙한 멜로디가 그때의 기억을 데려왔겠어요. 어떤 장면이 떠올랐나요?",
      },
      {
        user: "축제 준비하면서 친구들이랑 밤늦게까지 연습하던 장면.",
        assistant:
          "함께 준비하던 설렘과 분주함이 느껴져요. 어떤 공연을 준비하셨나요?",
      },
      {
        user: "기타 치면서 노래했어. 실수도 많았지만 정말 즐거웠지.",
        assistant:
          "완벽함보다 함께한 즐거움이 오래 남았네요. 라디오 속 노래가 그날의 기타 소리와 친구들의 얼굴을 다시 떠올리게 해주었군요.",
      },
    ],
  },
  {
    id: "walk",
    updatedAt: "2026-09-20T12:00:00+09:00",
    title: "저녁 산책길에서",
    turns: [
      {
        user: "요즘 저녁 먹고 동네 하천을 따라 걷고 있어.",
        assistant:
          "하루를 마무리하는 산책이군요. 걸으면서 가장 좋아하는 풍경이 있나요?",
      },
      {
        user: "다리 아래로 불빛이 비치는 모습. 물결 때문에 반짝거려.",
        assistant: "물결 위로 흔들리는 불빛이 예쁘겠어요. 혼자 걷는 편이세요?",
      },
      {
        user: "주로 혼자. 걸으면 복잡했던 생각이 정리되는 느낌이야.",
        assistant:
          "그 산책길이 생각을 천천히 정돈하는 시간이 되어주네요. 반짝이는 물결을 바라보며 하루를 내려놓는 순간이군요.",
      },
    ],
  },
  {
    id: "challenge",
    updatedAt: "2026-08-15T12:00:00+09:00",
    title: "새롭게 시작한 수채화",
    turns: [
      {
        user: "이번 달부터 수채화를 배우기 시작했어.",
        assistant:
          "새로운 취미를 시작하셨네요. 수채화를 선택한 계기가 있었나요?",
      },
      {
        user: "물감이 번지는 느낌이 좋아서. 생각처럼 그려지진 않지만.",
        assistant:
          "예상하지 못한 번짐도 수채화의 매력일 것 같아요. 첫 수업에서는 무엇을 그리셨나요?",
      },
      {
        user: "사과 하나 그렸어. 조금 삐뚤어도 내 그림이라 마음에 들어.",
        assistant:
          "직접 그린 첫 사과라 더 애정이 가겠어요. 잘 그려야 한다는 부담보다 그리는 즐거움을 발견한 시작이네요.",
      },
    ],
  },
  {
    id: "letter",
    updatedAt: "2026-07-01T12:00:00+09:00",
    title: "미래의 나에게 남기는 말",
    turns: [
      {
        user: "십 년 뒤의 나한테 편지를 써보고 싶어.",
        assistant:
          "지금의 마음을 미래에 전하는 편지군요. 가장 먼저 어떤 말을 건네고 싶으세요?",
      },
      {
        user: "너무 걱정하지 말고 좋아하는 일도 하면서 살라고.",
        assistant:
          "미래의 나에게 여유와 즐거움을 당부하고 싶으시군요. 지금 좋아하는 일도 함께 적어볼까요?",
      },
      {
        user: "가족이랑 밥 먹고, 걷고, 가끔 그림 그리는 거. 그때도 그랬으면 해.",
        assistant:
          "‘십 년 뒤에도 함께 밥을 먹고, 산책하고, 그림을 그리는 즐거움을 잊지 않았으면 해.’ 지금의 소박하고 소중한 바람이 잘 담긴 편지가 되겠어요.",
      },
    ],
  },
];

export const initialViewerConversations = [
  {
    id: "viewer-food",
    title: "어머니가 좋아하시는 음식",
    message: "어머니는 어떤 음식을 좋아하시나요?",
    path: "/viewer/chat",
    updatedAt: "2026-11-23T12:00:00+09:00",
  },
  {
    id: "viewer-family",
    title: "가족과의 식사에 담긴 기억",
    message: "어머니는 가족과 함께 먹는 시간을 어떻게 기억하시나요?",
    path: "/viewer/chat",
    updatedAt: "2026-11-12T12:00:00+09:00",
  },
  {
    id: "viewer-park",
    title: "아버지의 음식 취향",
    message: "아버지가 좋아하시는 음식이 궁금해요.",
    path: "/viewer/chat/park",
    updatedAt: "2026-10-28T12:00:00+09:00",
  },
];

export function getInitialConversations(mode) {
  return mode === "viewer" ? initialViewerConversations : initialConversations;
}

export function ChatSessionsProvider({ children }) {
  const [conversations, setConversations] = useState({});
  function startConversation() {
    return crypto.randomUUID();
  }
  function completeConversation(mode, text, conversationId, path) {
    const id = conversationId ?? crypto.randomUUID();
    const title = text.slice(0, 30);
    setConversations((previous) => {
      const current = previous[mode] ?? getInitialConversations(mode);
      if (current.some((item) => item.id === id)) return previous;
      return {
        ...previous,
        [mode]: [
          {
            id,
            title,
            message: text,
            path,
            updatedAt: new Date().toISOString(),
          },
          ...current,
        ],
      };
    });
    return { id, title };
  }
  function renameConversation(mode, id, title) {
    if (!title.trim()) return;
    setConversations((previous) => ({
      ...previous,
      [mode]: (previous[mode] ?? getInitialConversations(mode)).map((item) =>
        item.id === id ? { ...item, title: title.trim() } : item,
      ),
    }));
  }
  function deleteConversation(mode, id) {
    setConversations((previous) => ({
      ...previous,
      [mode]: (previous[mode] ?? getInitialConversations(mode)).filter(
        (item) => item.id !== id,
      ),
    }));
  }
  return (
    <ChatSessionsContext.Provider
      value={{
        conversations,
        startConversation,
        completeConversation,
        renameConversation,
        deleteConversation,
      }}
    >
      {children}
    </ChatSessionsContext.Provider>
  );
}

export function useChatSessions() {
  return useContext(ChatSessionsContext);
}
