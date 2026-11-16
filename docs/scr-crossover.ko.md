# SC:R 복제 모드 가이드

[English](scr-crossover.md) | **한국어**

SC:R 모드로 자신의 모드를 만드는 방법: 복제하고, 파일 몇 개를 바꾸고, jar 하나를 빌드해 `mods/`에 넣고, 런처에서 시작합니다. SC:R Crossover(이 저장소, 모드 id `scr-crossover`)가 모든 단계의 예시입니다. 처음부터 만드는 모드, 모드 API, 결정성, 네트워크 플레이는 [모드 제작 가이드](https://github.com/fogofwarlabs/sc4j/blob/main/docs/mod-guide.ko.md#18-기존-모드-복제)와 [API 정책](https://github.com/fogofwarlabs/sc4j/blob/main/docs/api-policy.ko.md)에 있습니다.

복제 모드는:

- SC:R 모드 소스(`scr/scr-data` ... `scr/scr-client`의 `src/main`)에서 파일 몇 개를 바꾸고 자기 클래스를 더해 `scr-crossover.jar` 같은 jar 하나로 빌드한 것입니다.
- 자기 모드 id, 설정, 저장 폴더, 로그를 가진 별도 모드이며, SC:R 모드 플레이어와 만나지 않습니다(6절).
- 스타크래프트: 리마스터 파일이 없는 jar입니다. 게임 데이터는 플레이어의 설치본에서 읽습니다. 설치본의 파일을 jar나 저장소에 넣지 않습니다.

필요한 것: 서브모듈 `scr`와 함께 받은 이 저장소(`git clone --recurse-submodules`), JDK 25, 플레이하려면 스타크래프트: 리마스터 설치본. 명령은 저장소 루트의 PowerShell에서 실행합니다(Linux나 macOS에서는 `./gradlew`).

1. [복제](#1-복제)
2. [변경](#2-변경)
3. [빌드](#3-빌드)
4. [`mods/`에 넣기](#4-mods에-넣기)
5. [런처에서 실행](#5-런처에서-실행)
6. [정체성: 복제 모드가 함께 플레이하는 상대](#6-정체성-복제-모드가-함께-플레이하는-상대)

## 1. 복제

### 복사 단계

`cloneScrMod`는 복제 도구 `ScrModClone`(`src/clone/java`)을 실행하며, 이 도구는:

1. 오버레이를 잠금과 대조합니다([SC:R 모드가 바뀔 때](#scr-모드가-바뀔-때)).
2. `scr/mod.properties`의 `modules`에 있는 모든 모듈의 `scr/<module>/src/main`을 `build/clone/<module>/src/main`에 복사합니다.
3. 그 위에 `overlay/`를 복사합니다. 각 파일은 같은 경로의 복제 파일을 대신하거나 추가됩니다.

```powershell
.\gradlew.bat cloneScrMod
Select-String -Path build\clone\scr-client\src\main\resources\sc4j-mod.json -Pattern '"id"'
```

```text
build\clone\scr-client\src\main\resources\sc4j-mod.json:3:  "id": "scr-crossover",
```

복제본은 SC:R 패키지 이름(`org.fogofwarlabs.sc4j.scr...`)을 유지하며, 모드마다 자체 클래스 로더에서 실행됩니다. 자기 클래스는 자기 패키지에 둡니다(SC:R Crossover: `org.fogofwarlabs.sc4j.scrcrossover`).

### 빌드 파일

| `build.gradle`의 부분 | 하는 일 |
|---|---|
| 소스 세트 `clone` | 복제 도구(`ScrModClone`, `CloneOverlay`). 빌드할 때만 |
| 태스크 `cloneScrMod` | 복사 단계. SC:R 소스, 오버레이 파일, 잠금이 바뀌면 다시 실행 |
| 태스크 `overlayDiff`, `acceptOverlay` | 오버레이 아래 SC:R 모드 변경을 보여 주고 기록([2절](#scr-모드가-바뀔-때)) |
| 소스 세트 `mod` | `src/mod/java`, `src/mod/resources`, `build/clone`의 모든 모듈 폴더 |
| `mod`의 의존성 | `sc4j-base`와 `sc4j-engine`(런타임이 제공), SC:R 모듈의 서드파티 라이브러리 |
| `mod`의 컴파일러 인자 | `scr/mod.properties`의 `compilerArgs`. 바꾸지 않은 SC:R 클래스는 `scr.jar`와 같음 |
| 소스 세트 `convert` | 그림 변환기 `ArtConverter`. 빌드할 때만 |
| 태스크 `convertArt` | `third-party/<set>`의 그림 세트를 `build/converted-art` 아래 그래픽 파일로 변환 |
| 태스크 `jar` | `mod`, 변환된 그림, 각 그림 세트의 `NOTICE.txt`와 `license/`를 `build/libs/scr-crossover.jar`로 재현 가능하게 묶음. 중복 항목이면 실패 |
| 테스트 | `src/test`. 빌드된 jar를 읽음 |

### 자신의 모드

1. 저장소 `my-mod`를 만들고, SC:R 모드를 서브모듈 `scr`로 추가하고(`git submodule add https://github.com/fogofwarlabs/sc4j-scr.git scr`), SC:R Crossover의 Gradle 래퍼, `settings.gradle`, `gradle.properties`, `build.gradle`, `src/clone/java`를 복사하고, `scr/scr-client/src/main/resources/sc4j-mod.json`을 `overlay/scr-client/src/main/resources/`에 복사합니다.
2. `settings.gradle`에서 `rootProject.name`을 `my-mod`로 바꿉니다.
3. `build.gradle`에서 `sc4jMod`의 `id`를 자기 모드 id로 바꾸고 설명을 바꿉니다. 그림과 테스트 블록은 빼거나 다시 씁니다.
4. `sc4j-mod.json`에 자기 id, 이름, 설명을 쓰고 `.\gradlew.bat acceptOverlay`를 한 번 실행합니다.

## 2. 변경

### 오버레이

`overlay/<module>/src/main/...` 아래 파일은 같은 경로의 복제 파일을 대신하거나 추가됩니다. 복제한 모듈의 `src/main`만 오버레이할 수 있습니다. SC:R 모드가 오버레이 파일을 바꿀 때마다 빌드가 멈추므로, 다음 순서로 고릅니다.

1. SC:R 모드가 읽는 작은 리소스 대신하기(매니페스트, `lan-lobby.properties`).
2. SC:R 모드의 확장 지점으로 호출되는 `src/mod`의 자기 클래스와 리소스.
3. SC:R 모드 소스 대신하기.

SC:R Crossover는 리소스 둘을 대신하고 콘텐츠 확장 하나를 추가합니다.

### SC:R 모드가 바뀔 때

`overlay.lock`에는 오버레이 파일마다 `<hash>  <path>` 한 줄이 있습니다. 기준이 된 SC:R 모드 파일의 SHA-256(줄 끝 형식은 세지 않음) 또는 `added`입니다. `overlay-base/`에는 그 파일들의 복사본이 있습니다. 둘 다 `acceptOverlay`가 씁니다. SC:R Crossover의 잠금:

```text
<SHA-256 of the SC:R mod's sc4j-mod.json>  scr-client/src/main/resources/sc4j-mod.json
<SHA-256 of the SC:R mod's lan-lobby.properties>  scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties
```

SC:R 모드가 이 파일 중 하나를 바꾸면 빌드가 멈춥니다.

```text
> The SC:R mod changed scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties after the overlay file was made
  from it: see :overlayDiff, merge the change into the overlay file by hand, then run :acceptOverlay.
```

1. `.\gradlew.bat overlayDiff`가 SC:R 모드의 변경(이전 기준과 새 기준 비교)과 자신의 변경(이전 기준과 오버레이 파일 비교)을 unified diff로 출력합니다.
2. 변경을 오버레이 파일에 손으로 병합하거나 자신의 것을 유지합니다.
3. `.\gradlew.bat acceptOverlay`가 새 기준을 기록합니다.

오버레이 파일이 잠금에 없거나 잠금이 없는 오버레이 파일을 가리켜도 빌드가 멈춥니다.

### 모드 정체성 (`overlay/scr-client/src/main/resources/sc4j-mod.json`)

```json
  "id": "scr-crossover",
  "name": {"en": "SC:R Crossover", "ko": "SC:R Crossover"},
```

모드 id는 설정 파일(`userdata/settings/scr-crossover.json`), 저장 폴더(`userdata/mods/scr-crossover/`), 로그 폴더의 이름이 되며, 온라인 모드 정체성의 첫 부분입니다(6절). 다른 사람이 쓰지 않는 id를 고릅니다(소문자, 숫자, 하이픈). `entry`, `engine`, `version`, `settings`는 SC:R 모드의 것을 유지합니다. `license` 줄과 각 `description`의 끝에는 그림 표기가 들어갑니다([아래](#라이선스-표기-overlayscr-clientsrcmainresourcessc4j-modjson-안)).

### LAN 참가 버전 (`overlay/scr-net/src/main/resources/org/fogofwarlabs/sc4j/scr/net/lan/lan-lobby.properties`)

```properties
joinVersion=0xD005
```

SC:R 모드는 `joinVersion=5`를 씁니다. 원본 SC:R LAN 게임에는 모드 id가 없으며, 호스트는 원본 SC:R 호스트처럼 다른 로비 버전의 참가 커맨드를 거부합니다(`LanLobby.JOIN_VERSION`, 이 파일에서 읽음). 자신의 16비트 수를 고릅니다. 같은 수를 고른 복제 모드끼리는 서로의 게임을 봅니다. 추가 유닛이나 그 값이 바뀔 때마다 바꿉니다. 리플레이와 LAN 게임에는 그 id와 값이 들어 있습니다.

### 콘텐츠 확장 (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverContentExtension.java`)

SC:R 모드는 jar가 밝힌 `ScrContentExtension` 구현(`org.fogofwarlabs.sc4j.scr.game.extension` 패키지)을 호출합니다.

- `extendInstalledData(GameData installed)`: 설치본 데이터를 불러온 뒤, 거기서 게임을 만들기 전에 한 번. 추가 유닛 타입은 여기서 등록합니다. 첫 게임 뒤에는 `GameData.getAddedUnitTypes()`가 `add`를 `IllegalStateException`으로 거부합니다.
- `extendGameData(GameData game)`: 라이브든 리플레이든 게임마다 한 번, 게임이 설치본 테이블을 복사한 뒤 Use Map Settings 맵이 유닛 설정을 적용하기 전. 여기서 정한 값은 데이터 파일을 바꾼 것처럼 동작합니다.

SC:R Crossover의 확장은 `ScrCrossoverUnitUtils.registerUnit`과 `ScrCrossoverValueUtils.applyValues`를 호출합니다. 서비스 파일 `src/mod/resources/META-INF/services/org.fogofwarlabs.sc4j.scr.game.extension.ScrContentExtension`이 이를 밝힙니다.

```text
org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverContentExtension
```

### 값 (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverValueUtils.java`)

Terran Marine은 미네랄 45에 300프레임 동안 훈련하고, Gauss Rifle은 피해 7을 줍니다. `extendGameData`가 이 값을 모든 게임의 테이블에 씁니다.

### 자체 그래픽을 가진 유닛 추가

SC:R Crossover는 Terran **Unity-chan**과 Hive가 변태하는 용인 Zerg **Myriarch**를 이 순서로 `extendInstalledData`에서 등록합니다(id 233과 234). 각각 등록 클래스와 라이선스 파일이 있는 그림 세트가 있고, 공유 부분을 씁니다: `ScrCrossoverUnitGraphics`(그림 세트의 이미지, 스크립트, 스프라이트, flingy 항목, 인터페이스 그림), `ScrCrossoverArtFiles`(그림 파일), `convertArt` 단계, 매니페스트의 라이선스 표기.

#### 유닛 (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverUnitUtils.java`)

`registerUnit`은 확장 API(`scr-data`의 `org.fogofwarlabs.sc4j.scr.content` 패키지)를 씁니다.

- `new ScrAddedUnitType.Builder(new UnitType(NAME), NAME, ScrUnits.TERRAN_MARINE, units)`는 Terran Marine에서 시작하고, `setValue`로 체력 50, 미네랄 75, 가스 25, 360프레임, 보급 1을 정합니다. 기반 타입은 하위 유닛이 없는 훈련 유닛이어야 합니다.
- 설치본에서 참조: Terran Ghost의 무기와 그 소리, Terran Medic의 목소리와 죽음 소리, Marine의 요구 조건(`setRequirementType`, Barracks).
- Terran Goliath의 크기(`ScrCrossoverUnitUtils.SIZE_TYPE`): 범위 16/16/15/15와 배치 상자 32 x 32(`setValue`), 스프라이트의 48픽셀 선택 원과 체력 막대 너비 34(`ScrAddedSprite.Builder.setValue`). 원은 스프라이트 위치보다 9픽셀 아래(그림 세트의 지면 오프셋)입니다. 충돌과 경로 탐색은 Goliath의 것이고, 크기 분류는 Marine의 것입니다.
- `setTrainButton(new ScrAddedUnitType.TrainButton(ScrUnits.TERRAN_BARRACKS, 5, 'u', "Train Unity-chan"))`: Barracks 버튼 위치 5, 단축키 U. 생산 건물 자신의 버튼이 쓰는 위치면 가려집니다.
- `gameData.getAddedUnitTypes().add(unit.build())`는 SC:R id를 반환합니다. 첫 추가 타입은 233입니다(none 228과 트리거 그룹 229..232 다음). 리플레이에는 이 id가 있으므로 같은 jar에서만 재생됩니다.
- 그래픽. 각 항목은 앞의 것을 참조합니다: 그림자 이미지(`ScrAddedImage`), 스크립트(`IscriptCode`를 쓰는 `ScrAddedScript`), 유닛 이미지, 스프라이트(`ScrAddedSprite`), flingy 항목(`ScrAddedFlingy`, `UnitsDat.FLINGY_TYPE`). 스크립트에는 `imgul`로 그림자를 만드는 대기 루프, 프레임마다 4픽셀 걷기, 무기 자체의 공격을 재생하는 지상과 공중 공격(근접은 `attackmelee`, 탄은 `attack`), 마지막 프레임을 유지하는 죽음이 있습니다. 프레임은 `ScrModGraphics.Builder(files, grpPath).setHdAnimation(..).setHd2Animation(..)`로 jar에서 가져오고, 프레임 번호, 재생 순서, 대기는 변환된 `animations.json`에서 읽습니다. 모든 애니메이션은 루프나 `end`로 끝납니다. 공격의 `ignorerest` 다음에는 대기 루프가 옵니다.
- jar의 인터페이스 그림: 초상화(`ScrModPortrait.Builder(files, sdIdle, sdTalking).setHd2(..).setHd(..).setIdleChances(85, 5, 5, 5)`, `graphics.addPortrait`, `UnitsDat.PORTRAIT`), 커맨드 아이콘(`ScrModSheetFrame`, `graphics.addCommandIcon`, `setCommandIcon`), 와이어프레임(`ScrModWireframe`, `graphics.addWireframe`, `setWireframe`). 없으면 `PORTRAIT` 값과 와이어프레임 타입(`setWireframeType`)의 것을 보여 줍니다.
- 컴퓨터 플레이어는 추가 유닛을 만들지 않습니다.

추가 유닛은 건물이나 하위 유닛을 기반으로 할 수 없고, 플레이어당 제한이 없으며, 저장 게임, EUD, 맵 유닛 설정은 이를 다루지 않습니다. 자체 무기(`ScrAddedWeapon`)와 요구 조건 스크립트(`ScrRequirementScript`, `setRequirementScript`)를 가질 수 있고 건물 변태의 결과가 될 수 있습니다(Myriarch).

#### 라이선스 표기 (`overlay/scr-client/src/main/resources/sc4j-mod.json` 안)

```json
  "license": "Unity-chan: © Unity Technologies Japan/UCL, Unity-chan License 3.0. Rifle: Quaternius, CC0 1.0. Myriarch: DRAGO by OSCAR CREATIVO, CC BY 4.0.",
```

각 `description`은 "Unity-chan: © Unity Technologies Japan/UCL (Unity-chan License 3.0)."(과 그 한국어 문장)으로 끝납니다. Unity-chan License는 표기를 작품과 함께 보여 줄 것을 요구하며, 런처의 모드 상세 정보가 설명과 `license` 줄을 보여 줍니다.

#### 그림 세트 (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverArtSet.java`)

그림 세트는 폴더 `third-party/<set>/`(베이크된 프레임, 인터페이스 그림, `NOTICE.txt`, `license/`)와, 이름, SD 캔버스, 지면 오프셋, 그림자 오프셋, 선택 원 오프셋, 베이크 품질을 주는 `ScrCrossoverArtSet` 상수입니다. 오버레이 이미지(Myriarch의 불 뿜기)는 유닛 폴더 안의, 그림자와 인터페이스 그림이 없는 세트입니다. `ArtConverter`가 모든 세트를 변환하고, `jar`가 라이선스 파일과 함께 `third-party/<set>/` 아래에 묶고, `ScrCrossoverArtFiles`가 SD 프레임을 만듭니다. 세트의 메서드(`getMainAnimation(quality)`, `getMainFrames()`, `getCommandIcon(quality)` 등)가 등록에 쓸 jar 경로를 줍니다.

| 세트 | 유닛 | SD 캔버스 | 지면 오프셋 | 그림자 오프셋 | 베이크 품질 | 프레임 파일 |
|---|---|---|---|---|---|---|
| `unity-chan` | Unity-chan | 128 | 9 | 0 | HD(미터당 160픽셀) | `main-hd.anim`, `main-hd2.anim`과 그림자의 것 |
| `myriarch` | Myriarch | 252 | 42 | 42 | HD2(미터당 80픽셀) | `main-hd2.anim`과 그림자의 것. HD도 이것으로 그림 |
| `myriarch/fire` | 불 뿜기(오버레이) | 252 | 0 | - | HD2 | `main-hd2.anim` |

- SD 캔버스: 최대 255, 4의 배수. 지면 오프셋: 이미지 위치에서 프레임 기준점까지 아래로의 SD 픽셀. 그림자 오프셋: 이미지 위치에서 그림자 이미지까지 아래로의 SD 픽셀(공중 유닛은 원본 공중 유닛의 `imgul` 그림자 `0 42`처럼 42).
- 큰 유닛을 HD2로 베이크하면 프레임 파일이 HD 크기의 4분의 1이 되며, HD에서도 같은 크기로 그립니다.
- 프레임 파일에는 베이크된 프레임에 `emissive` 층이 있을 때만 그 층이 있습니다.
- 오버레이가 있는 유닛 세트에는 `overlay-offsets.json`이 있습니다. 애니메이션, 프레임, 방향마다 오버레이 기준점(Myriarch의 입)을 이미지 위치로부터의 SD 픽셀로 적은 것입니다. 등록이 이를 `ScrAddedImage.Builder.setOverlayOffsets`에 넘기고, 공격의 `imgoluselo`가 그곳에 오버레이를 만듭니다.
- 세트 추가: 라이선스 파일과 `NOTICE.txt`가 있는 폴더를 `third-party/`에 넣고, `ScrCrossoverArtSet` 상수와 `ArtConverterTest`, `CloneArtScenario`의 세트 목록에 이름을 추가하고, 유닛을 등록합니다.

#### Myriarch (`src/mod/java/org/fogofwarlabs/sc4j/scrcrossover/ScrCrossoverMyriarchUtils.java`)

- 기반 타입 Zerg Guardian(비행, 공중 고도, 목소리, Zerg 비행 유닛 업그레이드), 특성 `UnitTraitFlags.BUILDING_MORPH_RESULT`(`setTrait`): Hive가 이 유닛으로 변태하며, 완료된 유닛은 Hive가 서 있던 곳에서 납니다.
- 버튼 `new ScrAddedUnitType.TrainButton(ScrUnits.ZERG_HIVE, 9, ScrAddedUnitType.ProductionAction.BUILDING_MORPH, 'm', text, Map.of("koKR", koreanText))`. 각 `ScrAddedUnitType.TrainButton.Text`에는 사용 가능 툴팁("Mutate into Myriarch")과 사용 불가 툴팁("Myriarch Requires:", "   Ultralisk Cavern", "   Defiler Mound")이 있습니다.
- 요구 조건 스크립트: `new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HIVE).requireNotBusy().requireCompleted(ScrUnits.ZERG_ULTRALISK_CAVERN).requireCompleted(ScrUnits.ZERG_DEFILER_MOUND).build()`.
- 값: 미네랄 400과 가스 400(시작할 때 차감), 2400프레임, 체력 2000, 방어력 3, 보급 8, 시야 10, 범위 30/25/29/24, 배치 상자 64 x 64, 수송 공간 255.
- 비행: Guardian의 flingy 항목(최고 속도 640, 가속도 27, 정지 거리 7585, 회전 속도 20). Hive의 선택 원과 체력 막대 너비. 원은 이미지 위치보다 16픽셀 아래.
- 불 뿜기: Firebat의 Flame Thrower에서 시작한 자체 무기(`gameData.getAddedUnitTypes().addWeapon(new ScrAddedWeapon.Builder(type, flameThrower, weapons)...)`, `weapons.dat` id 131). 공중과 지상 대상, 사거리 96, 피해 12 + Zerg 비행 유닛 공격 업그레이드당 1, 재사용 대기 30, 스플래시 반경 24/32/40. 동작 프레임이 입 위치에 `myriarch/fire` 세트의 오버레이를 만들고(`imgoluselo`) 앞쪽 32, 72, 112픽셀에 투사체를 발사합니다(`attkshiftproj`). 공격은 `gotorepeatattk` 뒤에 대기 루프로 이어지므로 유닛은 계속 떠 있습니다.
- 완료되면 Hive가 Myriarch가 됩니다. Larva는 부모를 잃고, Hive의 보급과 수량은 사라집니다. 취소하면 Hive가 돌아오고 4분의 3을 환불합니다.

`ScrCrossoverMyriarchTest`는 라이브 게임에서 변태, 비행, 불 뿜기를, 복제 모드 둘의 LAN 게임에서 변태와 비행을 재생합니다.

#### Myriarch 그림과 라이선스 (`third-party/myriarch/`)

OSCAR CREATIVO의 "DRAGO 3D MODEL FREE BY Oscar Creativo"(Sketchfab, CC BY 4.0)를 Fog of War Labs가 자세와 애니메이션을 바꿔 렌더한 것입니다: `idle` 8프레임, `walk` 16, `attack` 10(동작 프레임 4), `death` 10, 지면 위 1.41 m, 방향 17개, 그리고 초상화 클립, 와이어프레임 스트립, 커맨드 아이콘. 불 뿜기(`fire/`, emissive 층이 있는 6프레임)는 공격 프레임과 함께 유닛 위에 재생됩니다.

CC BY 4.0은 출처 표시, 라이선스 링크, 변경 사항 표시를 요구하며, 프레임이나 jar를 받는 사람에게도 적용됩니다. `NOTICE.txt`(출처, 변경 사항, "not endorsed")와 `license/CC-BY-4.0.txt`가 jar의 `third-party/myriarch/` 아래에 들어가고, 매니페스트의 `license` 줄이 출처를 밝힙니다. 이 프레임이 든 jar를 공개할 때는 이 셋을 유지합니다.

#### Unity-chan 그림과 라이선스 (`third-party/unity-chan/`)

| 파일 | 내용 |
|---|---|
| `metadata.json` | 베이크된 프레임의 설명(아래). jar에 없음 |
| `hd/<animation>/dDD_fFF*.png` | `idle`, `walk`, `attack`, `death`의 베이크된 HD 프레임. jar에 없음 |
| `NOTICE.txt` | 표기 "(c) Unity Technologies Japan/UCL", 라이선스, 파생 내용, 소총 출처, "not official". jar에 있음 |
| `license/` | Unity-chan License 3.0(영어와 일본어 PDF, 일본어 우선), 표기 규칙(`03Indication of License_EN_UCL2.02.pdf`), 로고, 소총 메모(`THIRD-PARTY-GUN.txt`)와 CC0 전문. jar에 있음 |

- 캐릭터 그림은 Unity-chan License 3.0에 따른 (c) Unity Technologies Japan/UCL이며, 프레임이나 이로 만든 jar를 받는 모든 사람에게 적용됩니다. `NOTICE.txt`, `license/`, 매니페스트 표기를 유지하고, 작품을 공식인 것처럼 보이게 하지 않으며, 공개 전에 `license/`의 조항을 읽습니다.
- 소총은 Quaternius의 Ultimate Guns Pack에 있는 AssaultRifle2_1이며 CC0 1.0입니다.
- 저장소와 jar에는 렌더한 이미지만 있습니다. Unity-chan의 모델, 텍스처, 애니메이션과 스타크래프트: 리마스터 파일은 없습니다.
- 다른 그림: 라이선스가 모드와 파생 이미지의 배포를 허용하는지 확인하고, 라이선스 파일과 `NOTICE.txt`와 함께 `third-party/<name>/`에 넣고, 요구되는 표기를 매니페스트에 보여 줍니다.

#### 프레임 베이크

3D 모델과 베이크 도구는 저장소에 없으며, 빌드는 체크인된 프레임에서 시작합니다. 자신의 유닛은 `sc4j-bake-intermediate 1` 형식으로 렌더합니다.

- `metadata.json`: `"format": "sc4j-bake-intermediate 1"`, `"quality"`(`"hd"` 또는 `"hd2"`), `"directions": {"count": 17}`, 프레임 세트 순서의 `"animations"`. 각 애니메이션에 `frameCount`, `frameSize` `{"<quality>": [width, height]}`, `pivot` `{"<quality>": [x, y]}`(왼쪽 위 모서리에서 본 지면 점. 크기와 기준점은 HD는 4, HD2는 2의 배수이며 모든 프레임과 층에서 같음), `playOrder`, `durationMs`(프레임마다 하나), `loop`, `holdLastFrame`, `emissiveFrames`, `actionFrames`. 다른 필드는 무시합니다. 스크립트는 애니메이션 `idle`, `walk`, `attack`, `death`를 요구하고 첫 동작 프레임에서 공격합니다.
- `<quality>/<animation>/`의 PNG: 방향 17개 `DD`(`00` 북, `08` 동, `16` 남, 시계 방향 11.25도 간격. 방향 17..31은 좌우 반전), SD 픽셀당 HD 4 또는 HD2 2픽셀(SC:R Crossover: 미터당 160 또는 80픽셀, 42도 아래를 보는 직교 카메라). 프레임 `FF`마다 `dDD_fFF.png` 색(RGBA, straight alpha), `_mask`(팀 색, 255 = 완전), `_shadow`(알파가 있는 검정), 그리고 HD 조명 모드용 `_bright`(조명 없는 albedo), `_normal`(x는 빨강, y는 알파, -1..1을 0..255로, +x 오른쪽, +y 위), `_specular`(RGB 또는 회색), `_ao_depth`(초록 AO, 알파 높이), `_emissive`(`emissiveFrames`에만). 읽지 않는 채널은 0이어도 됩니다.
- 조명 데이터는 설치된 유닛의 범위 안에 둡니다. SC:R Crossover는 노멀의 기울기를 절반으로, specular를 41 이상으로, 깊이를 약 0.45 아래로 둡니다. 어두운 광택 기본색의 모델(Myriarch)은 `_bright`를 색 프레임을 렌더러의 방향광(`_normal`의 lambert에 AO를 곱한 값, 최소 0.5)으로 나누고 0.88을 곱한 값으로 둡니다.
- `bright`, `normal`, `specular`, `ao_depth`가 없으면 HD 조명 모드에서 조명 없이 그립니다.
- 품질 폴더 `sd`, `hd2`, `hd`마다 인터페이스 그림: `portrait/<quality>/Fid00..03.webm`과 `Tlk00..02.webm`(WebM VP9, 초당 10프레임. SD 60 x 56, HD2 224 x 240, HD 448 x 480), `wireframe/<quality>/wirefram.png`(정사각형 셀: 0..3 손상 부위 마스크, 4와 5 보호막 윤곽. SD 384 x 64, HD2 1024 x 128, HD 2048 x 256), `wireframe/sd/grpwire.png`와 `tranwire.png`(192 x 32), `icon/<quality>/cmdicon.png`(회색. SD 32, HD2 64, HD 128). 변의 길이는 4의 배수입니다.

#### 그림 변환 (`convertArt`)

`ArtConverter`(`src/convert/java`)가 세트마다 `build/converted-art/third-party/<set>/`에 쓰고, `jar`가 `graphics/`, `interface/`, `portrait/`를 묶습니다.

```powershell
.\gradlew.bat convertArt
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

- `main-hd.anim`, `main-hd2.anim`: 방향과 프레임마다 프레임 하나(Unity-chan 425), 층 `diffuse`, `bright`, `teamcolor`, `emissive`, `normal`, `specular`, `ao_depth`, DXT5. 각 층은 너비 HD 텍셀 2048(HD2는 1024)의 아틀라스이며, 높이가 16384 텍셀을 넘게 되면 너비를 두 배로 합니다. HD2로 베이크한 세트는 `-hd2` 파일만 받습니다. `normal`은 빨강과 알파, `ao_depth`는 초록과 알파만 남깁니다. `diffuse`와 `bright`의 팀 색 영역은 밝기에 따라 밝은 회색이 됩니다. 프레임은 잘라서 세트의 SD 캔버스에 놓고, 지면 점은 유닛 위치보다 지면 오프셋만큼 아래입니다.
- `shadow-hd.anim`, `shadow-hd2.anim`: 그림자 이미지(검정, 베이크된 알파의 두 배).
- `sd-colors.png`, `sd-masks.png`: 프레임마다 캔버스 하나인 트루컬러 SD 프레임과 그 팀 색(빨강), 그림자(초록) 마스크.
- `animations.json`: 애니메이션마다 첫 프레임 세트, 프레임 수, 재생 순서, 게임 프레임 단위 대기(길이 / 42 ms, 최소 1), 루프, 유지, 동작 프레임.
- `interface/cmdicon-<quality>.dds.grp`, `wirefram-<quality>.dds.grp`, `grpwire-sd.dds.grp`, `tranwire-sd.dds.grp`: 설치된 `cmdicons.dds.grp`, `wirefram.dds.grp` 형식의 DXT5 한 프레임 시트. 버전 워드에 텍스처 배율(1, 2, 4). `portrait/<quality>/`에는 클립이 그대로 들어 있습니다.

```powershell
.\gradlew.bat test --tests org.fogofwarlabs.sc4j.scrcrossover.convert.ArtConverterTest
```

#### SD 프레임은 모드를 불러올 때 만든다

SD 프레임에는 jar에 둘 수 없는 설치본 타일셋 팔레트의 인덱스가 들어 있습니다. `ScrCrossoverArtFiles`는 모드가 처음 읽을 때 트루컬러 SD 이미지와 설치된 팔레트 8개로 세트마다 `main.grp`와 `shadow.grp`를 만듭니다. 알파가 128 이상인 픽셀을 그리고, 팀 마스크가 128 이상이면 밝기에 따라 팀 색 인덱스 8..15, 나머지는 8개 팔레트 모두에서 같은 색인 인덱스 16..247 중 가장 가까운 색이 됩니다. 그림자 마스크가 128 이상이면 그림자 픽셀입니다.

#### 유닛 확인

```powershell
.\gradlew.bat integrationTest --tests org.fogofwarlabs.sc4j.scrcrossover.ScrCrossoverUnitUtilsTest
```

`sc4j.data`가 필요합니다. 등록과 프레임, 두 번의 별도 실행에서 기록하고 재생한 리플레이의 프레임별 해시, 걷기, 전투, 죽음, 많은 커맨드를 따르는 그룹, 인터페이스 그림을 확인합니다. `ScrCrossoverArtFilesTest`는 모든 세트의 SD 프레임을 확인합니다. 게임에서는 Barracks를 지어 U를 누릅니다.

## 3. 빌드

```powershell
.\gradlew.bat jar
.\gradlew.bat test
```

`jar`는 `cloneScrMod`와 `convertArt` 뒤에 `build/libs/scr-crossover.jar`를 빌드합니다. `test`는 jar를 `scr.jar`와 비교하고(오버레이 파일, 자체 클래스, 서비스 파일, `third-party/`만 다름), 오버레이와 잠금, 그림 변환, 127.0.0.1에서의 LAN과 온라인 거부, 런처 목록을 확인합니다. 설치본이 필요한 테스트는 `integrationTest`와 `sc4j.data`로 실행합니다.

## 4. `mods/`에 넣기

모드는 jar를 `<install>/mods/`에 넣어 설치합니다. 그곳에 `scr.jar`는 필요 없습니다. 엔진 체크아웃이 `../sc4j`에 있으면:

```powershell
..\sc4j\gradlew.bat -p ..\sc4j devInstall
.\gradlew.bat installMod
```

`installMod`는 jar를 `../sc4j/build/dev-install/mods/` 또는 `-Psc4j.install=<폴더>`에 복사하며, 설치본이 없으면 실패합니다.

## 5. 런처에서 실행

```powershell
..\sc4j\build\dev-install\launcher\bin\sc4j-launcher.bat --list
```

SC:R 모드도 설치되어 있고 스타크래프트: 리마스터를 찾으면:

```text
Mods in <엔진 체크아웃>\build\dev-install\mods
READY   demo                 demo.jar
READY   scr-crossover        scr-crossover.jar
READY   scr                  scr.jar
```

두 모드는 폴더 설정 `gameFolder`를 함께 씁니다(`"shared": "starcraft-remastered"`, `userdata/shared-settings.json`에 저장). Windows가 게임을 기록한 곳에서 찾으며, 없으면 설정할 때까지 두 모드 모두 `value.required (gameFolder)`와 함께 `WARNING`입니다. 텍스트 언어는 모드마다 따로 둡니다(`userdata/settings/scr-crossover.json`).

런처를 열고(`..\sc4j\build\dev-install\launcher\bin\sc4j-launcher.bat` 또는 `..\sc4j\gradlew.bat -p ..\sc4j runDevLauncher`) "SC:R Crossover"를 골라 폴더와 언어를 확인하고 시작합니다.

## 6. 정체성: 복제 모드가 함께 플레이하는 상대

런타임은 엔진 버전, 모드 id, 모드 버전, jar 내용으로 모드 정체성을 계산하며, 정체성이 같은 플레이어끼리만 함께 플레이합니다.

- 온라인(Beacon.Link): 복제 모드는 SC:R 모드의 방을 보여 주지 않으며, 참가하면 "another mod"로 거부됩니다.
- LAN: `lan-lobby.properties`의 로비 버전으로 거부됩니다.
- 리플레이와 저장 게임은 복제 모드의 저장 폴더에 남습니다.

jar를 바꿀 때마다 정체성이 새로 생깁니다. 함께 플레이하려면 정확히 같은 jar가 필요합니다.
