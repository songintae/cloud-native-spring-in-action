---
paths:
  - "**/application*.yml"
  - "**/application*.yaml"
  - "**/application*.properties"
  - "**/build.gradle"
  - "**/settings.gradle"
---

# 설정 파일 규칙

## 주석 패턴
- `[실무]`: 설정 파일 내 실무 관점 주석

## Spring 설정
- 프로파일별 설정 분리 (default, test, prod)
- 외부화 설정 원칙 준수 (12-Factor App #3)
- 민감 정보는 환경변수 또는 Config Server로 관리
