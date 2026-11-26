package com.example.everme.dto;

/**
 * 엔드포인트 요청 계약 초안. 검증 규칙과 인증은 서비스 구현 단계에서 확정한다.
 * 자격 증명을 포함한 요청 객체는 로그로 출력하지 않는다.
 */
public final class EndpointRequests {
    private EndpointRequests() {}
    /** 프론트에 표시한 필수 약관 동의 여부. true 검증은 후속 구현하며 문서·동의 이력은 DB에 저장하지 않는다. */
    public record SignupRequest(String name, String email, String password, Boolean agreedToTerms) {}
    public record LoginRequest(String email, String password) {}
    public record PasswordResetRequest(String email) {}
    public record PasswordChangeRequest(String token, String newPassword) {}
    public record ProfileRequest(String name, java.time.LocalDate birthDate, String nationality, String phone, String gender) {}
    public record SettingsRequest(String voice) {}
    /** 대변인 대화의 대상 클론. 대화 종류와 참여자는 서버에서 결정한다. */
    public record ViewerConversationRequest(java.util.UUID cloneId) {}
    public record TitleRequest(String title) {}
    public record MessageRequest(String content) {}
    public record InvitationRequest(String email, Integer maxAccessLevel, java.util.UUID groupId) {}
    public record AccessRequest(Integer maxAccessLevel) {}
    public record GroupRequest(String name) {}
    public record MembershipRequest(java.util.UUID groupId) {}
    public record InvitationResponseRequest(String decision) {}
}

