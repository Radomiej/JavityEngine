# Deklaratywne natywne UI

`org.javity.ui.declarative` buduje keyed Scene2D/Yoga tree. `UiDocument` posiada aktory, listenery i Yoga nodes; `UiTheme` oraz Stage są pożyczone. Ten sam host jest używany przez Java/markup i opcjonalny React adapter.

```java
var document = new UiDocument(theme);
var node = UiMarkup.parse("""
    <div id="panel" class="flex-col p-4 gap-2 w-[320px] bg-slate-900 rounded-xl">
      <span id="label" class="text-lg">Count: ${count}</span>
      <button id="add" class="bg-blue-600 rounded-md" onClick="add">Add</button>
      <progress id="progress" class="h-5 w-full" value="0.5"/>
    </div>
    """, Map.of("add", (Runnable) () -> count++),
         Map.of("count", (Supplier<Integer>) () -> count));
document.render(node);
document.root().setSize(400, 300);
document.mount(stage);
// W klatce UI, na tym samym wątku:
document.refresh();
// Przy zamknięciu: document.close(); Stage/theme zgodnie z ich właścicielem.
```

`count` powyżej oznacza pole klasy, nie mutowany lokalny int w lambdzie. Alternatywnie twórz `UiNode.element(type,key,classes,props,children)` bez parsera.

Rodzaje: View, Text, Button, Progress, TextField, ScrollView, Toggle, Slider i Image. Typy markup: div/span/label/button/progress/input/scroll/toggle/slider/img i nazwy kanoniczne. Dokument wymaga globalnie unikalnych kluczy; pominięty id/key otrzymuje ścieżkę drzewa (jawny id zalecany dla aktualizowanego UI). Zgodny key/type zachowuje aktora i focus, nowy handler zastępuje poprzedni bez zmiany listenera podczas pointer capture.

Text/Button używają String `text`; Button ma `disabled` i Runnable `onClick`. TextField ma String `value`, `disabled` i Consumer<String> `onChange` w Java/React. Markup nie ma typed change-event table — onChange jest odrzucane. Progress przyjmuje Number `value/min/max` z finite float-range i max>min; display clamped do zakresu. ScrollView ma dokładnie jeden View child. Dynamiczne text/value są Supplier; refresh sprawdza wszystkie wyniki przed aplikacją, a programmatic TextField update nie wywołuje onChange.

Nieznane typy/properties/classes, duplikaty key i błędne wartości są jawnie odrzucane przed zmianą drzewa. Parser blokuje DTD/external entities, limituje 64 poziomy i 2048 elementów. To XML-compatible HTML subset; tagi muszą być domknięte i atrybuty cytowane.

Toggle wymaga Boolean `checked`; Slider wymaga skończonych `min/max/value/step`, `max > min` i `step > 0`. Java `onChange` przyjmuje odpowiednio Boolean lub Float, a React boolean lub number. Obie kontrolki są kontrolowane: propozycja użytkownika wywołuje handler, a stan zatwierdza dopiero nowy prop. Obsługują `disabled`, focus i klawiaturę; [przykład TSX i typowanych akcji](onejs-port.md).

Text przyjmuje opcjonalne Boolean `wrap`, domyślnie false. Przykład markup: `<span id="description" class="text-base p-2 border" wrap="true">Opis gry z polskimi znakami.</span>`. Szerokość kontenera wyznacza zawijanie; nowe linie są zachowane, powtarzające się spacje zwijane, końcowa nowa linia nie dodaje pustego wiersza. Długie słowo pozostaje w całości i może wystawać poza box. Tekst, dziedziczony font, szerokość i zmiana wrap powodują ponowny pomiar Yoga, również przy zachowaniu tej samej szerokości i tego samego aktora. Padding i border liczone są raz; tło i obramowanie Text są renderowane.

Klasy utility z `UtilityStyles`: flex-row/col/wrap, grow/shrink, items/justify/self, padding/margin/gap z osiami i krawędziami, w/h/min/max/basis, text-size/color, bg, rounded i border. Jednostka klasy `w-40` to 160 jednostek; `w-[160px]` daje jawną wartość. Hex bg/text/border obsługuje #RGB/#RGBA/#RRGGBB/#RRGGBBAA. Kolor i rozmiar tekstu dziedziczą się przez kontenery; tło, padding i border pozostają lokalne. Button, Toggle i Slider obsługują warianty hover/active/focus/disabled, a TextField hover/focus/disabled. Inne rodzaje węzłów odrzucają warianty; dotyczą one wyglądu, a nie geometrii layoutu. Pełne CSS, breakpointy i wszystkie warianty Tailwind pozostają poza zakresem. [Wzorzec HTML i odtwarzanie porównania](ui-html-parity.md).

Flex ma pojedynczy Yoga node; własne i parent layout classes są scalane w świeżym probe, zachowując własny visual style. Pomiar preferred size ScrollPane używa tymczasowego drzewa Yoga, więc zawartość większa niż viewport ma rzeczywisty zakres scrolla. Native Scene2D ScrollPane clipuje swoją zawartość; rounded rectangle samo nie clipuje dzieci i nie zmienia hitboxa.

Wymagany thread UI/OpenGL dla tworzenia Theme i kontrolek. View-only UiDocument(null) jest dostępny dla testów native Yoga bez GPU, nie zastępuje testów kontrolek z Theme.

## Efekty bez naruszania layoutu

`org.javity.ui.effects.UiTargets.alpha(owner, document, key)` tworzy cel `FloatTarget` dla przezroczystości natywnego Actor. Używaj `TweenPlayer(Clock.UI)` na tym samym właścicielu oraz scene-local `EffectSystem`. Cel zachowuje konkretny Actor; zamknięcie dokumentu, zastąpienie klucza innym Actor lub zniszczenie właściciela unieważnia go. Wartości alpha są skończone w zakresie 0..1. Adapter nie przejmuje własności UI ani nie zmienia bounds wyliczanych przez Yoga.

## Obrazy z rejestru zasobów

Theme pożycza regiony i tekstury; rejestr nie jest loaderem plików ani URL.

```java
theme.registerImage("pilot", new TextureRegion(pilotTexture));
```

Markup: `<img id="portrait" source="pilot" fit="cover" class="w-24 h-24 rounded-full"/>`. React: `<Image id="portrait" source="pilot" fit="cover" className="w-24 h-24 rounded-full"/>`, gdzie Image pochodzi z JavityReact. Źródłem jest nazwa String. Fit przyjmuje contain (domyślny, z letterbox), cover (centralne przycięcie) albo stretch. Padding wyznacza obszar treści; tło i obramowanie należą do całego elementu. Rounded przycina własne piksele obrazu, również przezroczystego PNG, i nie zmienia hitboxa.

Nieznane źródło lub fit są odrzucane przed zmianą drzewa. Aktualizacja source/fit/classes z tym samym kluczem zachowuje aktora. unregisterImage usuwa przyszłe wyszukiwania; już zamontowany obraz zachowuje pożyczony region, dopóki jego source się nie zmieni. Zmiana mapowania tej samej nazwy nie aktualizuje automatycznie zamontowanych aktorów. Właściciel musi utrzymać teksturę przy życiu aż do usunięcia ostatniego pożyczającego obrazu. Zamknięcie dokumentu/theme nie zwalnia tekstur z rejestru.

## Kolory animowanego postępu

Progress rysuje podstawowe wypełnienie w kolorze tekstu (domyślnie cyan-400), zielony segment wyprzedzający przy wzroście (emerald-500) i bursztynowy opóźniony ślad przy spadku (amber-400). `duration` i `delay` sterują modelem, a nie geometrią Yoga. Ukończony pasek zachowuje kolor podstawowy.
