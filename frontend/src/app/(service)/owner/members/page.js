"use client";

import { Fragment, useCallback, useState } from "react";
import Button from "@/components/Button";
import Badge from "@/components/Badge";
import Card from "@/components/Card";
import Notice from "@/components/Notice";
import Modal from "@/components/Modal";
import Field from "@/components/Field";
import Icon from "@/components/Icon";
const people = [
  {
    name: "김민수",
    email: "minsu@email.com",
    group: "가족",
    status: "정상",
    level: 2,
  },
  {
    name: "이수진",
    email: "sujin@email.com",
    group: "가족",
    status: "초대 중",
    level: 2,
  },
  {
    name: "박지은",
    email: "jieun@email.com",
    group: "친구",
    status: "정상",
    level: 1,
  },
  {
    name: "최서연",
    email: "seoyeon@email.com",
    group: null,
    status: "정상",
    level: 1,
  },
  {
    name: "정우진",
    email: "woojin@email.com",
    group: null,
    status: "초대 거절",
    level: 1,
  },
  {
    name: "한도윤",
    email: "doyun@email.com",
    group: null,
    status: "삭제",
    level: 2,
  },
];
const accessLevels = [
  "취미 · 관심사",
  "가족관계 · 개인적인 경험",
  "건강 · 종교",
  "인증정보 · 매우 민감한 기록",
];
function AccessSlider({
  value,
  label,
  disabled,
  mixed,
  onChange,
  fullWidth = false,
}) {
  return (
    <div className={`${fullWidth ? "w-full" : "w-60"} py-1`}>
      <div className="text-[12px] font-medium text-green">
        {mixed ? "개별 설정 · 변경 시 일괄 적용" : accessLevels[value - 1]}
      </div>
      <div className={`relative h-4 ${disabled ? "opacity-40" : ""}`}>
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-1.5 top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-green-light"
        >
          <span
            className={`absolute inset-y-0 left-0 rounded-full ${["bg-green/30", "bg-green/50", "bg-green/75", "bg-green"][value - 1]}`}
            style={{ width: `${((value - 1) / 3) * 100}%` }}
          />
          <span className="absolute left-1/3 top-1/2 h-1 w-px -translate-x-1/2 -translate-y-1/2 bg-ink/40" />
          <span className="absolute left-2/3 top-1/2 h-1 w-px -translate-x-1/2 -translate-y-1/2 bg-ink/40" />
        </div>
        <input
          type="range"
          min="1"
          max="4"
          step="1"
          value={value}
          disabled={disabled}
          aria-label={`${label} 접근 범위`}
          aria-valuetext={
            mixed ? "구성원별 접근 범위가 다름" : accessLevels[value - 1]
          }
          onChange={(event) => onChange(Number(event.target.value))}
          className="relative z-10 m-0 block h-4 w-full cursor-pointer appearance-none bg-transparent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green disabled:cursor-not-allowed [&::-webkit-slider-runnable-track]:h-1.5 [&::-webkit-slider-runnable-track]:bg-transparent [&::-webkit-slider-thumb]:-mt-0.75 [&::-webkit-slider-thumb]:size-3 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-green [&::-moz-range-track]:h-1.5 [&::-moz-range-track]:bg-transparent [&::-moz-range-thumb]:size-3 [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:border-0 [&::-moz-range-thumb]:bg-green"
        />
      </div>
    </div>
  );
}
export default function MembersPage() {
  const [members, setMembers] = useState(people);
  const [inviteLevel, setInviteLevel] = useState(1);
  const [groups, setGroups] = useState([
    { name: "가족", level: 2 },
    { name: "친구", level: 1 },
  ]);
  const [modal, setModal] = useState(null);
  const [expandedGroups, setExpandedGroups] = useState(["가족", "친구"]);
  const [selectedGroup, setSelectedGroup] = useState("가족");
  const [groupName, setGroupName] = useState("");
  const [selectedEmail, setSelectedEmail] = useState(null);
  const [destination, setDestination] = useState("");
  const selectedPerson = members.find(
    (person) => person.email === selectedEmail,
  );
  function saveGroup(event) {
    event.preventDefault();
    const name = groupName.trim();
    if (
      !name ||
      groups.some(
        (group) =>
          group.name === name &&
          (modal === "group-create" || group.name !== selectedGroup),
      )
    )
      return;
    if (modal === "group-create") {
      setGroups((current) => [...current, { name, level: 1 }]);
      setExpandedGroups((current) => [...current, name]);
    } else {
      setGroups((current) =>
        current.map((group) =>
          group.name === selectedGroup ? { ...group, name } : group,
        ),
      );
      setMembers((current) =>
        current.map((person) =>
          person.group === selectedGroup ? { ...person, group: name } : person,
        ),
      );
      setExpandedGroups((current) =>
        current.map((group) => (group === selectedGroup ? name : group)),
      );
    }
    closeModal();
  }
  const closeModal = useCallback(() => setModal(null), []);
  function toggleGroup(group) {
    setExpandedGroups((groups) =>
      groups.includes(group)
        ? groups.filter((item) => item !== group)
        : [...groups, group],
    );
  }
  return (
    <>
      <main className="p-[34px_40px_48px] max-w-400 m-[0_auto] [&_>_p.ui-muted]:mb-8 [@media(max-width:_1100px)]:p-[28px_24px] [@media(max-width:_760px)]:p-[24px_20px] [@media(max-width:_760px)]:[&_h1]:text-[23px]">
        <div className="flex justify-between items-center gap-5 [&_p]:m-0 [@media(max-width:_1100px)]:items-start [@media(max-width:_760px)]:block [@media(max-width:_760px)]:[&_>_.ui-button]:mt-4">
          <div>
            <h1>내 이야기를 나눌 사람들</h1>
            <p className="ui-muted text-muted">
              그룹별로 정보이용자와 접근 등급을 함께 관리하세요.
            </p>
          </div>
          <Button onClick={() => setModal("invite")}>정보이용자 초대</Button>
        </div>
        <Card>
          <div className="flex justify-between items-center gap-5 [&_h2]:m-0 [&_p]:m-[8px_0_20px]">
            <div>
              <h2>
                전체 {members.length}명 · 그룹 {groups.length}개
              </h2>
              <p className="ui-muted text-muted">
                그룹을 펼쳐 구성원을 확인하고 권한을 변경하세요.
              </p>
            </div>
            <Button
              variant="secondary"
              onClick={() => {
                setGroupName("");
                setModal("group-create");
              }}
            >
              ＋ 그룹 만들기
            </Button>
          </div>
          <div className="overflow-auto">
            <table className="min-w-190 [&_td]:py-2 [&_th]:py-3">
              <thead className="[&_th]:bg-green [&_th]:text-green-light">
                <tr>
                  {["이름", "이메일", "접근 범위", "상태"].map((title) => (
                    <th key={title}>{title}</th>
                  ))}
                  <th>
                    <span className="sr-only">이용자 메뉴</span>
                  </th>
                </tr>
              </thead>
              {[null, ...groups.map((group) => group.name)].map(
                (group, index) => {
                  const groupMembers = members.filter(
                    (person) => person.group === group,
                  );
                  const setting = groups.find((item) => item.name === group);
                  const mixed = groupMembers.some(
                    (person) =>
                      person.level !==
                      (groupMembers[0]?.level ?? setting?.level),
                  );
                  return (
                    <Fragment key={group ?? "ungrouped"}>
                      {group && (
                        <tbody>
                          <tr className="bg-[color-mix(in_srgb,var(--green)_20%,white)] font-semibold">
                            <td colSpan={2}>
                              <button
                                type="button"
                                className="flex items-center gap-3"
                                onClick={() => toggleGroup(group)}
                                aria-expanded={expandedGroups.includes(group)}
                                aria-controls={`members-${index}`}
                              >
                                <span
                                  className={`inline-flex transition-transform motion-reduce:transition-none ${expandedGroups.includes(group) ? "rotate-180" : "rotate-90"}`}
                                >
                                  <Icon name="chevron-up" size={18} />
                                </span>
                                {group}
                                <Badge>{groupMembers.length}명</Badge>
                              </button>
                            </td>
                            <td>
                              <AccessSlider
                                label={group}
                                value={groupMembers[0]?.level ?? setting.level}
                                mixed={mixed}
                                onChange={(level) => {
                                  setGroups((current) =>
                                    current.map((item) =>
                                      item.name === group
                                        ? { ...item, level }
                                        : item,
                                    ),
                                  );
                                  setMembers((current) =>
                                    current.map((person) =>
                                      person.group === group
                                        ? { ...person, level }
                                        : person,
                                    ),
                                  );
                                }}
                              />
                            </td>
                            <td />
                            <td>
                              <button
                                type="button"
                                className="rounded px-2 py-1 hover:bg-green-light"
                                aria-label={`${group} 그룹 수정`}
                                onClick={() => {
                                  setSelectedGroup(group);
                                  setGroupName(group);
                                  setModal("group-edit");
                                }}
                              >
                                ···
                              </button>
                            </td>
                          </tr>
                        </tbody>
                      )}
                      <tbody
                        id={`members-${index}`}
                        hidden={Boolean(
                          group && !expandedGroups.includes(group),
                        )}
                      >
                        {groupMembers.map((person) => (
                          <tr key={person.email}>
                            <td>
                              <span className={group ? "block pl-8" : "block"}>
                                {person.name}
                              </span>
                            </td>
                            <td className="text-muted">{person.email}</td>
                            <td>
                              <AccessSlider
                                label={person.name}
                                value={person.level}
                                disabled={
                                  person.status === "삭제" ||
                                  person.status === "초대 거절"
                                }
                                onChange={(level) =>
                                  setMembers((current) =>
                                    current.map((member) =>
                                      member.email === person.email
                                        ? { ...member, level }
                                        : member,
                                    ),
                                  )
                                }
                              />
                            </td>
                            <td>
                              {person.status === "정상" ? (
                                <Badge>{person.status}</Badge>
                              ) : (
                                <span className="inline-flex rounded-[7px] bg-surface px-3 py-1 text-[13px] text-muted">
                                  {person.status}
                                </span>
                              )}
                            </td>
                            <td>
                              <button
                                type="button"
                                className="rounded px-2 py-1 hover:bg-green-light"
                                aria-label={`${person.name} 이용자 관리`}
                                onClick={() => {
                                  setSelectedEmail(person.email);
                                  setDestination(person.group ?? "");
                                  setModal("person");
                                }}
                              >
                                ···
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </Fragment>
                  );
                },
              )}
            </table>
          </div>
        </Card>
        <Notice>
          정보이용자는 부여받은 최대 접근 등급 이하의 정보만 확인할 수 있어요.
        </Notice>
      </main>
      {modal === "invite" && (
        <Modal title="내 이야기를 나눌 사람 초대" onClose={closeModal}>
          <Field
            label="초대할 이메일"
            type="email"
            placeholder="이메일 주소를 입력해 주세요"
          />
          <div className="my-[22px] flex flex-col gap-3 text-[14px]">
            <span>최대 접근 등급</span>
            <AccessSlider
              fullWidth
              value={inviteLevel}
              label="초대할 이용자의 최대"
              onChange={setInviteLevel}
            />
          </div>
          <Notice title="접근 등급을 확인해 주세요">
            선택한 등급 이하의 정보만 이용할 수 있어요.
          </Notice>
          <div className="flex gap-3 mt-9 [&_.ui-button]:flex-1">
            <Button onClick={closeModal} variant="secondary">
              취소
            </Button>
            <Button onClick={closeModal}>초대 보내기</Button>
          </div>
        </Modal>
      )}
      {["group-create", "group-edit"].includes(modal) && (
        <Modal
          title={modal === "group-create" ? "그룹 만들기" : "그룹 수정"}
          onClose={closeModal}
        >
          <form onSubmit={saveGroup}>
            <Field
              label="그룹 이름"
              value={groupName}
              onChange={(event) => setGroupName(event.target.value)}
              required
            />
            {groups.some(
              (group) =>
                group.name === groupName.trim() &&
                (modal === "group-create" || group.name !== selectedGroup),
            ) && (
              <p role="alert" className="text-[13px] text-muted">
                이미 사용 중인 그룹 이름입니다.
              </p>
            )}
            {modal === "group-edit" && (
              <button
                type="button"
                className="text-red-600"
                onClick={() => setModal("group-delete")}
              >
                그룹 삭제
              </button>
            )}
            <div className="mt-6 flex gap-3 [&_.ui-button]:flex-1">
              <Button variant="secondary" onClick={closeModal}>
                취소
              </Button>
              <Button type="submit">
                {modal === "group-create" ? "생성" : "저장"}
              </Button>
            </div>
          </form>
        </Modal>
      )}
      {modal === "group-delete" && (
        <Modal title="그룹을 삭제하시겠어요?" onClose={closeModal}>
          <p className="text-muted">
            구성원은 삭제되지 않고 미분류 이용자로 이동합니다. 개인별 접근
            범위는 유지됩니다.
          </p>
          <div className="mt-6 flex gap-3 [&_.ui-button]:flex-1">
            <Button variant="secondary" onClick={closeModal}>
              취소
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                setGroups((current) =>
                  current.filter((group) => group.name !== selectedGroup),
                );
                setMembers((current) =>
                  current.map((person) =>
                    person.group === selectedGroup
                      ? { ...person, group: null }
                      : person,
                  ),
                );
                setExpandedGroups((current) =>
                  current.filter((group) => group !== selectedGroup),
                );
                closeModal();
              }}
            >
              그룹 삭제
            </Button>
          </div>
        </Modal>
      )}
      {modal === "person" && selectedPerson && (
        <Modal
          title={`${selectedPerson.name} 이용자 관리`}
          onClose={closeModal}
        >
          <div className="flex flex-col gap-3">
            <Button variant="secondary" onClick={() => setModal("person-move")}>
              그룹 이동
            </Button>
            <Button
              variant="danger"
              disabled={selectedPerson.status === "삭제"}
              onClick={() => setModal("person-delete")}
            >
              이용자 삭제
            </Button>
          </div>
        </Modal>
      )}
      {modal === "person-move" && selectedPerson && (
        <Modal title={`${selectedPerson.name} 그룹 이동`} onClose={closeModal}>
          <label className="flex flex-col gap-3 text-[14px]">
            이동할 그룹
            <select
              value={destination}
              onChange={(event) => setDestination(event.target.value)}
              className="h-12 rounded-lg border border-line bg-white px-4"
            >
              <option value="">미분류</option>
              {groups.map((group) => (
                <option key={group.name} value={group.name}>
                  {group.name}
                </option>
              ))}
            </select>
          </label>
          <p className="text-[13px] text-muted">
            이동 후에도 개인별 접근 범위는 유지됩니다.
          </p>
          <div className="mt-6 flex gap-3 [&_.ui-button]:flex-1">
            <Button variant="secondary" onClick={closeModal}>
              취소
            </Button>
            <Button
              onClick={() => {
                setMembers((current) =>
                  current.map((person) =>
                    person.email === selectedEmail
                      ? { ...person, group: destination || null }
                      : person,
                  ),
                );
                if (destination)
                  setExpandedGroups((current) =>
                    current.includes(destination)
                      ? current
                      : [...current, destination],
                  );
                closeModal();
              }}
            >
              이동
            </Button>
          </div>
        </Modal>
      )}
      {modal === "person-delete" && selectedPerson && (
        <Modal
          title={`${selectedPerson.name}님을 삭제하시겠어요?`}
          onClose={closeModal}
        >
          <p className="text-muted">
            삭제하면 더 이상 나의 AI 클론을 이용할 수 없습니다.
          </p>
          <div className="mt-6 flex gap-3 [&_.ui-button]:flex-1">
            <Button variant="secondary" onClick={closeModal}>
              취소
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                setMembers((current) =>
                  current.map((person) =>
                    person.email === selectedEmail
                      ? { ...person, status: "삭제" }
                      : person,
                  ),
                );
                closeModal();
              }}
            >
              이용자 삭제
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
