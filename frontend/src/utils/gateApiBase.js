// 실시간 게이트 인식 서비스(gate_api.py, FastAPI) 기본 주소.
// scargo 백엔드(Spring Boot, API_BASE=localhost:8080)와는 별개의 서버임 -
// gate_api.py가 uvicorn으로 localhost:8001에서 떠 있어야 이 주소로 접속됨.
// (실행법: gate_api.py 상단 docstring 참고 - uvicorn gate_api:app --port 8001)
export const GATE_SCAN_API_BASE = 'http://localhost:8001'
