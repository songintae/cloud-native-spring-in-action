---
paths:
  - "**/deploy/**/*.yml"
  - "**/deploy/**/*.yaml"
  - "**/Dockerfile"
  - "**/docker-compose*.yml"
---

# 컨테이너 & Kubernetes 규칙

## Dockerfile
- 멀티스테이지 빌드 사용
- non-root 사용자로 실행
- 레이어 캐싱 최적화 (의존성 먼저, 소스 나중에)

## Kubernetes 매니페스트
- 리소스 요청/제한(requests/limits) 명시
- 헬스체크(liveness/readiness probe) 설정
- ConfigMap/Secret으로 설정 외부화
- 네임스페이스 명시
