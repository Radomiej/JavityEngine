# Rzeczywisty podgląd dwóch klientów

Root uruchomił CLI z Java25, losowym portem localhost i Chromium154.0.8037.57 przez Playwright. [Raport](browser-verification.json) zawiera dokładny commit gatewaya i wynik, [zrzut](two-clients.png) pokazuje dwóch graczy po ruchu.

Scenariusz: szybkie podwójne Create tworzy jeden worker; wpisanie WASD w polu identyfikatora nie rusza postaci; druga karta dołącza do tego samego pokoju; D w pierwszej karcie zmienia pozycję w Javie, obie karty odczytują x6.4; Leave replikuje usunięcie; SIGTERM CLI z aktywnym pokojem kończy serwer exit0 i jego dziecko Java. Brak błędów strony. Weryfikacja własnych procesów przez PPID; środowisko nie udostępniało plików proc/task/children.

To przeglądarkowy klient Colyseusa i headless Game/Component na JVM. Nie jest to TeaVM/Wasm Javity, scena3D, fizyka sieciowa ani proof Androida. Pełne komendy developerskie i zakres testów: [local-multiplayer.md](../../local-multiplayer.md).
