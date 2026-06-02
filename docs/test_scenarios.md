# Testy aplikacji

Projekt: Detektor Nieoznakowanych  
Wersja aplikacji: 0.1.0-beta  
Data wykonania: 2026-06-03

## Środowisko testowe

| Element | Wartość |
| --- | --- |
| System projektu | Windows, PowerShell |
| Android Gradle Plugin | 8.5.1 |
| Gradle Wrapper | 8.7 |
| Kotlin | 1.9.24 |
| `compileSdk` / `targetSdk` | 35 / 35 |
| `minSdk` | 26 |
| Telefon testowy | Samsung SM-S901B |
| Android telefonu | 16, API 36 |
| Pakiet aplikacji | `com.example.unmarkeddetector` |

## Komendy wykonane podczas testów

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug bundleRelease --console=plain
```

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
& $adb shell dumpsys package com.example.unmarkeddetector
& $adb shell cmd appops get com.example.unmarkeddetector RUN_ANY_IN_BACKGROUND
```

## Podsumowanie wyników

| Obszar | Wynik |
| --- | --- |
| Testy jednostkowe | 15 testów, 0 błędów, 0 porażek, 0 pominiętych |
| Build debug APK | Sukces |
| Lint debug | 0 błędów, 77 ostrzeżeń |
| Build release AAB | Sukces |
| Instalacja APK na telefonie | Sukces |
| Uprawnienie pracy w tle na telefonie | `RUN_ANY_IN_BACKGROUND: allow` |

## Scenariusze testowe

| ID | Scenariusz | Kroki | Oczekiwany wynik | Wynik rzeczywisty | Status |
| --- | --- | --- | --- | --- | --- |
| T-01 | Walidacja poprawnej tablicy po OCR | Uruchomić testy `PlateValidatorTest`. | Poprawne ciągi tablic są normalizowane do kanonicznej postaci. | Testy przeszły. Przykład: `po 25y7p` daje `PO25Y7P`. | Pozytywny |
| T-02 | Odrzucanie tekstów niebędących tablicami | Uruchomić testy `PlateValidatorTest` i `DetectPlateUseCaseTest`. | Zwykłe słowa i za krótkie formaty nie są uznawane za tablice. | Testy przeszły. Przykłady tekstów `TABLICA`, `POLSKIE`, `GENERATOR` są odrzucane. | Pozytywny |
| T-03 | Korekta typowych błędów OCR | Uruchomić test `repairs likely OCR prefix mistakes`. | Typowe pomyłki, np. `OOT F038`, są naprawiane do poprawnego prefiksu. | Test przeszedł: wynik `WOTF038`. | Pozytywny |
| T-04 | Filtrowanie kandydatów po pewności OCR | Uruchomić testy `DetectPlateUseCaseTest`. | Kandydaci poniżej progu pewności są odrzucani. | Test przeszedł. Kandydat z pewnością `0.79` jest odrzucany. | Pozytywny |
| T-05 | Liczenie unikalnych tablic w sesji | Uruchomić test `counts only unique plates in session`. | Powtórzenia tej samej tablicy nie zwiększają licznika. | Test przeszedł. Dwie różne tablice dają licznik `2`, powtórki nie zwiększają wyniku. | Pozytywny |
| T-06 | Cooldown alertu dla tej samej tablicy | Uruchomić test `applies alert cooldown for same plate`. | Ten sam rekord z bazy nie generuje natychmiast wielu alertów. | Test przeszedł. Drugi alert dla tej samej tablicy został zablokowany. | Pozytywny |
| T-07 | Stabilizacja odczytu w czasie | Uruchomić testy `PlateTrackManagerTest`. | Stabilna detekcja pojawia się dopiero po powtórzonym potwierdzeniu. | Testy przeszły. Pojedyncza obserwacja nie emituje stabilnej tablicy. | Pozytywny |
| T-08 | Wygenerowanie zdarzenia alertu | Uruchomić test `TriggerAlertUseCaseTest`. | Dopasowana tablica tworzy `AlertEvent` i wywołuje dispatcher. | Test przeszedł. Zdarzenie zawiera właściwy rekord i pewność. | Pozytywny |
| T-09 | Kompilacja aplikacji debug | Uruchomić `assembleDebug`. | Powstaje APK debug. | APK powstał w `app/build/outputs/apk/debug/app-debug.apk`. | Pozytywny |
| T-10 | Analiza lint | Uruchomić `lintDebug`. | Brak błędów blokujących. | Lint zakończył się wynikiem `0 errors, 77 warnings`. | Pozytywny |
| T-11 | Przygotowanie paczki release | Uruchomić `bundleRelease`. | Powstaje Android App Bundle. | AAB powstał w `app/build/outputs/bundle/release/app-release.aab`. | Pozytywny |
| T-12 | Instalacja APK na telefonie | Wykonać `adb install -r app-debug.apk`. | Instalacja kończy się sukcesem. | `Performing Streamed Install` oraz `Success`. | Pozytywny |
| T-13 | Sprawdzenie manifestu na telefonie | Wykonać `adb shell dumpsys package com.example.unmarkeddetector`. | Pakiet ma właściwą wersję, target SDK i uprawnienia. | `versionName=0.1.0-beta`, `targetSdk=35`, widoczne uprawnienia kamery, lokalizacji, powiadomień i foreground service. | Pozytywny |
| T-14 | Sprawdzenie pracy w tle | Wykonać `adb shell cmd appops get ... RUN_ANY_IN_BACKGROUND`. | System nie blokuje pracy w tle. | Wynik: `RUN_ANY_IN_BACKGROUND: allow`. | Pozytywny |
| T-15 | Skanowanie po przejściu aplikacji w tło | Uruchomić skanowanie, przejść do innej aplikacji i obserwować dalszą pracę foreground service. | Skanowanie działa mimo niewidocznego preview. | Test manualny na telefonie użytkownika potwierdzony 2026-06-03: aplikacja działa w tle. | Pozytywny |

## Zakres testów jednostkowych

| Plik testowy | Liczba testów | Sprawdzany obszar |
| --- | ---: | --- |
| `PlateValidatorTest.kt` | 6 | Format tablic, normalizacja OCR, odrzucanie błędnych tekstów. |
| `PlateTrackManagerTest.kt` | 3 | Stabilizacja wyników OCR w czasie i potwierdzanie detekcji. |
| `PlateSessionManagerTest.kt` | 2 | Unikalne tablice w sesji i cooldown alertów. |
| `DetectPlateUseCaseTest.kt` | 3 | Filtrowanie kandydatów OCR i deduplikacja wyników. |
| `TriggerAlertUseCaseTest.kt` | 1 | Tworzenie i wysyłanie zdarzenia alertu. |

## Wnioski z testów

Aplikacja spełnia podstawowe kryteria wersji beta: kompiluje się, przechodzi testy jednostkowe, generuje APK i AAB oraz instaluje się na fizycznym telefonie. Najważniejsza logika domenowa jest pokryta testami automatycznymi. Test ręczny potwierdził również działanie skanowania po przejściu aplikacji w tło.

Ostrzeżenia lint nie blokują uruchomienia aplikacji. Dotyczą głównie zależności, drobnych optymalizacji layoutów i rekomendacji technicznych. Nie wykryto błędów krytycznych.

## Testy rekomendowane przed finalną prezentacją

1. Test drogowy w dzień na kilku rzeczywistych tablicach.
2. Test drogowy wieczorem lub przy słabszym świetle.
3. Test odmowy uprawnień na czystej instalacji aplikacji.
4. Test działania overlay nad aplikacją nawigacji.
5. Test termiczny po minimum 20 minutach ciągłego skanowania.
