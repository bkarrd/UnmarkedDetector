# Udostępnianie aplikacji kolegom

Wersja: 0.1.0-beta

## Plik do wysłania

Do ręcznego testowania poza Google Play używaj APK generowanego komendą:

```powershell
.\gradlew.bat :app:exportFriendBetaApk --console=plain
```

Gotowy plik pojawi się tutaj:

```text
dist/detektor-nieoznakowanych-0.1.0-beta-friend-beta.apk
```

Ten wariant jest podpisany lokalnym debug keystore, ale nie jest debuggable. Nadaje się do wysłania kilku testerom przez Dysk Google, OneDrive, Telegram, Discord albo kabel USB.

## Instrukcja dla testera

1. Pobierz plik APK na telefon z Androidem.
2. Otwórz APK z aplikacji Pliki, Dysk albo komunikatora.
3. Jeśli Android zapyta o zgodę na instalowanie z tego źródła, wybierz ustawienia i zezwól tylko dla tej jednej aplikacji źródłowej.
4. Zainstaluj aplikację.
5. Przy pierwszym uruchomieniu nadaj wymagane uprawnienia:
   - kamera,
   - lokalizacja,
   - powiadomienia.
6. Opcjonalnie włącz alert nad innymi aplikacjami.
7. W ustawieniach baterii ustaw aplikację jako nieograniczoną, jeśli telefon agresywnie ubija aplikacje w tle.
8. Uruchom skanowanie przyciskiem Start.

## Co tester ma sprawdzić

| Obszar | Co zanotować |
| --- | --- |
| Uruchomienie | Czy aplikacja startuje i przechodzi onboarding. |
| Uprawnienia | Czy komunikaty są jasne i czy aplikacja obsługuje odmowę. |
| Kamera | Czy podgląd działa po kliknięciu Start. |
| Tło | Czy skanowanie działa po przejściu do ekranu głównego albo nawigacji. |
| OCR | Jakie tablice zostały odczytane poprawnie, a jakie błędnie. |
| Alert | Czy powiadomienie i dźwięk/wibracja są zauważalne. |
| Bateria | Czy telefon pokazuje ograniczenie działania w tle. |

## Ważne ograniczenia

- To wersja beta do testów, nie finalna publikacja Google Play.
- APK jest podpisany kluczem z komputera autora. Kolejne aktualizacje dla testerów muszą być budowane na tym samym komputerze albo tym samym kluczem.
- Jeśli tester później zainstaluje wersję z Google Play podpisaną innym kluczem, może być konieczne odinstalowanie tej wersji testowej.
- Testy skanowania należy wykonywać bez rozpraszania kierowcy. Najlepiej, żeby telefon obsługiwał pasażer albo żeby test odbywał się na postoju.
