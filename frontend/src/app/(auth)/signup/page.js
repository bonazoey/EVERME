"use client";

import { useContext, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Button from "@/components/Button";
import Field from "@/components/Field";
import { memberSignUp } from "../../../../apis/memberApi";
import { AuthContext } from "@/app/context/AuthContext";
// 나라선택
const countries = [
  "가나",
  "가봉",
  "가이아나",
  "감비아",
  "건지",
  "과들루프",
  "과테말라",
  "괌",
  "그레나다",
  "그리스",
  "그린란드",
  "기니",
  "기니비사우",
  "나미비아",
  "나우루",
  "나이지리아",
  "남극 대륙",
  "남수단",
  "남아프리카",
  "네덜란드",
  "네덜란드령 카리브",
  "네팔",
  "노르웨이",
  "노퍽섬",
  "뉴질랜드",
  "뉴칼레도니아",
  "니우에",
  "니제르",
  "니카라과",
  "대만",
  "대한민국",
  "덴마크",
  "도미니카",
  "도미니카 공화국",
  "독일",
  "동티모르",
  "라오스",
  "라이베리아",
  "라트비아",
  "러시아",
  "레바논",
  "레소토",
  "레위니옹",
  "루마니아",
  "룩셈부르크",
  "르완다",
  "리비아",
  "리투아니아",
  "리히텐슈타인",
  "마다가스카르",
  "마르티니크",
  "마셜 제도",
  "마요트",
  "마카오(중국 특별행정구)",
  "말라위",
  "말레이시아",
  "말리",
  "맨섬",
  "멕시코",
  "모나코",
  "모로코",
  "모리셔스",
  "모리타니",
  "모잠비크",
  "몬테네그로",
  "몬트세라트",
  "몰도바",
  "몰디브",
  "몰타",
  "몽골",
  "미국",
  "미국령 버진아일랜드",
  "미국령 해외 제도",
  "미얀마",
  "미크로네시아",
  "바누아투",
  "바레인",
  "바베이도스",
  "바티칸 시국",
  "바하마",
  "방글라데시",
  "버뮤다",
  "베냉",
  "베네수엘라",
  "베트남",
  "벨기에",
  "벨라루스",
  "벨리즈",
  "보스니아 헤르체고비나",
  "보츠와나",
  "볼리비아",
  "부룬디",
  "부르키나파소",
  "부베섬",
  "부탄",
  "북마리아나제도",
  "북마케도니아",
  "북한",
  "불가리아",
  "브라질",
  "브루나이",
  "사모아",
  "사우디아라비아",
  "사우스조지아 사우스샌드위치 제도",
  "산마리노",
  "상투메 프린시페",
  "생마르탱",
  "생바르텔레미",
  "생피에르 미클롱",
  "서사하라",
  "세네갈",
  "세르비아",
  "세이셸",
  "세인트루시아",
  "세인트빈센트그레나딘",
  "세인트키츠 네비스",
  "세인트헬레나",
  "소말리아",
  "솔로몬 제도",
  "수단",
  "수리남",
  "스리랑카",
  "스발바르제도-얀마웬섬",
  "스웨덴",
  "스위스",
  "스페인",
  "슬로바키아",
  "슬로베니아",
  "시리아",
  "시에라리온",
  "신트마르턴",
  "싱가포르",
  "아랍에미리트",
  "아루바",
  "아르메니아",
  "아르헨티나",
  "아메리칸 사모아",
  "아이슬란드",
  "아이티",
  "아일랜드",
  "아제르바이잔",
  "아프가니스탄",
  "안도라",
  "알바니아",
  "알제리",
  "앙골라",
  "앤티가 바부다",
  "앵귈라",
  "에리트리아",
  "에스와티니",
  "에스토니아",
  "에콰도르",
  "에티오피아",
  "엘살바도르",
  "영국",
  "영국령 버진아일랜드",
  "영국령 인도양 지역",
  "예멘",
  "오만",
  "오스트레일리아",
  "오스트리아",
  "온두라스",
  "올란드 제도",
  "왈리스-푸투나 제도",
  "요르단",
  "우간다",
  "우루과이",
  "우즈베키스탄",
  "우크라이나",
  "이라크",
  "이란",
  "이스라엘",
  "이집트",
  "이탈리아",
  "인도",
  "인도네시아",
  "일본",
  "자메이카",
  "잠비아",
  "저지",
  "적도 기니",
  "조지아",
  "중국",
  "중앙 아프리카 공화국",
  "지부티",
  "지브롤터",
  "짐바브웨",
  "차드",
  "체코",
  "칠레",
  "카메룬",
  "카보베르데",
  "카자흐스탄",
  "카타르",
  "캄보디아",
  "캐나다",
  "케냐",
  "케이맨 제도",
  "코모로",
  "코스타리카",
  "코코스 제도",
  "코트디부아르",
  "콜롬비아",
  "콩고-브라자빌",
  "콩고-킨샤사",
  "쿠바",
  "쿠웨이트",
  "쿡 제도",
  "퀴라소",
  "크로아티아",
  "크리스마스섬",
  "키르기스스탄",
  "키리바시",
  "키프로스",
  "타지키스탄",
  "탄자니아",
  "태국",
  "터크스 케이커스 제도",
  "토고",
  "토켈라우",
  "통가",
  "투르크메니스탄",
  "투발루",
  "튀니지",
  "튀르키예",
  "트리니다드 토바고",
  "파나마",
  "파라과이",
  "파키스탄",
  "파푸아뉴기니",
  "팔라우",
  "팔레스타인 지구",
  "페로 제도",
  "페루",
  "포르투갈",
  "포클랜드 제도",
  "폴란드",
  "푸에르토리코",
  "프랑스",
  "프랑스령 기아나",
  "프랑스령 남방 지역",
  "프랑스령 폴리네시아",
  "피지",
  "핀란드",
  "필리핀",
  "핏케언 제도",
  "허드 맥도널드 제도",
  "헝가리",
  "홍콩(중국 특별행정구)"
];

function formatPhone(value) {
  const international = value.startsWith("+");
  const digits = value.replace(/\D/g, "").slice(0, 15);
  if (international) return `+${digits}`;
  if (!digits.startsWith("0")) return digits;
  const areaLength = digits.startsWith("02") ? 2 : 3;
  if (digits.length <= areaLength) return digits;
  const rest = digits.slice(areaLength);
  if (rest.length <= 4) return `${digits.slice(0, areaLength)}-${rest}`;
  const middleLength = rest.length > 7 ? 4 : 3;
  return `${digits.slice(0, areaLength)}-${rest.slice(0, middleLength)}-${rest.slice(middleLength)}`;
}

export default function SignupPage() {
  const { login } = useContext(AuthContext);
  const [step, setStep] = useState("account");
  const [errors, setErrors] = useState({});
  const [submitted, setSubmitted] = useState(false);
  const [consentErrors, setConsentErrors] = useState([]);
  const [signupForm, setSignupForm] = useState({
    name: "", email: "", birthDate: "", nationality: "", phone: "",
    gender: "", password: "", confirmation: "",
  });
  const [consents, setConsents] = useState([true, true, true, true]);
  const router = useRouter();
  const headingRef = useRef(null);
  const accountRef = useRef(null);
  const previousPhoneRef = useRef("");
  function goToStep(step) {
    setStep(step);
    headingRef.current?.focus();
  }
  function returnToAccount() {
    goToStep("account");
  }
  function handleAccountChange(event) {
    const { name, value } = event.currentTarget;
    setSignupForm((current) => ({ ...current, [name]: value }));
  }
  function validateAccount(container) {
    const fields = Object.fromEntries(
      [...container.querySelectorAll("[name]")].map((field) => [field.name, field])
    );
    const result = {};
    if (!fields.name.value.trim()) result.name = "이름을 입력해 주세요.";
    if (!fields.email.value.trim()) result.email = "이메일을 입력해 주세요.";
    else if (fields.email.validity.typeMismatch) result.email = "올바른 이메일 주소를 입력해 주세요.";
    if (!fields.birthDate.value) result.birthDate = "생년월일을 입력해 주세요.";
    else if (fields.birthDate.value > new Date().toLocaleDateString("en-CA") || fields.birthDate.validity.badInput) result.birthDate = "올바른 생년월일을 입력해 주세요.";
    if (!fields.nationality.value) result.nationality = "국적을 선택해 주세요.";
    const phone = fields.phone.value;
    const phoneDigits = phone.replace(/\D/g, "");
    if (!phone) result.phone = "전화번호를 입력해 주세요.";
    else if (phone.startsWith("+") ? !/^\+[1-9][0-9]{6,14}$/.test(phone) : !/^0(?:2[0-9]{7,8}|[1-9][0-9]{8,9})$/.test(phoneDigits)) result.phone = "올바른 국내 전화번호 또는 +로 시작하는 국제 전화번호를 입력해 주세요.";
    if (!fields.gender.value) result.gender = "성별을 선택해 주세요.";
    if (!fields.password.value) result.password = "비밀번호를 입력해 주세요.";
    else if (fields.password.value.length < 8 || !/[a-zA-Z]/.test(fields.password.value) || !/[0-9]/.test(fields.password.value)) result.password = "비밀번호는 영문자와 숫자를 포함해 8자 이상 입력해 주세요.";
    if (!fields.confirmation.value) result.confirmation = "비밀번호를 다시 입력해 주세요.";
    else if (fields.password.value !== fields.confirmation.value) result.confirmation = "비밀번호가 일치하지 않습니다.";
    return result;
  }
  function confirmAccount() {
    const container = accountRef.current;
    const nextErrors = validateAccount(container);
    setSubmitted(true);
    setErrors(nextErrors);
    const firstError = Object.keys(nextErrors)[0];
    if (firstError) {
      container.querySelector(`[name="${firstError}"]`)?.focus();
      return;
    }
    goToStep("consent");
  }
  // 가입완료 
  async function confirmConsent() {
    const missingConsent = consents.flatMap((checked, index) => checked ? [] : [`consent-${index}`]);
    setConsentErrors(missingConsent);

    if (missingConsent.length) return;

    try {
      const resp = await memberSignUp(signupForm);
      const token = resp.data.token;

      login(signupForm.email, token);
      router.push("/owner/chat");
    } catch (error) {
      console.log(error);
    }


  }
  return (
    <>
      <h2 ref={headingRef} tabIndex={-1} className="signup-page">
        {step === "account" ? "회원가입" : "개인정보 및 AI 활용 동의"}
      </h2>
      <p className="ui-muted text-muted">
        안전한 시작을 위해 필요한 정보를 확인해 주세요.
      </p>
      <div
        ref={accountRef}
        hidden={step !== "account"}
        onChange={(event) => {
          if (submitted) setErrors(validateAccount(event.currentTarget));
        }}
        className="mt-5 gap-x-5 gap-y-3 [&:not([hidden])]:grid [&:not([hidden])]:grid-cols-2 [&_.ui-field]:m-0 [&_.ui-field]:gap-1.5 [&_.ui-field_input]:h-11 [&_.ui-field_select]:h-11 [&_>_.ui-button]:col-span-2 [&_>_a]:col-span-2 [@media(max-width:_480px)]:[&:not([hidden])]:grid-cols-1 [@media(max-width:_480px)]:[&_>_.ui-button]:col-span-1 [@media(max-width:_480px)]:[&_>_a]:col-span-1"
      >
        <Field
          label="이름"
          name="name"
          value={signupForm.name}
          onChange={handleAccountChange}
          error={errors.name}
          autoComplete="name"
          placeholder="이름을 입력해 주세요"
          required
        />
        <Field
          label="이메일"
          placeholder="이메일 주소를 입력해 주세요"
          type="email"
          name="email"
          value={signupForm.email}
          onChange={handleAccountChange}
          error={errors.email}
          autoComplete="email"
          required
        />
        <Field
          label="생년월일"
          type="date"
          name="birthDate"
          value={signupForm.birthDate}
          onChange={handleAccountChange}
          error={errors.birthDate}
          autoComplete="bday"
          required
        />
        <label className="ui-field flex flex-col gap-3 text-[14px]">
          <span>국적</span>
          <select
            name="nationality"
            autoComplete="country-name"
            value={signupForm.nationality}
            onChange={handleAccountChange}
            required
            aria-invalid={errors.nationality ? true : undefined}
            aria-describedby={errors.nationality ? "nationality-error" : undefined}
            className="h-12.5 w-full rounded-lg border border-line bg-white px-4 text-ink"
          >
            <option value="" disabled>선택해 주세요</option>
            {countries.map((country) => (
              <option key={country} value={country}>{country}</option>
            ))}
          </select>
          {errors.nationality && <span id="nationality-error" role="alert" className="text-sm text-red-600">{errors.nationality}</span>}
        </label>
        <Field
          label="전화번호"
          type="tel"
          name="phone"
          value={signupForm.phone}
          error={errors.phone}
          autoComplete="tel"
          inputMode="tel"
          placeholder="예: 010-1234-5678"
          required
          onChange={(event) => {
            const input = event.currentTarget;
            let raw = input.value;
            let cursor = input.selectionStart ?? raw.length;
            const previous = previousPhoneRef.current;
            const inputType = event.nativeEvent.inputType;
            if (inputType?.startsWith("delete") && raw.replace(/\D/g, "") === previous.replace(/\D/g, "")) {
              const removeAt = inputType === "deleteContentBackward" ? cursor - 1 : cursor;
              if (removeAt >= 0 && /\d/.test(raw[removeAt] ?? "")) {
                raw = raw.slice(0, removeAt) + raw.slice(removeAt + 1);
                if (inputType === "deleteContentBackward") cursor--;
              }
            }
            const digitsBeforeCursor = raw.slice(0, cursor).replace(/\D/g, "").length;
            const formatted = formatPhone(raw);
            input.value = formatted;
            setSignupForm((current) => ({ ...current, phone: formatted }));
            previousPhoneRef.current = formatted;
            let nextCursor = formatted.startsWith("+") && cursor > 0 ? 1 : 0;
            let seen = 0;
            while (nextCursor < formatted.length && seen < digitsBeforeCursor) {
              if (/\d/.test(formatted[nextCursor])) seen++;
              nextCursor++;
            }
            input.setSelectionRange(nextCursor, nextCursor);
          }}
        />
        <label className="ui-field flex flex-col gap-3 text-[14px]">
          <span>성별</span>
          <select
            name="gender"
            value={signupForm.gender}
            onChange={handleAccountChange}
            required
            aria-invalid={errors.gender ? true : undefined}
            aria-describedby={errors.gender ? "gender-error" : undefined}
            className="h-12.5 w-full rounded-lg border border-line bg-white px-4 text-ink"
          >
            <option value="" disabled>선택해 주세요</option>
            <option value="male">남성</option>
            <option value="female">여성</option>
            <option value="other">기타</option>
            <option value="unspecified">선택하지 않음</option>
          </select>
          {errors.gender && <span id="gender-error" role="alert" className="text-sm text-red-600">{errors.gender}</span>}
        </label>
        <Field
          label="비밀번호"
          name="password"
          value={signupForm.password}
          onChange={handleAccountChange}
          error={errors.password}
          autoComplete="new-password"
          required
          placeholder="안전한 비밀번호를 입력해 주세요"
          type="password"
        />
        <Field
          label="비밀번호 확인"
          name="confirmation"
          value={signupForm.confirmation}
          onChange={handleAccountChange}
          error={errors.confirmation}
          autoComplete="new-password"
          required
          placeholder="비밀번호를 다시 입력해 주세요"
          type="password"
        />
        <Button onClick={confirmAccount}>다음 · 동의 확인</Button>
        <Link
          className="block text-center text-green text-[14px] mt-1"
          href="/"
        >
          이미 계정이 있어요 · 로그인
        </Link>
      </div>
      <div
        hidden={step !== "consent"}
        className="mt-11 [&_>_.ui-button]:flex [&_>_.ui-button]:w-full [&_>_.ui-button]:m-[10px_0] [&_.ui-field]:m-[0_0_26px]"
      >
        {[
          "서비스 이용약관",
          "개인정보 수집 및 이용",
          "대화 기록 및 AI 분석 활용",
          "AI 클론 활용 및 권한 기반 제공",
        ].map((t, i) => (
          <label
            className="flex items-start gap-3.5 m-[22px_0] p-[8px_0] [&_small]:block [&_small]:text-muted [&_small]:mt-2"
            key={t}
          >
            <input type="checkbox" name={`consent-${i}`} required checked={consents[i]} aria-invalid={consentErrors.includes(`consent-${i}`) ? true : undefined} aria-describedby={consentErrors.includes(`consent-${i}`) ? `consent-${i}-error` : undefined} onChange={(event) => { const checked = event.currentTarget.checked; setConsents((current) => current.map((value, index) => index === i ? checked : value)); if (checked) setConsentErrors((current) => current.filter(name => name !== `consent-${i}`)); }} />
            <span>
              <strong>{t}</strong>
              <small>
                필수 ·{" "}
                {
                  [
                    "서비스 이용 조건을 확인해 주세요.",
                    "계정과 대화 기록을 안전하게 관리해요.",
                    "대화에서 나의 기억과 선호를 기록해요.",
                    "허용한 정보이용자가 클론과 대화할 수 있어요.",
                  ][i]
                }
              </small>
              {consentErrors.includes(`consent-${i}`) && <small id={`consent-${i}-error`} role="alert" className="!text-red-600">필수 항목에 동의해 주세요.</small>}
            </span>
          </label>
        ))}
        <Button onClick={confirmConsent}>동의하고 시작하기</Button>
        <Button variant="secondary" onClick={returnToAccount}>
          이전 · 가입 정보 수정
        </Button>
        <p className="ui-muted text-muted">
          동의 상태는 계정 설정에서 언제든 확인할 수 있어요.
        </p>
      </div>
    </>
  );
}
