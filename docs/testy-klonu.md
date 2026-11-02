# Test na sklonowanym głosie (2026-11-02)

Pytanie: czy aplikacja z Mother Voice v1 odróżni prawdziwy głos od klonu tego samego głosu w warunkach
zbliżonych do rozmowy WhatsApp na głośniku?

## Jak testowaliśmy
Wszystko liczyło się lokalnie na laptopie (CPU). Nagranie głosu i klony nie opuściły komputera i nie ma ich w repo.

1. **Oryginał**: 15,5 s nagrania głosu Ignacego (z wideo, `extract.py`).
2. **Klony** tego głosu, po 6 zdań (scenariusz „na wnuczka” i zdania codzienne, `teksty.txt`):
   - **Chatterbox Multilingual**: generator, na którym Jan trenował Mother Voice (widziany),
   - **XTTS-v2** (Coqui): generator, którego model nie widział.
3. **Czy klon jest przekonujący** (`podobienstwo.py`): cosinus embeddingów mówcy względem oryginału.
   Dwie połowy oryginału dają 0,930 (ten sam człowiek), a obcy głos syntezatora Windows 0,624.
   Chatterbox daje **0,931**, a XTTS **0,888**. Oba klony brzmią jak Ignacy.
4. **Kanał** (`ocena.py`), czyli każde nagranie w 4 wersjach:
   - `czysty`: nagranie bez zmian,
   - `whatsapp`: kodek Opus 16 kb/s,
   - `glosnik`: Opus, głośnik telefonu, pogłos pokoju, szum mikrofonu i AGC, czyli tak aplikacja słyszy rozmówcę,
   - `przy_mik`: pogłos, szum i AGC bez kodeka, czyli tak aplikacja słyszy właściciela telefonu.
5. **Pipeline jak w aplikacji**: okna 4 s co 1 s, VAD z odrzucaniem tonów na ostatnich 3 s, model, `RiskEngine`.

## Wyniki

| Źródło | Okna | Czerwony alarm (pliki) | Maks. wynik |
|---|---|---|---|
| prawdziwy głos, każdy z 4 kanałów | 12 na kanał | **0/1** we wszystkich | 0,063 |
| Chatterbox: czysty / whatsapp / przy_mik | 23 | **6/6** | 1,000 |
| Chatterbox: głośnik | 23 | **4/6** | 0,999 |
| XTTS: każdy z 4 kanałów | 14 | **0/6** we wszystkich | 0,377 |

Scenariusz rozmowy (`scenariusz.py`): sygnał wołania 5 s, potem prawdziwy głos, potem klon XTTS przejmuje
rozmowę, wszystko przez kanał `glosnik`. Wołanie zostaje pominięte, prawdziwy głos dostaje OK, a klon XTTS
przez całe 18 s ma wynik 0,000 i aplikacja zostaje na OK.

### Wnioski
- **Fałszywych alarmów na prawdziwym głosie nie było**, także po symulacji głośnika. To jednak tylko 12 okien
  jednej osoby, więc za mało, żeby mówić o odsetku fałszywych alarmów.
- **Klon z generatora znanego z treningu jest wykrywany.** Chatterbox odpala czerwony alarm także po kodeku
  i (w 4 na 6 przypadków) po symulacji głośnika.
- **Klon z nieznanego generatora (XTTS) przechodzi niezauważony**, choć dla systemu rozpoznawania mówcy brzmi
  jak Ignacy. To główne ograniczenie Mother Voice v1: model rozpoznaje generatory z treningu, a nie
  „syntetyczność” w ogóle. Jan sam pisze o tym w `INTEGRATION.md` jako o ocenie do zrobienia.
- Dlatego aplikacja **nie pokazuje już zielonego** „Nie wykryto zagrożenia”. W trakcie rozmowy baner jest granatowy:
  „Brak oznak klonu głosu. To nie potwierdza, kto mówi”, z przypomnieniem o Kodzie Rozmowy.

### Poprawki w aplikacji z tego testu
- VAD: stały ton musi trwać ≥ 500 ms, a nie ≥ 300 ms. Przy 300 ms odpadało do 5 z 12 okien prawdziwej mowy
  (przeciągnięte samogłoski z pogłosem). Przy 500 ms nie odpada żadne okno mowy, a sygnał wołania (1 s) nadal tak.
- Brak zieleni w trakcie rozmowy, brak procentów w historii, większy tekst baneru i odczyt przez TalkBack.

## Jak powtórzyć
```bash
cd C:/Users/<ty>/voxtest   # katalog POZA repo, tu leżą nagrania
uv venv -p 3.11 env-tools && uv pip install -p env-tools/Scripts/python.exe av numpy scipy soundfile onnxruntime
uv venv -p 3.11 env-xtts && uv pip install -p env-xtts/Scripts/python.exe coqui-tts six soundfile
uv pip install -p env-xtts/Scripts/python.exe torch torchaudio --index-url https://download.pytorch.org/whl/cpu
uv venv -p 3.11 env-chatterbox && uv pip install -p env-chatterbox/Scripts/python.exe chatterbox-tts "setuptools<81"
T=<repo>/tools/clone_tests; M=<repo>/android/app/src/main/assets/models/voice_clone.onnx
env-tools/Scripts/python $T/extract.py nagranie.mp4 dane/oryginal_24k.wav
env-xtts/Scripts/python $T/klon_xtts.py dane/oryginal_24k.wav dane/klon
env-chatterbox/Scripts/python $T/klon_chatterbox.py dane/oryginal_24k.wav dane/klon
env-tools/Scripts/python $T/ocena.py --model $M --etykieta prawdziwy dane/oryginal_24k.wav
env-tools/Scripts/python $T/ocena.py --model $M --etykieta klon dane/klon/*.wav
env-tools/Scripts/python $T/scenariusz.py --model $M --prawdziwy dane/oryginal_24k.wav --klon dane/klon/xtts_0.wav
env-chatterbox/Scripts/python $T/podobienstwo.py dane/oryginal_24k.wav dane/klon/*.wav
```
Pobierane jest ok. 5 GB modeli z HuggingFace. Klonowanie na CPU zajmuje 1–5 min na zdanie.
`ocena.py --zapisz-kanaly dir` zapisuje wersje po kanale, które można puścić w aplikacji (Debug → symulacja WAV).
Klonuj tylko własny głos albo głos osoby, która się na to zgodziła.

## Plan kolejnych testów

### Na telefonie (najważniejsze, bo tego nie da się zasymulować)
1. **Prawdziwa rozmowa, prawdziwy głos.** WhatsApp na głośniku przez 2–3 min, potem to samo na słuchawce.
   Podczas rozmowy `adb logcat -s VoxVerum` pokazuje dla każdego okna `mowa`, `ton` i `klon`.
   Oczekiwane: brak czerwonego alarmu, a wołanie pomijane (`klon=-`).
2. **Atak przez odtworzenie klonu.** Kolega puszcza z laptopa do swojego telefonu pliki Chatterbox i XTTS w trakcie
   rozmowy. Oczekiwane według testu offline: Chatterbox daje alarm, XTTS nie. Jeśli Chatterbox też przejdzie,
   problemem jest prawdziwy kanał (głośnik, pokój), a nie tylko generator.
3. **Czas inferencji i bateria.** Ekran Debug → `czas` (budżet to 1 s na okno) przy 10-minutowej rozmowie.
   Do tego temperatura i zużycie baterii w ustawieniach.
4. **Źródło mikrofonu.** `VOICE_RECOGNITION`, `MIC` i `UNPROCESSED` na 2–3 różnych telefonach: który daje
   najczystszy głos rozmówcy i najmniej fałszywych alarmów.
5. **Notatka głosowa WhatsApp.** Czy jej odtwarzanie uruchamia analizę (`MODE_IN_COMMUNICATION`).

### Model (dla Jana)
1. **Nieznane generatory**: XTTS-v2 przeszedł w 100%. Dodać do treningu albo przynajmniej do testu z wydzielonym
   generatorem: XTTS, F5-TTS, OpenVoice, Fish Speech, a komercyjnie ElevenLabs.
2. **Konwersja głosu na żywo (RVC, so-vits-svc)**: oszust mówi sam, a program zamienia jego głos w głos ofiary.
   Przy rozmowach na żywo to bardziej prawdopodobny atak niż TTS i trzeba go przetestować.
3. **Więcej prawdziwych głosów**: po 1–2 min od każdej osoby z zespołu i rodziny (za zgodą), różne telefony.
   Wtedy da się policzyć odsetek fałszywych alarmów.
4. **Prawdziwy kanał głośnika**: nagrać klony i prawdziwe głosy odtworzone przez głośnik telefonu i nagrane drugim
   telefonem. Taki zbiór lepiej sprawdza model niż symulacja i nadaje się też do augmentacji.

### Aplikacja i UI
1. Sprawdzić baner i ekrany przy dużej czcionce systemowej (130–200%) i w trybie ciemnym.
2. TalkBack: czy zmiana baneru na czerwony jest odczytywana (tytuł ma `accessibilityLiveRegion`).
3. Gdy wykrywanie wyłudzeń dostanie model: test tego samego scenariusza „na wnuczka” na tekstach z `teksty.txt`.
