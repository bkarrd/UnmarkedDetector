# Ocena ryzyka

Projekt: Detektor Nieoznakowanych  
Wersja dokumentu: 1.0  
Data: 2026-06-03

## Skala oceny

| Parametr | Skala |
| --- | --- |
| Prawdopodobieństwo | 1 - bardzo niskie, 2 - niskie, 3 - średnie, 4 - wysokie, 5 - bardzo wysokie |
| Wpływ | 1 - mały, 2 - umiarkowany, 3 - średni, 4 - duży, 5 - krytyczny |
| Poziom ryzyka | Prawdopodobieństwo x Wpływ |

Interpretacja poziomu:
- 1-5: niskie,
- 6-10: średnie,
- 11-15: wysokie,
- 16-25: krytyczne.

## Rejestr ryzyk

| ID | Ryzyko | P | W | Poziom | Skutek | Działania ograniczające |
| --- | --- | ---: | ---: | ---: | --- | --- |
| R-01 | Błędny odczyt OCR tablicy | 4 | 4 | 16 | Fałszywy alert albo pominięcie pojazdu z bazy. | Walidacja formatów tablic, stabilizacja wyniku w czasie, testy na realnych zdjęciach, logowanie OCR. |
| R-02 | Model detekcji nie wykryje tablicy w trudnych warunkach | 4 | 4 | 16 | Brak alertu mimo obecności tablicy. | Testy w różnych warunkach światła, poprawa cropu, dostrajanie progów detekcji. |
| R-03 | Android ograniczy pracę aplikacji w tle | 3 | 5 | 15 | Foreground service przestanie analizować obraz. | Komunikat w onboardingu, wykrywanie `backgroundRestricted`, status "Nieaktywny", akcja ustawień baterii w powiadomieniu. |
| R-04 | Nadmierne zużycie baterii i nagrzewanie telefonu | 4 | 4 | 16 | Telefon może ograniczyć aplikację albo użytkownik przerwie skanowanie. | ImageAnalysis z backpressure, adaptacyjny interwał skanowania, limit OCR na klatkę, komunikat o baterii. |
| R-05 | Brak wymaganych uprawnień | 3 | 4 | 12 | Aplikacja nie uruchomi skanowania albo nie pokaże alertu. | Onboarding uprawnień, obsługa odmowy, statusy uprawnień, przejście do ustawień aplikacji. |
| R-06 | Ograniczenia Google Play dotyczące uprawnień i pracy w tle | 3 | 5 | 15 | Ryzyko odrzucenia wersji beta lub wymóg dodatkowych wyjaśnień. | Jasne uzasadnienie uprawnień, lokalne przetwarzanie obrazu, opcjonalny overlay, target SDK 35, polityka prywatności przed publikacją. |
| R-07 | Użytkownik rozprasza się podczas jazdy | 3 | 5 | 15 | Ryzyko bezpieczeństwa w ruchu drogowym. | Minimalny UI, alerty dźwiękowe/wibracyjne, obsługa nad nawigacją, brak konieczności patrzenia w aplikację. |
| R-08 | Nieaktualna lokalna baza tablic | 3 | 4 | 12 | Alerty mogą być niepełne albo błędne. | Pokazywanie wersji bazy, możliwość synchronizacji, plan backendu aktualizacji. |
| R-09 | Fragmentacja urządzeń Android | 4 | 3 | 12 | Różne telefony mogą mieć inne zachowanie kamery, baterii i overlay. | CameraX, testy na fizycznych urządzeniach, obsługa stanu ograniczeń baterii. |
| R-10 | Błąd integracji CameraX z foreground service | 2 | 5 | 10 | Skanowanie działa tylko na widocznym ekranie aplikacji. | Kamera bindowana do usługi, preview odpinane przy `onStop`, test manualny pracy w tle. |
| R-11 | Awaria modelu TFLite lub niezgodność assetu | 2 | 5 | 10 | OCR albo detekcja nie uruchomi się. | Modele w assets, logowanie błędów inicjalizacji, test kompilacji APK/AAB. |
| R-12 | Zbyt duży zakres projektu semestralnego | 3 | 3 | 9 | Opóźnienie albo brak stabilnej wersji demonstracyjnej. | Ograniczenie zakresu do kamery telefonu, lokalnej bazy i alertów; Viofo, billing i backend poza zakresem. |
| R-13 | Błędy synchronizacji Gradle/Android Studio | 3 | 3 | 9 | Projekt nie otwiera się u prowadzącego albo nie buduje się lokalnie. | AGP zgodny z Android Studio 2024.1, Gradle wrapper w repo, sprawdzony sync przez `gradlew projects`. |
| R-14 | Fałszywe alarmy z powodu podobnych znaków | 4 | 3 | 12 | Użytkownik traci zaufanie do aplikacji. | Cooldown alertów, wymóg stabilizacji odczytu, walidacja prefiksów i formatów. |
| R-15 | Brak internetu podczas synchronizacji bazy | 3 | 2 | 6 | Nie uda się pobrać nowszych rekordów. | Aplikacja działa na lokalnej bazie; internet nie jest wymagany do samej detekcji. |

## Najważniejsze ryzyka

Najwyżej ocenione ryzyka to:
1. błędny odczyt OCR,
2. brak detekcji tablicy w trudnych warunkach,
3. zużycie baterii i nagrzewanie telefonu,
4. ograniczenia pracy w tle,
5. wymagania Google Play dotyczące uprawnień.

Te ryzyka mają największy wpływ na użyteczność aplikacji, dlatego zostały uwzględnione bezpośrednio w architekturze: aplikacja stabilizuje wyniki OCR, działa jako foreground service, wykrywa ograniczenia baterii, przetwarza obraz lokalnie i pokazuje użytkownikowi jasne statusy.

## Ryzyka zaakceptowane w wersji beta

| Ryzyko | Powód akceptacji |
| --- | --- |
| Niedoskonałość OCR w trudnym świetle | Projekt jest wersją beta i wymaga dalszych testów terenowych. |
| Brak integracji Viofo | Integracja sprzętowa znacząco zwiększa zakres projektu. |
| Brak pełnego backendu | Lokalna baza wystarcza do demonstracji głównego mechanizmu. |
| Ostrzeżenia lint dotyczące zależności i drobnych optymalizacji | Lint nie zgłasza błędów blokujących, a ostrzeżenia nie uniemożliwiają prezentacji. |

## Plan reakcji na ryzyka

| Sytuacja | Reakcja |
| --- | --- |
| OCR często myli prefiks tablicy | Dodać przypadki testowe, zebrać logi, dostroić normalizację i progi stabilizacji. |
| Aplikacja nie działa po przejściu w tło | Sprawdzić status foreground service, logi CameraX i ograniczenia baterii. |
| Telefon się nagrzewa | Zwiększyć interwał skanowania, ograniczyć liczbę OCR na klatkę, obniżyć rozdzielczość analizy. |
| Google Play wymaga dodatkowego uzasadnienia uprawnień | Przygotować politykę prywatności, opis lokalnego przetwarzania i uzasadnienie foreground service. |
| Baza tablic jest nieaktualna | Wykonać synchronizację albo zaktualizować seed bazy przed prezentacją. |

## Wniosek

Projekt ma kilka istotnych ryzyk technicznych, ale najważniejsze z nich są zidentyfikowane i częściowo ograniczone w obecnej implementacji. Dla celów projektu z inżynierii oprogramowania aplikacja posiada realistyczny rejestr ryzyk, mechanizmy ograniczające ryzyka krytyczne oraz plan dalszych działań przed publikacją lub rozbudową systemu.
