# Dokumentacja wymagań

Projekt: Detektor Nieoznakowanych  
Wersja dokumentu: 1.0  
Data: 2026-06-03  
Wersja aplikacji: 0.1.0-beta

## Cel systemu

Aplikacja Android wspiera kierowcę podczas jazdy przez lokalne rozpoznawanie tablic rejestracyjnych z obrazu tylnej kamery telefonu. System wykrywa obszary tablic, odczytuje tekst tablicy modelem OCR, porównuje wynik z lokalną bazą pojazdów i pokazuje alert, gdy tablica znajduje się w bazie.

## Zakres projektu

W zakresie wersji beta znajduje się:
- skanowanie obrazu z tylnej kamery telefonu,
- wykrywanie tablic modelem lokalnym,
- rozpoznawanie tekstu tablic modelem TFLite,
- walidacja i stabilizacja wyników OCR,
- lokalna baza tablic,
- alerty przez powiadomienia i opcjonalną nakładkę nad innymi aplikacjami,
- praca jako foreground service,
- ekran ustawień i podstawowa obsługa uprawnień.

Poza zakresem bieżącej wersji:
- integracja z kamerą samochodową Viofo,
- pełny backend administracyjny,
- płatności Google Play Billing,
- długoterminowa historia tras,
- automatyczne zgłoszenia błędnych alarmów do backendu.

## Aktorzy

| Aktor | Opis |
| --- | --- |
| Kierowca / użytkownik | Uruchamia aplikację, nadaje uprawnienia, rozpoczyna i kończy skanowanie. |
| System Android | Dostarcza kamerę, lokalizację, powiadomienia, ograniczenia baterii i ekran overlay. |
| Lokalna baza tablic | Przechowuje rekordy tablic, marek, modeli i regionów. |
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
| F-13 | Aplikacja porównuje rozpoznaną tablicę z lokalną bazą. | Wysoki | Dla tablicy znajdującej się w bazie powstaje zdarzenie alertu. |
| F-14 | Aplikacja pokazuje alert dla dopasowania z bazą. | Wysoki | Użytkownik widzi powiadomienie, a przy zgodzie overlay również nakładkę. |
| F-15 | Aplikacja pokazuje status działania skanowania. | Wysoki | UI i powiadomienie pokazują stan: aktywny, wstrzymany lub nieaktywny. |
| F-16 | Aplikacja wykrywa ograniczone działanie w tle. | Średni | Przy systemowym ograniczeniu w tle status zmienia się na "Nieaktywny". |
| F-17 | Użytkownik może zmienić głośność alertu i wibrację. | Średni | Zmiany ustawień są zapisywane lokalnie. |
| F-18 | Aplikacja pokazuje wersję lokalnej bazy. | Niski | Ekran ustawień wyświetla wersję bazy i opis synchronizacji. |

## Wymagania niefunkcjonalne

| ID | Wymaganie | Priorytet | Kryterium akceptacji |
| --- | --- | --- | --- |
| N-01 | Analiza obrazu działa lokalnie na telefonie. | Wysoki | Do rozpoznawania tablic nie jest wymagane stałe połączenie z internetem. |
| N-02 | UI pozostaje responsywne podczas skanowania. | Wysoki | Analiza obrazu działa poza głównym wątkiem interfejsu. |
| N-03 | Aplikacja ogranicza zużycie zasobów. | Średni | Wykorzystywane jest ImageAnalysis z backpressure i adaptacyjnym skanowaniem. |
| N-04 | Aplikacja wspiera Android API 26+. | Średni | `minSdk = 26`. |
| N-05 | Aplikacja jest przygotowana pod Google Play beta. | Średni | `targetSdk = 35`, aplikacja ma spójne teksty i ikonę. |
| N-06 | Aplikacja komunikuje ograniczenia pracy w tle. | Wysoki | Powiadomienie nie pokazuje fałszywego stanu aktywnego przy ograniczeniach systemu. |
| N-07 | Dane bazy są przechowywane lokalnie. | Średni | Room przechowuje rekordy i aplikacja może działać offline na danych lokalnych. |
| N-08 | Najważniejsza logika biznesowa ma testy jednostkowe. | Wysoki | Testy walidacji tablic, sesji, stabilizacji i alertów przechodzą bez błędów. |
| N-09 | Projekt ma separację warstw. | Średni | Kod jest podzielony na warstwy presentation, service, detection, domain i data. |

## Wymagania danych

| ID | Wymaganie | Opis |
| --- | --- | --- |
| D-01 | Rekord tablicy | System przechowuje numer tablicy, markę, model, region, czas aktualizacji i identyfikator wersji. |
| D-02 | Wynik detekcji | System przechowuje tekst tablicy, pewność i czas wykrycia. |
| D-03 | Stan sesji | System przechowuje liczbę unikalnych tablic, liczbę alertów, ostatnie skany i status pracy. |
| D-04 | Ustawienia użytkownika | System przechowuje głośność alertu i stan wibracji. |

## Ograniczenia i założenia

1. Telefon musi mieć sprawną tylną kamerę.
2. Użytkownik musi nadać uprawnienia do kamery, lokalizacji i powiadomień.
3. Overlay jest opcjonalny, ponieważ podstawowy alert działa jako powiadomienie.
4. Jakość OCR zależy od oświetlenia, ostrości obrazu i kąta widzenia tablicy.
5. System Android może ograniczyć działanie aplikacji w tle mimo ustawień baterii, dlatego aplikacja wykrywa i komunikuje taki stan.
6. Aplikacja jest projektem akademickim i wersją beta, więc nie zastępuje profesjonalnego systemu rozpoznawania tablic.

## Kryteria zakończenia wersji beta

Wersję beta uznaje się za gotową do demonstracji, jeżeli:
- projekt kompiluje się bez błędów,
- testy jednostkowe przechodzą,
- lint nie zgłasza błędów blokujących,
- APK instaluje się na telefonie testowym,
- aplikacja działa w tle jako foreground service,
- użytkownik dostaje jasny komunikat przy braku uprawnień lub ograniczeniach baterii.
