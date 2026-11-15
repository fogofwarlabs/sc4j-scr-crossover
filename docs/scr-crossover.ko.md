# SC:R 복제 모드 가이드 (초안)

[English](scr-crossover.md) | **한국어**

이 가이드는 SC4J의 SC:R 모드로 자신의 모드를 만드는 방법을 설명합니다. SC:R 모드를 복제하고, 파일 몇 개를 바꾸고, 모드 jar 하나를 빌드해 `mods/`에 넣고, 런처에서 시작합니다. SC:R Crossover(이 저장소, 모드 id `scr-crossover`)가 이 방법으로 만들어졌고 모든 단계의 예시입니다. 그 빌드는 가이드와 같은 단계를 실행합니다.

이 가이드는 [모드 제작 가이드](https://github.com/fogofwarlabs/sc4j/blob/main/docs/mod-guide.ko.md#18-기존-모드-복제)의 복제 장입니다. 모드 제작 가이드는 처음부터 만드는 모드, 모드 API, 결정성, 네트워크 플레이, [API 정책](https://github.com/fogofwarlabs/sc4j/blob/main/docs/api-policy.ko.md)을 다룹니다.

**복제 모드란**

- SC:R 모드 소스(`scr/scr-data` ... `scr/scr-client`, 각각의 `src/main` 폴더)의 복사본에, 그중 몇몇 파일의 자기 판과 자기 클래스를 더해 `scr-crossover.jar` 같은 jar 하나로 빌드한 것입니다.
- 자기 정체성을 가진 별개의 모드입니다. 자기 모드 id가 있으므로 설정, 저장 폴더, 로그 폴더도 따로이며, SC:R 모드 플레이어와 만나지 않습니다(6절).
- `scr.jar`처럼 스타크래프트: 리마스터의 에셋이나 데이터 파일이 없는 jar입니다. 게임의 그림, 소리, 데이터는 실행할 때 플레이어 본인의 스타크래프트: 리마스터 설치본에서 읽습니다. 설치본의 파일을 jar나 저장소에 넣지 마세요.

**필요한 것**: 서브모듈 `scr`와 함께 받은 이 저장소(`git clone --recurse-submodules`), JDK 25, 플레이하려면 스타크래프트: 리마스터 1.23.10 설치본. 아래 명령은 저장소 루트의 PowerShell에서 실행합니다. Linux나 macOS에서는 `.\gradlew.bat` 대신 `./gradlew`를 씁니다.

**목차**

1. [복제](#1-복제): 복사 단계, 빌드 파일, 자신의 모드
2. [변경](#2-변경): 오버레이와 잠금, 모드 정체성, LAN 참가 버전, 콘텐츠 확장, 값, 자체 그래픽을 가진 추가 유닛
3. [빌드](#3-빌드)
4. [`mods/`에 넣기](#4-mods에-넣기)
5. [런처에서 실행](#5-런처에서-실행)
6. [정체성: 복제 모드가 함께 플레이하는 상대](#6-정체성-복제-모드가-함께-플레이하는-상대)

## 1. 복제

### 복사 단계

복제본은 저장소에 체크인되어 있지 않습니다. 빌드가 만듭니다. `cloneScrMod` 태스크가 복제 도구 `ScrModClone` (`src/clone/java`)을 실행하며, 이 도구는

1. 오버레이를 잠금과 대조합니다(2절 "SC:R 모드가 바뀔 때").
2. SC:R 모드의 모든 모듈(`scr-data`, `scr-game`, `scr-render`, `scr-net`, `scr-client`. 목록은 `scr/mod.properties`의 `modules`)의 `scr/<module>/src/main`을 `build/clone/<module>/src/main`에 복사합니다. 손으로 복사했을 때와 같은 폴더 구조입니다.
3. 그 위에 오버레이 `overlay/`를 복사합니다. 거기 있는 모든 파일은 같은 경로의 복제 파일을 대신하거나 새 파일로 추가됩니다.

```powershell
.\gradlew.bat cloneScrMod
```

결과: `build/clone/`에 `scr-client`, `scr-data`, `scr-game`, `scr-net`, `scr-render` 폴더가 생기고, 복제된 매니페스트는 SC:R Crossover의 것입니다.

```powershell
Select-String -Path build\clone\scr-client\src\main\resources\sc4j-mod.json -Pattern '"id"'
```

SC:R Crossover의 id가 있는 줄을 출력합니다.

```text
build\clone\scr-client\src\main\resources\sc4j-mod.json:3:  "id": "scr-crossover",
```

편집한 복사본이 아니라 오버레이를 쓰는 이유: 오버레이에는 바꾼 파일만 일반 파일로 들어 있고, 그 파일을 편집합니다. SC:R 모드가 바뀌면 다음 빌드가 새 소스를 복제합니다. 자신이 대신한 파일을 SC:R 모드가 바꾸면, 빌드는 이전 SC:R 코드를 말없이 유지하지 않고 멈춰서 그 파일을 알려 줍니다(2절).

복제본은 SC:R 모드의 패키지 이름(`org.fogofwarlabs.sc4j.scr...`)을 유지합니다. 세션 하나에서는 모드 하나가 자체 클래스 로더에서 실행되므로, 패키지 이름이 같은 두 모드가 만나는 일은 없습니다. 자기 클래스는 자기 패키지에 둡니다(SC:R Crossover는 `org.fogofwarlabs.sc4j.scrcrossover`를 씁니다).

### 빌드 파일

`build.gradle`이 복제 모드의 빌드 파일입니다. 구성:

| 부분 | 하는 일 |
|---|---|
| 소스 세트 `clone` | 복제 도구(`ScrModClone`, `CloneOverlay`). 빌드할 때만 실행되며 jar에는 없습니다. |
| 태스크 `cloneScrMod` | `overlay.lock`을 확인하고, SC:R 모드의 소스를 `build/clone`에 복사하고, 그 위에 `overlay/`를 복사합니다. SC:R 소스, 오버레이 파일, 잠금이 바뀌면 다시 실행됩니다. |
| 태스크 `overlayDiff`, `acceptOverlay` | 자기 오버레이 파일 아래에서 SC:R 모드가 바꾼 내용을 보여 주고, 병합한 뒤 SC:R 모드 파일을 기록합니다(2절). |
| 소스 세트 `mod` | 자신의 모드: `src/mod/java`와 `src/mod/resources`(자기 클래스와 리소스)에 `build/clone`의 모든 모듈 폴더를 더한 것 |
| `mod`의 의존성 | `sc4j-base`와 `sc4j-engine`(런타임이 제공하며 jar에는 없음), 그리고 SC:R 모드 모듈의 서드파티 라이브러리. 그 모듈에서 가져옵니다(목록을 두 번 두지 않음). |
| `mod`의 컴파일러 인자 | SC:R 모드 자신의 것(`-parameters`, `scr/mod.properties`의 `compilerArgs`). 그래서 바꾸지 않은 SC:R 소스의 클래스는 `scr.jar`와 바이트 단위로 같습니다. |
| 소스 세트 `convert` | 그림 변환기(`ArtConverter`, 2절). 복제본을 대상으로 컴파일하고, 빌드할 때만 실행되며 jar에는 없습니다. |
| 태스크 `convertArt` | `third-party/<set>`의 모든 그림 세트(2절 "그림 세트")의 베이크된 프레임을 `build/converted-art` 아래 유닛 그래픽 파일로 변환합니다. `third-party` 아래 파일이 바뀌면 다시 실행됩니다. |
| 태스크 `jar` | `mod` 소스 세트, `convertArt`의 출력, 각 그림 세트의 `NOTICE.txt`와 `license/`를 `build/libs/scr-crossover.jar`로 묶습니다. 재현 가능한 순서, 시간 기록 없음, 중복 항목이면 빌드 실패 |
| 테스트 | `src/test`: 빌드된 jar를 읽으며, 복제본의 클래스를 직접 읽지 않습니다. |

### 자신의 모드

자신의 복제 모드를 시작하려면 SC:R Crossover처럼 자기 저장소를 만듭니다. 서브모듈 `scr`의 SC:R 빌드를 포함하는 Gradle 빌드입니다.

1. 저장소 `my-mod`를 만들고, SC:R 모드를 서브모듈 `scr`로 추가하고(`git submodule add https://github.com/fogofwarlabs/sc4j-scr.git scr`), SC:R Crossover의 Gradle 래퍼, `settings.gradle`, `gradle.properties`, `build.gradle`, 복제 도구 `src/clone/java`를 거기에 복사하고, `scr/scr-client/src/main/resources/sc4j-mod.json`을 `overlay/scr-client/src/main/resources/`에 복사합니다.
2. `settings.gradle`에서 `rootProject.name`을 `my-mod`로 바꿉니다.
3. `build.gradle`에서: `sc4jMod`의 `id`를 자기 모드 id로 바꾸고(jar는 `my-mod.jar`가 됩니다), 설명도 바꿉니다. 그림과 테스트 블록은 빼거나 직접 작성합니다.
4. 자기 `sc4j-mod.json`의 id, 이름, 설명을 자신의 것으로 고친 뒤(모든 복제 모드에는 자기 모드 id가 필요합니다) `.\gradlew.bat acceptOverlay`를 한 번 실행해 `overlay.lock`을 씁니다.

명령은 아래와 같고, 자기 저장소에서 실행합니다.

## 2. 변경

### 오버레이

`overlay/`는 복제본의 폴더 구조를 따릅니다: `overlay/<module>/src/main/...`. 거기 있는 파일은 같은 경로의 복제 파일을 통째로 대신하고, SC:R 모드에 없는 파일은 추가됩니다. 오버레이 파일은 일반 소스와 리소스입니다. 편집기에서 고치고 다시 빌드합니다. 복제한 모듈의 `src/main`만 오버레이할 수 있습니다.

오버레이는 작게 유지합니다. 오버레이 파일 하나하나는 이제 자신이 관리하는 SC:R 모드 파일의 복사본이며, SC:R 모드가 그 파일을 바꿀 때마다 병합할 때까지 빌드가 멈춥니다. 그러니 다음 순서로 선택합니다.

1. 데이터: SC:R 모드가 읽는 작은 리소스를 대신합니다(매니페스트, `lan-lobby.properties`).
2. `src/mod/java`와 `src/mod/resources` 아래의 자기 클래스와 리소스. SC:R 모드가 확장 지점으로 호출합니다(아래 콘텐츠 확장).
3. 둘 다 안 될 때만 오버레이에서 SC:R 모드 소스를 대신합니다.

SC:R Crossover는 리소스 둘을 대신하고 확장 하나를 추가합니다. SC:R 모드 소스는 하나도 대신하지 않습니다.

### SC:R 모드가 바뀔 때

`overlay.lock`은 오버레이 파일마다 그 파일을 만들 때 기준으로 삼은 SC:R 모드 파일의 SHA-256(SC:R 모드에 없는 파일은 `added`)을 기록하며, 파일마다 `<hash>  <path>` 한 줄입니다. 줄 끝 형식은 세지 않습니다. `overlay-base/`에는 그 SC:R 모드 파일들의 복사본이 있습니다. 둘 다 `acceptOverlay`가 쓰며, 손으로 고치지 않습니다. SC:R Crossover의 잠금은 두 줄입니다.

```text
<SHA-256 of the SC:R mod's sc4j-mod.json>  scr-client/src/main/resources/sc4j-mod.json
<SHA-256 of the SC:R mod's lan-lobby.properties>  scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties
```

SC:R 모드가 이 파일 중 하나를 바꾸면 빌드는 한 줄과 함께 멈춥니다.

```text
> The SC:R mod changed scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties after the overlay file was made
  from it: see :overlayDiff, merge the change into the overlay file by hand, then run :acceptOverlay.
```

그러면:

1. `.\gradlew.bat overlayDiff`는 바뀐 파일마다 SC:R 모드의 변경(당시 기준과 현재 기준의 비교)과 자신의 변경(당시 기준과 오버레이 파일의 비교)을 unified diff로 출력합니다.
2. SC:R 모드의 변경을 오버레이 파일에 손으로 병합합니다(또는 상관없다고 판단합니다).
3. `.\gradlew.bat acceptOverlay`가 현재 SC:R 모드 파일을 기록하고, 다음 빌드가 진행됩니다.

오버레이 파일이 잠금에 없거나(새 오버레이 파일: `acceptOverlay` 실행) 잠금이 오버레이에 더는 없는 파일을 가리켜도 빌드가 멈춥니다.

### 모드 정체성 (`overlay/scr-client/src/main/resources/sc4j-mod.json`)

SC:R Crossover의 매니페스트는 SC:R 모드의 것에 자기 `id`, `name`, `description`과 `license` 줄을 더한 것입니다.

```json
  "id": "scr-crossover",
  "name": {"en": "SC:R Crossover", "ko": "SC:R Crossover"},
```

이유: 모드 id가 복제본을 다른 모드로 만듭니다. id는 설정 파일(`userdata/settings/scr-crossover.json`), 저장 폴더(`userdata/mods/scr-crossover/`, 리플레이, 저장 게임, 옵션), 로그 폴더의 이름이 되므로, 복제 모드는 SC:R 모드의 파일을 읽거나 바꾸지 않습니다. 런처는 두 모드를 나란히 보여 주며, id는 온라인 방이 비교하는 모드 정체성의 첫 부분입니다(6절). `entry`, `engine`, `version`, `settings` 스키마는 SC:R 모드의 것을 그대로 둡니다. 복제 모드는 같은 방식으로 시작하고 같은 설치 폴더를 묻습니다. 다른 사람이 쓰지 않는 id를 고릅니다(소문자, 숫자, 하이픈). `license` 줄과 각 `description`의 끝에는 추가 유닛 그림의 표기가 들어갑니다("라이선스 표기" 절).

### LAN 참가 버전 (`overlay/scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties`)

```properties
joinVersion=0xD005
```

SC:R 모드의 파일에는 `joinVersion=5`가 있습니다. 이유: 원본 SC:R LAN 게임에는 모드 id가 없으므로, LAN 호스트는 복제 모드와 SC:R 모드를 구별할 수 없습니다. 호스트는 이미 플레이어의 참가 커맨드에 있는 로비 버전을 확인하고 다르면 거부합니다(`LanLobby.JOIN_VERSION`을 이 파일에서 읽음). 자기 버전을 쓰면 LAN 프로토콜을 바꾸지 않고도 SC:R 모드 호스트가 복제 모드 플레이어를 거부하고(원본 SC:R 호스트도 같은 확인을 함), 복제 모드 호스트가 그 반대를 거부합니다. 버전은 16비트 수이며 자신의 것을 고릅니다. 같은 수를 고른 두 복제 모드는 서로의 LAN 게임을 봅니다. 복제 모드의 추가 유닛이나 그 수치가 바뀔 때마다 바꿉니다. 리플레이와 LAN 게임에는 추가된 id와 값이 들어 있으므로, 유닛이 다른 jar끼리는 동기화가 어긋납니다.

### 콘텐츠 확장 (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverContentExtension.java`)

SC:R 모드는 모드 jar가 밝힌 콘텐츠 확장을 `ScrContentExtension`(`scr-game`의 `org.fogofwarlabs.sc4j.scr.game.extension` 패키지)을 통해 호출합니다.

- `extendInstalledData(GameData installed)`: 설치본의 데이터를 불러온 뒤(`GameData.load`), 거기서 게임을 만들기 전에 한 번. 복제 모드가 설치본을 불러오는 모든 경로(클라이언트 세션, 리플레이, 테스트)가 여기를 지나며, 불러온 데이터의 추가 유닛 타입과 그래픽은 그 데이터로 첫 게임을 만들 때까지만 열려 있습니다(그 뒤의 `add`는 `GameData.getAddedUnitTypes()`가 `IllegalStateException`으로 거부합니다). 추가 유닛 타입은 여기서 등록합니다.
- `extendGameData(GameData game)`: 라이브든 리플레이든 게임마다 한 번. 게임이 설치본 테이블의 자기 복사본 위에 만들어진 직후, Use Map Settings 맵이 유닛 설정을 적용하기 전입니다. 여기서 쓴 값은 데이터 파일을 바꾼 것처럼 동작합니다. 모든 게임이 그 값을 쓰고, 맵 자체의 유닛 설정은 여전히 그 값을 대신합니다.

SC:R Crossover의 확장은 `ScrCrossoverUnitUtils.registerUnit`과 `ScrCrossoverValueUtils.applyValues`를 호출합니다. jar는 서비스 파일 `src/mod/resources/META-INF/services/org.fogofwarlabs.sc4j.scr.game.extension.ScrContentExtension`에 줄마다 클래스 이름 하나로 이를 밝힙니다.

```text
org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverContentExtension
```

### 값 (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverValueUtils.java`)

Terran Marine은 미네랄 45에 300프레임 동안 훈련하고, Gauss Rifle은 피해 7을 줍니다. 콘텐츠 확장이 이 값을 모든 게임의 자기 테이블에 씁니다(`extendGameData`). 값은 SC:R Crossover의 패키지에 있으므로 다음 SC:R 업데이트와 충돌하지 않습니다.

### 자체 그래픽을 가진 유닛 추가

SC:R Crossover는 자체 그래픽을 가진 유닛 둘을 추가합니다. 캐릭터 Unity-chan으로 그린 Terran **Unity-chan**과, Hive가 변태하는 거대한 용인 Zerg **Myriarch**(아래)입니다. 각 유닛에는 콘텐츠 확장에서 호출하는 SC:R Crossover 자체 패키지의 클래스(`extendInstalledData`. Unity-chan이 먼저이므로 id는 233과 234), 라이선스 파일이 있는 그림 세트, 그리고 공유 부분이 있습니다. 그래픽 등록(`ScrCrossoverUnitGraphics`: 그림 세트의 이미지, 스크립트, 스프라이트, flingy 항목, 인터페이스 그림), 그림 파일(`ScrCrossoverArtFiles`), 그림을 변환하는 빌드 단계 하나, 매니페스트의 라이선스 표기입니다.

#### 유닛 (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverUnitUtils.java`)

`registerUnit`은 SC:R 모드의 확장 API(`scr-data`의 `org.fogofwarlabs.sc4j.scr.content` 패키지)로 유닛을 만듭니다.

- `new ScrAddedUnitType.Builder(new UnitType(NAME), NAME, ScrUnits.TERRAN_MARINE, units)`는 Terran Marine의 모든 값에서 시작합니다. 그다음 `setValue`로 자기 체력(50), 비용(미네랄 75, 가스 25), 생산 시간(360프레임), 보급(1)을 줍니다. 기반 타입은 하위 유닛이 없는 훈련 유닛이어야 합니다(건물이나 포탑 불가).
- 설치본의 부분은 복사하지 않고 참조합니다. Terran Ghost의 무기(Ghost의 값으로 무기 필드에 `setValue`)와 그 발사 소리, Terran Medic의 목소리와 죽음 소리, Marine의 요구 조건, Barracks(`setRequirementType`)입니다.
- 크기: 설치본에서 읽은 Terran Goliath의 것(`ScrCrossoverUnitUtils.SIZE_TYPE`). `setValue`로 범위(왼쪽 16, 위 16, 오른쪽 15, 아래 15)와 배치 상자(32 x 32)를, `ScrAddedSprite.Builder.setValue`로 유닛 스프라이트의 선택 원(48픽셀짜리)과 체력 막대 너비(34)를 정합니다. 선택 원은 스프라이트 위치보다 9픽셀 아래(Goliath의 `SpritesDat.SELECTION_CIRCLE_OFFSET`)에 있으며, 그림에서 캐릭터의 발이 놓이는 곳(그림 세트의 지면 오프셋)입니다. 충돌과 경로 탐색은 Goliath의 것이고, 크기 분류(받는 피해)는 Marine의 것을 유지합니다.
- `setTrainButton(new ScrAddedUnitType.TrainButton(ScrUnits.TERRAN_BARRACKS, 5, 'u', "Train Unity-chan"))`: Barracks의 5번 위치 버튼, 단축키 U(Barracks에서 비어 있음: Marine M, Firebat F, Ghost G, Medic C, Lift Off L). 생산 건물 자신의 버튼이 이미 쓰는 위치는 그 버튼에 가려집니다(먼저 보이는 버튼이 이김).
- `gameData.getAddedUnitTypes().add(unit.build())`는 유닛의 SC:R id를 반환합니다. 첫 추가 타입은 233입니다(none 값 228과 트리거 그룹 229..232 다음). 리플레이와 커맨드에는 이 id가 들어 있으므로, 리플레이는 같은 등록, 곧 같은 jar에서만 재생됩니다.
- 그래픽. 각 항목은 앞의 항목을 참조합니다. 그림자 이미지(`ScrAddedImage`), 스크립트(`IscriptCode`를 쓰는 `ScrAddedScript`: 먼저 `imgul`로 그림자를 만드는 대기 루프, Marine처럼 게임 프레임마다 4픽셀 움직이는 걷기, 동작 프레임에서 무기 타입 자체 스크립트의 공격(그 소리와 공격 opcode: 근접 무기는 바로 피해를 주는 `attackmelee`, 탄이 있는 무기는 `attack`)을 재생하는 지상과 공중 공격, 마지막 프레임을 유지하는 죽음), 유닛 이미지(그 스크립트를 쓰는 `ScrAddedImage`), 스프라이트(`ScrAddedSprite`), 유닛 타입이 가리키는 flingy 항목(`ScrAddedFlingy`, `UnitsDat.FLINGY_TYPE`)입니다. 각 이미지의 프레임은 `ScrModGraphics.Builder(files, grpPath).setHdAnimation(..).setHd2Animation(..)`로 jar에서 가져옵니다. 스크립트의 프레임 번호, 재생 순서, 대기는 변환된 `animations.json`에서 읽으므로 그림과 스크립트가 어긋나지 않습니다. 모든 애니메이션은 루프나 `end`로 끝납니다. 코드 블록이 이어져 있어서, 끝을 지나친 블록은 다음 블록을 실행하기 때문입니다. 원본 스크립트처럼 공격의 `ignorerest` 다음에는 대기 루프가 옵니다. 대상이 없으면 대기로 가는 길을 시작하고 다음 명령어로 넘어갑니다.
- jar에서 가져오는 자체 인터페이스 그림. 초상화(`ScrModPortrait.Builder(files, sdIdle, sdTalking).setHd2(..).setHd(..) .setIdleChances(85, 5, 5, 5)`, `graphics.addPortrait`로 등록하고 유닛의 `UnitsDat.PORTRAIT` 값이 가리킴), 커맨드 아이콘 (`ScrModSheetFrame`, `graphics.addCommandIcon`, `setCommandIcon`), 와이어프레임(단일, 그룹, 수송 스트립의 `ScrModWireframe`, `graphics.addWireframe`, `setWireframe`)입니다. 아무것도 가리키지 않는 타입은 `PORTRAIT` 값의 초상화와 와이어프레임 타입 (`setWireframeType`)의 아이콘과 와이어프레임을 보여 줍니다.
- 컴퓨터 플레이어는 이 유닛을 만들지 않습니다. 그들의 스크립트는 원본 유닛 타입만 압니다.

추가 유닛의 한계: 건물이나 하위 유닛은 기반 타입이 될 수 없고, 플레이어당 수량 제한이 없으며, 저장 게임, EUD, 맵의 유닛 설정은 원본 유닛 테이블만 다룹니다. 추가 유닛은 자체 무기(`ScrAddedWeapon`)와 자체 요구 조건 스크립트(`ScrRequirementScript`, `setRequirementScript`)를 가질 수 있고, 건물 변태의 결과가 될 수 있습니다(아래 Myriarch).

#### 라이선스 표기 (`overlay/scr-client/src/main/resources/sc4j-mod.json` 안)

```json
  "license": "Unity-chan: © Unity Technologies Japan/UCL, Unity-chan License 3.0. Rifle: Quaternius, CC0 1.0. Myriarch: DRAGO by OSCAR CREATIVO, CC BY 4.0.",
```

그리고 각 `description` 줄 끝의 한 문장("Unity-chan: © Unity Technologies Japan/UCL (Unity-chan License 3.0)."과 그 한국어 문장). 이유: Unity-chan License는 캐릭터의 라이선스 표기를 작품과 함께 보여 줄 것을 요구합니다. 런처의 모드 상세 정보가 설명과 `license` 줄을 보여 주므로, 모든 플레이어가 모드를 시작하기 전에 봅니다.

#### 그림 세트 (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverArtSet.java`)

추가 유닛마다 그림은 그림 세트 하나입니다. 베이크된 프레임, 인터페이스 그림, `NOTICE.txt`, `license/`가 있는 폴더 `third-party/<set>/`와, 그 이름(폴더), SD 캔버스, 지면 오프셋, 그림자 오프셋, 선택 원 오프셋, 프레임을 베이크한 품질을 주는 `ScrCrossoverArtSet` 상수 하나입니다. 유닛의 오버레이 이미지(Myriarch의 불 뿜기)는 그림자와 인터페이스 그림이 없는 별도 세트이며, 그 유닛의 폴더 안 폴더(`third-party/myriarch/fire/`)에 있습니다. `ArtConverter`가 모든 세트를 변환하고, `jar`가 모든 세트의 변환된 파일, `NOTICE.txt`, `license/`를 jar의 `third-party/<set>/` 아래에 묶고, `ScrCrossoverArtFiles`가 모든 세트의 SD 프레임을 만듭니다. 세트의 메서드는 그 파일의 jar 경로를 주며(`getMainAnimation(quality)`, `getMainFrames()`, `getCommandIcon(quality)` 등), 유닛 등록이 이를 확장 API에 넘깁니다.

| 세트 | 유닛 | SD 캔버스 | 지면 오프셋 | 그림자 오프셋 | 베이크 품질 | 프레임 파일 |
|---|---|---|---|---|---|---|
| `unity-chan` | Unity-chan(등록됨, 위) | 128 | 9 | 0 | HD(미터당 160픽셀) | `main-hd.anim`, `main-hd2.anim`과 그림자의 것 |
| `myriarch` | 나는 용 Myriarch(아래) | 252 | 42 | 42 | HD2(미터당 80픽셀) | `main-hd2.anim`과 그림자의 것. HD 품질도 이것으로 그림 |
| `myriarch/fire` | Myriarch의 불 뿜기(오버레이) | 252 | 0 | - | HD2 | `main-hd2.anim` |

- SD 캔버스는 최대 255입니다(GRP 프레임 크기가 바이트). 4의 배수로 둡니다. 지면 오프셋은 베이크된 프레임의 기준점(유닛 아래의 지면 점)이 이미지 위치보다 아래에 놓이는 거리이며, SD 픽셀 단위입니다. 그림자 오프셋은 그림자 이미지가 이미지 위치보다 아래에 놓이는 거리입니다. 지상 유닛의 그림자는 자기 위치에, 공중 유닛의 그림자는 원본 공중 유닛처럼 42픽셀 아래에 그립니다(`imgul` 그림자 `0 42`). 변환기는 그림자 프레임의 지면 점이 그림자 이미지의 위치가 되도록 그림자 프레임을 옮깁니다.
- 큰 유닛은 큰 캔버스를 채웁니다. HD2로 베이크하면 프레임 파일이 HD 크기의 4분의 1(Myriarch의 `main-hd2.anim`은 약 433 MB, Myriarch 그림 전체는 jar에 묶어 약 103 MB), 베이크된 프레임은 PNG 크기의 4분의 1이 됩니다. 렌더러는 프레임 파일을 그 자체의 텍스처 배율로 그리므로, HD 품질은 HD2 프레임을 같은 크기로 보여 줍니다.
- 프레임 파일에는 베이크된 프레임에 `emissive` 층이 있을 때만 그 층이 있습니다(Myriarch에는 없고, 불 뿜기에는 모든 프레임에 있음).
- 오버레이가 있는 유닛 세트에는 `overlay-offsets.json`이 있습니다. 애니메이션, 프레임, 방향마다 오버레이의 기준점(Myriarch의 입)을 이미지 위치로부터의 SD 픽셀로 적은 것입니다. 변환기가 이를 프레임 순서로 jar에 쓰고, 등록이 유닛 이미지에 공격 오버레이 표로 줍니다 (`ScrAddedImage.Builder.setOverlayOffsets`). 공격의 `imgoluselo`가 그 위치에 오버레이를 만듭니다.
- 세트를 추가하려면: 그 폴더를 `third-party/`에 넣고(형식은 아래, 라이선스 파일과 `NOTICE.txt` 포함), `ScrCrossoverArtSet`에 상수를, `ArtConverterTest`와 `CloneArtScenario`의 그림 세트 목록에 이름을 추가하고, 세트의 경로로 유닛을 등록합니다.

#### Myriarch (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverMyriarchUtils.java`)

`registerUnit`은 Unity-chan 다음에 Myriarch를 추가하며, SC:R 모드의 건물 변태, 무기, 오버레이 확장 지점을 씁니다.

- 기반 타입은 Zerg Guardian(큰 비행 유닛: 비행 플래그와 공중 유닛의 고도, Guardian의 목소리, Zerg 비행 유닛 업그레이드), 특성 `UnitTraitFlags.BUILDING_MORPH_RESULT`(`setTrait`): 이 유닛으로의 건물 변태는 유닛으로 완료되며, 타입이 날기 때문에 Hive가 이륙합니다. 완료된 유닛은 Hive가 서 있던 곳에서 납니다(지상 충돌 없음, 공중 유닛의 고도).
- 버튼: `new ScrAddedUnitType.TrainButton(ScrUnits.ZERG_HIVE, 9, ScrAddedUnitType.ProductionAction.BUILDING_MORPH, 'm', text, Map.of("koKR", koreanText))`, Hive의 9번 위치. 각 `ScrAddedUnitType.TrainButton.Text`에는 사용 가능할 때의 툴팁("Mutate into Myriarch")과 사용 불가일 때의 툴팁이 설치본의 사용 불가 문구 형식("Myriarch Requires:", 그다음 "   Ultralisk Cavern"과 "   Defiler Mound")으로 들어 있습니다. 클라이언트는 자기 로캘의 텍스트를, 없으면 기본 텍스트를 보여 줍니다.
- 자체 요구 조건 스크립트(`setRequirementScript`): `new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HIVE).requireNotBusy() .requireCompleted(ScrUnits.ZERG_ULTRALISK_CAVERN).requireCompleted(ScrUnits.ZERG_DEFILER_MOUND).build()`.
- 값: 미네랄 400과 가스 400(변태를 시작할 때 차감), 2400프레임, 체력 2000, 방어력 3, 보급 8(빈 보급이 8 있어야 변태 시작), 시야 10, 범위 30/25/29/24, 배치 상자 64 x 64, 수송 공간 255(어떤 수송 유닛도 태우지 않음).
- 비행: Guardian의 flingy 항목(최고 속도 640, 가속도 27, 정지 거리 7585, 회전 속도 20, 큰 공중 유닛의 비행). 날갯짓은 속도와 묶이지 않습니다. Hive의 선택 원과 체력 막대 너비. 원은 이미지 위치보다 16픽셀 아래.
- 불 뿜기: 지상의 원본 Firebat Flame Thrower처럼, 지상과 공중 유닛을 대상으로 합니다. 자체 무기 (`gameData.getAddedUnitTypes().addWeapon(new ScrAddedWeapon.Builder(type, flameThrower, weapons)...)`, `weapons.dat` id 131, none id 다음의 첫 id)이며 Flame Thrower(공격자 위치에 나타나는 보이지 않는 투사체, 적에게만 스플래시)에서 시작합니다. 대상 플래그 공중과 지상, 사거리 96, 피해 12 + Zerg 비행 유닛 공격 업그레이드당 1, 일반 피해, 재사용 대기 30, 스플래시 반경 24/32/40. 공격의 동작 프레임이 불길을 만들고(`imgoluselo`, Firebat 불꽃의 소리를 가진 `myriarch/fire` 세트의 오버레이 이미지, 유닛 이미지의 공격 오버레이 오프셋에 따른 입 위치), 앞쪽 32, 72, 112픽셀에 투사체 셋을 발사합니다(`attkshiftproj`, Firebat의 24, 52, 80처럼). 각 투사체는 주변의 적에게 스플래시 피해를 줍니다. 스플래시보다 오래 남는 투사체는 없습니다. 원본 공중 공격 유닛처럼 재사용 대기 동안 계속 떠 있습니다. 공격은 `gotorepeatattk` 뒤에 대기 루프로 이어지고(지상 유닛은 `ignorerest`로 조준 프레임을 유지), 반복 공격은 공격 전체를 다시 재생합니다.
- 변태가 완료되면 Hive는 그 자리에서 Myriarch가 됩니다. Larva는 부모를 잃고 크립이 줄어들면 죽으며, Hive의 보급, 자원 반납, 수량은 사라집니다. 취소하면 Hive가 돌아오고 4분의 3을 환불합니다.

`ScrCrossoverMyriarchTest`는 복제 모드의 라이브 게임에서 변태(버튼, 사용 불가 툴팁, 요구 조건, 비용, 비행 유닛으로 완료, Larva와 크립), 비행(지상 유닛이 설 수 없는 지형 위), 건물 하나와 공중 유닛 둘에 대한 불 뿜기(스플래시 비율에 따른 피해, 두 번째 유닛의 스플래시, 남는 투사체 없음)를 재생하고, 복제 모드 클라이언트 둘의 LAN 게임에서 변태와 비행이 동기화를 유지하는지 확인합니다.

#### Myriarch 그림과 라이선스 (`third-party/myriarch/`)

프레임은 OSCAR CREATIVO(Sketchfab)의 용 "DRAGO 3D MODEL FREE BY Oscar Creativo"(CC BY 4.0)를 Fog of War Labs가 자세를 바꾸고 애니메이션을 만들어 스프라이트 프레임으로 렌더한 것입니다. 애니메이션: `idle` 8프레임(떠 있기), `walk` 16(비행), `attack` 10(동작 프레임 4, 불 뿜기), `death` 10(지면으로 떨어짐, 마지막 프레임 유지). 지면 위 1.41 m에 떠 있습니다(원본 공중 유닛 그림자의 42 SD 픽셀). 방향 17개, 층은 color, mask, shadow, `bright`, `normal`, `specular`, `ao_depth`. 같은 모델의 초상화 클립, 와이어프레임 스트립, 커맨드 아이콘도 있습니다. 불 뿜기(`fire/`, emissive 층이 있는 6프레임)는 모델의 일부가 아닙니다. 불 뿜기의 각 프레임은 공격 프레임 하나와 함께 재생되며(같은 길이), 오버레이는 유닛 위에 그려집니다.

CC BY 4.0은 출처 표시, 라이선스 링크, 변경 사항 표시를 요구합니다. 프레임이나 jar를 받는 사람은 같은 조건으로 받습니다. `NOTICE.txt`에는 저작자 출처 표시 줄, 변경 사항, "not endorsed"가, `license/CC-BY-4.0.txt`에는 라이선스 전문이 있습니다. 빌드가 둘을 jar의 `third-party/myriarch/` 아래에 묶고, 매니페스트의 `license` 줄이 출처를 밝힙니다. 이 프레임이 든 jar를 공개할 때는 이 셋을 모두 유지합니다.

#### 그림 폴더와 라이선스 (`third-party/unity-chan/`)

| 파일 | 내용 |
|---|---|
| `metadata.json` | 베이크된 프레임의 설명(아래). 변환의 입력이며 jar에는 없음 |
| `hd/<animation>/dDD_fFF*.png` | 애니메이션 `idle`, `walk`, `attack`, `death`의 베이크된 HD 프레임. 변환의 입력이며 jar에는 없음 |
| `NOTICE.txt` | 표기 "(c) Unity Technologies Japan/UCL", 라이선스, 무엇을 파생했는지, 소총의 출처, "not official". jar에 있음 |
| `license/` | Unity-chan License 3.0 조항(영어와 일본어 PDF, 일본어 문서가 우선), 라이선스 표기 규칙(`03Indication of License_EN_UCL2.02.pdf`), 라이선스 로고, 소총에 대한 메모(`THIRD-PARTY-GUN.txt`)와 그 CC0 전문. jar에 있음 |

라이선스가 자신과 jar를 받는 플레이어에게 요구하는 것:

- 캐릭터 그림은 Unity-chan License 3.0(UCL)에 따른 (c) Unity Technologies Japan/UCL입니다. 프레임이나 이로 만든 모드 jar를 받는 사람은 같은 라이선스로 받습니다. jar의 플레이어와 그들이 다시 전달하는 사람도 그림에 대해 UCL 조항을 따라야 합니다. `NOTICE.txt`와 `license/`를 jar에 유지하고(빌드가 `third-party/unity-chan/` 아래에 묶음), 매니페스트의 표기를 유지하며, 작품을 공식이거나 Unity Technologies Japan이 승인한 것처럼 보이게 하지 않습니다. 이 프레임이 든 jar를 공개하기 전에 `license/`의 조항을 읽습니다. 표기 규칙은 표기 PDF에 있습니다.
- 캐릭터가 든 소총은 Quaternius의 Ultimate Guns Pack에 있는 모델 AssaultRifle2_1이며 CC0 1.0(퍼블릭 도메인 기증)입니다. 조건은 없고, `NOTICE.txt`와 매니페스트의 출처 표시는 예의로 둔 것입니다.
- 저장소와 jar에는 렌더한 이미지만 있습니다. Unity-chan의 모델, 텍스처, 애니메이션 파일은 없고, 스타크래프트: 리마스터 파일도 없습니다 (목소리와 무기 소리는 플레이어의 설치본에서 참조로 가져옴).
- 다른 그림을 쓸 때: 라이선스가 모드와 파생 이미지의 배포를 허용하는지 확인하고, 라이선스 파일과 `NOTICE.txt`와 함께 자기 `third-party/<name>/` 폴더에 넣고, `jar`가 Unity-chan에 하듯 이를 jar에 묶고, 라이선스가 요구하는 표기를 매니페스트에 보여 줍니다.

#### 프레임 베이크 (저장소에 없음)

3D 모델과 베이크 도구는 저장소에 없으며, 빌드에도 필요 없습니다. 빌드는 체크인된 프레임에서 시작합니다. 자신의 캐릭터를 쓰려면 어떤 도구로든 같은 형식 `sc4j-bake-intermediate 1`, 곧 `convertArt`의 입력으로 렌더합니다.

- `metadata.json`: `"format": "sc4j-bake-intermediate 1"`, `"quality"`(`"hd"` 또는 `"hd2"`. 프레임을 베이크한 품질, 곧 세트의 베이크 품질), `"directions": {"count": 17}`, `"animations"`. `"animations"`는 프레임 세트 순서의 애니메이션 객체이며, 각각 `frameCount`, `frameSize` `{"<quality>": [width, height]}`, `pivot` `{"<quality>": [x, y]}`(프레임 왼쪽 위 모서리에서 본 유닛 아래 지면 점. 크기와 기준점은 텍스처 배율의 배수이며 HD는 4, HD2는 2. 애니메이션의 모든 프레임과 층에서 같음), `playOrder`(프레임 인덱스), `durationMs`(프레임마다 하나), `loop`, `holdLastFrame`, emissive 층이 있는 프레임에 `emissiveFrames`, 유닛이 동작하는 프레임에 `actionFrames`가 있습니다. 변환기는 이 필드만 읽습니다. 나머지는 프레임을 만든 방법의 기록입니다. SC:R Crossover의 애니메이션 이름은 `idle`, `walk`, `attack`, `death`입니다(유닛의 스크립트가 이 이름을 요청하고, 공격의 첫 동작 프레임에서 공격합니다).
- 애니메이션, 방향 `DD`, 프레임 `FF`마다 `<quality>/<animation>/`에 PNG 파일: 방향 17개, `00` 북, `08` 동, `16` 남, 시계 방향으로 11.25도 간격(렌더러가 이를 방향 17..31로 좌우 반전하므로, 빛과 모양은 반전해도 될 만큼 대칭이어야 함). SD 픽셀당 HD는 4, HD2는 2픽셀(SC:R Crossover: 미터당 160 또는 80픽셀, Unity-chan은 화면에서 원본 Goliath 정도의 키, 카메라는 42도 아래를 보는 직교 투영). 파일: `dDD_fFF.png` 색(RGBA, straight alpha), `_mask`(팀 색 영역, 255 = 완전), `_shadow`(그림자의 알파를 가진 검정), 그리고 HD 조명 렌더러의 조명 층: `_bright`(조명 없는 albedo), `_normal`(화면 공간 노멀, x는 빨강, y는 알파, -1..1을 0..255로, +x 오른쪽, +y 위), `_specular`(RGB 세기, 또는 회색 채널 하나), `_ao_depth`(초록: 앰비언트 오클루전, 알파: 지면 위 높이), `_emissive`(RGB 발광, `emissiveFrames`의 프레임에만). 변환기는 렌더러가 읽지 않는 채널(노멀의 초록과 파랑, ao_depth의 빨강과 파랑)을 지우므로, 베이크는 파일을 작게 하려고 그 채널을 0으로 저장해도 됩니다.
- 조명 데이터는 설치된 유닛의 범위 안에 둡니다. 그렇지 않으면 HD 조명 모드가 이 유닛을 원본 유닛보다 강하게 음영 처리합니다. 렌더러에는 앰비언트 항이 없으므로, 강한 노멀 맵은 딱딱하고 어두운 면을 만듭니다. SC:R Crossover의 베이크는 모든 노멀이 보는 쪽으로 기우는 정도를 절반으로 줄이고(그러면 조명을 받은 밝기와 퍼짐이 조명 없는 색 프레임과 맞음), specular를 41(원본 Marine의 가장 낮은 값) 이상으로, 깊이 (`_ao_depth` 알파)를 약 0.45 아래로 둡니다(원본 Goliath는 0.37..0.5).
- `_bright`는 렌더러가 lambert 항으로 비추는 색입니다. 어두운 기본색 위의 광택이나 금속 표면으로 모습이 나오는 모델(Myriarch: 기본색 약 0.1)은 `_bright`가 기본색이면 조명을 받았을 때 어둡고 얼룩져 보입니다. 이 모델의 베이크는 색 프레임을 렌더러의 방향광(`_normal`의 lambert에 AO를 곱한 값, 최소 0.5)으로 나누고 0.88을 곱합니다. 그래서 조명을 받으면 조명 없는 프레임처럼 보입니다(조명 받은 밝기 약 1.07배, 원본 Ultralisk는 1.01).
- `bright`, `normal`, `specular`, `ao_depth` 네 층이 없으면 HD 조명 모드에서 유닛을 조명 없이 그립니다(팀 색은 유지). SD와 일반 HD 모드는 색, 마스크, 그림자만 씁니다.
- 인터페이스 그림, 품질 폴더 `sd`, `hd2`, `hd`마다: `portrait/<quality>/Fid00..03.webm`(대기 클립)과 `Tlk00..02.webm`(말하기 클립), 설치된 초상화 클립의 형식(WebM VP9, 초당 10프레임. SD 60 x 56, HD2 224 x 240, HD 448 x 480). `wireframe/<quality>/wirefram.png`, 정사각형 셀의 스트립(셀 0..3은 손상 부위의 흰 마스크, 4와 5는 보호막 윤곽. SD 384 x 64, HD2 1024 x 128, HD 2048 x 256)과 `wireframe/sd/grpwire.png`, `tranwire.png`(192 x 32). `icon/<quality>/cmdicon.png`, 회색 커맨드 아이콘(SD 32, HD2 64, HD 128 정사각형). 변의 길이는 4의 배수입니다.

#### 그림 변환 (`convertArt`)

빌드는 그림 변환기 `ArtConverter`(`src/convert/java`)로 모든 그림 세트의 베이크된 프레임을 모드가 읽는 파일로 변환합니다. `jar`가 이를 실행하고 그 출력을 `third-party/<set>/graphics/`, `interface/`, `portrait/` 아래에 묶습니다. Unity-chan의 경우:

```powershell
.\gradlew.bat convertArt
```

`build/converted-art/third-party/unity-chan/graphics/`에 씁니다.

```powershell
Get-ChildItem -Name build\converted-art\third-party\unity-chan\graphics
```

```text
animations.json
main-hd.anim
main-hd2.anim
sd-colors.png
sd-masks.png
shadow-hd.anim
shadow-hd2.anim
```

- `main-hd.anim`과 `main-hd2.anim`: 유닛의 HD 프레임(배율 4)과 HD2 프레임(배율 2, 절반 해상도). 방향과 프레임마다 프레임 하나(425 = 프레임 25개 x 방향 17개, 애니메이션은 메타데이터 순서), 층은 `diffuse`(색), `bright`, `teamcolor`(마스크), `emissive`, `normal`, `specular`, `ao_depth`, DXT5 압축. HD2로 베이크한 세트는 `-hd2` 파일만 받습니다(Myriarch: 748프레임, `emissive` 층 없음. 그 불 뿜기는 102프레임, `emissive` 있음). 각 층은 프레임을 행으로 늘어놓은 아틀라스 하나이며, 너비는 HD 텍셀 2048(HD2는 1024)이고, 높이가 16384 텍셀을 넘게 될 때마다 너비를 두 배로 합니다. `normal`과 `ao_depth`는 설치된 프레임의 채널 배치를 따릅니다. 빨강과 알파(노멀 x와 y), 초록과 알파(AO와 깊이)만 남고 다른 채널은 검정입니다(DXT5 색 블록은 빨강, 초록, 파랑이 단계 인덱스 하나를 공유하므로, 노멀 x 옆의 다른 값은 조명 모드에서 블록으로 보임). `diffuse`와 `bright`의 팀 색 영역은 밝기에 따라 밝은 회색으로 바뀌어, 렌더러가 플레이어 색으로 물들입니다. 각 프레임은 보이는 픽셀로 잘라 유닛 위치를 둘러싼 캔버스 하나(세트의 SD 캔버스)에 놓고, 지면 점은 그보다 세트의 지면 오프셋만큼 아래에 둡니다(Unity-chan: 9 SD 픽셀, 선택 원 위치).
- `shadow-hd.anim`과 `shadow-hd2.anim`: 그림자 이미지(검정, 베이크된 그림자의 알파를 두 배로).
- `sd-colors.png`와 `sd-masks.png`: 트루컬러 SD 프레임(베이크된 프레임을 SD로 축소), 프레임마다 캔버스 하나(Unity-chan 128 x 128, Myriarch 252 x 252), 그리고 그 팀 색(빨강)과 그림자(초록) 마스크.
- `animations.json`: 애니메이션마다 첫 프레임 세트, 프레임 수, 재생 순서, 게임 프레임 단위 대기(길이를 가장 빠른 게임 속도인 42 ms로 나눈 값, 최소 1), 루프, 유지, 동작 프레임.
- `interface/cmdicon-<quality>.dds.grp`, `interface/wirefram-<quality>.dds.grp`, `interface/grpwire-sd.dds.grp`, `interface/tranwire-sd.dds.grp`: 커맨드 아이콘과 와이어프레임 스트립을 DDS 프레임 하나(DXT5)의 시트로 만든 것. 설치된 `cmdicons.dds.grp`와 `wirefram.dds.grp`의 형식이며, 시트의 버전 워드에 텍스처 배율(1, 2, 4)이 있습니다. `portrait/<quality>/`에는 클립이 그대로 들어 있습니다.

베이크된 PNG와 `metadata.json`은 jar에 들어가지 않습니다. 변환기의 테스트가 출력을 메타데이터와 jar 내용(라이선스 파일은 있고 베이크된 프레임은 없음)에 대조합니다.

```powershell
.\gradlew.bat test --tests org.fogofwarlabs.sc4j.scrcrossover.convert.ArtConverterTest
```

#### SD 프레임은 모드를 불러올 때 만든다

SD 프레임(GRP 파일)에는 설치본 타일셋 팔레트의 팔레트 인덱스가 들어 있습니다. 팔레트는 설치본 데이터이며 저장소와 jar에 둘 수 없으므로, jar에는 트루컬러 SD 이미지가 있고 `ScrCrossoverArtFiles`가 모드가 처음 읽을 때 그 이미지와 설치된 타일셋 팔레트 8개로 각 그림 세트의 GRP 파일 둘 `main.grp`와 `shadow.grp`를 만듭니다. 알파가 128 이상인 픽셀을 그립니다. 팀 마스크가 128 이상인 곳은 밝기에 따라 팀 색 인덱스 8..15가 되고, 나머지는 8개 팔레트 모두에서 같은 색을 가진 팔레트 인덱스 16..247 중 가장 가까운 색이 됩니다(그래서 유닛이 어느 타일셋에서나 같아 보임). 그림자 마스크가 128 이상인 곳에 그림자 픽셀을 둡니다. 같은 설치본은 항상 같은 프레임을 줍니다. 이 작업은 설치본 데이터를 불러올 때마다 한 번 합니다.

#### 유닛 확인

유닛을 플레이하는 테스트에는 설치본이 필요합니다(Gradle 속성 `sc4j.data`, 3절).

```powershell
.\gradlew.bat integrationTest --tests org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverUnitUtilsTest
```

테스트는 `scr-crossover.jar`에서 복제 모드 자신의 클래스를 실행합니다. 등록(값, 참조, 훈련 버튼, 이미지, 스프라이트, flingy 항목, 스크립트, 설치된 팔레트로 만든 SD 프레임, HD와 HD2 프레임), Barracks가 유닛을 훈련하는 리플레이를 따로 실행한 두 복제 모드에서 기록하고 읽고 재생해 프레임별 해시가 같은지, 반전된 두 방향으로 걷고 자기 애니메이션으로 싸우고 죽는 유닛, 걷고 싸우는 동안 긴 커맨드 열을 따르는 Marine과의 그룹, jar 안의 인터페이스 그림입니다. 직접 보려면 런처에서 복제 모드를 시작하고(5절) Barracks를 지어 U를 누릅니다.

`ScrCrossoverArtFilesTest`는 유닛으로 등록되었든 아니든 모든 그림 세트의 SD 프레임이 설치된 팔레트로 만들어지는지 확인합니다(프레임마다 세트 크기의 캔버스 하나, 팀 색과 공유 유닛 색만, 그림자 하나).

## 3. 빌드

```powershell
.\gradlew.bat jar
```

`build/libs/scr-crossover.jar`를 빌드합니다(먼저 `cloneScrMod`와 `convertArt`를 실행). SC:R Crossover의 테스트는 jar를 `scr.jar`와 비교하고(오버레이의 파일만 다르고, 추가 항목은 SC:R Crossover 자신의 클래스, 서비스 파일, `third-party/` 아래의 그림이며, 매니페스트에는 자기 id가 있음), 오버레이와 잠금, 그림 변환, 127.0.0.1에서의 LAN과 온라인 거부, 두 모드의 런처 목록을 확인합니다.

```powershell
.\gradlew.bat test
```

스타크래프트: 리마스터 설치본이 필요한 테스트는 `integrationTest`와, 설치본을 가리키는 Gradle 속성 `sc4j.data`로 실행합니다.

## 4. `mods/`에 넣기

모드는 jar를 SC4J 설치본의 `mods/` 폴더(`<install>/mods/`)에 넣어 설치합니다. 복제 모드에는 그곳의 `scr.jar`가 필요 없습니다. 엔진 체크아웃이 이 체크아웃 옆(`../sc4j`)에 있으면 그 개발용 설치본은 `../sc4j/build/dev-install/`입니다.

```powershell
..\sc4j\gradlew.bat -p ..\sc4j devInstall
.\gradlew.bat installMod
```

엔진의 `devInstall`이 개발용 설치본(런처, 런타임, `mods/demo.jar`)을 만들고, `installMod`가 `scr-crossover.jar`를 그 `mods/`에 복사합니다. 설치본이 없으면 `installMod`는 실패합니다. 다른 설치본에는 `-Psc4j.install=<설치본 폴더>`를 붙입니다. `userdata/` 아래에는 아무것도 쓰지 않습니다. 복제 모드의 설정은 런처가 저장합니다(5절).

## 5. 런처에서 실행

런처는 `mods/`의 모든 jar를 상태와 함께 보여 줍니다. 창 없이:

```powershell
..\sc4j\build\dev-install\launcher\bin\sc4j-launcher.bat --list
```

SC:R 모드도 설치한([sc4j-scr](https://github.com/fogofwarlabs/sc4j-scr)의 `installMod`) 개발용 설치본이고 스타크래프트: 리마스터가 설치된 컴퓨터라면 다음을 출력합니다.

```text
Mods in <엔진 체크아웃>\build\dev-install\mods
READY   demo                 demo.jar
READY   scr-crossover        scr-crossover.jar
READY   scr                  scr.jar
```

두 모드 모두 폴더 설정 `gameFolder`를 같은 공유 이름(`"shared": "starcraft-remastered"`)으로 선언하므로, 스타크래프트: 리마스터 폴더 하나를 `userdata/shared-settings.json`에 저장해 함께 씁니다. 첫 시작 때 Windows가 게임을 기록한 곳(제거 항목 `StarCraft`, 그다음 프로그램 폴더 아래의 `StarCraft`)에서 찾습니다. 찾지 못하면 폴더를 한 번 설정할 때까지 두 모드 모두 `value.required (gameFolder): A value is required.`와 함께 `WARNING`입니다. 텍스트 언어는 모드마다 자기 설정 파일 (`userdata/settings/scr-crossover.json`, SC:R 모드의 `scr.json` 옆)에 둡니다. 플레이하려면 런처를 엽니다.

```powershell
..\sc4j\build\dev-install\launcher\bin\sc4j-launcher.bat
```

(또는 `..\sc4j\gradlew.bat -p ..\sc4j runDevLauncher`). "SC:R Crossover"를 고르고, 스타크래프트: 리마스터 폴더(`.build.info`와 `x86_64/StarCraft.exe`가 있는 폴더)와 텍스트 언어를 확인하고 시작합니다. 런처는 언어를 `scr-crossover.json`에, 폴더를 `shared-settings.json`에 저장하고, `scr-crossover.jar`, `userdata/settings/scr-crossover.json`, `userdata/mods/scr-crossover/`, `userdata/logs/scr-crossover/`로 런타임을 시작합니다.

## 6. 정체성: 복제 모드가 함께 플레이하는 상대

런타임은 엔진 버전, 모드 id, 모드 버전, jar 내용으로 모드 정체성을 계산합니다. 플레이어는 정체성이 같을 때만 함께 플레이할 수 있습니다. 그래서:

- 온라인(Beacon.Link): 복제 모드는 SC:R 모드의 방을 목록에 보여 주지 않으며, 그런 방에 참가하면 "another mod" 사유로 거부됩니다(먼저 모드 id가 다름).
- LAN: 오버레이의 `lan-lobby.properties`에 있는 로비 버전으로 거부됩니다(원본 SC:R LAN에는 모드 id가 없음).
- 복제 모드의 리플레이와 저장 게임은 복제 모드의 것입니다(자기 저장 폴더). 다른 플레이어에게는 같은 jar를 줍니다.

jar를 바꿀 때마다 정체성이 새로 생깁니다. 함께 플레이하려면 플레이어들이 정확히 같은 jar를 가져야 합니다.
