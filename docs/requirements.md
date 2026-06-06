# Dokumentacja wymagań

Projekt: Detektor Nieoznakowanych  
Wersja dokumentu: 1.1  
Data: 2026-06-03  
Wersja aplikacji: 0.1.0-beta

## Cel systemu

Aplikacja Android wspiera kierowcę podczas jazdy przez lokalne rozpoznawanie tablic rejestracyjnych z obrazu tylnej kamery telefonu. System wykrywa obszary tablic, odczytuje tekst tablicy modelem OCR, porównuje wynik z lokalną kopią bazy pojazdów synchronizowaną z Supabase i pokazuje alert, gdy tablica znajduje się w bazie.

## Zakres projektu

W zakresie wersji beta znajduje się:
- skanowanie obrazu z tylnej kamery telefonu,
- wykrywanie tablic modelem lokalnym,
- rozpoznawanie tekstu tablic modelem TFLite,
- walidacja i stabilizacja wyników OCR,
- lokalna baza tablic w Room, synchronizowana z bazą Supabase,
- ręczne sprawdzanie aktualizacji bazy z poziomu ustawień,
- alerty przez powiadomienia i opcjonalną nakładkę nad innymi aplikacjami,
- praca jako foreground service,
- ekran ustawień i podstawowa obsługa uprawnień.

Poza zakresem bieżącej wersji:
- integracja z kamerą samochodową Viofo,
- pełny panel administracyjny i proces moderacji rekordów w bazie,
- płatności Google Play Billing,
- długoterminowa historia tras,
- automatyczne zgłoszenia błędnych alarmów do backendu.

## Aktorzy

| Aktor | Opis |
| --- | --- |
| Kierowca / użytkownik | Uruchamia aplikację, nadaje uprawnienia, rozpoczyna i kończy skanowanie. |
| System Android | Dostarcza kamerę, lokalizację, powiadomienia, ograniczenia baterii i ekran overlay. |
| Lokalna baza tablic | Przechowuje na telefonie rekordy tablic, marek, modeli i regionów pobrane z Supabase. |
| Supabase | Zdalne źródło aktualizacji bazy tablic. |
| Usługa detekcji | Foreground service analizujący obraz także po przejściu aplikacji w tło. |

## Wymagania funkcjonalne

| ID | Wymaganie | Priorytet | Kryterium akceptacji |
| --- | --- | --- | --- |
| F-01 | Aplikacja pokazuje ekran startowy i przechodzi do onboardingu albo ekranu jazdy. | Wysoki | Po uruchomieniu użytkownik widzi spójny ekran ładowania i trafia do właściwego ekranu. |
| F-02 | Aplikacja informuje o wymaganych uprawnieniach: kamera, lokalizacja, powiadomienia. | Wysoki | Brak uprawnienia blokuje start skanowania i pokazuje czytelny komunikat. |
| F-03 | Aplikacja obsługuje odmowę uprawnień. | Wysoki | Po odmowie użytkownik dostaje wyjaśnienie i może przejść do ustawień aplikacji. |
| F-04 | Użytkownik może opcjonalnie włączyć alert nad innymi aplikacjami. | Średni | Overlay jest opisany jako opcjonalny; bez niego nadal działa powiadomienie. |
| F-05 | Użytkownik może uruchomić i zatrzymać tryb skanowania. | Wysoki | Przyciski Start i Stop zmieniają stan usługi oraz powiadomienia. |
| F-06 | Aplikacja analizuje obraz z tylnej kamery telefonu. | Wysoki | Po starcie usługi CameraX dostarcza klatki do analizatora. |
| F-07 | Skanowanie działa po przejściu aplikacji w tło. | Wysoki | Kamera jest zbindowana do foreground service, a preview jest tylko dodatkiem UI. |
| F-08 | Aplikacja wykrywa regiony tablic na klatce obrazu. | Wysoki | Pipeline zwraca listę regionów tablic z wynikiem pewności. |
| F-09 | Aplikacja rozpoznaje tekst tablic z wykrytego regionu. | Wysoki | FastPlateOCR zwraca kandydata tekstowego dla cropu tablicy. |
| F-10 | Aplikacja filtruje wyniki OCR do formatów polskich tablic. | Wysoki | Niepoprawne słowa są odrzucane, a typowe pomyłki OCR są normalizowane. |
| F-11 | Aplikacja stabilizuje odczyt tablicy w czasie. | Wysoki | Pojedyncza obserwacja nie generuje stabilnej detekcji bez potwierdzenia. |
| F-12 | Aplikacja liczy unikalne tablice w sesji. | Średni | Powtarzająca się tablica nie zwiększa licznika drugi raz. |
| F-13 | Aplikacja porównuje rozpoznaną tablicę z lokalną kopią bazy. | Wysoki | Dla tablicy znajdującej się w bazie powstaje zdarzenie alertu także bez stałego połączenia z internetem. |
| F-14 | Aplikacja pokazuje alert dla dopasowania z bazą. | Wysoki | Użytkownik widzi powiadomienie, a przy zgodzie overlay również nakładkę. |
| F-15 | Aplikacja pokazuje status działania skanowania. | Wysoki | UI i powiadomienie pokazują stan: aktywny, wstrzymany lub nieaktywny. |
| F-16 | Aplikacja wykrywa ograniczone działanie w tle. | Średni | Przy systemowym ograniczeniu w tle status zmienia się na "Nieaktywny". |
| F-17 | Użytkownik może zmienić głośność alertu i wibrację. | Średni | Zmiany ustawień są zapisywane lokalnie. |
| F-18 | Aplikacja pokazuje wersję lokalnej bazy. | Niski | Ekran ustawień wyświetla wersję bazy i opis synchronizacji z Supabase. |
| F-19 | Użytkownik może ręcznie zsynchronizować bazę tablic. | Średni | Kliknięcie "Sprawdź aktualizacje" pobiera rekordy z Supabase, zapisuje je w Room i pokazuje wynik synchronizacji. |

## Wymagania niefunkcjonalne

| ID | Wymaganie | Priorytet | Kryterium akceptacji |
| --- | --- | --- | --- |
| N-01 | Analiza obrazu działa lokalnie na telefonie. | Wysoki | Do detekcji, OCR i porównania z ostatnio pobraną bazą nie jest wymagane stałe połączenie z internetem. |
| N-02 | UI pozostaje responsywne podczas skanowania. | Wysoki | Analiza obrazu działa poza głównym wątkiem interfejsu. |
| N-03 | Aplikacja ogranicza zużycie zasobów. | Średni | Wykorzystywane jest ImageAnalysis z backpressure i adaptacyjnym skanowaniem. |
| N-04 | Aplikacja wspiera Android API 26+. | Średni | `minSdk = 26`. |
| N-05 | Aplikacja jest przygotowana pod Google Play beta. | Średni | `targetSdk = 35`, aplikacja ma spójne teksty i ikonę. |
| N-06 | Aplikacja komunikuje ograniczenia pracy w tle. | Wysoki | Powiadomienie nie pokazuje fałszywego stanu aktywnego przy ograniczeniach systemu. |
| N-07 | Dane bazy są przechowywane lokalnie i aktualizowane ze zdalnego źródła. | Średni | Room przechowuje rekordy, Supabase dostarcza aktualizacje, a aplikacja może działać offline na ostatnio zsynchronizowanych danych. |
| N-08 | Najważniejsza logika biznesowa ma testy jednostkowe. | Wysoki | Testy walidacji tablic, sesji, stabilizacji i alertów przechodzą bez błędów. |
| N-09 | Projekt ma separację warstw. | Średni | Kod jest podzielony na warstwy presentation, service, detection, domain i data. |
| N-10 | Brak internetu nie blokuje skanowania. | Średni | Nieudana synchronizacja bazy pokazuje komunikat błędu, ale nie usuwa ostatniej lokalnej kopii danych. |

## Wymagania danych

| ID | Wymaganie | Opis |
| --- | --- | --- |
| D-01 | Rekord tablicy | System przechowuje numer tablicy, markę, model, region, czas aktualizacji i identyfikator wersji pobrane z Supabase lub z danych zapasowych. |
| D-02 | Wynik detekcji | System przechowuje tekst tablicy, pewność i czas wykrycia. |
| D-03 | Stan sesji | System przechowuje liczbę unikalnych tablic, liczbę alertów, ostatnie skany i status pracy. |
| D-04 | Ustawienia użytkownika | System przechowuje głośność alertu i stan wibracji. |
| D-05 | Wynik synchronizacji | System informuje użytkownika o liczbie rekordów pobranych z Supabase albo o błędzie synchronizacji. |

## Ograniczenia i założenia

1. Telefon musi mieć sprawną tylną kamerę.
2. Użytkownik musi nadać uprawnienia do kamery, lokalizacji i powiadomień.
3. Overlay jest opcjonalny, ponieważ podstawowy alert działa jako powiadomienie.
4. Jakość OCR zależy od oświetlenia, ostrości obrazu i kąta widzenia tablicy.
5. System Android może ograniczyć działanie aplikacji w tle mimo ustawień baterii, dlatego aplikacja wykrywa i komunikuje taki stan.
6. Połączenie z internetem jest potrzebne do aktualizacji bazy z Supabase, ale nie do samej detekcji i alertów na ostatnio zapisanej lokalnej kopii.
7. Aplikacja jest projektem akademickim i wersją beta, więc nie zastępuje profesjonalnego systemu rozpoznawania tablic.

## Kryteria zakończenia wersji beta

Wersję beta uznaje się za gotową do demonstracji, jeżeli:
- projekt kompiluje się bez błędów,
- testy jednostkowe przechodzą,
- lint nie zgłasza błędów blokujących,
- APK instaluje się na telefonie testowym,
- aplikacja działa w tle jako foreground service,
- użytkownik dostaje jasny komunikat przy braku uprawnień lub ograniczeniach baterii,
- ekran ustawień umożliwia ręczne sprawdzenie aktualizacji bazy z Supabase.
