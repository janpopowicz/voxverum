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

Rozmowa na zmianę (właściciel ok. 2,5 s przy mikrofonie, potem klon przez głośnik, 6 wymian):

| Klon | Okna ≥ próg | Wynik aplikacji |
|---|---|---|
| Chatterbox | 19% (bez głosu właściciela było ok. 100%) | czerwony alarm po 19 s |
| XTTS | 0% | OK |

Głos właściciela w tym samym oknie rozcieńcza sygnał klonu, więc alarm przychodzi później. Jeden mikrofon nie pozwala
oddzielić rozmówcy od właściciela. Pomóc mogłoby ocenianie tylko fragmentów, w których mówi rozmówca (diaryzacja
albo wzór głosu właściciela zapisany przy konfiguracji), ale to temat na później.

### Wnioski
- **Fałszywych alarmów na prawdziwym głosie nie było**, także po symulacji głośnika. To jednak tylko 12 okien
  jednej osoby, więc za mało, żeby mówić o odsetku fałszywych alarmów.
- **Klon z generatora znanego z treningu jest wykrywany.** Chatterbox odpala czerwony alarm także po kodeku
  i (w 4 na 6 przypadków) po symulacji głośnika.
- **Klon z nieznanego generatora (XTTS) przechodzi niezauważony**, choć dla systemu rozpoznawania mówcy brzmi
  jak Ignacy. To główne ograniczenie Mother Voice v1: model rozpoznaje generatory z treningu, a nie
  „syntetyczność” w ogóle. Jan sam pisze o tym w `INTEGRATION.md` jako o ocenie do zrobienia.
- Dlatego aplikacja **nie pokazuje już zielonego** „Nie wykryto zagrożenia”. W trakcie rozmowy baner jest granatowy:
  „Brak oznak klonu głosu. To nie potwierdza, kto mówi”, z przypomnieniem o potwierdzeniu prośby innym kanałem.

### Poprawki w aplikacji z tego testu
- VAD: stały ton musi trwać ≥ 500 ms, a nie ≥ 300 ms. Przy 300 ms odpadało do 5 z 12 okien prawdziwej mowy
  (przeciągnięte samogłoski z pogłosem). Przy 500 ms nie odpada żadne okno mowy, a sygnał wołania (1 s) nadal tak.
- Brak zieleni w trakcie rozmowy, brak procentów w historii, większy tekst baneru i odczyt przez TalkBack.

## Obce prawdziwe głosy i klony obcych głosów (2026-11-03)

Pytanie: ile fałszywych alarmów daje Mother Voice v1 na prawdziwych głosach innych ludzi i czy klon XTTS
przechodzi też przy innych głosach niż Ignacy?

- **Prawdziwe głosy**: 338 nagrań polskiej mowy czytanej z FLEURS (zbiór dev, wielu mówców, różne mikrofony).
- **Klony obcych głosów**: XTTS-v2, 8 mówców z FLEURS jako wzorzec, po 2 zdania (16 plików).
- Ten sam pipeline co wyżej (`ocena.py`, 4 kanały). Wyniki każdego okna są w JSONL, więc reguły alarmu
  porównujemy na tych samych wynikach modelu.

### Odsetek okien z wynikiem ≥ progu (0,572)

| Kanał | FLEURS prawdziwe | XTTS obcych głosów | Ignacy prawdziwy | Chatterbox (Ignacy) |
|---|---|---|---|---|
| czysty | **99,9%** | 42,9% | 0% | 100% |
| whatsapp | **28,5%** | 38,1% | 0% | 100% |
| głośnik | 0,0% | 0,0% | 0% | 39,1% |
| przy_mik | **17,4%** | 0,0% | 0% | 100% |

**Model ma bardzo dużo fałszywych alarmów na nieznanych prawdziwych głosach.** Na czystym nagraniu FLEURS
prawie każde okno wygląda na klon. Po kodeku albo po pogłosie z szumem wciąż co 4–6 okno przekracza próg.
Głos Ignacego nie daje żadnego trafienia, więc test na jednej osobie (wyżej) był zbyt optymistyczny.
Trafienia skupiają się w części nagrań: w kanale `przy_mik` 198 z 337 plików nie ma żadnego trafienia, a 64 mają je
w co najmniej połowie okien. Model reaguje więc na cechy nagrania (mikrofon, pomieszczenie, mówca), a nie na
syntetyczność.

### Reguła alarmu: „2 z 3 okien” kontra dwa niezależne trafienia

Okna mają 4 s z krokiem 1 s, a model widzi ostatnie 3 s, więc trzy kolejne okna oceniają prawie ten sam dźwięk.
„2 z 3 okien” w praktyce oznaczało „jeden fragment dźwięku”. Nowa reguła (`RiskEngine`) wymaga dwóch trafień
odległych o co najmniej 3 okna w ostatnich 6.

| Czerwony alarm (odsetek plików) | 2 z 3 | nowa reguła |
|---|---|---|
| FLEURS prawdziwe, przy_mik | 27,3% | **6,8%** |
| FLEURS prawdziwe, whatsapp | 33,8% | **14,9%** |
| FLEURS prawdziwe, czysty | 89,2% | 60,1% |
| FLEURS prawdziwe, głośnik | 0% | 0% |
| XTTS obcych głosów, czysty / whatsapp | 37,5% / 43,8% | 0% / 0% |
| Chatterbox (Ignacy), wszystkie zdania po kolei, każdy kanał | alarm | alarm |
| XTTS (Ignacy), każdy kanał | brak | brak |

Pliki mają 4–20 s, a rozmowa trwa minuty. Symulacja minutowej rozmowy (60 okien prawdziwych głosów FLEURS
sklejonych losowo) daje czerwony alarm w kanale `przy_mik` w **81%** rozmów przy nowej regule i 98% przy starej.
Kanał `glosnik` daje 0%. Wniosek: **nowa reguła zmniejsza fałszywe alarmy, ale problemu nie rozwiązuje.**
Przyczyna leży w modelu, nie w regule.

### Na co reaguje model: pasmo powyżej 7 kHz
`domena.py` ocenia te same 60 plików FLEURS (225 okien, czysty kanał) po jednej zmianie naraz:

| Wariant | Okna ≥ progu |
|---|---|
| bez zmian | 100% |
| filtr górnoprzepustowy 300 Hz | 100% |
| głośniej o 12 dB / ciszej o 12 dB | 100% / 99% |
| **filtr dolnoprzepustowy 7 kHz** | **21%** |
| pasmo 300 Hz – 7 kHz | 3% |

Fałszywe alarmy bierze więc głównie z wąskiego pasma 7–8 kHz, a nie z głośności ani niskich tonów. Ten sam filtr na klonach:

| Okna ≥ progu (czysty kanał) | bez filtra | filtr 7 kHz |
|---|---|---|
| Chatterbox (Ignacy) | 100% | 100% |
| XTTS (Ignacy) | 0% | 29% |
| XTTS obcych głosów | 43% | 81% |
| Ignacy prawdziwy | 0% | 0% |

### Filtr 7 kHz w aplikacji (`lowpass_hz` w `voice_clone.json`)
Pełna ocena z filtrem liczonym dla każdego okna, tak jak w telefonie (`ocena.py`, nowa reguła alarmu).
Liczą się kanały, które słyszy aplikacja: `przy_mik` (właściciel) i `glosnik` (rozmówca).

| | bez filtra | filtr 7 kHz |
|---|---|---|
| FLEURS prawdziwe, przy_mik: okna ≥ progu | 17,4% | **2,1%** |
| FLEURS prawdziwe, przy_mik: minutowe rozmowy z czerwonym | 82% | **15%** |
| FLEURS prawdziwe, głośnik | 0% | 0% |
| Chatterbox (Ignacy), przy_mik: okna ≥ progu | 100% | 100% |
| Chatterbox (Ignacy), głośnik: okna ≥ progu | 39% | 9% (wszystkie zdania po kolei nadal dają alarm) |
| XTTS (Ignacy i obce głosy), przy_mik / głośnik | 0% | 0% |
| Ignacy prawdziwy, każdy kanał | 0% | 0% |

Filtr zmniejsza fałszywe alarmy na głosie właściciela ponad 5 razy, ale osłabia wykrywanie Chatterboxa przez głośnik.
Przy 82% minutowych rozmów z fałszywym alarmem aplikacja byłaby bezużyteczna, więc filtr jest włączony.
15% to nadal za dużo. Kanały `czysty` i `whatsapp` (bez mikrofonu) aplikacja w praktyce nie słyszy, ale tam filtr
mało pomaga (minutowe rozmowy z alarmem: 95% i 98%), więc model trzeba poprawić u źródła.

### Co to znaczy dla aplikacji
- Rozmówca przez głośnik (kanał `glosnik`): 0 fałszywych alarmów, ale też słabe wykrywanie (Chatterbox 9–39% okien,
  XTTS 0%).
- **Głos właściciela przy mikrofonie** (`przy_mik`) to główne źródło fałszywych alarmów, a aplikacja słyszy go zawsze.
  Prawdopodobnie to też tłumaczy alarm z prawdziwej rozmowy ze zgłoszenia #2, obok sygnału wołania.
- Kierunki naprawy:
  1. Model (Jan): trening na różnorodnych prawdziwych nagraniach z telefonów i z pogłosem (FLEURS, Common Voice,
     nagrania przez głośnik), z losowym filtrem dolnoprzepustowym w augmentacji, i kalibracja progu na obcych
     głosach, a nie na dev z treningu. Przed wpięciem nowego modelu: ten sam test (`ocena.py` na FLEURS).
  2. Aplikacja: nie oceniać głosu właściciela. Przy konfiguracji zapisać jego wzór głosu (embedding mówcy,
     np. ECAPA w ONNX) i pomijać okna, w których mówi właściciel.
  3. Do czasu lepszego modelu czerwony alarm to tylko sygnał pomocniczy (tak mówią już teksty baneru).

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
FLEURS (pl_pl, dev) pobierasz z HuggingFace (`google/fleurs`, ok. 400 MB). Pełna ocena 338 plików na CPU trwa ok. 20 min.
`domena.py --model $M pliki...` sprawdza, na co model jest czuły (filtr 300 Hz, 7 kHz, głośność).
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
