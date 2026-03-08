"""
NLWeb TripAdvisor Agent
=======================
Microsoft NLWeb 기술이 적용된 TripAdvisor 파트너 사이트를 활용하는
Claude 기반 여행 계획 에이전트입니다.

NLWeb이란?
- Microsoft Build 2025에서 발표된 오픈 프로토콜
- 자연어로 웹사이트/API와 대화할 수 있는 인터페이스 제공
- TripAdvisor, Shopify, Eventbrite 등이 얼리 파트너로 참여
- 모든 NLWeb 인스턴스는 MCP(Model Context Protocol) 서버로도 동작

TripAdvisor NLWeb 활용 예:
- "Pike Place 근처에서 와인이 좋은 아버지날 저녁 식사"
- "아이와 함께 이번 가을에 갈 만한 곳"
- 자연어로 표현된 복잡한 여행 의도를 한 번에 파악

참고:
- NLWeb REST API 스펙: https://github.com/nlweb-ai/NLWeb/blob/main/docs/nlweb-rest-api.md
- TripAdvisor 파트너 사례: https://techcommunity.microsoft.com/blog/azure-ai-foundry-blog/nlweb-pioneer-qa-tripadvisor/4415289
"""

import os
import json
import httpx
import anthropic
from typing import Optional

# ─────────────────────────────────────────────
# NLWeb 엔드포인트 설정
# TripAdvisor는 자체 NLWeb 인스턴스를 운영합니다.
# 실제 공개 URL이 확인되면 아래 환경변수로 오버라이드하세요.
# 예: export NLWEB_BASE_URL="https://www.tripadvisor.com/nlweb"
# 로컬 NLWeb 서버 실행: https://github.com/nlweb-ai/NLWeb
# ─────────────────────────────────────────────
NLWEB_BASE_URL = os.environ.get("NLWEB_BASE_URL", "http://localhost:8000")
NLWEB_SITE = os.environ.get("NLWEB_SITE", "tripadvisor")  # 백엔드 site 토큰

# Claude 클라이언트 초기화 (ANTHROPIC_API_KEY 환경변수 사용)
claude = anthropic.Anthropic()
MODEL = "claude-opus-4-6"


# ─────────────────────────────────────────────
# NLWeb API 클라이언트
# ─────────────────────────────────────────────

def query_nlweb(
    query: str,
    prev_queries: Optional[list[str]] = None,
    mode: str = "list",
    num_results: int = 5,
) -> dict:
    """
    NLWeb /ask 엔드포인트를 호출합니다.

    Args:
        query: 자연어 검색 질의
        prev_queries: 이전 대화 쿼리 목록 (멀티턴 컨텍스트)
        mode: "list" | "summarize" | "generate"
              - list: 관련 결과 목록 반환 (기본값)
              - summarize: 요약 + 결과 목록
              - generate: RAG 방식의 LLM 생성 답변
        num_results: 반환할 결과 수

    Returns:
        Schema.org JSON 형식의 NLWeb 응답 딕셔너리
    """
    payload = {
        "query": query,
        "site": NLWEB_SITE,
        "mode": mode,
        "streaming": False,
        "num_results": num_results,
    }

    if prev_queries:
        # 이전 쿼리들을 콤마 구분 문자열로 전달 (NLWeb 스펙)
        payload["prev"] = ",".join(prev_queries)

    try:
        resp = httpx.post(
            f"{NLWEB_BASE_URL}/ask",
            json=payload,
            timeout=30.0,
            headers={"Content-Type": "application/json"},
        )
        resp.raise_for_status()
        return resp.json()
    except httpx.ConnectError:
        return {
            "error": "connection_failed",
            "message": (
                f"NLWeb 서버에 연결할 수 없습니다 ({NLWEB_BASE_URL}).\n"
                "환경변수 NLWEB_BASE_URL을 실제 TripAdvisor NLWeb 엔드포인트로 설정하거나,\n"
                "로컬에 NLWeb 서버를 실행하세요: https://github.com/nlweb-ai/NLWeb"
            ),
        }
    except httpx.HTTPStatusError as e:
        return {
            "error": "http_error",
            "status_code": e.response.status_code,
            "message": e.response.text,
        }
    except Exception as e:
        return {"error": "unexpected", "message": str(e)}


def query_nlweb_summarize(
    query: str,
    prev_queries: Optional[list[str]] = None,
) -> dict:
    """NLWeb summarize 모드: 요약 + 결과 목록 반환"""
    return query_nlweb(query, prev_queries, mode="summarize")


def query_nlweb_generate(
    query: str,
    prev_queries: Optional[list[str]] = None,
) -> dict:
    """NLWeb generate 모드: RAG 방식 생성형 답변 반환"""
    return query_nlweb(query, prev_queries, mode="generate")


def format_nlweb_results(nlweb_response: dict) -> str:
    """
    NLWeb Schema.org JSON 응답을 사람이 읽기 쉬운 텍스트로 변환합니다.

    NLWeb 응답 구조:
    {
        "query_id": "...",
        "results": [
            {
                "url": "https://tripadvisor.com/...",
                "name": "장소명",
                "score": 0.95,
                "description": "LLM이 생성한 설명",
                "schema_object": { Schema.org 타입 객체 }
            },
            ...
        ]
    }
    """
    if "error" in nlweb_response:
        return f"[오류] {nlweb_response.get('message', '알 수 없는 오류')}"

    results = nlweb_response.get("results", [])
    if not results:
        # summarize/generate 모드의 텍스트 응답 확인
        if "answer" in nlweb_response:
            return nlweb_response["answer"]
        return "검색 결과가 없습니다."

    lines = []
    for i, item in enumerate(results, 1):
        name = item.get("name", "이름 없음")
        url = item.get("url", "")
        score = item.get("score", 0)
        description = item.get("description", "")

        # Schema.org 객체에서 추가 정보 추출
        schema_obj = item.get("schema_object", {})
        if isinstance(schema_obj, str):
            try:
                schema_obj = json.loads(schema_obj)
            except json.JSONDecodeError:
                schema_obj = {}

        address = schema_obj.get("address", {})
        if isinstance(address, dict):
            address_str = address.get("streetAddress", "") or address.get("addressLocality", "")
        else:
            address_str = str(address) if address else ""

        rating = schema_obj.get("aggregateRating", {})
        if isinstance(rating, dict):
            rating_str = (
                f"★ {rating.get('ratingValue', '')} "
                f"({rating.get('reviewCount', '')} 리뷰)"
            )
        else:
            rating_str = ""

        price_range = schema_obj.get("priceRange", "")
        cuisine = schema_obj.get("servesCuisine", "")
        if isinstance(cuisine, list):
            cuisine = ", ".join(cuisine)

        lines.append(f"**{i}. {name}**")
        if rating_str:
            lines.append(f"   평점: {rating_str}")
        if cuisine:
            lines.append(f"   요리 종류: {cuisine}")
        if price_range:
            lines.append(f"   가격대: {price_range}")
        if address_str:
            lines.append(f"   주소: {address_str}")
        if description:
            lines.append(f"   설명: {description}")
        if url:
            lines.append(f"   링크: {url}")
        if score:
            lines.append(f"   관련성 점수: {score:.2f}")
        lines.append("")

    return "\n".join(lines)


# ─────────────────────────────────────────────
# Claude Tool 정의 (NLWeb API를 Tool로 노출)
# ─────────────────────────────────────────────

TOOLS: list[anthropic.types.ToolParam] = [
    {
        "name": "search_tripadvisor",
        "description": (
            "TripAdvisor NLWeb API를 사용해 여행지, 레스토랑, 호텔, 액티비티를 자연어로 검색합니다. "
            "NLWeb은 Microsoft가 발표한 오픈 프로토콜로, 복잡한 자연어 의도를 한 번에 이해합니다. "
            "예: '아이 친화적인 시애틀 레스토랑', 'Pike Place 근처 와인 좋은 곳', '가을 가족 여행지'. "
            "기본적으로 관련 결과 목록을 반환합니다."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "자연어로 표현된 여행/장소 검색 질의 (한국어 또는 영어 가능)",
                },
                "num_results": {
                    "type": "integer",
                    "description": "반환할 결과 수 (기본값: 5, 최대: 20)",
                    "default": 5,
                },
            },
            "required": ["query"],
        },
    },
    {
        "name": "summarize_tripadvisor",
        "description": (
            "TripAdvisor NLWeb API의 summarize 모드를 사용합니다. "
            "검색 결과를 요약하고 핵심 인사이트를 함께 제공합니다. "
            "특정 여행지나 주제에 대한 종합적인 정보가 필요할 때 사용하세요."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "요약이 필요한 여행 관련 질의",
                },
            },
            "required": ["query"],
        },
    },
    {
        "name": "generate_travel_answer",
        "description": (
            "TripAdvisor NLWeb API의 generate 모드를 사용합니다. "
            "RAG(검색 증강 생성) 방식으로 TripAdvisor 데이터에 기반한 구체적인 답변을 생성합니다. "
            "여행 계획, 추천 일정, 상세 비교가 필요할 때 사용하세요."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "생성형 답변이 필요한 구체적인 여행 질문",
                },
            },
            "required": ["query"],
        },
    },
]


# ─────────────────────────────────────────────
# Tool 실행기
# ─────────────────────────────────────────────

def execute_tool(tool_name: str, tool_input: dict, conversation_history: list[str]) -> str:
    """
    Claude가 요청한 Tool을 실행하고 결과를 반환합니다.

    Args:
        tool_name: 호출할 tool 이름
        tool_input: tool 입력 파라미터
        conversation_history: 이전 쿼리 목록 (멀티턴 컨텍스트용)

    Returns:
        Tool 실행 결과 문자열
    """
    query = tool_input.get("query", "")
    num_results = tool_input.get("num_results", 5)

    # 이전 쿼리 컨텍스트 (NLWeb은 stateless이므로 클라이언트가 관리)
    prev = conversation_history[-3:] if len(conversation_history) > 1 else None

    print(f"\n[NLWeb 호출] tool={tool_name}, query='{query}'")

    if tool_name == "search_tripadvisor":
        result = query_nlweb(query, prev_queries=prev, mode="list", num_results=num_results)
    elif tool_name == "summarize_tripadvisor":
        result = query_nlweb_summarize(query, prev_queries=prev)
    elif tool_name == "generate_travel_answer":
        result = query_nlweb_generate(query, prev_queries=prev)
    else:
        return f"알 수 없는 tool: {tool_name}"

    formatted = format_nlweb_results(result)
    print(f"[NLWeb 응답] {len(result.get('results', []))}개 결과 반환")
    return formatted


# ─────────────────────────────────────────────
# 메인 에이전트 (Claude + NLWeb Tool Use Loop)
# ─────────────────────────────────────────────

class TripAdvisorNLWebAgent:
    """
    Claude + TripAdvisor NLWeb 통합 여행 계획 에이전트

    동작 방식:
    1. 사용자 자연어 질문을 Claude가 분석
    2. Claude가 적절한 NLWeb Tool을 선택하여 TripAdvisor 데이터 조회
    3. NLWeb Schema.org 응답을 Claude가 해석하여 최종 답변 생성
    4. 멀티턴 대화로 맥락 유지
    """

    def __init__(self):
        self.messages: list[anthropic.types.MessageParam] = []
        self.nlweb_query_history: list[str] = []
        self.system_prompt = """당신은 TripAdvisor NLWeb API를 활용하는 여행 계획 전문 AI 어시스턴트입니다.

Microsoft NLWeb은 자연어로 웹사이트와 대화할 수 있는 오픈 프로토콜입니다.
TripAdvisor는 NLWeb의 얼리 파트너로, 레스토랑, 호텔, 액티비티 등 풍부한 여행 데이터를 제공합니다.

당신의 역할:
- 사용자의 여행 관련 질문을 이해하고 TripAdvisor NLWeb을 통해 정확한 정보를 찾아드립니다
- 복잡한 조건 (예: "아이와 함께, 와인이 좋은, Pike Place 근처")도 자연어 그대로 검색합니다
- Schema.org 형식으로 반환된 구조화된 데이터를 사람이 읽기 쉽게 정리합니다
- 여행 일정 계획, 레스토랑 추천, 관광지 비교 등을 도와드립니다

Tool 사용 가이드:
- search_tripadvisor: 일반적인 장소/레스토랑/호텔 검색 (기본)
- summarize_tripadvisor: 특정 여행지나 주제의 종합 정보가 필요할 때
- generate_travel_answer: 구체적인 여행 계획이나 상세 비교가 필요할 때

응답 시 항상 한국어로 친절하게 안내하고, 검색 결과의 출처(TripAdvisor)를 명시하세요."""

    def chat(self, user_message: str) -> str:
        """
        사용자 메시지를 처리하고 에이전트 응답을 반환합니다.

        Args:
            user_message: 사용자 입력 메시지

        Returns:
            에이전트 최종 응답 텍스트
        """
        # 사용자 메시지를 대화 기록에 추가
        self.messages.append({"role": "user", "content": user_message})

        # Claude + Tool Use 루프
        while True:
            response = claude.messages.create(
                model=MODEL,
                max_tokens=4096,
                thinking={"type": "adaptive"},  # Opus 4.6 adaptive thinking
                system=self.system_prompt,
                tools=TOOLS,
                messages=self.messages,
            )

            # 어시스턴트 응답을 대화 기록에 추가
            self.messages.append({"role": "assistant", "content": response.content})

            # Tool 호출이 없으면 최종 응답 반환
            if response.stop_reason == "end_turn":
                final_text = next(
                    (block.text for block in response.content if block.type == "text"),
                    ""
                )
                return final_text

            # Tool 호출 처리
            if response.stop_reason == "tool_use":
                tool_results = []

                for block in response.content:
                    if block.type != "tool_use":
                        continue

                    # Tool 실행
                    tool_output = execute_tool(
                        tool_name=block.name,
                        tool_input=block.input,
                        conversation_history=self.nlweb_query_history,
                    )

                    # NLWeb 쿼리 히스토리 업데이트 (멀티턴 컨텍스트)
                    query = block.input.get("query", "")
                    if query:
                        self.nlweb_query_history.append(query)

                    tool_results.append({
                        "type": "tool_result",
                        "tool_use_id": block.id,
                        "content": tool_output,
                    })

                # Tool 결과를 대화에 추가하고 계속
                self.messages.append({"role": "user", "content": tool_results})
                continue

            # 예상치 못한 stop_reason
            break

        return "응답 처리 중 오류가 발생했습니다."

    def reset(self):
        """대화 기록 초기화"""
        self.messages = []
        self.nlweb_query_history = []


# ─────────────────────────────────────────────
# 대화형 CLI 인터페이스
# ─────────────────────────────────────────────

def run_interactive():
    """대화형 CLI 모드로 에이전트를 실행합니다."""
    print("=" * 60)
    print("TripAdvisor NLWeb Agent (powered by Claude + Microsoft NLWeb)")
    print("=" * 60)
    print(f"NLWeb 엔드포인트: {NLWEB_BASE_URL}")
    print(f"Site 토큰: {NLWEB_SITE}")
    print("\n사용법:")
    print("  - 자연어로 여행 관련 질문을 입력하세요")
    print("  - 'reset' 입력 시 대화 초기화")
    print("  - 'quit' 또는 'exit' 입력 시 종료")
    print("\n예시 질문:")
    print("  • 시애틀에서 아이와 함께 가기 좋은 레스토랑을 추천해줘")
    print("  • Pike Place Market 근처에서 와인이 좋은 아버지날 저녁 장소")
    print("  • 뉴욕 여행 3박 4일 일정 계획해줘")
    print("  • 파리에서 가장 인기 있는 관광지 5곳")
    print("=" * 60)

    agent = TripAdvisorNLWebAgent()

    while True:
        try:
            user_input = input("\n나: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n\n에이전트를 종료합니다.")
            break

        if not user_input:
            continue

        if user_input.lower() in ("quit", "exit", "종료"):
            print("에이전트를 종료합니다.")
            break

        if user_input.lower() == "reset":
            agent.reset()
            print("[대화 초기화 완료]")
            continue

        print("\n에이전트: ", end="", flush=True)
        response = agent.chat(user_input)
        print(response)


# ─────────────────────────────────────────────
# 단일 쿼리 실행 유틸리티
# ─────────────────────────────────────────────

def run_single_query(query: str) -> str:
    """
    단일 쿼리를 실행하고 응답을 반환합니다.

    Args:
        query: 여행 관련 질의

    Returns:
        에이전트 응답 텍스트
    """
    agent = TripAdvisorNLWebAgent()
    return agent.chat(query)


if __name__ == "__main__":
    run_interactive()
