# Box2D dla nowego rdzenia

Kontynuacja autoryzowanej migracji Javity. Celem tego etapu jest prawdziwa fizyka 2D w nowym modelu komponentów, bez Artemis i bez wprowadzania fizyki 3D.

## Kontrakt

Opcjonalny moduł `javity-physics2d` zależy od `javity-core` i libGDX Box2D 1.14.2. `GameSystem` należy do jednej sceny, jest rejestrowany przed startem gry i zamykany wraz z nią. Fazy: UI, przygotowanie systemów, aktualizacja WORLD, wykonanie systemów, late update, sprzątanie. Systemy WORLD nie pracują przy pauzie.

`Physics2D` ma własny World i akumulator kroku 1/60 s. Współczynnik jednostek świata na metr jest jawny; pozycje i prędkości API używają jednostek świata, obrót radianów. Gravity w API jest w jednostkach świata/s² i jest przeliczana na metry/s². Zwykłe sprite'y nie są automatycznie colliderami; skala grafiki nie zmienia bryły kolizji.

Obiekt może mieć jeden `RigidBody2D` i wiele `Collider2D`. Bez rigidbody collidery tworzą ciało statyczne. Obsługujemy dynamiczne i kinematyczne ciała, prostokąty i koła, offset, materiały, sensory oraz 16-bitowe category/mask. Zmiany konfiguracji i transformacji są synchronizowane przed krokiem. Wyłączenie obiektu lub rigidbody wyłącza jego ciało; wyłączenie collidera usuwa jego fixture.

Box2D nie jest udostępniane przez API gameplay. `CollisionListener2D` zgłasza enter/exit kolizji i triggera przez własny `Contact2D`. Callbacki są kolejkowane i wywoływane po odblokowaniu świata; mogą modyfikować scenę. Zniszczony lub wyłączony odbiorca nie dostaje dalszych zdarzeń. Przy usunięciu drugiej strony żywy odbiorca może otrzymać exit.

## Weryfikacja

Testy natywnego Box2D bez OpenGL: grawitacja i lądowanie, niezależność od dzielenia klatek, pauza, enter/exit i triggery, filtry, compound collidery, zmiany konfiguracji, teleport, usuwanie podczas kontaktu, dynamiczne dodanie, zwalnianie World oraz izolacja scen. Demo ma fizyczny sprite, podłoże, trigger, debug colliderów i UI pauzy. Smoke sprawdza rzeczywisty kontakt, pauzę/wznowienie, resize i poprawne zamknięcie zasobów.

## Dodane polecenie użytkownika: rounded, border i rectangle

UI ma widget `Rectangle` oraz klasy `rounded`, warianty radius, `border`, `border-N` i `border-COLOR`. Promień numeric N oznacza N×4 jednostek; border-N oznacza N jednostek. Yoga uwzględnia obramowanie w layoucie. Wspólny shader signed-distance w UiTheme rysuje tło, obramowanie i antyaliasing bez bitmapy zależnej od rozmiaru. Shader Batch jest przywracany po rysowaniu widgetu; zasób należy do theme i jest zamykany z nim.

Wsparcie dotyczy paneli, przycisków i prostokątów. Rounded jest wizualne, bez przycinania dzieci i zmian hitboxa. Testy obejmują clamp radius/border, layout border oraz rzeczywiste piksele w smoke OpenGL.
