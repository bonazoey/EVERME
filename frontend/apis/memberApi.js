import api from "./axios";

export function memberSignUp(signupForm){
    return api.post("/api/member/signup", signupForm);
}

export function memberLogin(loginForm){
    return api.post("/api/member/login", loginForm);
}
