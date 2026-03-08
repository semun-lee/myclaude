"""
NLWeb TripAdvisor Agent 사용 예시
====================================
다양한 여행 시나리오에서 에이전트를 활용하는 방법을 보여줍니다.

실행 전 준비:
  1. pip install -r requirements.txt
  2. export ANTHROPIC_API_KEY="your-api-key"
  3. (선택) export NLWEB_BASE_URL="https://your-nlweb-endpoint"
     - TripAdvisor NLWeb 공개 엔드포인트 또는
     - 로컬 NLWeb 서버: https://github.com/nlweb-ai/NLWeb
"""

from nlweb_tripadvisor_agent import TripAdvisorNLWebAgent, run_single_query, query_nlweb, format_nlweb_results


def example_1_basic_search():
    """예시 1: 기본 레스토랑 검색 (단일 쿼리)"""
    print("\n" + "=" * 50)
    print("예시 1: 기본 레스토랑 검색")
    print("=" * 50)

    result = run_single_query(
        "시애틀 Pike Place Market 근처에서 와인이 좋고 분위기 있는 저녁 식사 장소를 추천해줘"
    )
    print(result)


def example_2_multi_turn_conversation():
    """예시 2: 멀티턴 대화로 여행 계획 수립"""
    print("\n" + "=" * 50)
    print("예시 2: 멀티턴 대화 - 뉴욕 여행 계획")
    print("=" * 50)

    agent = TripAdvisorNLWebAgent()

    # 첫 번째 질문: 전반적인 관광지
    print("\n[Turn 1] 뉴욕 관광지 문의")
    response1 = agent.chat("뉴욕 여행 처음인데 꼭 가봐야 할 관광지 5곳 알려줘")
    print(f"에이전트: {response1}")

    # 두 번째 질문: 식사 장소 (이전 컨텍스트 유지)
    print("\n[Turn 2] 관광지 근처 레스토랑 문의")
    response2 = agent.chat("그 중에서 타임스퀘어 근처에서 점심 먹기 좋은 곳은 어디야?")
    print(f"에이전트: {response2}")

    # 세 번째 질문: 구체적 일정
    print("\n[Turn 3] 일정 정리 요청")
    response3 = agent.chat("위 정보를 바탕으로 1일차 오전 관광지 → 점심 → 오후 관광지 순으로 일정 짜줘")
    print(f"에이전트: {response3}")


def example_3_family_travel():
    """예시 3: 아이 동반 가족 여행 (NLWeb의 복잡한 자연어 처리 능력 활용)"""
    print("\n" + "=" * 50)
    print("예시 3: 아이 동반 가족 여행 추천")
    print("=" * 50)

    # NLWeb의 핵심 강점: 복잡한 자연어 의도를 한 번에 파악
    result = run_single_query(
        "7살, 10살 아이 둘과 함께하는 올 가을 미국 서부 여행지 추천해줘. "
        "아이들이 즐길 수 있는 액티비티가 많고 교육적인 곳 위주로"
    )
    print(result)


def example_4_direct_nlweb_api():
    """예시 4: NLWeb API 직접 호출 (Claude 없이)"""
    print("\n" + "=" * 50)
    print("예시 4: NLWeb /ask 엔드포인트 직접 호출")
    print("=" * 50)

    # list 모드: 관련 장소 목록
    print("\n[list 모드] 파리 레스토랑 검색:")
    result = query_nlweb(
        query="romantic restaurants in Paris with Eiffel Tower view",
        mode="list",
        num_results=3,
    )
    print(format_nlweb_results(result))

    # summarize 모드: 요약 정보
    print("\n[summarize 모드] 바르셀로나 관광 요약:")
    result = query_nlweb(
        query="best things to do in Barcelona",
        mode="summarize",
        num_results=5,
    )
    print(format_nlweb_results(result))


def example_5_nlweb_mcp_info():
    """예시 5: NLWeb MCP 서버 정보 출력 (교육용)"""
    print("\n" + "=" * 50)
    print("예시 5: NLWeb 기술 소개")
    print("=" * 50)

    info = """
NLWeb 핵심 개념:
━━━━━━━━━━━━━━

1. REST API (/ask 엔드포인트)
   POST /ask
   {
     "query": "자연어 질의",
     "site": "tripadvisor",        // 백엔드 site 토큰
     "mode": "list|summarize|generate",
     "prev": "이전쿼리1,이전쿼리2", // 멀티턴 컨텍스트
     "streaming": false,
     "num_results": 5
   }

2. MCP 서버 (/mcp 엔드포인트)
   - 모든 NLWeb 인스턴스는 MCP 서버로도 동작
   - AI 에이전트가 TripAdvisor를 하나의 tool로 사용 가능
   - list_tools, call_tool 등 MCP 메서드 지원

3. Schema.org 응답 형식
   {
     "results": [
       {
         "url": "https://tripadvisor.com/...",
         "name": "장소명",
         "score": 0.95,
         "description": "AI 생성 설명",
         "schema_object": {
           "@type": "Restaurant",
           "aggregateRating": {...},
           "servesCuisine": [...],
           "priceRange": "$$$"
         }
       }
     ]
   }

4. TripAdvisor NLWeb 통합 사례 (Microsoft Build 2025)
   - 백엔드: Qdrant 벡터 DB 연동
   - 활용: "Father's Day dinner in Seattle with good wine near Pike Place"
     → 복잡한 의도를 필터 없이 자연어 한 문장으로 검색

참고 링크:
- NLWeb GitHub: https://github.com/nlweb-ai/NLWeb
- TripAdvisor 파트너 사례: https://techcommunity.microsoft.com/blog/azure-ai-foundry-blog/nlweb-pioneer-qa-tripadvisor/4415289
- NLWeb REST API 스펙: https://github.com/nlweb-ai/NLWeb/blob/main/docs/nlweb-rest-api.md
"""
    print(info)


if __name__ == "__main__":
    import sys

    examples = {
        "1": ("기본 레스토랑 검색", example_1_basic_search),
        "2": ("멀티턴 뉴욕 여행 계획", example_2_multi_turn_conversation),
        "3": ("아이 동반 가족 여행", example_3_family_travel),
        "4": ("NLWeb API 직접 호출", example_4_direct_nlweb_api),
        "5": ("NLWeb 기술 소개", example_5_nlweb_mcp_info),
    }

    if len(sys.argv) > 1 and sys.argv[1] in examples:
        name, fn = examples[sys.argv[1]]
        print(f"\n[실행] {name}")
        fn()
    else:
        print("\nNLWeb TripAdvisor Agent 예시 모음")
        print("사용법: python example_usage.py <번호>")
        print()
        for num, (name, _) in examples.items():
            print(f"  {num}: {name}")
        print("\n모든 예시 실행 중 (NLWeb 연결 필요)...")
        example_5_nlweb_mcp_info()  # 연결 없이도 실행 가능한 예시만
