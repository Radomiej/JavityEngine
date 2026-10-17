# Porównanie natywnego UI z HTML / Tailwind

Wzorzec jest rzeczywistym HTML renderowanym w Chromium, z Tailwind CSS **4.3.3**. Oba renderery używają tego samego drzewa `web/javity-ui-reference/fixture.json`, fontu Open Sans oraz viewportu 1100 × 720 przy DPR 1. Font jest celowo wspólny: domyślny font systemowy przeglądarki zależy od platformy.

Fixture obejmuje zagnieżdżone kontenery, tekst, przyciski, input, progress, kartę i przewijany panel. HTML progress oraz scrollbar mają jawny styl; wygląd domyślnych kontrolek systemowych przeglądarki nie jest kontraktem silnika.

## Odtworzenie porównania

Z katalogu repozytorium, z pełnym JDK 25:

```sh
npm --prefix web/javity-ui-reference ci
npm --prefix web/javity-ui-reference run css
npm --prefix web/javity-ui-reference run compare
./gradlew :javity-desktop:uiParitySmoke
```

Skrypt browserowy wymaga Python, Playwright, Pillow i Chromium (`/usr/bin/chromium`). Natywny smoke wymaga OpenGL; na serwerze uruchom go pod Xvfb. Zwykły build Gradle nie wymaga Node ani przeglądarki.

Zrzuty HTML i pomiary DOM znajdują się w `web/javity-ui-reference`; wyniki natywne trafiają do `artifacts/ui-parity-*`. Smoke porównuje układ Yoga z pomiarami DOM oraz próbki renderowanych kolorów. Testy klas i drzewa uzupełniają to o aktualizację stylów i zachowanie tożsamości aktorów.

## Zakres

Natywne UI implementuje jawny podzbiór utility classes, a nie silnik HTML/CSS. Nieznane klasy są odrzucane. Paleta i podstawowe rozmiary tekstu odpowiadają wskazanej wersji Tailwind: sm 14/20, base 16/24, lg 18/28, xl 20/28, 2xl 24/32 (font/line-height w pikselach). `rounded-sm` ma 4 px, md 6, lg 8, xl 12, 2xl 16, 3xl 24.

Kolor tekstu i rozmiar fontu dziedziczą się przez deklaratywne kontenery. Padding, tło i border pozostają lokalne. Warianty stanów dotyczą obsługiwanego stylu kontrolek; breakpointy, dowolne selektory CSS i pełny DOM pozostają poza zakresem.

Warianty są sprawdzane również według rodzaju węzła: Button obsługuje hover/active/focus/disabled, TextField hover/focus/disabled, a Toggle/Slider mają własne stany kontrolek. Pozostałe rodzaje odrzucają warianty stanów. Zmiana szerokości border w wariancie jest odrzucana, ponieważ zmieniałaby geometrię kontrolki.

FreeType i Chromium inaczej rasteryzują glyphy i wyliczają ich szerokość. Zgodność układu nie oznacza identyczności wszystkich pikseli tekstu. Bazowy fixture sprawdza tekst jednoliniowy. Osobny fixture `k2-wrap-fixture.json` obejmuje opt-in `Text.wrap` oraz rzeczywiste zmiany treści i fontu na tym samym aktorze. Zaokrąglenie tła nie przycina dzieci kontenera.

## Wynik pomiaru

W fixture maksymalna różnica geometrii aktora wynosi **2,12 px**, przy naturalnej szerokości nagłówka. Stabilne próbki kolorów wnętrz i bazowego border inputu mają różnicę **0**. Próbki hover/focus/disabled różnią się o najwyżej **1/255** na kanał. Thumb scrollbara różni się o najwyżej **1 px** w pomierzonych granicach; HTML używa jawnego, własnego thumb połączonego z rzeczywistym `scrollTop`, ponieważ Chromium ukrywało swój scrollbar nakładany na treść.

Zmierzony obrys liter różni się szerokością od −1 do +4 px. Początek liter w tytule karty jest przesunięty o −1 px w obu osiach; pozostałe opisane próbki mają zgodną pozycję pionową. Te różnice pozostają jawne w [raporcie pomiarów](../web/javity-ui-reference/c1-report.md).

Smoke sprawdza również obrys liter: maksymalnie 1 px różnicy pozycji pionowej/wysokości oraz 4 px szerokości dla pomierzonych próbek. Przekroczenie kończy test błędem. Knob scrollbara ma minimum 8 px; aktualizacja klas zachowuje tę konfigurację.

## Przełącznik i suwak

`k1-controls-fixture.json` napędza oddzielną parę natywnego Stage i rzeczywistych HTML checkbox/range, ze wspólnym Open Sans i 1100 × 720 przy DPR 1. Referencja korzysta z Tailwind 4.3.3 i jawnego stylu kontrolek. Sprawdzamy idle, focus suwaka, hover przełącznika i disabled.

Zmierzona geometria aktorów ma różnicę 0 px, położenie pionowe liter 0 px, szerokość liter najwyżej 2 px, a próbki kolorów najwyżej 1 poziom kanału. Nominalne prostokąty track/knob różnią się najwyżej o 0,02 px; rzeczywiste granice rasteryzowanych krawędzi mają osobny wynik 1–2 px. Nie są to te same pomiary.

Chromium sprawdza też zmianę danych fixture: pozycję panelu oraz zakres 20–80 z wartością 44, który daje widoczne wypełnienie 0,4. Wartość 120 jest przez rzeczywisty input ograniczana do 80, a wypełnienie dochodzi do końca. Zrzuty i pomiary: `artifacts/k1-fix2-*`; natywna para: `artifacts/k1-fix1-native-*`.

Po uruchomieniu `./gradlew :javity-desktop:pluginsSmoke` odtwórz tę parę poleceniem `python3 web/javity-ui-reference/k1-controls-compare.py`. Skrypt odświeża obrazy browserowe i porównuje je z rzeczywistym pomiarem natywnym.

## Akapity i zmiana tekstu

`uiParitySmoke` zapisuje pary `artifacts/k2-wrap-*`; odtwórz referencję przez `python3 web/javity-ui-reference/k2-wrap-compare.py`. Sześć zwykłych akapitów przy wąskiej szerokości i siedem przy szerokiej ma zgodną geometrię, liczbę linii oraz podział słów. Pionowe granice liter są zgodne; kolory tła różnią się najwyżej o 1 poziom kanału. Próba Stage/Graal zachowuje ten sam aktor przy zmianie tekstu, fontu, wrap, kolejności rodzeństwa i szerokości. Zmiana krótkiego tekstu na długi przy stałych 140 px zmienia wysokość z 20 na 60 px, a powrót na krótki przywraca 20 px.

Długi tekst 24 px ma natywnie o 9–11 px węższy obrys, około 1,9%. Osobny przypadek graniczny o szerokości 410 px ma przy wąskim układzie 3 linie w Chromium i 2 natywnie, czyli 32 px różnicy wysokości. Pozostaje w raporcie jako ograniczenie metryk fontów. Nie zmienia to wcześniejszych bramek krótkiego tekstu C1. Długie słowo przy boxie 190 px wystaje o 60 px w Chromium i 62 px natywnie.

## Obrazy i rounded masks

`k4-image-fixture.json` oraz ten sam lokalny PNG napędzają dziewięć rzeczywistych elementów img w Chromium i natywne UiImage. Porównanie obejmuje contain/cover/stretch, różny padding na każdej krawędzi, border, koło, opacity 0,5 oraz box, którego padding zużywa całą treść. Drugi stan zmienia rozmiar i fit jednego obrazu; viewport obu pozostaje 1100 × 720.

Zmierzona geometria jest zgodna (0 px). Największa średnia różnica kanałów RGB we wnętrzu obrazu to 1,728 w stanie początkowym i 1,732 po zmianie. Bramka skryptu wynosi 3. Próbki opacity różnią się najwyżej o 3 poziomy kanału; wybrane próbki paddingu, narożnika, border i pustej treści są identyczne. To pomiar RGB po kompozycji; kanał alpha framebufferu zależy od backendu. Natywne próby crop atlasu i odbicia poziomego/pionowego są osobne i nie są przedstawiane jako browserowe porównanie atlasu.

Odtworzenie po `uiParitySmoke`:

```sh
python3 web/javity-ui-reference/k4-image-render.py
python3 web/javity-ui-reference/k4-image-compare.py
```

Zrzuty obu stanów i raport bramek są w `artifacts/k4-image-*`. Skrypty wymagają Chromium, Playwright, Pillow oraz NumPy. Referencja korzysta z wygenerowanego Tailwind CSS; natywne szerokości pochodzą z authored fixture, bez przepisywania pomiarów DOM do silnika.

## HUD Relay Ward

`mm.tsx` i rzeczywista referencja HTML używają wspólnego `mm-fixture.json`, Open Sans, atlasowego portretu oraz viewportu 1280×720/DPR1. Pięć stanów obejmuje idle, hit, settings, loading i nową arenę. Natywna scena 2D/3D jest sprawdzana osobno; HTML porównuje HUD.

```sh
./gradlew :javity-desktop:mmSmoke
python3 web/javity-ui-reference/mm-compare.py
```

Skrypt wymaga wszystkich identyfikatorów i obu zestawów screenshotów; sprawdza ramki aktorów, line-box i zakotwiczenie tekstu, wybrane obrysy glyphów, wnętrza kolorów, kształt oraz zaokrąglone narożniki suwaka i atlasowy portret. Wyniki: `artifacts/mm-systems/mm-comparison.json`. Geometria stałych aktorów jest zgodna (0 px); różnice krawędzi rasteryzacji kontrolek do 4 px i średnia różnica RGB portretu 3,364 są jawne. Naturalna szerokość dłuższego tekstu różni się do 5,55 px w tym fixture. Błąd całego sidebara jest diagnostyką, a nie zastępstwem testów komponentów.

Do powtarzalnej próby przeładowania gotowej paczki służy `web/javity-ui-reference/packaged-dev-proof.py` z argumentami `--launcher`, `--bundle`, `--artifacts`. Skrypt pracuje na własnej kopii bundla w nowym katalogu tymczasowym: poprawna zmiana, błędna składnia z zachowaniem UI, przywrócenie, nowa scena i świeża aktualizacja health. Nie modyfikuje źródłowego TSX.

Aktualna para z tego samego punktu animacji: [natywny screenshot](demo-mm.png), [rzeczywisty HTML/Tailwind](demo-mm-html.png). Każdy Progress we wszystkich pięciu stanach ma sprawdzony koniec wypełnienia (różnica najwyżej 1 px), a bursztynowy ślad obrażeń i zielony ślad wzrostu mają zgodne zmierzone końce i kolory. Wartości checkpointów wynikają z czasu modelu, są wspólnie opisane w fixture i porównywane z rzeczywistym stanem natywnego ProgressBar.
