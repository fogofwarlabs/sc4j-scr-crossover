<h1 align="center">
  <img src="https://raw.githubusercontent.com/fogofwarlabs/sc4j-scr/main/docs/branding/icon.svg" alt="SC:R Crossover 로고" width="160">
  <br>
  SC:R Crossover
</h1>

<p align="center">SC:R 모드를 복제해 값을 바꾸고 유닛 둘을 더한 별도 모드.</p>

<p align="center">
  <a href="https://github.com/fogofwarlabs/sc4j-scr-crossover/releases"><img src="https://img.shields.io/github/v/release/fogofwarlabs/sc4j-scr-crossover" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="License: Apache-2.0"></a>
</p>

<p align="center"><a href="README.md">English</a> | <b>한국어</b></p>

---

[SC:R 모드](https://github.com/fogofwarlabs/sc4j-scr)를 별도 SC4J 모드로 복제한 것입니다(모드 id `scr-crossover`, jar `scr-crossover.jar`). 자체 LAN 참가 버전, 바꾼 Terran Marine 값, 추가 유닛 둘이 있습니다: Terran Unity-chan(Barracks)과 Zerg Myriarch(Hive 변태). 게임 데이터는 플레이어 본인의 스타크래프트: 리마스터 설치본에서 읽습니다.

[SC:R 복제 모드 가이드](docs/scr-crossover.ko.md)가 이 모드를 예시로 모든 단계를 설명합니다.

- [구성](#구성)
- [복제와 오버레이](#복제와-오버레이)
- [빌드와 실행](#빌드와-실행)
- [그림 라이선스](#그림-라이선스)
- [릴리즈](#릴리즈)
- [라이선스](#라이선스)

## 구성

| 경로 | 내용 |
|---|---|
| `scr/` | SC:R 모드, [sc4j-scr](https://github.com/fogofwarlabs/sc4j-scr)의 git 서브모듈 |
| `overlay/` | 복제한 소스를 대신하거나 더하는 파일(`overlay/<module>/src/main/...`) |
| `overlay.lock`, `overlay-base/` | 오버레이 파일의 기준인 SC:R 모드 파일 각각의 SHA-256과 복사본 |
| `src/clone`, `src/convert` | 복제 도구와 그림 변환기(빌드할 때만) |
| `src/mod` | 모드 자체의 클래스와 리소스 |
| `src/test` | 테스트. 빌드한 jar를 읽음 |
| `third-party/` | 추가 유닛의 그림 세트와 라이선스 파일 |

복제 모드는 별도 모드입니다. 설정, 저장 폴더, 로그가 따로이며 SC:R 모드와 방이나 LAN 게임을 함께하지 않습니다. 스타크래프트: 리마스터 폴더 설정과 런처 그림은 SC:R 모드와 같고, `scr.jar` 없이 실행됩니다.

## 복제와 오버레이

| 작업 | 하는 일 |
|---|---|
| `cloneScrMod` | `overlay.lock`을 확인하고, SC:R 모드 모듈마다 `src/main`을 `build/clone`에 복사한 뒤 `overlay/`를 덮어씀 |
| `overlayDiff` | 오버레이 아래 파일에 대한 SC:R 모드의 변경을 보여 줌 |
| `acceptOverlay` | 현재 SC:R 모드 파일을 `overlay.lock`과 `overlay-base/`에 기록 |
| `convertArt` | 그림 세트의 베이크된 프레임을 그래픽 파일로 변환 |

오버레이가 대신하는 파일을 SC:R 모드가 바꾸면 빌드가 멈추고 그 파일을 알려 줍니다. `overlayDiff`를 실행하고, 변경을 오버레이 파일에 병합하고, `acceptOverlay`를 실행합니다. 오버레이는 작게 유지합니다([가이드 2절](docs/scr-crossover.ko.md#오버레이)).

## 빌드와 실행

JDK 25가 필요합니다. 명령은 저장소 루트에서 실행합니다(Linux에서는 `./gradlew`).

```powershell
git clone --recurse-submodules https://github.com/fogofwarlabs/sc4j-scr-crossover.git
cd sc4j-scr-crossover
.\gradlew.bat build
```

엔진 버전은 `scr/gradle.properties`에서 가져옵니다. `../sc4j`(또는 `-Psc4j.engine.dir=<폴더>`)에 엔진 체크아웃이 있으면 Maven Central 대신 그것을 쓰고, `-Psc4j.engine.local=false`로 끕니다.

SC:R 모드를 갱신하려면 `git submodule update --remote scr`로 받고, 빌드하고, 멈춘 오버레이 파일을 병합하고, `scr`를 `overlay/`, `overlay.lock`, `overlay-base/`와 함께 커밋합니다.

| 명령 | 하는 일 |
|---|---|
| `.\gradlew.bat jar` | `build/libs/scr-crossover.jar` |
| `.\gradlew.bat test` | 설치본이 필요 없는 테스트 |
| `.\gradlew.bat integrationTest` | 설치본이 필요한 테스트(`sc4j.data`) |
| `..\sc4j\gradlew.bat -p ..\sc4j devInstall` | 엔진 개발용 설치본 `../sc4j/build/dev-install/` |
| `.\gradlew.bat installMod` | jar를 그 설치본 또는 `-Psc4j.install=<폴더>`의 `mods/`에 복사 |
| `.\gradlew.bat run` | 그 설치본의 사용자 데이터로 jar 실행 |
| `..\sc4j\gradlew.bat -p ..\sc4j runDevLauncher` | 개발용 설치본의 런처 열기 |

## 그림 라이선스

| 자료 | 라이선스 | 자세히 |
|---|---|---|
| Unity-chan | Unity-chan License 3.0 | [`third-party/unity-chan/`](third-party/unity-chan/) |
| Unity-chan의 소총(Quaternius) | CC0 1.0 | [`third-party/unity-chan/`](third-party/unity-chan/) |
| Myriarch(OSCAR CREATIVO의 DRAGO 3D 모델) | CC BY 4.0 | [`third-party/myriarch/`](third-party/myriarch/) |

jar는 세트마다 `NOTICE.txt`와 `license/`를 `third-party/<set>/` 아래에 담고, 매니페스트의 `license` 줄이 이를 밝힙니다. 이 프레임이 든 jar를 공개할 때는 이를 유지합니다.

## 릴리즈

`develop`에서 `gradle.properties`에 `version=X.Y.Z`를 정하고 `scr`를 릴리즈된 `sc4jVersion`의 커밋으로 옮긴 뒤, `develop`에서 `main`으로 ``Release version `X.Y.Z` `` 제목의 풀 리퀘스트를 열어 merge commit으로 병합합니다. `main`에 푸시되면 `scr-crossover.jar`와 `scr-crossover.jar.sha256`이 붙은 GitHub 릴리즈 `vX.Y.Z`가 만들어집니다.

## 라이선스

[Apache License 2.0](LICENSE). 그림 세트는 각자의 라이선스를 따릅니다. 서드파티 자료: [NOTICE](NOTICE).

StarCraft는 Blizzard Entertainment, Inc.의 상표입니다. SC4J는 Blizzard Entertainment와 제휴하거나 승인받지 않았습니다.
