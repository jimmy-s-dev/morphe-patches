# YouTube Music Android Auto browse lab

YouTube Music 9.15.51의 휴대폰 목록 요청을 Android Auto 탐색 화면에 연결하는 로컬 실험 패치다. 매니저 자체의 UI 변경이 아니다. 패치 선택 이름은 `Restore playlists in Android Auto`이며 기본 선택은 꺼져 있다.

## 동작

- 최상위에 재생목록, 최근 감상, 추천을 제공한다.
- 로그인한 앱의 Library, history, Home 요청과 원래 미디어 ID 생성기를 사용한다.
- 재생목록을 누르면 내부 곡을 탐색한다. 탐색 코드에서 재생 명령을 보내지 않는다.
- 서버의 후속 페이지를 최대 20페이지/30초까지 수집한다. 응답이 늦으면 확보된 결과로 한 번만 완료한다.
- 화면 응답은 최대 40항목 및 Parcel 기준 90,000바이트로 제한한다. 나머지는 `더 보기` 폴더로 연결한다. 후속 화면은 원본 목록을 다시 요청하므로 그 사이 목록이 변경되면 경계 항목이 달라질 수 있다.
- 지원하지 않는 행은 건너뛰고 중복 미디어 ID는 합친다. 매우 큰 단일 항목은 전송하지 않는다.

Home은 9.15.51에서 관찰한 두 카드 형식만 지원한다. 서버 형식 변경, 20페이지를 초과하는 대형 목록, 순정 앱과의 모든 항목·배치 일치는 보장하지 않는다. 곡 재생은 별도 검증 대상이다.

## 빌드와 검사

저장소의 JDK/Android SDK 및 Maven 의존성 설정이 필요하다. GitHub Maven에 접근할 수 없다면 저장소가 고정한 공개 의존성 버전을 Maven Local에 먼저 빌드해야 한다. 자격 증명을 소스에 기록하지 않는다.

```powershell
.\gradlew.bat :aa-playlist-patch:verifyBrowseBridge :patches:buildAndroid --no-daemon --console=plain
.\gradlew.bat :aa-playlist-patch:applyToApk `
  '-PpatchBundle=C:\path\morphe-patches\patches\build\libs\patches-1.42.0.mpp' `
  '-PinputApk=C:\path\original-youtube-music-9.15.51.apk' `
  '-PoutputApk=C:\path\music-auto-lab-unsigned.apk'
```

개발용 runner에는 **전체 번들**을 전달한다. GmsCore 지원·인증서 대응·백그라운드 재생 제한 제거·별도 패키지·별도 이름을 함께 적용한다. GmsCore 지원은 스트림 호환 패치에도 의존한다. 목록만 표시되는 APK와 Android Auto에서 실제 재생 가능한 APK는 필요한 패치 구성이 다르다. 결과는 서명되지 않은 APK이며 설치를 자동 수행하지 않는다. 이름은 `Music Auto Lab`, 패키지는 `app.morphe.android.apps.youtube.music.aalab`이다. 기존 앱을 삭제하거나 데이터를 지우지 않고 별도로 설치할 수 있다. 이후 테스트 앱 업데이트에는 같은 서명 키가 필요하다.

`aa-playlist-patch`의 단독 번들은 확장 개발용이며, 원본 APK에 필요한 다른 패치들을 모두 포함하지 않는다. 일반 패치 작업에는 전체 번들에서 해당 패치를 선택한다. 전체 번들의 기반 버전 문자열은 1.42.0이지만 이 작업 트리의 결과는 공식 배포본과 다르다.

자동 검사는 루트 폴더, 첫 응답/후속 응답 구분, 오류 행 격리, 중복 제거, 폴더 ID 왕복, 곡 목록, 제한 시간/늦은 응답, Home 형식 파싱, 화면 분할을 검증한다. Android API fixture는 검사 전용이며 배포 확장에 포함하지 않는다.

## 확인 결과와 범위

2026-09-10에 실제 휴대폰과 PC DHU로 별도 테스트 앱의 재생목록 이름, 내부 곡 목록, 최근 감상, 추천 및 최근 감상의 `더 보기` 후속 항목 표시를 확인했다. 재생목록의 표시상 곡 수와 변환된 항목 수는 중복 제거·누락 가능한 항목 때문에 다를 수 있다. 모든 곡의 일대일 일치는 검증하지 않았다.

이후 재생 검증에서 기본 클라이언트를 로그인되지 않은 Android VR에서 visionOS로 변경했고, runner에 누락된 백그라운드 재생 패치를 추가했다. v8 APK에서는 휴대폰 Activity를 먼저 열지 않고 DHU에서 곡을 선택해 2분 35초 재생 후 일시정지했으며, AudioTrack의 `state:started`, `mutedState:none`을 확인했다. 재생목록의 `더 보기` 내부 곡 표시도 확인했다. 실제 청취는 PC/RDP의 공통 무음 문제 때문에 아직 확인하지 못했다(사용자는 PC 브라우저 영상도 무음이라고 확인했다).

실제 차량, 계정 전환, 오프라인 및 장시간 잠금 상태의 안정성은 검증하지 않았다. 휴대폰에서 계정·지역·네트워크 조건을 먼저 정상화해야 한다. DHU에서 폴더와 탭만 열어도 목록 검증이 가능하다.

추가로 v8의 재생목록 `더 보기`, 최근 감상 및 추천에서 직접 항목을 선택해 곡 전환과 재생 시간 증가를 확인했다. 검증 종료 시 앱은 일시정지했다.

개인 계정 응답, 스크린샷, APK 및 서명 키는 소스에 포함하지 않는다.

## Service-only playback and Galaxy Modes

Additional v8 checks on 2026-09-10 used Android 16 and Samsung Modes and Routines 5.0.04.0. These exercise native `onPlay()` queue restoration, separately from DHU's `onPlayFromMediaId()` path and the browse bridge.

- With the service available and playback paused, a manually triggered mode targeting the test app resumed playback and advanced its position.
- After `am kill` terminated the background process without setting the package's stopped state, Android recreated `MusicBrowserService` before the next manual mode ran. That mode restored the saved queue and resumed playback. `BackgroundPlayerService` reported `isForeground=true` with the media playback service type. This does not prove that Modes alone can start an absent process.
- A manual mode did not recover the force-stopped app. A separate privileged shell request did: starting the foreground `MusicBrowserService` with `ACTION_MEDIA_BUTTON`, a PLAY key event, and `FLAG_INCLUDE_STOPPED_PACKAGES` restored the saved 25-entry queue and advanced playback without launching an Activity or creating a hidden display.
- Starting the service without a media event prepared an inactive session. An ordinary background `startService` request was rejected. One warm-service playback attempt also logged foreground-service restriction warnings, so short playback success must not be generalized to long-term service persistence.

No browse-patch implementation change was needed for these tests. Native queue restoration already works with the v8 patch set; this result does not add force-stop recovery to the built-in mode action. The privileged startup path has not been integrated into phone-only vehicle automation. Android Auto automatic music start remained off, and playback was paused after testing.

Long locked-screen playback, connection-trigger timing, actual vehicle behavior, and audible output remain unverified. Keep force-stop and normal process termination as separate test cases; see [Android stopped-state changes](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state). Private diagnostics and extracted application code are excluded from this repository's tracked sources.

## 출처

[Morphe PR #2489](https://github.com/MorpheApp/morphe-patches/pull/2489)의 기여 코드를 바탕으로 확장했다. 참고 커밋은 zappybiby/morphe-patches의 `9df878a71fea5ca618d04fd05ac771be761818c5`이다. 해당 PR은 병합되지 않았으며 이 구현은 공식 Morphe 배포를 의미하지 않는다. 원본 저작권 표시 및 루트 `NOTICE`/`LICENSE`를 유지한다.
