import React, { useEffect, useMemo, useState } from 'react';
import { UI_COLORS } from '../constants';
import logoImg from '../assets/img/로고.png';
import { authService } from '../services/authService';

// 카카오(다음) 주소검색 window 타입 선언
declare global {
  interface Window {
    daum?: any;
  }
}

interface LoginProps {
  onLogin: () => void;
  onBack: () => void;
}

type Gender = 'M' | 'F' | '';

const Login: React.FC<LoginProps> = ({ onLogin, onBack }) => {
  const [isSignup, setIsSignup] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const [passwordConfirm, setPasswordConfirm] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 회원가입 추가 필드 상태
  const [name, setName] = useState('');
  const [phone1, setPhone1] = useState('010');
  const [phone2, setPhone2] = useState('');
  const [phone3, setPhone3] = useState('');
  const [zipCode, setZipCode] = useState('');        // 우편번호
  const [roadAddress, setRoadAddress] = useState(''); // 도로명주소(주소검색으로만)
  const [detailAddress, setDetailAddress] = useState(''); // 상세주소(직접입력)
  const [birth, setBirth] = useState(''); // YYYY-MM-DD
  const [gender, setGender] = useState<Gender>('');
  const [photo, setPhoto] = useState(''); // 임시 String
  const [isOauthProfile, setIsOauthProfile] = useState(false);

  const [agreeTerms, setAgreeTerms] = useState(false);     // 이용약관
  const [agreePrivacy, setAgreePrivacy] = useState(false); // 개인정보 수집 및 이용

  const [termsModal, setTermsModal] = useState<null | 'terms' | 'privacy'>(null);

  const TERMS_TEXT = `이용 약관(더미)

  제1조(목적)
  본 약관은 WealthAI(이하 “회사”)가 제공하는 개인 자산 관리/분석 서비스(이하 “서비스”)의 이용 조건 및 절차, 회사와 이용자 간 권리·의무 및 책임사항을 규정함을 목적으로 합니다.

  제2조(정의)
  1. “이용자”란 본 약관에 동의하고 회사가 제공하는 서비스를 이용하는 자를 말합니다.
  2. “회원”이란 이메일/소셜 로그인 등을 통해 계정을 생성하고 서비스를 지속적으로 이용할 수 있는 자를 말합니다.
  3. “콘텐츠”란 서비스 내에서 이용자가 입력하거나 회사가 제공하는 모든 정보(텍스트, 이미지, 통계, 리포트 등)를 의미합니다.

  제3조(약관의 효력 및 변경)
  1. 회사는 관련 법령을 위배하지 않는 범위에서 약관을 변경할 수 있습니다.
  2. 약관이 변경되는 경우 적용일 및 변경 사유를 서비스 화면에 공지할 수 있습니다.
  3. 이용자가 변경된 약관에 동의하지 않는 경우 서비스 이용을 중단하고 회원 탈퇴를 요청할 수 있습니다.

  제4조(서비스 제공 및 제한)
  1. 회사는 서비스의 안정적 제공을 위해 정기 점검, 서버 증설, 기능 개선 등을 수행할 수 있습니다.
  2. 다음 각 호에 해당하는 경우 회사는 서비스 제공을 제한할 수 있습니다.
    - 시스템 점검, 장애, 천재지변 등 불가항력 사유가 있는 경우
    - 이용자가 타인의 권리를 침해하거나 불법적인 목적으로 서비스를 이용하는 경우
    - 운영 정책 위반 또는 비정상적인 접근/트래픽이 감지되는 경우

  제5조(이용자의 의무)
  1. 이용자는 서비스 이용 시 관계 법령, 본 약관, 운영 정책을 준수해야 합니다.
  2. 이용자는 계정 정보(토큰 포함)를 제3자에게 공유하거나 양도할 수 없습니다.
  3. 이용자는 본인의 입력 데이터의 정확성에 대해 책임을 부담합니다.

  제6조(책임 제한)
  1. 회사는 무료/데모 성격의 서비스 제공에 있어 법령이 허용하는 범위 내에서 책임을 제한할 수 있습니다.
  2. 회사는 이용자의 귀책사유로 인해 발생한 손해에 대해 책임을 지지 않습니다.

  부칙
  본 약관은 2026년 1월 1일부터 적용됩니다.
  `;

  const PRIVACY_TEXT = `개인정보 수집 및 이용(더미)

  1. 수집 항목
  - 필수: 이메일, 이름, 휴대폰번호, 주소, 생년월일, 성별
  - 선택: 프로필 이미지(소셜 로그인 제공 시)

  2. 수집 방법
  - 회원가입/프로필 입력 폼을 통한 직접 입력
  - 소셜 로그인 제공자(카카오 등)로부터 제공받는 정보(동의한 항목에 한함)

  3. 이용 목적
  - 회원 식별 및 본인 확인
  - 자산 관리 기능 제공 및 맞춤형 리포트 생성
  - 고객 문의 대응 및 공지사항 전달
  - 서비스 품질 개선 및 부정 이용 방지(로그 분석 등)

  4. 보유 및 이용 기간
  - 원칙적으로 회원 탈퇴 시까지 보관 후 지체 없이 파기합니다.
  - 다만, 관련 법령에 따라 일정 기간 보관이 필요한 경우 해당 기간 동안 보관할 수 있습니다.

  5. 동의 거부 권리 및 불이익
  - 이용자는 개인정보 수집 및 이용 동의를 거부할 권리가 있습니다.
  - 필수 항목 동의 거부 시 회원가입 및 서비스 이용이 제한될 수 있습니다.

  6. 파기 절차 및 방법
  - 전자적 파일 형태: 복구 불가능한 방법으로 영구 삭제
  - 출력물: 분쇄 또는 소각

  부칙
  본 안내는 2026년 1월 1일부터 적용됩니다.
  `;




  // phone 합치기(서버 전송용: 이어붙인 문자열)
  const fullPhone = useMemo(() => `${phone1}${phone2}${phone3}`, [phone1, phone2, phone3]);

  const fullAddr = useMemo(() => {
    const zip = zipCode.trim();
    const base = roadAddress.trim();
    const detail = detailAddress.trim();
    return `(${zip})${base},${detail}`;
  }, [zipCode, roadAddress, detailAddress]);

  // 카카오(다음) 우편번호 스크립트 로드 (index.html에 넣는 방식도 가능)
  useEffect(() => {
    if (!isSignup && !isOauthProfile) return;

    const scriptId = 'daum-postcode-script';
    if (document.getElementById(scriptId)) return;

    const script = document.createElement('script');
    script.id = scriptId;
    script.src = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
    script.async = true;
    document.body.appendChild(script);
  }, [isSignup, isOauthProfile]);

  useEffect(() => {
    // ✅ 1) URL QueryString에서 OAuth2 결과(토큰/에러/추가정보필요여부) 파싱
    const params = new URLSearchParams(window.location.search);
    const accessToken = params.get('accessToken');
    const refreshToken = params.get('refreshToken');
    const oauthError = params.get('error');
    const needsProfile = params.get('needsProfile') === 'true';

    // ✅ 2) OAuth 에러가 있으면 에러 메시지 표시 후 URL 정리
    if (oauthError) {
      setError('소셜 로그인에 실패했습니다. 다시 시도해주세요.');
      window.history.replaceState({}, document.title, window.location.pathname); // 쿼리 제거
      return;
    }

    // ✅ 3) 토큰이 있다면 "OAuth 로그인 성공" 상태
    if (accessToken && refreshToken) {
      // 🆕 useEffect에서는 await를 직접 못 쓰니 async IIFE로 감싸서 처리
      (async () => {
        try {
          // ✅ 3-1) 토큰 저장 (isAuthenticated()가 true가 되도록)
          localStorage.setItem('accessToken', accessToken);
          localStorage.setItem('refreshToken', refreshToken);

          // ✅ 3-2) 토큰 저장 직후, 현재 사용자 정보(/me)를 받아 localStorage USER_KEY도 채움
          // 🆕 SSO 재로그인 시 "토큰만 저장되고 user가 비어있던 문제" 해결 목적
          const me = await authService.getCurrentUser(); // Authorization: Bearer accessToken 사용
          localStorage.setItem('user', JSON.stringify(me)); // 🆕 USER_KEY가 'user'인 전제 (authService.ts와 동일)

          // ✅ 3-3) 추가 정보 입력이 필요한 경우: 메인으로 보내지 말고 추가정보 모드로 전환
          if (needsProfile) {
            sessionStorage.setItem('OAUTH_NEEDS_PROFILE', '1'); // 🆕 App.tsx에서 튕김 방지 플래그
            setIsOauthProfile(true); // ✅ 추가정보 입력 화면 모드
            setIsSignup(false);      // ✅ 로컬 회원가입 모드로 넘어가지 않게 강제

            // ✅ 3-3-1) 추가정보 화면에서 email/name을 보여주기 위해 state 채움
            // (이미 위에서 me를 받아왔으니 그걸 재사용)
            setEmail(me.email);
            setName(me.name);

            // ✅ 3-3-2) 토큰이 URL에 남지 않게 쿼리 제거
            window.history.replaceState({}, document.title, window.location.pathname);
            return; // ✅ needsProfile=true면 여기서 종료(메인 이동 금지)
          }

          // ✅ 3-4) 추가정보 불필요하면: URL 정리 후 메인 이동(onLogin)
          window.history.replaceState({}, document.title, window.location.pathname);
          onLogin();
        } catch (e) {
          // ✅ 4) /me 호출 실패나 토큰 저장 이후 처리 중 오류 대응
          setError('토큰 처리 중 오류가 발생했습니다.');
        }
      })();
    }
  }, [onLogin]);


  // 주소 검색 팝업
  const openAddressSearch = () => {
    setError(null);

    if (!window.daum?.Postcode) {
      setError('주소 검색 스크립트가 아직 로드되지 않았습니다. 잠시 후 다시 시도해주세요.');
      return;
    }

    new window.daum.Postcode({
      oncomplete: (data: any) => {
        // 우편번호 자동 입력
        setZipCode(data.zonecode || '');

        // 도로명주소 우선, 없으면 지번주소
        const pickedRoad = data.roadAddress || data.address || '';
        setRoadAddress(pickedRoad);

        // 상세주소는 사용자가 입력하므로 그대로 두거나, 원하면 초기화
        // setDetailAddress(''); // 필요하면 주석 해제
      },
    }).open();
  };

  // 숫자만 입력되도록 보정
  const onlyDigits = (v: string) => v.replace(/\D/g, '');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {

      if (isOauthProfile) {

        if (!email.trim() || !name.trim()) {
          setError("SSO 기본 정보를 불러오지 못했습니다. 새로고침 후 다시 시도해주세요.");
          return;
        }

        // 필수 검증(기존 signup 검증 로직 재사용)
        if (!name.trim()) {
          setError('이름을 입력해주세요.');
          return;
        }
        if (phone1.length !== 3 || phone2.length < 3 || phone2.length > 4 || phone3.length !== 4) {
          setError('휴대폰 번호를 올바르게 입력해주세요.');
          return;
        }
        if (!zipCode.trim() || !roadAddress.trim()) {
          setError('주소를 입력(검색)해주세요.');
          return;
        }
        if (!birth) {
          setError('생년월일을 선택해주세요.');
          return;
        }
        if (!gender) {
          setError('성별을 선택해주세요.');
          return;
        }

        await authService.completeProfile({
          name,
          phone: fullPhone,
          addr: fullAddr,
          birth,
          gender
        });

        sessionStorage.removeItem('OAUTH_NEEDS_PROFILE');
        setIsOauthProfile(false);
        onLogin();
        return;
      }

      if (isSignup) {
        // 회원가입
        if (!agreeTerms || !agreePrivacy) {
          setError('회원가입을 위해 이용 약관 및 개인정보 수집 및 이용에 동의해주세요.');
          return;
        }

        if (password !== passwordConfirm) {
          setError('비밀번호가 일치하지 않습니다.');
          setLoading(false);
          return;
        }

        // 간단 유효성(프론트)
        if (!name.trim()) {
          setError('이름을 입력해주세요.');
          setLoading(false);
          return;
        }
        if (phone1.length !== 3 || phone2.length < 3 || phone2.length > 4 || phone3.length !== 4) {
          setError('휴대폰 번호를 올바르게 입력해주세요.');
          setLoading(false);
          return;
        }
        if (!zipCode.trim()) {
          setError('우편번호를 입력(검색)해주세요.');
          setLoading(false);
          return;
        }
        if (!roadAddress.trim()) {
          setError('도로명주소를 입력(검색)해주세요.');
          setLoading(false);
          return;
        }
        if (!birth) {
          setError('생년월일을 선택해주세요.');
          setLoading(false);
          return;
        }
        if (!gender) {
          setError('성별을 선택해주세요.');
          setLoading(false);
          return;
        }

        // 백엔드 연동 대비 payload 구성(현재는 콘솔 확인용)
        const signupPayload = {
          email,
          password,
          passwordConfirm,
          name,
          phone: fullPhone,
          zipCode,
          roadAddress,
          detailAddress,
          addr: fullAddr,
          birth,
          gender,
          photo, // 임시 String
          provider: 'local',
          role: 'user'
        };
        // 추후 백엔드 연동 시 이 payload를 signup API로 전송하도록 변경
        // eslint-disable-next-line no-console
        console.log('[signup payload]', signupPayload);

        // 기존 authService 시그니처 유지 (백엔드 구성 후 payload 전달하도록 변경)
        await authService.signup(signupPayload);
      } else {
        // 로그인
        await authService.login(email, password);
      }
      onLogin();
    } catch (err: any) {
      setError(err.message || '인증에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-6 relative overflow-hidden">
      {/* Background Decor */}
      <div className="absolute top-0 left-0 w-full h-full -z-10">
        <div className="absolute top-[10%] left-[20%] w-[300px] h-[300px] bg-blue-600/10 blur-[100px] rounded-full"></div>
        <div className="absolute bottom-[10%] right-[20%] w-[300px] h-[300px] bg-indigo-600/10 blur-[100px] rounded-full"></div>
      </div>

      <button
        onClick={() => {
          sessionStorage.removeItem('OAUTH_NEEDS_PROFILE');
          onBack();
        }}
        className="absolute top-8 left-8 p-3 text-slate-500 hover:text-white transition-colors"
      >
        <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
        </svg>
      </button>

      <div className="w-full max-w-md animate-in zoom-in-95 duration-500">
        <div className="text-center mb-10">
          <img src={logoImg} alt="SmartBudget" className="w-16 h-16 object-contain mx-auto mb-6" />
          <h1 className="text-3xl font-bold text-white mb-2 tracking-tight">
            {isOauthProfile ? '추가 정보 입력' : isSignup ? '환영합니다!' : '반가워요!'}
          </h1>
          <p className="text-slate-500">
            {isOauthProfile
              ? '전화번호/주소 등 필수 정보를 입력하면 가입이 완료됩니다.'
              : isSignup
                ? '회원 정보를 입력해 계정을 만들어주세요.'
                : 'SmartBudget 계정으로 로그인하세요.'
            }
          </p>
        </div>

        <div className={`p-10 rounded-[40px] ${UI_COLORS.surface}`}>
          {error && (
            <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-6">
            {/* 공통: 이메일 */}
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-400 ml-1">이메일 주소</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="email@example.com"
                className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-5 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all placeholder:text-slate-700"
                required
                disabled={loading}
                readOnly={isOauthProfile}
              />
            </div>

            {/* 공통: 비밀번호 */}
            {!isOauthProfile && (
              <div className="space-y-2">
                <label className="text-sm font-medium text-slate-400 ml-1">비밀번호</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-5 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all placeholder:text-slate-700"
                  required
                  disabled={loading}
                  minLength={8}
                />
              </div>
            )}
            

            {/* 회원가입: 비밀번호 확인 */}
            {isSignup && !isOauthProfile && (
              <div className="space-y-2">
                <label className="text-sm font-medium text-slate-400 ml-1">비밀번호 확인</label>
                <input
                  type="password"
                  value={passwordConfirm}
                  onChange={(e) => setPasswordConfirm(e.target.value)}
                  placeholder="••••••••"
                  className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-5 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all placeholder:text-slate-700"
                  required
                  disabled={loading}
                  minLength={8}
                />
              </div>
            )}

            {/* 회원가입 추가 정보 영역 */}
            {(isSignup || isOauthProfile) && (
              <>
                {/* 이름 */}
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-400 ml-1">이름</label>
                  <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="홍길동"
                    className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-5 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all placeholder:text-slate-700"
                    required
                    disabled={loading}
                    readOnly={isOauthProfile}
                  />
                </div>

                {/* 휴대폰(3칸) */}
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-400 ml-1">휴대폰 번호</label>
                  <div className="flex gap-3">
                    <input
                      type="text"
                      inputMode="numeric"
                      value={phone1}
                      onChange={(e) => setPhone1(onlyDigits(e.target.value).slice(0, 3))}
                      className="w-[90px] bg-slate-950 border border-slate-800 rounded-2xl px-4 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all placeholder:text-slate-700 text-center"
                      required
                      disabled={loading}
                      maxLength={3}
                      placeholder="010"
                    />
                    <input
                      type="text"
                      inputMode="numeric"
                      value={phone2}
                      onChange={(e) => setPhone2(onlyDigits(e.target.value).slice(0, 4))}
                      className="w-[90px] flex-1 bg-slate-950 border border-slate-800 rounded-2xl px-4 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all placeholder:text-slate-700 text-center"
                      required
                      disabled={loading}
                      maxLength={4}
                      placeholder="1234"
                    />
                    <input
                      type="text"
                      inputMode="numeric"
                      value={phone3}
                      onChange={(e) => setPhone3(onlyDigits(e.target.value).slice(0, 4))}
                      className="w-[90px] flex-1 bg-slate-950 border border-slate-800 rounded-2xl px-4 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all placeholder:text-slate-700 text-center"
                      required
                      disabled={loading}
                      maxLength={4}
                      placeholder="5678"
                    />
                  </div>
                  <p className="text-xs text-slate-600 ml-1">
                    서버 전송 시 <span className="text-slate-500 font-medium">"{fullPhone}"</span> 형태로 이어붙여 전송됩니다.
                  </p>
                </div>

                {/* 주소(카카오 주소 API) */}
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-400 ml-1">주소</label>

                  {/* 1) 우편번호 + 주소 검색 버튼 */}
                  <div className="flex gap-3">
                    <input
                      type="text"
                      value={zipCode}
                      onChange={(e) => setZipCode(e.target.value)} // 필요 없으면 readOnly로 두셔도 됩니다.
                      placeholder="우편번호"
                      className="w-[160px] bg-slate-950 border border-slate-800 rounded-2xl px-5 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all placeholder:text-slate-700"
                      required
                      disabled={loading}
                      readOnly // 🧩 주소검색으로 자동 채움 (원하면 제거)
                    />
                    <button
                      type="button"
                      onClick={openAddressSearch}
                      disabled={loading}
                      className="flex-1 px-5 py-4 rounded-2xl font-bold bg-white/5 border border-white/10 text-white hover:bg-white/10 transition-all disabled:opacity-50"
                    >
                      주소 검색
                    </button>
                  </div>

                  {/* 2) 도로명주소(주소검색으로만 채움) */}
                  <input
                    type="text"
                    value={roadAddress}
                    onChange={(e) => setRoadAddress(e.target.value)}
                    placeholder="도로명주소"
                    className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-5 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all placeholder:text-slate-700"
                    required
                    disabled={loading}
                    readOnly // 🧩 사용자가 직접 입력 불가
                  />

                  {/* 3) 상세주소(직접 입력) */}
                  <input
                    type="text"
                    value={detailAddress}
                    onChange={(e) => setDetailAddress(e.target.value)}
                    placeholder="상세주소"
                    className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-5 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all placeholder:text-slate-700"
                    disabled={loading}
                  />
                </div>

                {/* 생년월일(달력) */}
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-400 ml-1">생년월일</label>
                  <input
                    type="date"
                    value={birth}
                    min="1900-01-01"
                    max={new Date().toISOString().slice(0, 10)}
                    onFocus={(e) => (e.currentTarget as any).showPicker?.()}
                    onChange={(e) => {
                      const v = e.target.value; // 보통 "YYYY-MM-DD" 또는 ""
                      if (!v) {
                        setBirth('');
                        return;
                      }
                      const year = v.split('-')[0] ?? '';
                      if (year.length !== 4) return; // 6자리 등은 반영하지 않음
                      setBirth(v);
                    }}
                    className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-5 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all"
                    required
                    disabled={loading}
                  />
                </div>

                {/* 성별(토글) */}
                <div className="space-y-2">
                  <label className="text-sm font-medium text-slate-400 ml-1">성별</label>
                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={() => setGender('M')}
                      disabled={loading}
                      className={[
                        'flex-1 py-4 rounded-2xl font-bold border transition-all',
                        gender === 'M'
                          ? 'bg-blue-600/20 border-blue-500/40 text-white'
                          : 'bg-slate-950 border-slate-800 text-slate-400 hover:text-white hover:bg-white/5',
                      ].join(' ')}
                    >
                      남
                    </button>
                    <button
                      type="button"
                      onClick={() => setGender('F')}
                      disabled={loading}
                      className={[
                        'flex-1 py-4 rounded-2xl font-bold border transition-all',
                        gender === 'F'
                          ? 'bg-blue-600/20 border-blue-500/40 text-white'
                          : 'bg-slate-950 border-slate-800 text-slate-400 hover:text-white hover:bg-white/5',
                      ].join(' ')}
                    >
                      여
                    </button>
                  </div>
                </div>

                {/* photo(임시 String) */}
                {!isOauthProfile && (
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-slate-400 ml-1">프로필 사진(임시)</label>
                    <input
                      type="text"
                      value={photo}
                      onChange={(e) => setPhoto(e.target.value)}
                      placeholder="추후 파일 업로드로 대체 (예: https://... 또는 파일명)"
                      className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-5 py-4 outline-none focus:ring-2 focus:ring-blue-600 text-white transition-all placeholder:text-slate-700"
                      disabled={loading}
                    />
                  </div>
                )}
                
              </>
            )}

            {/* 로그인 전용 옵션 */}
            {!isSignup && !isOauthProfile && (
              <div className="flex items-center justify-between text-sm px-1">
                <label className="flex items-center gap-2 text-slate-500 cursor-pointer">
                  <input type="checkbox" className="w-4 h-4 rounded border-slate-800 bg-slate-950" />
                  로그인 유지
                </label>
                <button type="button" className="text-blue-500 hover:text-blue-400 font-medium">비밀번호 찾기</button>
              </div>
            )}

            {/* 약관 동의 (회원가입 전용) */}
            {isSignup && !isOauthProfile && (
              <div className="space-y-3 rounded-2xl border border-white/10 bg-white/5 p-4">
                <div className="flex items-start gap-3">
                  <input
                    type="checkbox"
                    checked={agreeTerms}
                    onChange={(e) => setAgreeTerms(e.target.checked)}
                    disabled={loading}
                    className="mt-1 w-4 h-4 rounded border-slate-700 bg-slate-950"
                  />
                  <div className="text-sm text-slate-300">
                    <button
                      type="button"
                      onClick={() => setTermsModal('terms')}
                      className="text-blue-400 hover:underline font-semibold"
                      disabled={loading}
                    >
                      이용 약관
                    </button>
                    <span className="text-slate-400">에 동의합니다. (필수)</span>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <input
                    type="checkbox"
                    checked={agreePrivacy}
                    onChange={(e) => setAgreePrivacy(e.target.checked)}
                    disabled={loading}
                    className="mt-1 w-4 h-4 rounded border-slate-700 bg-slate-950"
                  />
                  <div className="text-sm text-slate-300">
                    <button
                      type="button"
                      onClick={() => setTermsModal('privacy')}
                      className="text-blue-400 hover:underline font-semibold"
                      disabled={loading}
                    >
                      개인정보 수집 및 이용
                    </button>
                    <span className="text-slate-400">에 동의합니다. (필수)</span>
                  </div>
                </div>
              </div>
            )}

            {/* 제출 버튼 */}
            <button
              type="submit"
              disabled={loading}
              className={`w-full py-5 rounded-2xl font-bold text-lg shadow-xl shadow-blue-600/20 active:scale-95 transition-all ${UI_COLORS.primary} disabled:opacity-50 disabled:cursor-not-allowed`}
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                  </svg>
                  처리중...
                </span>
              ) : isOauthProfile ? '추가 정보 저장' : isSignup ? '회원가입' : '로그인하기'}
            </button>
          </form>

          {/* 소셜 로그인(로그인 모드만 노출) */}
          {!isSignup && !isOauthProfile && (
            <div className="mt-8 pt-8 border-t border-white/5 text-center">
              <p className="text-slate-500 text-sm mb-4">소셜 계정으로 시작하기</p>
              <div className="flex justify-center gap-4">
                <button className="w-12 h-12 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 transition-all">
                  <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                    <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                    <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z" fill="#FBBC05"/>
                    <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                  </svg>
                </button>
                <button className="w-12 h-12 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 transition-all text-white">
                  <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.477 2 2 6.477 2 12c0 4.418 2.865 8.166 6.839 9.489.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.603-3.369-1.34-3.369-1.34-.454-1.156-1.11-1.463-1.11-1.463-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.025A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.294 2.747-1.025 2.747-1.025.546 1.377.203 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482C19.138 20.161 22 16.416 22 12c0-5.523-4.477-10-10-10z" />
                  </svg>
                </button>
                <button
                  type="button"
                  className="w-12 h-12 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 transition-all"
                  aria-label="카카오로 시작하기"
                  onClick={() => {
                    window.location.href = "/oauth2/authorization/kakao"; 
                  }}
                >
                  <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                    <path d="M12 3C6.477 3 2 6.477 2 10.79c0 2.63 1.78 4.95 4.57 6.35l-.65 3.3a.6.6 0 0 0 .88.64l3.77-2.1c.47.06.96.09 1.43.09 5.523 0 10-3.477 10-7.79C22 6.477 17.523 3 12 3z" />
                  </svg>
                </button>
              </div>
            </div>
          )}
        </div>


        {!isOauthProfile && (
          <p className="text-center mt-8 text-slate-600 text-sm">
            {isSignup ? (
              <>이미 계정이 있으신가요? <button onClick={() => setIsSignup(false)} className="text-blue-500 hover:underline font-bold ml-1">로그인</button></>
            ) : (
              <>아직 계정이 없으신가요? <button onClick={() => setIsSignup(true)} className="text-blue-500 hover:underline font-bold ml-1">회원가입</button></>
            )}
          </p>
        )}
        
      </div>

      {/* 약관 모달 */}
      {termsModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-6"
          role="dialog"
          aria-modal="true"
          onClick={() => setTermsModal(null)} // 바깥 클릭 닫기
        >
          <div
            className="w-full max-w-lg h-[70vh] rounded-3xl border border-white/10 bg-slate-950 shadow-2xl flex flex-col" // 🆕
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header (고정) */}
            <div className="p-6 border-b border-white/10 flex items-center justify-between gap-4">
              <h2 className="text-lg font-bold text-white">
                {termsModal === 'terms' ? '이용 약관' : '개인정보 수집 및 이용'}
              </h2>
              <button
                type="button"
                onClick={() => setTermsModal(null)}
                className="px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-slate-200 hover:bg-white/10"
              >
                닫기
              </button>
            </div>

            {/* Body (스크롤 영역) */}
            <div className="pr-2 flex-1 p-6 overflow-y-auto whitespace-pre-line text-sm text-slate-300 leading-relaxed">
              {termsModal === 'terms' ? TERMS_TEXT : PRIVACY_TEXT}
            </div>

            {/* Footer (고정) */}
            <div className="p-6 border-t border-white/10 flex justify-end">
              <button
                type="button"
                onClick={() => {
                  if (termsModal === 'terms') setAgreeTerms(true);
                  if (termsModal === 'privacy') setAgreePrivacy(true);
                  setTermsModal(null);
                }}
                className="px-5 py-3 rounded-2xl font-bold bg-blue-600/20 border border-blue-500/30 text-white hover:bg-blue-600/30 transition-all"
              >
                동의
              </button>
            </div>
          </div>
        </div>
      )}




    </div>
  );
};

export default Login;
