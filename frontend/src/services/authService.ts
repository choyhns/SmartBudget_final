import { AuthResponse, UserInfo } from '../types';

const API_BASE_URL = '/api/auth';

// 토큰 저장 키
const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';
const USER_KEY = 'user';

export const authService = {
  /**
   * 회원가입
   */
  async signup(signupPayload: Record<string, unknown>): Promise<AuthResponse> {
    const response = await fetch(`${API_BASE_URL}/signup`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(signupPayload),
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || '회원가입에 실패했습니다.');
    }

    const data: AuthResponse = await response.json();
    this.saveTokens(data);
    return data;
  },

  /**
   * 로그인
   */
  async login(email: string, password: string): Promise<AuthResponse> {
    const response = await fetch(`${API_BASE_URL}/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password }),
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || '로그인에 실패했습니다.');
    }

    const data: AuthResponse = await response.json();
    this.saveTokens(data);
    return data;
  },

  /**
   * 토큰 갱신
   */
  async refreshToken(): Promise<AuthResponse> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      throw new Error('리프레시 토큰이 없습니다.');
    }

    const response = await fetch(`${API_BASE_URL}/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) {
      this.logout();
      throw new Error('토큰 갱신에 실패했습니다.');
    }

    const data: AuthResponse = await response.json();
    this.saveTokens(data);
    return data;
  },

  /**
   * 현재 사용자 정보 조회
   */
  async getCurrentUser(): Promise<UserInfo> {
    const response = await fetch(`${API_BASE_URL}/me`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${this.getAccessToken()}`,
      },
    });

    if (!response.ok) {
      throw new Error('사용자 정보를 가져오는데 실패했습니다.');
    }

    return await response.json();
  },

  /**
   * 로그아웃
   */
  logout(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },

  /**
   * 토큰 저장
   */
  saveTokens(data: AuthResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(data.user));
  },

  /**
   * Access Token 가져오기
   */
  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  },

  /**
   * Refresh Token 가져오기
   */
  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  },

  /**
   * 저장된 사용자 정보 가져오기
   */
  getUser(): UserInfo | null {
    const user = localStorage.getItem(USER_KEY);
    return user ? JSON.parse(user) : null;
  },

  /**
   * 로그인 상태 확인
   */
  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  },

  /**
   * 현재 사용자 ID 가져오기
   */
  getUserId(): number | null {
    const user = this.getUser();
    return user?.userId || null;
  },


  async completeProfile(payload: {
    phone: string;
    addr: string;
    birth: string;   // "YYYY-MM-DD"
    gender: string;  // "M" | "F"
    name?: string;
    photo?: string;
  }): Promise<UserInfo> {
    const response = await fetch(`${API_BASE_URL}/me/profile`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${this.getAccessToken()}`,
      },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.message || '추가 정보 저장에 실패했습니다.');
    }

    const user: UserInfo = await response.json();
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    return user;
  },




};

/**
 * 인증 헤더 생성 헬퍼
 */
export function getAuthHeaders(): Record<string, string> {
  const token = authService.getAccessToken();
  return token ? { 'Authorization': `Bearer ${token}` } : {};
}
