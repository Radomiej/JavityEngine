# Dowód działania Feel3D na desktopie

Polecenie `./gradlew :javity-desktop:feel3dSmoke` uruchamia fixture renderujący do bufora w GL30. Zachowany [verification.json](verification.json) zawiera wynik zapisany przez test: `PASS`, aktywny GL30 oraz zaliczone sprawdzenia zachowania kanonicznej transformacji i bazowego FOV, rozdzielenia świata i viewmodelu, pikseli modelu i cząstek, pikseli postprocessingu świata, zwolnienia własnych zasobów i dalszej dostępności pożyczonej tekstury.

Pliki PNG to rzeczywiste zrzuty bufora klatki z fixture:

- [base.png](base.png) — bazowy model świata i viewmodel.
- [model-punch.png](model-punch.png) — model po przesunięciu przez efekt prezentacyjny.
- [viewmodel-recoil-bob.png](viewmodel-recoil-bob.png) — viewmodel po odrzucie i kołysaniu.
- [burst-world.png](burst-world.png) — model świata z widocznym pomarańczowym rozbłyskiem cząstek.
- [world-desaturated.png](world-desaturated.png) — przetworzony świat z zachowaną nakładką viewmodelu.
- [world-restored.png](world-restored.png) — świat po zwolnieniu efektu postprocessingu.

Fixture wymaga dodatniej liczby pikseli pierwszego planu, zanim uzna przesunięcie modelu lub viewmodelu za poprawne. Dla nieruchomej pozycji sprawdza czerwone piksele viewmodelu przed włączeniem efektu świata, a potem ponownie po jego włączeniu. Zapisane pomiary pokazują zmianę liczby zielonych pikseli świata z 3 721 w bazowej klatce do 3 449 po przesunięciu modelu. Viewmodel miał 1 809 pikseli w klatce bazowej, a po odrzucie i kołysaniu jego środek zmienił się z `[183, 90]` na `[182, 71]`. Warstwa świata zawiera 1 713 pomarańczowych pikseli cząstek; podczas desaturacji liczba zielonych pikseli świata spada do zera, a po przywróceniu świata wynosi 3 136. Zapisana odległość w pikselach viewmodelu przed i po postprocessingu świata to `0`.

Niezależny przegląd porównał zachowane zrzuty i JSON z asercjami fixture. Dodatkowo porównał czerwone piksele nakładki: przed efektem było ich 1 718, a po efekcie 1 693 miało dokładnie te same pozycje i kolor. Wyniki te potwierdzają izolację warstw w desktopowym przykładzie i działanie prezentacji. Nie dowodzą zgodności z komercyjnym API Unity, zgodności z produkcyjnym rigem kamery, gotowości runtime Androida ani odczuwalnych haptycznych wrażeń na Androidzie.
