"use client";

import Link from "next/link";
import Card from "@/components/Card";
import Button from "@/components/Button";
import Field from "@/components/Field";
import Icon from "@/components/Icon";

export default function BasicSettings({ onSave, onOpenModal }) {
  return (
    <section aria-labelledby="basic-settings" className="mt-10">
      <h2 id="basic-settings" className="mb-5 text-[22px] font-bold">
        기본 설정
      </h2>
      <div className="grid grid-cols-2 items-stretch gap-6 [&_>_.ui-card]:m-0 [&_>_.ui-card]:min-w-0 [@media(max-width:_1100px)]:grid-cols-1">
        <Card>
          <h3>내 정보</h3>
          <Field label="이름" defaultValue="김영희" />
          <Field
            label="이메일"
            type="email"
            defaultValue="younghee@email.com"
            disabled
            className="disabled:cursor-not-allowed disabled:bg-surface disabled:text-muted"
          />
          <div className="grid grid-cols-2 gap-x-6 [@media(max-width:_760px)]:grid-cols-1">
            <Field
              label="생년월일"
              type="date"
              name="birthDate"
              autoComplete="bday"
            />
            <Field
              label="국적"
              name="nationality"
              placeholder="예: 대한민국"
            />
            <Field
              label="전화번호"
              type="tel"
              name="phone"
              autoComplete="tel"
              placeholder="010-1234-5678"
            />
            <label className="my-[22px] flex flex-col gap-3 text-[14px]">
              <span>성별</span>
              <select
                name="gender"
                defaultValue=""
                className="h-12.5 w-full rounded-lg border border-line bg-white px-4 text-ink"
              >
                <option value="" disabled>
                  선택해 주세요
                </option>
                <option value="male">남성</option>
                <option value="female">여성</option>
                <option value="other">기타</option>
                <option value="unspecified">선택하지 않음</option>
              </select>
            </label>
          </div>
          <div className="flex justify-end">
            <Button onClick={onSave}>변경사항 저장</Button>
          </div>
        </Card>
        <Card>
          <h3>비밀번호 및 계정</h3>
          <p>
            <Link
              className="ui-text-link flex items-center justify-between gap-3 text-green font-medium"
              href="/reset-password"
            >
              비밀번호 재설정
              <span className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-green text-white">
                <Icon name="arrow-right" size={18} />
              </span>
            </Link>
          </p>
          <div className="my-6 divide-y divide-line">
            {[
              [
                "devices",
                "로그인 기기 관리",
                "접속 중인 기기를 확인하고 개별 로그아웃해요.",
              ],
              [
                "logout-all",
                "전체 기기 로그아웃",
                "현재 기기를 포함한 모든 로그인 세션을 종료해요.",
              ],
              [
                "two-factor",
                "2단계 인증",
                "로그인 시 추가 인증을 사용하는 설정이에요.",
              ],
              [
                "connections",
                "연결된 계정",
                "소셜 로그인 계정의 연결 상태를 관리해요.",
              ],
              [
                "export",
                "내 데이터 내려받기",
                "대화와 기억 기록을 파일로 보관해요.",
              ],
            ].map(([id, title, description]) => (
              <button
                key={id}
                type="button"
                onClick={() => onOpenModal(id)}
                className="flex w-full items-center justify-between gap-4 py-5 text-left hover:text-green"
              >
                <span className="min-w-0">
                  <strong className="block text-[14px]">{title}</strong>
                  <span className="mt-1 block text-[13px] text-muted">
                    {description}
                  </span>
                </span>
                <span aria-hidden="true" className="shrink-0">
                  →
                </span>
              </button>
            ))}
          </div>
          <button
            type="button"
            className="ui-muted text-muted"
            onClick={() => onOpenModal("delete")}
          >
            회원탈퇴
          </button>
        </Card>
      </div>
    </section>
  );
}
