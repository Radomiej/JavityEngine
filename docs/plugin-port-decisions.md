# Ustalenia portów — 2026-10-14

- Kontynuacja `/goal` obejmuje odwracalne porty i pracę agentów Luna; nie wymaga ponownego zatwierdzania między zadaniami. Pozostają lokalne zmiany na istniejącej gałęzi, bez commit/push.
- `Game.getFrameIndex()` rośnie także podczas pauzy i delta0. Nowy feedback uruchomiony z callbacku efektu czeka na kolejną klatkę zamiast wywoływać rekurencyjne aktualizacje.
- Native animation delta/fade musi mieścić się w float, a pojedynczy update ma limit 1024 pętli. Zapobiega to bardzo długiemu update libGDX przy ekstremalnej konfiguracji speed/clip.
- Java–JS udostępnia jawne nazwy akcji i prymitywne odczyty; rejestracje przeżywają reload i zwalniają się przy close. Nie eksportujemy dowolnych obiektów Java.
- Aplikacja TSX używa shimu istniejącego runtime React; callback `mountSample(onIncrement)` łączy realny handler JSX z gameplay. Nie modyfikujemy stringów skompilowanego bundle ani nie instalujemy drugiego hosta.
- Zdarzenie startu animacji czeka na pierwszy aktywny niepauzowany update, aby callback można było dopiąć po otrzymaniu handle. Restart kolejkuje nowy start, a stare stany nie zapisują pozy.
- Close z callbacku JS jest logicznie natychmiastowy, fizyczny teardown czeka na jego powrót. Reentrant reload jest jawnie odrzucany przed mutacją; reload wykonujemy z pętli hosta.
- Publiczne OneJS/onejs-react MIT są źródłami architektury portu. Feel/Animancer są wzorcem zachowania własnego API; nie deklarujemy całego katalogu Unity/MM ani kopiowania komercyjnych assetów.
- Zachowano ledger i snapshoty przeglądów: zmiany są niecommitowane, więc usunięcie tych plików usunęłoby część historii weryfikacji.

Potwierdzenie decyzji w kodzie i testach: [status](modernization-status.md), [publiczne API](README.md), [braki MM](mm-systems-audit.md).
