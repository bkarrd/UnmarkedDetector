# Biznesowa ocena ryzyka

Projekt: Detektor Nieoznakowanych  
Wersja dokumentu: 2.2  
Data: 2026-06-03  
Charakter projektu: aplikacja mobilna w wersji beta, przygotowana jako projekt z inżynierii oprogramowania

## Cel oceny ryzyka

Celem dokumentu jest ocena ryzyk biznesowych związanych z rozwojem, testowaniem, udostępnianiem i ewentualną publikacją aplikacji. Ocena nie skupia się wyłącznie na implementacji, ale na wpływie projektu na użytkowników, koszty, reputację, zgodność z regulaminami, licencje modeli AI, możliwość dalszego rozwoju i opłacalność produktu.

## Kontekst biznesowy

Aplikacja rozpoznaje tablice rejestracyjne z kamery telefonu, porównuje je z lokalną kopią bazy pojazdów synchronizowaną z Supabase i informuje użytkownika o dopasowaniu. Potencjalną grupą odbiorców są kierowcy zainteresowani ostrzeganiem o pojazdach z określonej bazy. Produkt jest na etapie wersji beta i nie jest jeszcze gotowy jako komercyjna usługa masowa.

W obecnej wersji detekcja i OCR działają lokalnie na telefonie i korzystają z dwóch modeli TFLite. Dane tablic są cache'owane lokalnie w Room, a aktualizacja bazy odbywa się z Supabase przy inicjalizacji pustej bazy oraz ręcznie z poziomu ustawień.

| Obszar | Asset w aplikacji | Model / źródło | Licencja źródła | Znaczenie biznesowe |
| --- | --- | --- | --- | --- |
| Detekcja tablic | `license-plate-v1n-fp16.tflite` | YOLOv11, model bazujący na `morsetechlab/yolov11-license-plate-detection` / ekosystemie Ultralytics YOLO | AGPL-3.0 według karty modelu Hugging Face i polityki licencyjnej Ultralytics | Ryzyko wysokie dla zamkniętej komercyjnej aplikacji. AGPL nie zakazuje użycia komercyjnego, ale może wymagać udostępnienia kodu źródłowego i większego dzieła na tej samej licencji. Dla produktu zamkniętego potrzebna jest licencja Enterprise albo wymiana modelu na model z licencją permissive. |
| OCR tablic | `cct_s_v2_global_float32.tflite` | `ankandrew/fast-plate-ocr`, wariant `cct-s-v2-global` wyeksportowany do TFLite | MIT | Ryzyko niskie. MIT dopuszcza użycie komercyjne, modyfikację i dystrybucję, pod warunkiem zachowania informacji o licencji i prawach autorskich. |

Uwaga: powyższa ocena nie jest poradą prawną. Przed komercjalizacją należy potwierdzić licencje modeli, wag, procesu konwersji do TFLite oraz ewentualnych danych treningowych.

## Interesariusze

| Interesariusz | Interes / oczekiwanie |
| --- | --- |
| Użytkownik końcowy | Chce otrzymywać szybkie, czytelne i wiarygodne alerty podczas jazdy. |
| Autor projektu | Chce dostarczyć działający projekt akademicki i potencjalnie rozwinąć go w produkt. |
| Prowadzący przedmiot | Ocenia kompletność wymagań, testów, dokumentacji i analizę ryzyka. |
| Google Play / dystrybutor aplikacji | Wymaga zgodności z politykami prywatności, uprawnień i pracy w tle. |
| Twórcy i właściciele modeli AI | Oczekują przestrzegania licencji modeli, wag i kodu treningowego. |
| Osoby testujące aplikację | Oczekują łatwej instalacji, jasnej instrukcji i braku problemów z telefonem. |
| Potencjalni partnerzy / dostawcy danych | Oczekują wiarygodnego i zgodnego z prawem sposobu użycia bazy tablic. |
| Dostawca infrastruktury Supabase | Zapewnia dostępność, bezpieczeństwo i limity usługi używanej do aktualizacji bazy. |

## Skala oceny

| Parametr | Skala |
| --- | --- |
| Prawdopodobieństwo | 1 - bardzo niskie, 2 - niskie, 3 - średnie, 4 - wysokie, 5 - bardzo wysokie |
| Wpływ biznesowy | 1 - mały, 2 - umiarkowany, 3 - średni, 4 - duży, 5 - krytyczny |
| Poziom ryzyka | Prawdopodobieństwo x Wpływ biznesowy |

Interpretacja:
- 1-5: ryzyko niskie,
- 6-10: ryzyko średnie,
- 11-15: ryzyko wysokie,
- 16-25: ryzyko krytyczne.

## Rejestr ryzyk biznesowych

| ID | Obszar | Ryzyko | P | W | Poziom | Wpływ biznesowy | Działania ograniczające |
| --- | --- | --- | ---: | ---: | ---: | --- | --- |
| B-01 | Prawo i prywatność | Aplikacja przetwarza obraz z kamery i lokalizację, co może zostać uznane za dane wymagające szczególnego wyjaśnienia. | 4 | 5 | 20 | Ryzyko naruszenia zaufania użytkowników, problemów z publikacją i konieczności wycofania aplikacji. | Jasny onboarding, polityka prywatności, lokalne przetwarzanie obrazu, minimalizacja zbieranych danych, brak wysyłania obrazu na serwer w wersji beta. |
| B-02 | Google Play | Aplikacja używa kamery, lokalizacji, foreground service i opcjonalnego overlay, więc może wymagać dodatkowego uzasadnienia w Play Console. | 4 | 5 | 20 | Odrzucenie aplikacji lub opóźnienie publikacji. | Przygotować opis funkcji, formularz Data Safety, politykę prywatności i uzasadnienie użycia foreground service. |
| B-03 | Licencje modeli AI | Detektor YOLOv11 jest oparty o model/ekosystem z licencją AGPL-3.0. Zamknięta komercyjna aplikacja może być niezgodna z warunkami licencji bez dodatkowej licencji komercyjnej. | 4 | 5 | 20 | Ryzyko blokujące publikację komercyjną, konieczność otwarcia kodu aplikacji, zakupu licencji Enterprise albo wymiany modelu detekcji. | Nie komercjalizować obecnego wariantu bez audytu licencyjnego. Przed publikacją wybrać jedną ścieżkę: licencja Enterprise/zgoda właściciela, model detekcji z licencją permissive, albo publikacja projektu na warunkach zgodnych z AGPL. |
| B-04 | Reputacja | Fałszywe alarmy lub błędne odczyty tablic mogą obniżyć zaufanie do aplikacji. | 4 | 4 | 16 | Użytkownicy mogą przestać korzystać z aplikacji albo negatywnie ją ocenić. | Oznaczenie wersji beta, komunikowanie ograniczeń, testy terenowe, zbieranie przykładów błędów OCR, dalsze strojenie modeli. |
| B-05 | Bezpieczeństwo użytkownika | Aplikacja używana podczas jazdy może rozpraszać kierowcę. | 3 | 5 | 15 | Ryzyko wypadku, negatywnej opinii i odpowiedzialności wizerunkowej. | Minimalny UI, alerty dźwiękowe i wibracyjne, komunikat o bezpiecznym użyciu, rekomendacja obsługi przez pasażera lub na postoju. |
| B-06 | Akceptacja użytkowników | Użytkownicy mogą nie chcieć nadawać wielu uprawnień, szczególnie kamery, lokalizacji i nakładki. | 4 | 4 | 16 | Niższa konwersja instalacji do aktywnego użycia. | Proste wyjaśnienia w onboardingu, overlay jako opcja, jasne rozróżnienie uprawnień wymaganych i opcjonalnych. |
| B-07 | Wartość produktu | Użytkownicy mogą nie uznać aplikacji za wystarczająco użyteczną, jeśli baza w Supabase nie będzie aktualna i wiarygodna. | 3 | 5 | 15 | Brak sensu biznesowego produktu mimo działającej technologii. | Utrzymywać bazę w Supabase, wersjonowanie bazy, ręczną synchronizację w aplikacji i jasne kryteria dodawania rekordów. |
| B-08 | Dane i jakość bazy | Lokalna kopia bazy może być nieaktualna względem Supabase albo zawierać błędne rekordy. | 4 | 4 | 16 | Fałszywe dopasowania, brak alertów i spadek zaufania. | Synchronizacja z Supabase, procedura aktualizacji, testy rekordów, oznaczanie źródła danych i komunikat błędu przy nieudanej synchronizacji. |
| B-09 | Pochodzenie modeli i danych treningowych | Publiczny model z Hugging Face może mieć niepełną dokumentację danych treningowych, wersji wag albo procesu konwersji. | 3 | 4 | 12 | Trudność w audycie, ryzyko konieczności wymiany modelu przed publikacją, problem z due diligence dla partnerów. | Archiwizować karty modeli, licencje, wersje wag i checksumy. Przed publikacją sprawdzić pochodzenie danych treningowych lub wytrenować własny model na danych z jasną licencją. |
| B-10 | Koszty utrzymania | Rozwój OCR, detekcji, testów na wielu telefonach i aktualizacji bazy może wymagać więcej czasu niż zakładano. | 4 | 3 | 12 | Projekt może być trudny do utrzymania jednoosobowo. | Ograniczyć zakres wersji beta, dokumentować testy, automatyzować buildy, priorytetyzować krytyczne funkcje. |
| B-11 | Monetyzacja | Użytkownicy mogą nie chcieć płacić za aplikację, zwłaszcza jeśli skuteczność OCR nie będzie stabilna. | 3 | 4 | 12 | Brak przychodu albo niska opłacalność komercjalizacji. | Najpierw walidacja darmowej bety, ankiety wśród testerów, pomiar użycia, dopiero potem model płatności. |
| B-12 | Publikacja poza sklepem | Ręczne udostępnianie APK kolegom może budzić obawy bezpieczeństwa i utrudniać aktualizacje. | 3 | 3 | 9 | Testerzy mogą nie zainstalować aplikacji lub używać starej wersji. | Instrukcja instalacji, podpisany wariant friend beta, jasna nazwa pliku, docelowo test wewnętrzny Google Play. |
| B-13 | Zgodność z oczekiwaniami akademickimi | Projekt może zostać oceniony niżej, jeśli dokumentacja będzie zbyt techniczna albo nie pokaże procesu IO. | 3 | 4 | 12 | Ryzyko niższej oceny mimo działającej aplikacji. | Osobne dokumenty wymagań, testów i biznesowego ryzyka, scenariusze testowe z wynikami, opis zakresu beta. |
| B-14 | Konkurencja / alternatywy | Użytkownicy mogą korzystać z gotowych rozwiązań lub uznać, że aplikacja nie daje unikalnej wartości. | 2 | 3 | 6 | Ograniczony potencjał rynkowy. | Skupić się na niszy, prostocie użycia i lokalnym działaniu offline. |
| B-15 | Skalowanie produktu | Rozszerzenie o panel administracyjny, moderację bazy, monitoring i płatności zwiększy koszty oraz złożoność organizacyjną. | 3 | 4 | 12 | Ryzyko przeciążenia projektu i opóźnień. | Etapowanie rozwoju: beta z Supabase, testy, panel administracyjny, publikacja, monetyzacja. |
| B-16 | Odpowiedzialność za sposób użycia | Aplikacja może zostać użyta w sposób niezgodny z intencją autora. | 2 | 5 | 10 | Ryzyko reputacyjne i prawne. | Regulamin, opis dozwolonego użycia, brak funkcji masowego śledzenia, lokalne przetwarzanie w wersji beta. |
| B-17 | Dostępność na różnych telefonach | Różne modele Androida mogą inaczej ograniczać kamerę, baterię i powiadomienia. | 4 | 3 | 12 | Więcej zgłoszeń problemów od testerów, większy koszt wsparcia. | Testy na kilku telefonach, komunikaty diagnostyczne, status ograniczeń baterii w powiadomieniu. |
| B-18 | Infrastruktura Supabase | Awaria, limit darmowego planu, błędna konfiguracja RLS albo ujawnienie klucza anon może utrudnić aktualizację bazy. | 3 | 4 | 12 | Użytkownicy mogą działać na starej bazie, a projekt może wymagać dodatkowej administracji i kosztów. | Użyć klucza anon tylko z właściwymi regułami RLS, monitorować limity, zachować lokalny cache, nie usuwać lokalnej bazy po błędzie synchronizacji. |

## Najważniejsze ryzyka biznesowe

Najwyższy priorytet mają ryzyka:
1. zgodność z prywatnością i zasadami Google Play,
2. licencja detektora YOLOv11 i możliwość komercyjnego użycia zamkniętej aplikacji,
3. zaufanie użytkowników do jakości OCR i alertów,
4. bezpieczeństwo użycia podczas jazdy,
5. akceptacja wymaganych uprawnień,
6. jakość, aktualność i bezpieczeństwo bazy tablic w Supabase.

Te ryzyka mogą bezpośrednio zdecydować, czy aplikacja będzie mogła zostać pokazana szerszej grupie użytkowników, opublikowana w sklepie lub rozwijana jako produkt.

## Strategia biznesowa ograniczania ryzyka

| Etap | Działanie | Cel |
| --- | --- | --- |
| Beta akademicka | Udostępnienie APK małej grupie testerów. | Sprawdzenie użyteczności bez kosztów publikacji. |
| Audyt licencyjny modeli | Sprawdzenie licencji YOLOv11, FastPlateOCR, wag TFLite i danych treningowych. | Uniknięcie sytuacji, w której działająca aplikacja nie może zostać legalnie skomercjalizowana. |
| Testy użytkowników | Zebranie opinii o jakości OCR, alertach, baterii i onboardingu. | Walidacja wartości produktu. |
| Przygotowanie publikacji | Polityka prywatności, Data Safety, opis uprawnień, target SDK, licencje open-source. | Zmniejszenie ryzyka odrzucenia przez Google Play i ryzyka prawnego. |
| Rozwój danych | Utrzymanie bazy w Supabase, ręczna synchronizacja w aplikacji i procedura dodawania rekordów. | Zwiększenie wiarygodności alertów. |
| Decyzja o modelu biznesowym | Wybór między projektem open-source, licencją komercyjną modeli albo wymianą detektora. | Dopasowanie licencji technologii do planowanej dystrybucji. |
| Decyzja o monetyzacji | Ankieta lub test zainteresowania płatnością po wersji beta. | Uniknięcie budowania płatności bez potwierdzonego popytu. |

## Ryzyka zaakceptowane w wersji beta

| Ryzyko | Powód akceptacji |
| --- | --- |
| Aplikacja nie jest jeszcze publikowana w Google Play. | Na etapie projektu IO wystarczy testowe udostępnienie małej grupie. |
| Detektor YOLOv11 ma licencję AGPL-3.0. | Ryzyko jest akceptowalne tylko dla wersji akademickiej/beta. Nie należy traktować obecnego wariantu jako gotowego do zamkniętej komercyjnej publikacji. |
| OCR nie jest perfekcyjny w każdych warunkach. | Wersja beta służy do zebrania przykładów błędów i dalszego strojenia. |
| Brak pełnego panelu administracyjnego dla bazy Supabase. | Projekt ma działającą synchronizację bazy, ale wersja beta nie musi jeszcze mieć procesu moderacji, audytu i panelu dla wielu administratorów. |
| Brak gotowego modelu płatności. | Monetyzacja nie jest wymagana do zaliczenia projektu i powinna wynikać z testów użytkowników. |

## Wymagane działania przed komercjalizacją

1. Przygotować politykę prywatności opisującą kamerę, lokalizację, powiadomienia, overlay i lokalne przetwarzanie.
2. Wypełnić Data Safety w Google Play Console zgodnie z faktycznym użyciem danych.
3. Przygotować opis uprawnień i foreground service zgodny z funkcją aplikacji.
4. Przeprowadzić testy z kilkoma użytkownikami i zebrać feedback.
5. Opisać źródła danych, proces dodawania rekordów do Supabase, reguły moderacji i sposób wersjonowania bazy.
6. Doprecyzować regulamin i zastrzeżenie dotyczące bezpiecznego używania aplikacji podczas jazdy.
7. Zdecydować, czy produkt ma być darmowy, płatny jednorazowo, subskrypcyjny czy tylko demonstracyjny.
8. Wykonać audyt licencji modeli AI i zachować kopie licencji oraz kart modeli użytych w wydaniu.
9. Dla detektora YOLOv11 wybrać jedną ścieżkę: kupić/uzyskać licencję komercyjną, wymienić model na permissive, albo opublikować aplikację na warunkach zgodnych z AGPL.
10. Dla FastPlateOCR dołączyć wymagane informacje o licencji MIT i prawach autorskich w dokumentacji aplikacji lub ekranie informacji prawnych.
11. Zweryfikować konfigurację bezpieczeństwa Supabase, szczególnie RLS, zakres klucza anon i limity planu.

## Źródła i odniesienia

Przy ocenie ryzyk publikacji uwzględniono aktualne zasady Google Play dotyczące danych użytkownika, uprawnień i foreground service:
- Google Play User Data Policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play permissions and APIs policy: https://support.google.com/googleplay/android-developer/answer/16810878

Przy ocenie ryzyk licencyjnych modeli uwzględniono:
- morsetechlab/yolov11-license-plate-detection, karta modelu Hugging Face, licencja AGPL-3.0: https://huggingface.co/morsetechlab/yolov11-license-plate-detection
- Ultralytics License, informacja o AGPL-3.0 dla modeli YOLO i licencji Enterprise: https://www.ultralytics.com/license
- ankandrew/fast-plate-ocr, repozytorium GitHub z licencją MIT: https://github.com/ankandrew/fast-plate-ocr
- GNU AGPLv3, opis warunków: https://choosealicense.com/licenses/agpl-3.0/
- MIT License, opis warunków: https://choosealicense.com/licenses/mit/

## Wniosek

Największe ryzyko biznesowe projektu nie wynika wyłącznie z jakości OCR, ale z połączenia zaufania użytkowników, jakości bazy danych w Supabase, bezpieczeństwa użycia podczas jazdy, zgodności z zasadami prywatności oraz licencji modeli AI. Obecna wersja beta nadaje się do demonstracji i testów z małą grupą użytkowników, ale przed publikacją komercyjną wymaga dopracowania dokumentów prawnych, procesu utrzymania bazy i decyzji licencyjnej dotyczącej detektora YOLOv11.
