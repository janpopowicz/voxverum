# Testy na sklonowanym głosie

Narzędzia do testu opisanego w [docs/testy-klonu.md](../../docs/testy-klonu.md): klonowanie głosu lokalnie
(XTTS-v2, Chatterbox), symulacja kanału WhatsApp i głośnika, ocena pipeline'em aplikacji, podobieństwo mówcy.
Nagrania trzymaj poza repo (`.gitignore` blokuje WAV i MP4 w tym katalogu).

Test zgodności scenariusza z oceną offline (bez nagrań i modeli do klonowania):
`python -m unittest discover -s tools/clone_tests -v`, uruchomione z katalogu repo w środowisku `env-tools`.
