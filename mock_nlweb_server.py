"""
TripAdvisor NLWeb 호환 목 서버
================================
실제 TripAdvisor NLWeb 엔드포인트와 동일한 스펙으로 동작하는 로컬 서버입니다.
- NLWeb REST API 스펙 준수: /ask 엔드포인트
- Schema.org 형식 응답
- list / summarize / generate 모드 지원
- TripAdvisor 실제 데이터 구조 반영 (Restaurant, TouristAttraction, Hotel)
"""

import re
from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional
import uvicorn

app = FastAPI(title="TripAdvisor NLWeb Mock Server")

# ── TripAdvisor 스타일 Schema.org 데이터셋 ──────────────────────────────────
TRIPADVISOR_DATA = [
    # 시애틀 레스토랑
    {
        "url": "https://www.tripadvisor.com/Restaurant_Review-g60878-d407964-Reviews-Canlis-Seattle_Washington.html",
        "name": "Canlis",
        "site": "tripadvisor",
        "keywords": ["seattle", "restaurant", "wine", "fine dining", "anniversary", "romantic", "dinner"],
        "schema_object": {
            "@type": "Restaurant",
            "@context": "https://schema.org",
            "name": "Canlis",
            "description": "시애틀 최고의 파인다이닝 레스토랑. Lake Union 전망과 함께하는 특별한 저녁식사.",
            "servesCuisine": ["American", "Pacific Northwest"],
            "priceRange": "$$$$",
            "aggregateRating": {"@type": "AggregateRating", "ratingValue": "4.7", "reviewCount": "3241"},
            "address": {"@type": "PostalAddress", "streetAddress": "2576 Aurora Ave N", "addressLocality": "Seattle", "addressRegion": "WA"},
            "telephone": "+1-206-283-3313",
            "openingHours": "Tu-Sa 17:30-21:30",
            "hasMenu": "https://canlis.com/menu",
        }
    },
    {
        "url": "https://www.tripadvisor.com/Restaurant_Review-g60878-d1058791-Reviews-The_Pink_Door-Seattle_Washington.html",
        "name": "The Pink Door",
        "site": "tripadvisor",
        "keywords": ["seattle", "pike place", "wine", "italian", "romantic", "dinner", "father's day", "market"],
        "schema_object": {
            "@type": "Restaurant",
            "@context": "https://schema.org",
            "name": "The Pink Door",
            "description": "Pike Place Market 바로 옆, 이탈리아 요리와 탁월한 와인 셀렉션. 숨겨진 입구가 매력적인 시애틀 명소.",
            "servesCuisine": ["Italian", "Mediterranean"],
            "priceRange": "$$$",
            "aggregateRating": {"@type": "AggregateRating", "ratingValue": "4.5", "reviewCount": "5872"},
            "address": {"@type": "PostalAddress", "streetAddress": "1919 Post Alley", "addressLocality": "Seattle", "addressRegion": "WA"},
            "telephone": "+1-206-443-3241",
            "openingHours": "Mo-Su 11:30-22:00",
        }
    },
    {
        "url": "https://www.tripadvisor.com/Restaurant_Review-g60878-d2394018-Reviews-Maximilien-Seattle_Washington.html",
        "name": "Maximilien",
        "site": "tripadvisor",
        "keywords": ["seattle", "pike place", "wine", "french", "romantic", "dinner", "waterfront", "market"],
        "schema_object": {
            "@type": "Restaurant",
            "@context": "https://schema.org",
            "name": "Maximilien",
            "description": "Pike Place Market 안의 프렌치 비스트로. Elliott Bay 전망과 함께하는 프랑스 와인 컬렉션.",
            "servesCuisine": ["French", "European"],
            "priceRange": "$$$",
            "aggregateRating": {"@type": "AggregateRating", "ratingValue": "4.4", "reviewCount": "2108"},
            "address": {"@type": "PostalAddress", "streetAddress": "81A Pike St", "addressLocality": "Seattle", "addressRegion": "WA"},
            "telephone": "+1-206-682-7270",
            "openingHours": "Mo-Su 09:00-22:00",
        }
    },
    {
        "url": "https://www.tripadvisor.com/Restaurant_Review-g60878-d2547451-Reviews-Ivar_s_Acres_of_Clams-Seattle_Washington.html",
        "name": "Ivar's Acres of Clams",
        "site": "tripadvisor",
        "keywords": ["seattle", "seafood", "waterfront", "family", "kids", "casual", "lunch", "dinner"],
        "schema_object": {
            "@type": "Restaurant",
            "@context": "https://schema.org",
            "name": "Ivar's Acres of Clams",
            "description": "워터프론트에 위치한 시애틀 전통 해산물 레스토랑. 가족 단위 방문객에게 인기.",
            "servesCuisine": ["Seafood", "American"],
            "priceRange": "$$",
            "aggregateRating": {"@type": "AggregateRating", "ratingValue": "4.2", "reviewCount": "4531"},
            "address": {"@type": "PostalAddress", "streetAddress": "1001 Alaskan Way", "addressLocality": "Seattle", "addressRegion": "WA"},
        }
    },
    # 시애틀 관광지
    {
        "url": "https://www.tripadvisor.com/Attraction_Review-g60878-d503561-Reviews-Pike_Place_Market-Seattle_Washington.html",
        "name": "Pike Place Market",
        "site": "tripadvisor",
        "keywords": ["seattle", "market", "attraction", "tourist", "kids", "family", "sightseeing"],
        "schema_object": {
            "@type": "TouristAttraction",
            "@context": "https://schema.org",
            "name": "Pike Place Market",
            "description": "1907년 개장한 시애틀의 상징. 신선한 해산물, 꽃, 수공예품. 물고기 던지기 퍼포먼스로 유명.",
            "aggregateRating": {"@type": "AggregateRating", "ratingValue": "4.6", "reviewCount": "28441"},
            "address": {"@type": "PostalAddress", "streetAddress": "85 Pike St", "addressLocality": "Seattle", "addressRegion": "WA"},
            "openingHours": "Mo-Su 09:00-18:00",
        }
    },
    {
        "url": "https://www.tripadvisor.com/Attraction_Review-g60878-d597579-Reviews-Space_Needle-Seattle_Washington.html",
        "name": "Space Needle",
        "site": "tripadvisor",
        "keywords": ["seattle", "attraction", "landmark", "view", "kids", "family", "sightseeing", "tower"],
        "schema_object": {
            "@type": "TouristAttraction",
            "@context": "https://schema.org",
            "name": "Space Needle",
            "description": "시애틀의 아이콘. 1962 세계박람회 유산. 전망대에서 360도 시애틀 파노라마 뷰.",
            "aggregateRating": {"@type": "AggregateRating", "ratingValue": "4.5", "reviewCount": "32109"},
            "address": {"@type": "PostalAddress", "streetAddress": "400 Broad St", "addressLocality": "Seattle", "addressRegion": "WA"},
            "openingHours": "Mo-Su 09:00-23:00",
        }
    },
    # 뉴욕 레스토랑
    {
        "url": "https://www.tripadvisor.com/Restaurant_Review-g60763-d812118-Reviews-Katz_s_Delicatessen-New_York_City_New_York.html",
        "name": "Katz's Delicatessen",
        "site": "tripadvisor",
        "keywords": ["new york", "nyc", "deli", "sandwich", "lunch", "iconic", "lower east side"],
        "schema_object": {
            "@type": "Restaurant",
            "@context": "https://schema.org",
            "name": "Katz's Delicatessen",
            "description": "1888년 창업, 뉴욕 최고의 델리. '해리가 샐리를 만났을 때' 촬영지. 전설적인 파스트라미 샌드위치.",
            "servesCuisine": ["Deli", "American", "Jewish"],
            "priceRange": "$$",
            "aggregateRating": {"@type": "AggregateRating", "ratingValue": "4.4", "reviewCount": "12876"},
            "address": {"@type": "PostalAddress", "streetAddress": "205 E Houston St", "addressLocality": "New York", "addressRegion": "NY"},
        }
    },
    {
        "url": "https://www.tripadvisor.com/Restaurant_Review-g60763-d452803-Reviews-Le_Bernardin-New_York_City_New_York.html",
        "name": "Le Bernardin",
        "site": "tripadvisor",
        "keywords": ["new york", "nyc", "fine dining", "seafood", "french", "michelin", "romantic", "dinner", "wine"],
        "schema_object": {
            "@type": "Restaurant",
            "@context": "https://schema.org",
            "name": "Le Bernardin",
            "description": "미슐랭 3스타, 뉴욕 최고의 프렌치 시푸드 레스토랑. Eric Ripert 셰프의 걸작.",
            "servesCuisine": ["French", "Seafood"],
            "priceRange": "$$$$",
            "aggregateRating": {"@type": "AggregateRating", "ratingValue": "4.8", "reviewCount": "4209"},
            "address": {"@type": "PostalAddress", "streetAddress": "155 W 51st St", "addressLocality": "New York", "addressRegion": "NY"},
        }
    },
    # 파리 레스토랑
    {
        "url": "https://www.tripadvisor.com/Restaurant_Review-g187147-d2220671-Reviews-Septime-Paris_Ile_de_France.html",
        "name": "Septime",
        "site": "tripadvisor",
        "keywords": ["paris", "restaurant", "french", "modern", "wine", "romantic", "dinner", "bistro"],
        "schema_object": {
            "@type": "Restaurant",
            "@context": "https://schema.org",
            "name": "Septime",
            "description": "파리에서 가장 예약 구하기 힘든 레스토랑. 현대적 프렌치 요리와 자연 와인의 완벽한 조화.",
            "servesCuisine": ["French", "Contemporary"],
            "priceRange": "$$$",
            "aggregateRating": {"@type": "AggregateRating", "ratingValue": "4.6", "reviewCount": "3102"},
            "address": {"@type": "PostalAddress", "streetAddress": "80 Rue de Charonne", "addressLocality": "Paris"},
        }
    },
    # 아이 친화 레스토랑
    {
        "url": "https://www.tripadvisor.com/Restaurant_Review-g60878-d1010918-Reviews-Cutter_s_Bayhouse-Seattle_Washington.html",
        "name": "Cutter's Bayhouse",
        "site": "tripadvisor",
        "keywords": ["seattle", "family", "kids", "children", "waterfront", "seafood", "casual", "lunch", "dinner"],
        "schema_object": {
            "@type": "Restaurant",
            "@context": "https://schema.org",
            "name": "Cutter's Bayhouse",
            "description": "Pike Place Market 인근, 가족 친화적 시푸드 레스토랑. 아이들 메뉴 완비, 베이 전망.",
            "servesCuisine": ["Seafood", "American"],
            "priceRange": "$$",
            "aggregateRating": {"@type": "AggregateRating", "ratingValue": "4.3", "reviewCount": "2891"},
            "address": {"@type": "PostalAddress", "streetAddress": "2001 Western Ave", "addressLocality": "Seattle", "addressRegion": "WA"},
        }
    },
]


class NLWebRequest(BaseModel):
    query: str
    site: Optional[str] = "tripadvisor"
    prev: Optional[str] = None
    mode: Optional[str] = "list"
    streaming: Optional[bool] = False
    num_results: Optional[int] = 5
    decontextualized_query: Optional[str] = None


def simple_search(query: str, num_results: int) -> list[dict]:
    """키워드 기반 간단 검색 (실제 NLWeb은 벡터 DB + LLM 사용)"""
    query_lower = query.lower()
    query_words = set(re.findall(r'\w+', query_lower))

    scored = []
    for item in TRIPADVISOR_DATA:
        kw = set(item["keywords"])
        name_words = set(item["name"].lower().split())
        desc_words = set(item["schema_object"].get("description", "").lower().split())

        keyword_overlap = len(query_words & kw)
        name_overlap = len(query_words & name_words)
        desc_overlap = len(query_words & desc_words)

        score = (keyword_overlap * 0.5) + (name_overlap * 0.3) + (desc_overlap * 0.2)
        if score > 0:
            scored.append((score, item))

    scored.sort(key=lambda x: x[0], reverse=True)
    return [item for _, item in scored[:num_results]]


def build_result(item: dict, score: float, description_override: str = "") -> dict:
    """NLWeb 스펙에 맞는 결과 딕셔너리 생성"""
    return {
        "url": item["url"],
        "name": item["name"],
        "site": item["site"],
        "score": round(score, 2),
        "description": description_override or item["schema_object"].get("description", ""),
        "schema_object": item["schema_object"],
    }


@app.post("/ask")
async def ask(req: NLWebRequest):
    """
    NLWeb /ask 엔드포인트
    TripAdvisor NLWeb과 동일한 요청/응답 스펙
    """
    results_raw = simple_search(req.query, req.num_results or 5)

    if req.mode == "list":
        results = [
            build_result(item, 0.95 - i * 0.05)
            for i, item in enumerate(results_raw)
        ]
        return {
            "query_id": f"mock-{hash(req.query) % 100000:05d}",
            "query": req.query,
            "mode": "list",
            "site": req.site,
            "results": results,
        }

    elif req.mode == "summarize":
        results = [build_result(item, 0.95 - i * 0.05) for i, item in enumerate(results_raw)]
        names = [r["name"] for r in results[:3]]
        summary = (
            f"'{req.query}'에 대한 TripAdvisor 검색 결과 요약: "
            f"총 {len(results)}개의 관련 장소를 찾았습니다. "
            f"주요 추천 장소로는 {', '.join(names)} 등이 있습니다. "
            f"모두 높은 리뷰 평점을 보유한 검증된 명소입니다."
        )
        return {
            "query_id": f"mock-{hash(req.query) % 100000:05d}",
            "query": req.query,
            "mode": "summarize",
            "site": req.site,
            "answer": summary,
            "results": results,
        }

    elif req.mode == "generate":
        results = [build_result(item, 0.95 - i * 0.05) for i, item in enumerate(results_raw)]
        if results:
            top = results[0]
            generated = (
                f"'{req.query}'에 가장 적합한 곳은 **{top['name']}**입니다. "
                f"{top['description']} "
                f"TripAdvisor 평점: {top['schema_object'].get('aggregateRating', {}).get('ratingValue', 'N/A')}점. "
                f"가격대: {top['schema_object'].get('priceRange', 'N/A')}."
            )
        else:
            generated = f"'{req.query}'에 해당하는 장소를 찾지 못했습니다."
        return {
            "query_id": f"mock-{hash(req.query) % 100000:05d}",
            "query": req.query,
            "mode": "generate",
            "site": req.site,
            "answer": generated,
            "results": results,
        }

    return {"error": f"지원하지 않는 모드: {req.mode}"}


@app.get("/health")
async def health():
    return {"status": "ok", "site": "tripadvisor", "nlweb_version": "1.0"}


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8000, log_level="warning")
