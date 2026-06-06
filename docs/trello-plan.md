# UnmarkedDetector - gotowa tresc do Trello

Projekt: aplikacja Android, ktora podczas jazdy rozpoznaje tablice rejestracyjne z kamery telefonu, porownuje je z lokalna kopia bazy synchronizowana z Supabase i pokazuje alert, gdy wykryta tablica znajduje sie w bazie.

## Listy na tablicy Trello

1. Wymagania i zakres
2. Do zrobienia - MVP
3. W toku
4. Gotowe
5. Testy i poprawki
6. Poza zakresem semestru

## Wymagania funkcjonalne

| ID | Wymaganie | Priorytet | Status |
| --- | --- | --- | --- |
| F-01 | Aplikacja wyswietla ekran startowy i prowadzi uzytkownika przez pierwsza konfiguracje. | Wysoki | Gotowe |
| F-02 | Aplikacja sprawdza uprawnienia: kamera, lokalizacja, powiadomienia i nakladka systemowa. | Wysoki | Gotowe |
| F-03 | Uzytkownik moze uruchomic i zatrzymac tryb jazdy. | Wysoki | Gotowe |
| F-04 | Aplikacja pokazuje podglad z kamery telefonu. | Wysoki | Gotowe |
| F-05 | Aplikacja analizuje obraz i wyszukuje obszary podobne do tablic. | Wysoki | W toku |
| F-06 | Aplikacja rozpoznaje tekst tablic z uzyciem OCR. | Wysoki | W toku |
| F-07 | Aplikacja filtruje wyniki OCR do formatow polskich tablic. | Wysoki | Gotowe |
| F-08 | Aplikacja liczy unikalne tablice wykryte w aktualnej sesji. | Sredni | Gotowe |
| F-09 | Klikniecie w "Unikalne tablice" pokazuje liste tablic z aktualnej sesji. | Sredni | Gotowe |
| F-10 | Aplikacja porownuje wykryte tablice z lokalna kopia bazy pojazdow. | Wysoki | Gotowe |
| F-11 | Po dopasowaniu tablicy aplikacja pokazuje alert jako powiadomienie i nakladke ekranowa. | Wysoki | Gotowe |
| F-12 | Uzytkownik moze zglosic bledny alarm z poziomu nakladki. | Sredni | Czesc. gotowe |
| F-13 | Detekcja dziala jako foreground service. | Wysoki | Gotowe |
| F-14 | Aplikacja pokazuje predkosc GPS i status sesji. | Sredni | Gotowe |
| F-15 | Uzytkownik moze zmienic glosnosc alertu i wlaczyc lub wylaczyc wibracje. | Sredni | Gotowe |
| F-16 | Aplikacja pokazuje wersje lokalnej bazy tablic i pozwala recznie sprawdzic aktualizacje. | Niski | Gotowe |
| F-17 | Aplikacja automatycznie dostosowuje interwal skanowania do wynikow detekcji. | Sredni | Gotowe |

## Wymagania niefunkcjonalne

| ID | Wymaganie | Priorytet | Status |
| --- | --- | --- | --- |
| N-01 | Detekcja, OCR i porownanie z ostatnio pobrana baza dzialaja lokalnie na telefonie, bez stalego internetu. | Wysoki | Gotowe |
| N-02 | Analiza obrazu nie blokuje interfejsu uzytkownika. | Wysoki | Gotowe |
| N-03 | Skanowanie jest ograniczane adaptacyjnie, aby zmniejszyc zuzycie baterii. | Sredni | Gotowe |
| N-04 | Alert jest czytelny podczas korzystania z nawigacji. | Wysoki | Gotowe |
| N-05 | Brak uprawnien jest obslugiwany jasnym komunikatem. | Wysoki | Gotowe |
| N-06 | Kod jest podzielony na warstwy: prezentacja, detekcja, domena, dane. | Sredni | Gotowe |
| N-07 | Najwazniejsze reguly walidacji tablic i sesji maja testy jednostkowe. | Sredni | Gotowe |
| N-08 | Projekt da sie rozbudowac o zewnetrzna kamere, panel administracyjny bazy i platnosci. | Niski | Czesc. gotowe |

## Karty do Trello

### Wymagania i zakres

Karta: Opis projektu

Opis: UnmarkedDetector wspiera kierowce podczas jazdy. Telefon zamontowany przy szybie analizuje obraz z kamery, rozpoznaje tablice, porownuje je z lokalna kopia bazy synchronizowana z Supabase i ostrzega, gdy znajdzie dopasowanie.

Kryteria akceptacji:
- opis wskazuje problem, odbiorce i scenariusz uzycia,
- zakres MVP obejmuje kamere telefonu, OCR, lokalna baze, synchronizacje z Supabase i alerty,
- funkcje przyszle sa oddzielone od zakresu semestralnego.

Karta: Zakres MVP

Opis: MVP obejmuje tryb jazdy, podglad kamery, OCR tablic, walidacje wynikow, lokalna baze synchronizowana z Supabase, alerty, statystyki sesji i ustawienia alertow.

Kryteria akceptacji:
- w Trello sa osobne karty dla funkcji MVP,
- kazda karta ma status i priorytet,
- zadania poza semestrem sa w osobnej liscie.

### Do zrobienia - MVP

Karta: Poprawa skutecznosci OCR na nagraniach testowych

Opis: Dostroic wykrywanie regionow tablic i filtrowanie OCR na realnych logach z telefonu. Celem jest zmniejszenie liczby losowych odczytow.

Kryteria akceptacji:
- aplikacja nadal wykrywa poprawne tablice z testow,
- liczba przypadkowych ciagow tekstu jest mniejsza,
- logi pokazuja czas przetwarzania klatki.

Karta: Ekran historii ostatnich wykryc w sesji

Opis: Rozszerzyc ekran trybu jazdy o czytelna historie kilku ostatnich wykrytych tablic.

Kryteria akceptacji:
- uzytkownik widzi kilka ostatnich tablic,
- historia nie zaslania podgladu kamery,
- po restarcie sesji historia jest czyszczona.

Karta: Dopracowanie komunikatow aplikacji

Opis: Ujednolic teksty statusow, przyciskow, uprawnien, ustawien i alertow.

Kryteria akceptacji:
- teksty sa spojne,
- komunikaty jasno mowia, co zrobic,
- w podstawowym scenariuszu nie ma roboczych TODO.

### W toku

Karta: Detekcja tablic z obrazu kamery

Opis: Pipeline pobiera klatke z CameraX, wykrywa regiony tablic modelem YOLO i przekazuje crop z tej samej klatki do FastPlateOCR TFLite.

Aktualny stan:
- CameraX dziala i przekazuje klatki do analizatora,
- YOLO wykrywa regiony tablic na obrazie z ImageAnalysis,
- FastPlateOCR rozpoznaje znaki lokalnie na telefonie.

Kryteria akceptacji:
- dla widocznej tablicy pipeline zwraca kandydata,
- wynik jest walidowany jako polska tablica,
- UI nie zawiesza sie podczas przetwarzania.

Karta: Testy drogowe i strojenie progow

Opis: Uruchamiac aplikacje na telefonie, zbierac logcat i dostrajac progi wykrywania, OCR oraz odrzucania falszywych wynikow.

Kryteria akceptacji:
- zebrano logi z kilku prob,
- w Trello opisano najwieksze problemy,
- po zmianach aplikacja kompiluje sie i przechodzi testy.

### Gotowe

Karta: Konfiguracja projektu Android

Opis: Projekt Android/Kotlin z Gradle, Hilt, CameraX, Room, LiteRT, Navigation i Material Components.

Kryteria akceptacji:
- projekt buduje sie przez Gradle,
- aplikacja uruchamia sie na telefonie.

Karta: Onboarding i uprawnienia

Opis: Ekran startowy sprawdza uprawnienia do kamery, lokalizacji, powiadomien i overlay.

Kryteria akceptacji:
- aplikacja wykrywa brakujace uprawnienia,
- bez wymaganych uprawnien nie przechodzi do pelnego trybu jazdy.

Karta: Tryb jazdy z podgladem kamery

Opis: Glowny ekran zawiera podglad z kamery, status sesji, predkosc GPS, liczbe unikalnych tablic, liczbe alertow i ostatni skan.

Kryteria akceptacji:
- podglad kamery jest widoczny,
- Start i Stop zmieniaja stan sesji,
- statystyki sesji aktualizuja sie w UI.

Karta: Lista unikalnych tablic w sesji

Opis: Klikniecie w kafelek "Unikalne tablice" otwiera dialog z lista tablic wykrytych w aktualnej sesji.

Kryteria akceptacji:
- klikniecie w licznik otwiera liste,
- pusta lista pokazuje komunikat "Brak wykrytych tablic w tej sesji",
- lista odswieza sie po kolejnych wykryciach.

Karta: Foreground service dla detekcji

Opis: Skanowanie dziala jako foreground service z powiadomieniem systemowym i akcja zatrzymania.

Kryteria akceptacji:
- po starcie detekcji widoczne jest powiadomienie,
- przycisk w powiadomieniu zatrzymuje usluge.

Karta: Lokalna baza tablic i synchronizacja Supabase

Opis: Room przechowuje lokalne rekordy tablic, aplikacja potrafi pobrac aktualna baze z Supabase i zapisac ja lokalnie. Przy pustej bazie aplikacja probuje pobrac rekordy z Supabase, a uzytkownik moze wymusic reczna synchronizacje w ustawieniach.

Kryteria akceptacji:
- repozytorium potrafi znalezc tablice po numerze,
- aplikacja zna wersje lokalnej bazy,
- przycisk sprawdzania aktualizacji pobiera rekordy z Supabase i pokazuje wynik synchronizacji,
- blad synchronizacji nie usuwa ostatniej lokalnej kopii danych.

Karta: Alert po dopasowaniu tablicy

Opis: Po rozpoznaniu tablicy z lokalnej bazy aplikacja pokazuje powiadomienie i nakladke ekranowa.

Kryteria akceptacji:
- alert zawiera tablice, marke/model i region,
- alert nie powtarza sie stale dla tej samej tablicy,
- uzytkownik moze zamknac nakladke.

Karta: Ustawienia alertow

Opis: Ekran ustawien pozwala zmienic glosnosc alertu i wlaczyc lub wylaczyc wibracje.

Kryteria akceptacji:
- zmiana glosnosci zapisuje sie w ustawieniach,
- przelacznik wibracji zmienia stan ustawienia.

Karta: Testy jednostkowe logiki domenowej

Opis: Testy obejmuja walidacje tablic, wykrywanie kandydatow, sesje unikalnych tablic, cooldown alertow i wyszukiwanie w bazie.

Kryteria akceptacji:
- testy jednostkowe przechodza,
- najwazniejsze reguly biznesowe sa sprawdzane automatycznie.

### Testy i poprawki

Karta: Test kompilacji po kazdej wiekszej zmianie

Opis: Po zmianach w logice detekcji lub UI uruchomic build debug i testy jednostkowe.

Kryteria akceptacji:
- `./gradlew assembleDebug` konczy sie sukcesem,
- `./gradlew testDebugUnitTest` konczy sie sukcesem albo blad jest opisany w Trello.

Karta: Test na telefonie w realnym swietle

Opis: Sprawdzic aplikacje w warunkach dziennych i wieczornych. Zanotowac, czy kamera widzi tablice, czy OCR zwraca poprawne wyniki i czy alert jest czytelny.

Kryteria akceptacji:
- w Trello jest notatka z daty testu, telefonu i wyniku,
- zapisano problemy albo potwierdzono brak krytycznych bledow.

Karta: Kontrola falszywych odczytow OCR

Opis: Przejrzec logi i liste kandydatow OCR. Dodac reguly odrzucania najczestszych falszywych wynikow.

Kryteria akceptacji:
- falszywe wyniki sa opisane przykladami,
- nowe reguly nie odrzucaja poprawnych tablic z testow.

### Poza zakresem semestru

Karta: Integracja z kamera Viofo przez Wi-Fi

Opis: Pobieranie snapshotow z kamery Viofo przez Wi-Fi i przekazywanie ich do tego samego pipeline OCR.

Powod odlozenia: wymaga stabilnej integracji sprzetowej i testow z konkretnym modelem kamery.

Karta: Panel administracyjny i moderacja bazy tablic

Opis: Panel do dodawania, weryfikowania i wersjonowania rekordow w bazie Supabase oraz proces moderacji zmian.

Powod odlozenia: aplikacja ma juz synchronizacje z Supabase, ale pelny panel administracyjny i proces moderacji zwiekszaja zakres projektu.

Karta: Google Play Billing

Opis: Subskrypcja lub zakup jednorazowy dla wersji Pro.

Powod odlozenia: funkcja produktowa, niepotrzebna do udowodnienia glownego mechanizmu detekcji.

Karta: Wysylka zgloszen blednych alarmow do backendu

Opis: Przycisk "Zglos blad" wysyla raport do backendu razem z tablica, czasem i wersja bazy.

Powod odlozenia: w obecnym MVP zgloszenie jest lokalnym placeholderem.

Karta: Historia tras i statystyki dlugoterminowe

Opis: Zapisywanie historii sesji, liczby alertow, mapy trasy i statystyk wykryc w czasie.

Powod odlozenia: w semestrze wystarczy historia biezacej sesji i licznik unikalnych tablic.

## Tabela postepu do aktualizacji co tydzien

| Data | Co zrobiono | Co jest w toku | Problem / ryzyko | Nastepny krok |
| --- | --- | --- | --- | --- |
| 2026-05-11 | Przygotowano wymagania, zakres MVP i backlog Trello. Dodano liste unikalnych tablic po kliknieciu licznika. | Strojenie OCR i detekcji tablic. | Falszywe odczyty OCR na realnym obrazie. | Poprawic filtrowanie wynikow OCR na podstawie logow z telefonu. |

## Krotki opis do prezentacji

UnmarkedDetector to aplikacja Android wspierajaca kierowce w wykrywaniu tablic rejestracyjnych podczas jazdy. Telefon zamontowany przy szybie analizuje obraz z kamery, rozpoznaje tekst tablic, filtruje wyniki OCR i porownuje je z lokalna kopia bazy synchronizowana z Supabase. Gdy tablica zostanie dopasowana do rekordu w bazie, aplikacja pokazuje alert w formie powiadomienia oraz nakladki nad innymi aplikacjami. W semestrze skupiamy sie na MVP, w ktorym detekcja i alerty dzialaja lokalnie na telefonie, a baza moze byc aktualizowana z Supabase. Funkcje takie jak kamera Viofo, panel administracyjny bazy i platnosci zostaja zaplanowane jako dalszy rozwoj.
