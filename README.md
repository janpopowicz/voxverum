# VoxVerum

„Głos to nie dowód tożsamości. Sprawdź, zanim przelejesz.”

Prototyp aplikacji na Androida. W trakcie rozmowy WhatsApp słucha dźwięku z mikrofonu
i puszcza go przez dwa modele AI **na telefonie**:

1. **voice_clone**: czy głos rozmówcy może być sklonowany,
2. **scam**: czy rozmowa wygląda na próbę wyłudzenia.

Wynik widać na bieżąco na banerze nad rozmową (zielony, żółty albo czerwony), z wibracją
i powiadomieniem. Dźwięk nie jest zapisywany ani wysyłany poza urządzenie. Biznesplan: `VoxVerum_biznesplan.pdf`.

## Struktura repo
| Ścieżka | Co tam jest |
|---|---|
| `android/` | aplikacja (Kotlin, Jetpack Compose, ONNX Runtime) |
| `models/README.md` | **kontrakt dla modeli**: wejście, wyjście, plik `.json`, jak wpiąć model z PyTorch |
| `tools/model_export/` | `export_onnx.py` (PyTorch → ONNX + test zgodności), `make_mock_models.py` (atrapy) |

## Jak to działa i ograniczenia
- Android nie daje zwykłym aplikacjom dźwięku z rozmów VoIP. Jedyny wyjątek, z którego korzystamy, to
  **usługa ułatwień dostępu z widoczną nakładką**: wtedy system pozwala jej współdzielić mikrofon z rozmową.
- Mikrofon słyszy rozmówcę tylko wtedy, gdy rozmowa jest **na głośniku**.
- Google Play nie dopuszcza takich aplikacji (polityka z 2022 r.), więc dystrybuujemy **APK ręcznie**.
- Wykrycie rozmowy: zdarzenia z WhatsApp (okno lub powiadomienie o połączeniu) + tryb audio `MODE_IN_COMMUNICATION`.
- Teraz w aplikacji są **atrapy modeli** (reagują tylko na głośność). Prawdziwe modele: patrz `models/README.md`.

## Uruchamianie

### Wymagania
- Android Studio (z wbudowanym JDK), Android SDK 36.
- Telefon z Androidem 10+ i WhatsApp do testu rozmów. Emulator wystarcza do UI i trybu symulacji WAV.

### Android Studio (najprościej)
1. *File → Open* → wskaż folder `android/`. Poczekaj na synchronizację Gradle.
2. Na telefonie: *Ustawienia → Informacje o telefonie →* 7× „Numer kompilacji” → *Opcje programisty → Debugowanie USB*.
3. Podłącz telefon kablem, zaakceptuj komunikat o debugowaniu, kliknij ▶ **Run**.

### Z wiersza poleceń (Windows, Git Bash)
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd android && ./gradlew installDebug
```
Testy jednostkowe: `./gradlew testDebugUnitTest`. Test na urządzeniu (modele ONNX): `./gradlew connectedDebugAndroidTest`.
Logi: `adb logcat -s VoxVerum` (`adb` leży w `%LOCALAPPDATA%\Android\Sdk\platform-tools`).

### Pierwsze uruchomienie na telefonie
1. Zaakceptuj ekran zgody.
2. Na ekranie **Ochrona** zezwól na mikrofon i powiadomienia.
3. Kliknij **Włącz** przy usłudze ułatwień dostępu → *Zainstalowane aplikacje → VoxVerum – ochrona rozmów → włącz*.
   Na Androidzie 13+ przełącznik bywa wyszarzony („ustawienie ograniczone”). Wtedy: *Informacje o aplikacji → ⋮ → Zezwól na ustawienia ograniczone* i spróbuj ponownie.
4. Status zmieni się na „Ochrona aktywna”. Zadzwoń przez WhatsApp i włącz głośnik: pojawi się baner.

## Test wykonalności (do zrobienia na waszych telefonach)
To pierwsza rzecz do sprawdzenia, bo od niej zależy całe podejście:
1. Zakładka **Debug**: zostaw źródło `VOICE_RECOGNITION` i włącz „Zapisuj 10 s rozmowy do WAV” (tylko wersja debug).
2. Zrób rozmowę WhatsApp z kimś z zespołu (za zgodą obu stron), raz na głośniku, raz na słuchawce.
3. W trakcie obserwuj na ekranie Debug poziom dBFS i flagę „System WYCISZYŁ mikrofon”.
4. Ściągnij nagrania: `adb pull /sdcard/Android/data/pl.voxverum.app/files/debug` i odsłuchaj, czy słychać rozmówcę.
5. Powtórz z `MIC` i `UNPROCESSED`. Zapiszcie, co działa na jakim telefonie (model i wersja Androida).

## Tryb symulacji
Zakładka **Debug → Wybierz plik WAV** puszcza nagranie przez ten sam pipeline co rozmowę.
Tak testujemy modele bez dzwonienia, także na emulatorze (plik wrzucasz przez `adb push plik.wav /sdcard/Download/`).
