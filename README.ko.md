# SC:R Crossover

[English](README.md) | **한국어**

SC4J용 모드입니다. 자기 정체성을 가진 [SC:R 모드](https://github.com/fogofwarlabs/sc4j-scr)의 복제 모드로, 모드 id는 `scr-crossover`, jar는 `scr-crossover.jar`입니다. 자체 LAN 참가 버전, 바꾼 Terran Marine 값, 자체 그래픽을 가진 추가 유닛 둘이 있습니다. Barracks에서 훈련하는 Terran Unity-chan과, Hive가 변태하는 용인 Zerg Myriarch입니다.

`scr.jar`처럼 jar에는 스타크래프트: 리마스터의 에셋이나 데이터 파일이 없습니다. 게임의 그림, 소리, 데이터는 실행할 때 플레이어 본인의 설치본에서 읽습니다.

[SC:R 복제 모드 가이드](docs/scr-crossover.ko.md)가 이 모드를 예시로 모든 단계를 설명합니다.

- [SC:R 모드와의 관계](#scr-모드와의-관계)
- [구성](#구성)
- [복제와 오버레이](#복제와-오버레이)
- [빌드와 실행](#빌드와-실행)
- [그림 라이선스](#그림-라이선스)
- [라이선스](#라이선스)

## SC:R 모드와의 관계

복제본은 저장소에 넣지 않습니다. 빌드가 SC:R 모드([`scr/`](https://github.com/fogofwarlabs/sc4j-scr), 그 `mod.properties`의 `modules` 목록)의 모든 모듈에서 `src/main`을 복사하고 그 위에 오버레이를 덮어씁니다. 바꾸지 않은 SC:R 소스는 SC:R 모드의 컴파일러 인자로 컴파일되므로 클래스가 `scr.jar`와 같습니다.

복제 모드는 다른 모드입니다. 설정, 저장 폴더, 로그 폴더가 따로입니다. 온라인에서는 SC:R 모드의 방을 보여 주지 않고, LAN에서는 참가 버전으로 SC:R 모드 플레이어를 거부하며 그쪽도 이 모드를 거부합니다. 두 모드는 스타크래프트: 리마스터 폴더 설정 하나를 함께 씁니다. 복제 모드는 설치본에 `scr.jar`가 없어도 실행됩니다.

SC:R Crossover에는 자체 런처 그림이 없습니다. `meta/icon.png`와 `meta/banner.png`는 `scr/scr-client/src/main/resources/meta/`에서 복제한 SC:R 모드의 것입니다(원본은 `scr/docs/branding/`).

## 구성

| 경로 | 내용 |
|---|---|
| `scr/` | SC:R 모드. [sc4j-scr](https://github.com/fogofwarlabs/sc4j-scr)의 git 서브모듈 |
| `overlay/` | 복제한 소스를 대신하거나 더하는 파일. 구조는 `overlay/<module>/src/main/...` |
| `overlay.lock`, `overlay-base/` | 오버레이 파일을 만든 SC:R 모드 파일 각각의 SHA-256과 복사본. `acceptOverlay`가 씀 |
| `src/clone` | 복제 도구. 빌드할 때만 씀 |
| `src/convert` | 그림 변환기. 빌드할 때만 씀 |
| `src/mod` | 모드 자체의 클래스와 리소스: 콘텐츠 확장, 값, 추가 유닛 |
| `src/test` | 테스트. 빌드한 jar를 읽음 |
| `third-party/` | 추가 유닛의 그림 세트와 라이선스 파일 |
| `docs/` | SC:R 복제 모드 가이드 |

## 복제와 오버레이

| 작업 | 하는 일 |
|---|---|
| `cloneScrMod` | `overlay.lock`을 확인하고, SC:R 모드 소스를 `build/clone`에 복사한 뒤 `overlay/`를 덮어씀 |
| `overlayDiff` | 오버레이 파일 아래에서 SC:R 모드가 바꾼 내용을 보여 줌 |
| `acceptOverlay` | 현재 SC:R 모드 파일을 `overlay.lock`과 `overlay-base/`에 기록 |
| `convertArt` | 모든 그림 세트의 베이크된 프레임을 유닛의 그래픽 파일로 변환 |
| `jar` | `build/libs/scr-crossover.jar` 빌드 |

오버레이가 대신하는 파일을 SC:R 모드가 바꾸면 빌드가 멈추고 그 파일을 알려 줍니다. `overlayDiff`를 실행하고, 바뀐 내용을 오버레이 파일에 손으로 병합한 뒤 `acceptOverlay`를 실행합니다.

오버레이는 작게 유지합니다. 작은 리소스를 대신하는 것을 먼저, 다음으로 SC:R 모드의 확장 지점 뒤에 둔 자기 클래스를, 마지막으로만 SC:R 모드 소스를 대신합니다. SC:R Crossover는 리소스 둘(`sc4j-mod.json`, `lan-lobby.properties`)을 대신하고 콘텐츠 확장 하나를 추가합니다.

## 빌드와 실행

JDK 25가 필요합니다. 플레이하려면 스타크래프트: 리마스터 1.23.10 설치본이 필요하지만, 빌드에는 필요하지 않습니다. 아래 명령은 저장소 루트의 PowerShell에서 실행합니다. Linux에서는 `./gradlew`를 씁니다.

```powershell
git clone --recurse-submodules https://github.com/fogofwarlabs/sc4j-scr-crossover.git
cd sc4j-scr-crossover
.\gradlew.bat build
```

SC:R 모드는 git 서브모듈 `scr`입니다. 빌드는 이를 포함하고 엔진 버전을 `scr/gradle.properties`에서 가져옵니다. 엔진 모듈과 모드 플러그인은 Maven Central이나 그 스냅샷에서 가져옵니다.

새 SC:R 모드로 옮기려면 `git submodule update --remote scr`를 실행하고 빌드합니다. 빌드가 오버레이 파일에서 멈추면 [복제와 오버레이](#복제와-오버레이)대로 병합합니다. `scr`는 `overlay/`, `overlay.lock`, `overlay-base/`와 함께 커밋합니다.

엔진 체크아웃 [sc4j](https://github.com/fogofwarlabs/sc4j)가 이 체크아웃 옆(`../sc4j`)에 있으면 빌드는 그 체크아웃을 대신 씁니다. `-Psc4j.engine.dir=<폴더>`로 다른 엔진 체크아웃을 지정하고, `-Psc4j.engine.local=false`로 끕니다.

| 명령 | 하는 일 |
|---|---|
| `.\gradlew.bat jar` | `build/libs/scr-crossover.jar` 빌드 |
| `.\gradlew.bat test` | 저장소 밖의 것이 필요 없는 테스트 |
| `.\gradlew.bat integrationTest` | 설치본이 필요한 테스트(Gradle 속성 `sc4j.data`) |
| `.\gradlew.bat installMod` | `scr-crossover.jar`를 설치본의 `mods/`에 복사. 엔진 체크아웃이 있으면 그 개발용 설치본 `../sc4j/build/dev-install/`, 없으면 설치본 `-Psc4j.install=<폴더>` |
| `.\gradlew.bat run` | `scr-crossover.jar`를 그 설치본의 사용자 데이터로 엔진 런타임에서 실행(설치본이 없으면 `build/sc4j-run/`) |
| `..\sc4j\gradlew.bat -p ..\sc4j devInstall` | 엔진 체크아웃의 개발용 설치본 만들기. `installMod` 전에 실행 |
| `..\sc4j\gradlew.bat -p ..\sc4j runDevLauncher` | 그 개발용 설치본의 런처 열기 |

`installMod`는 설치본이 없으면 실패합니다.

## 그림 라이선스

| 자료 | 라이선스 | 자세히 |
|---|---|---|
| 유닛 그림: Unity-chan | Unity-chan License 3.0 | [`third-party/unity-chan/`](third-party/unity-chan/) |
| 유닛 그림: Unity-chan이 든 소총(Quaternius) | CC0 1.0 | [`third-party/unity-chan/`](third-party/unity-chan/) |
| 유닛 그림: Myriarch(OSCAR CREATIVO의 DRAGO 3D 모델) | CC BY 4.0 | [`third-party/myriarch/`](third-party/myriarch/) |

빌드가 그림 세트마다 `NOTICE.txt`와 `license/`를 jar의 `third-party/<set>/` 아래에 묶고, 매니페스트의 `license` 줄이 표기를 밝힙니다. 이 프레임이 든 jar를 공개할 때는 이를 유지합니다.

## 라이선스

SC:R Crossover는 [Apache License 2.0](LICENSE)으로 배포됩니다. 그림 세트는 위의 각 라이선스를 따릅니다. 서드파티 자료는 [NOTICE](NOTICE)에 있습니다.

StarCraft는 Blizzard Entertainment, Inc.의 상표입니다. SC4J는 Blizzard Entertainment와 제휴하거나 승인받지 않았습니다. SC:R Crossover는 플레이어 본인의 스타크래프트: 리마스터 설치본에서 게임 데이터를 읽으며, 저장소와 배포본에는 그런 데이터가 들어 있지 않습니다.
