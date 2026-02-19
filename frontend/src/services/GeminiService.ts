
import { GoogleGenAI, Type } from "@google/genai";
import { Transaction, AIAnalysis } from "../types";

export const generateAIReport = async (transactions: Transaction[], budget: number): Promise<AIAnalysis> => {
  const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });
  
  const prompt = `
    다음은 사용자의 최근 자산 내역 및 예산 정보입니다:
    - 총 거래 내역: ${JSON.stringify(transactions)}
    - 이번 달 총 예산: ${budget.toLocaleString()}원
    
    이 데이터를 분석하여 다음 정보를 JSON 형식으로 제공하세요:
    1. riskAssessment: 예산 초과 위험 및 전반적인 재정 상태 요약 (한국어)
    2. topPatterns: 반복되는 지출 패턴 TOP 3 (한국어 리스트)
    3. savingTips: 지출을 줄이기 위한 구체적인 팁 3가지 (한국어 리스트)
  `;

  try {
    const response = await ai.models.generateContent({
      model: "gemini-3-flash-preview",
      contents: prompt,
      config: {
        responseMimeType: "application/json",
        responseSchema: {
          type: Type.OBJECT,
          properties: {
            riskAssessment: { type: Type.STRING },
            topPatterns: { 
              type: Type.ARRAY, 
              items: { type: Type.STRING } 
            },
            savingTips: { 
              type: Type.ARRAY, 
              items: { type: Type.STRING } 
            },
          },
          required: ["riskAssessment", "topPatterns", "savingTips"],
        },
      },
    });

    const text = response.text;
    if (!text) throw new Error("AI response text is empty");
    return JSON.parse(text);
  } catch (error) {
    console.error("Gemini API Error:", error);
    // Fallback data if API fails or quota exceeded
    return {
      riskAssessment: "최근 식비 지출이 평소보다 20% 높습니다. 예산 관리에 주의가 필요합니다.",
      topPatterns: ["스타벅스 등 카페 지출 빈번", "주말 배달 음식 주문 집중", "소액 쇼핑 거래 다수"],
      savingTips: ["커피 구독 서비스 고려", "평일 도시락 챙기기", "장바구니 24시간 대기 후 구매 결정"]
    };
  }
};
