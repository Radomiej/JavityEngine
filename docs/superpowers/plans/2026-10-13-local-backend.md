# Modernizacja Javity — Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for native execution; user did not authorize delegation.

**Goal:** Uruchomić Javity na Java 25 i Gradle, z lokalnym backendem, prostym rdzeniem, 3D i Yoga UI.

**Architecture:** Nowe API jest odseparowane od backendu legacy. Wszystkie migrowane moduły są częścią jednego buildu.

**Tech Stack:** JDK 25 LTS, Gradle 9.8.1, libGDX 1.14.2, LWJGL 3.4.3, Artemis ODB 2.3.0 w legacy.

**Spec:** ../specs/2026-10-13-local-backend-design.md

## Global Constraints

- JDK 25 LTS; Gradle 9.8.1; libGDX 1.14.2; LWJGL 3.4.3.
- Własne źródła lokalnie, bez historycznych snapshotów Sonatype.
- Nowy rdzeń bez Artemis; historyczne API zachowane do migracji.

## Review Focus

- Wyszukiwanie klas bazowych przy wielu komponentach typu.
- Mutacje list podczas aktualizacji i jednokrotne niszczenie.
- Niezależność instancji współdzielonego modelu i cykle transformacji.
- Zmiana rozmiaru, przenoszenie elementów oraz natywna pamięć Yoga.
- Osi X/Y i mutacja argumentów w projekcji map.

## Zadania

- [x] Pobrać Javity, Galaxy Rapid, pluginy i historyczne demo.
- [x] Odtworzyć brakujące zależności oryginalnego buildu.
- [x] Włączyć lokalny backend i mapy, usunąć silver-reflection.
- [x] Przenieść build na Gradle Wrapper i JDK 25; ujednolicić libGDX/LWJGL.
- [x] Dodać testy i nowy rdzeń komponentów z indeksami, pauzą i cyklem życia.
- [x] Dodać Transform3D, ModelRenderer, kamerę i wspólny renderer 2D/3D.
- [x] Dodać testy i Yoga UI z klasami utility.
- [x] Naprawić projekcję map z testami regresji.
- [x] Przygotować nowe demo oraz legacy smoke; sprawdzić prawdziwe OpenGL.
- [x] Dodać AGENTS.md, README i pochodzenie kodu.
- [x] Końcowe clean build przez wrapper i ponowny smoke po ostatnich zmianach.
