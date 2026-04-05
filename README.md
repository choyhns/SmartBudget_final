# 💰 SmartBudget

AI 기반 개인 자산 및 소비 분석 서비스

---

## 📌 프로젝트 소개

SmartBudget은 사용자의 소비 데이터를 기반으로 지출 패턴을 분석하고,
AI를 활용하여 개인 맞춤형 금융 리포트 및 인사이트를 제공하는 서비스입니다.

단순한 가계부 기능을 넘어,
데이터 집계 → 분석 → AI 응답까지 이어지는 구조를 설계하여
사용자가 자신의 소비 흐름을 직관적으로 이해할 수 있도록 하는 것을 목표로 합니다.

---

## 🧱 시스템 아키텍처

* Frontend: React
* Backend: Spring Boot
* Database: PostgreSQL
* AI Server: Python (FastAPI)
* (Optional) Reverse Proxy: Nginx

> ※ 백엔드와 AI 서버를 분리하여 확장성과 유지보수성을 고려한 구조

---

## ⚙️ 주요 기능

### 1. 소비 데이터 관리

* 사용자 소비 내역 등록 및 조회 API
* 카테고리 기반 지출 분류

### 2. 월별 리포트 생성

* 월별 총 지출 및 카테고리별 소비 비율 계산
* 최근 소비 흐름 및 통계 데이터 제공

### 3. AI 기반 소비 분석

* 집계된 데이터를 기반으로 LLM을 활용한 소비 분석 리포트 생성
* 사용자 질문 기반 금융 Q&A 기능 (RAG 구조 적용)

### 4. OCR 기반 영수증 처리

* 영수증 이미지 → 텍스트 변환 → 소비 데이터 자동 입력

---

## 🏗️ 아키텍처 설계 특징

### ✅ 1. 백엔드 - AI 서버 분리 구조

* Spring Boot: 데이터 처리 및 API 제공
* Python(FastAPI): OCR 및 LLM 처리

👉 역할 분리를 통해 유지보수성과 확장성 확보

---

### ✅ 2. 데이터 집계 후 AI 전달 구조

* 원시 데이터를 그대로 LLM에 전달하지 않고
  백엔드에서 통계 데이터를 먼저 집계하여 전달

👉 응답 일관성 향상 및 성능 개선

---

### ✅ 3. REST API 기반 설계

* Controller-Service-Repository 구조 적용
* 역할 분리를 통한 유지보수성 확보

---

## 🧩 담당 역할

* 소비 데이터 관리 및 분석 API 설계 및 구현
* 월별 리포트 생성 로직 및 통계 데이터 집계 구조 설계
* 백엔드와 AI 서버 간 API 연동 구조 설계
* Controller-Service-Repository 기반 백엔드 구조 설계

---

## 🚀 기술 스택

### Backend

* Java, Spring Boot, Spring Security, JPA

### Database

* PostgreSQL

### AI

* Python, FastAPI

### Frontend

* React

---

## 📈 성과 및 개선

* 데이터 집계 후 AI에 전달하는 구조를 설계하여 응답 일관성 향상
* 백엔드와 AI 서버를 분리하여 서비스 확장성과 유지보수성 개선
* 사용자 질문 기반 응답을 위한 RAG 구조 설계

---

## 🔧 실행 방법

### Backend

```bash
./gradlew bootRun
```

### Frontend

```bash
npm install
npm run dev
```

### AI Server

```bash
uvicorn app.main:app --reload
```

---

## 📎 향후 개선 계획

* 실시간 소비 분석 기능 추가
* 사용자 맞춤 추천 기능 고도화
* 배포 자동화 및 CI/CD 구축

---

## 🙋‍♂️ 한 줄 요약

> 데이터를 구조적으로 가공하고 AI와 연결하여
> 사용자에게 의미 있는 금융 인사이트를 제공하는 백엔드 중심 서비스
