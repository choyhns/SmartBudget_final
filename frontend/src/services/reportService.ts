import { AIAnalysis } from '../types';

const API_BASE_URL = '/api';

export interface ReportRequest {
  monthlyBudget: number;
}

export interface ReportResponse {
  riskAssessment: string;
  topPatterns: string[];
  savingTips: string[];
}

export const reportService = {
  // DB에서 거래 내역을 가져와서 분석 리포트 생성
  async generateReport(monthlyBudget: number): Promise<AIAnalysis> {
    const response = await fetch(`${API_BASE_URL}/transactions/report`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify({
        monthlyBudget,
      }),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 리포트를 생성하는데 실패했습니다.`);
    }

    const data: ReportResponse = await response.json();
    return {
      riskAssessment: data.riskAssessment,
      topPatterns: data.topPatterns,
      savingTips: data.savingTips,
    };
  },
};
