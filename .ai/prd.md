# Dokument wymagań produktu (PRD) - Apriary Summary

## 1. Przegląd produktu

Apriary Summary to aplikacja internetowa w wersji MVP (Minimum Viable Product), zaprojektowana w celu automatyzacji procesu tworzenia podsumowań prac pasiecznych. Aplikacja skierowana jest do właścicieli małych gospodarstw pasiecznych, którzy potrzebują szybkiego i efektywnego sposobu na dokumentowanie historii prac przy ulach w pasiece. Użytkownicy mogą importować dane w formacie CSV, na podstawie których sztuczna inteligencja (AI) generuje zwięzłe podsumowania. Aplikacja umożliwia również manualne tworzenie, przeglądanie, edycję oraz usuwanie podsumowań. System oparty jest na Biff oraz XTDB do zarządzania danymi oraz wykorzystuje OpenRouter do generowania treści przez AI.

## 2. Problem użytkownika

Głównym problemem, który rozwiązuje Apriary Summary, jest czasochłonność i nieefektywność manualnego tworzenia podsumowań historii prac w pasiece. Pszczelarze często wykonują wiele czynności, a brak czasu na ich systematyczne notowanie prowadzi do niekompletnych lub nieprecyzyjnych zapisów. Skutkuje to utrudnioną analizą historii ula, podejmowaniem decyzji w oparciu o niepełne dane i ogólnym spadkiem efektywności zarządzania pasieką. Aplikacja ma na celu zminimalizowanie tego obciążenia poprzez automatyzację procesu i dostarczenie narzędzia do łatwego zarządzania dokumentacją.

## 3. Wymagania funkcjonalne

- RF-001: System uwierzytelniania i autoryzacji użytkowników oparty na XTDB, zapewniający, że każdy użytkownik ma dostęp wyłącznie do swoich danych (Row-Level Security).
- RF-002: Możliwość importu zawartości pliku w formacie CSV (kodowanie UTF-8, separator ';', plik z nagłówkiem).
- RF-003: Przetwarzanie wsadowe, gdzie każdy wiersz w pliku CSV generuje jedno osobne podsumowanie.
- RF-004: Walidacja danych wejściowych: pole tekstowe z obserwacjami musi zawierać od 50 do 10 000 znaków po usunięciu białych znaków z początku i końca (trim). Wiersze niespełniające tego warunku nie będą przetwarzane.
- RF-005: Integracja z OpenRouter w trybie "ekonomicznym" w celu generowania podsumowań przez AI.
- RF-006: Format wyjściowy generowanego podsumowania: nagłówek z datą i numerem ula (jeśli dostępne), przeniesione pole "cecha szczególna" oraz lista streszczenia w maksymalnie 10 punktach.
- RF-007: Obsługa brakujących danych: jeśli data (format DD-MM-YYYY) lub numer ula są puste lub niepoprawne, podsumowanie jest generowane bez tych informacji, a użytkownik może je uzupełnić później.
- RF-008: Pełna funkcjonalność CRUD (Create, Read, Update, Delete) dla podsumowań:
  - Tworzenie: manualne lub poprzez import CSV.
  - Odczyt: prosta lista wygenerowanych podsumowań.
  - Aktualizacja: edycja w miejscu (inline-edit) z natychmiastowym zapisem; edycja nadpisuje istniejącą wersję.
  - Usuwanie: trwałe usuwanie rekordu z bazy danych.
- RF-009: Funkcja "Akceptuj": dedykowany przycisk przy każdym podsumowaniu, którego kliknięcie zapisuje zdarzenie akceptacji (summary_id, user_id, timestamp) w osobnej tabeli.
- RF-010: Zapisywanie zdarzeń (generowanie, edycja, akceptacja, usunięcie) w bazie danych w celu umożliwienia późniejszej analizy metryk.

## 4. Granice produktu

### W zakresie MVP:

- Uwierzytelnianie użytkowników i zarządzanie kontami.
- Import danych wyłącznie z plików CSV o zdefiniowanej strukturze.
- Generowanie podsumowań przy użyciu AI (OpenRouter).
- Pełne operacje CRUD na podsumowaniach.
- Funkcjonalność akceptacji podsumowań.
- Zapisywanie podstawowych zdarzeń do analizy.

### Poza zakresem MVP:

- Import plików w formatach innych niż CSV (np. PDF, DOCX).
- Współdzielenie podsumowań i danych między użytkownikami.
- Integracje z zewnętrznymi platformami lub usługami.
- Dedykowane aplikacje mobilne (iOS, Android).
- Mechanizm "miękkiego usuwania" (soft-delete) i kosz.
- Zaawansowane mechanizmy obsługi błędów (np. automatyczne ponawianie prób).
- Wykrywanie duplikatów podczas importu.
- Wersjonowanie podsumowań.
- Zaawansowane raportowanie i analizy wewnątrz aplikacji.

## 5. Historyjki użytkowników

- ID: US-001
- Tytuł: Rejestracja i logowanie użytkownika
- Opis: Jako nowy użytkownik, chcę móc założyć konto i zalogować się do aplikacji, aby uzyskać dostęp do moich prywatnych podsumowań.
- Kryteria akceptacji:
  - Użytkownik może utworzyć konto, podając adres e-mail i hasło.
  - Użytkownik może zalogować się przy użyciu swoich poświadczeń.
  - Po zalogowaniu użytkownik jest przekierowany do głównego panelu aplikacji gdzie jest opcja generowania podsumowań.
  - Użytkownik widzi tylko podsumowania powiązane z jego kontem.

- ID: US-002
- Tytuł: Import danych w formacie CSV w celu wygenerowania podsumowań
- Opis: Jako zalogowany użytkownik, chcę móc wgrać danych w formacie CSV z moimi notatkami, aby system automatycznie wygenerował dla mnie podsumowania.
- Kryteria akceptacji:
  - W interfejsie użytkownika dostępna jest opcja importu CSV.
  - System akceptuje CSV w kodowaniu UTF-8 z separatorem ';'.
  - Po wgraniu pliku system przetwarza każdy wiersz i wysyła dane do modelu AI.
  - Wygenerowane podsumowania pojawiają się na liście podsumowań użytkownika.
  - Jeśli plik jest pusty, użytkownik otrzymuje stosowny komunikat.

- ID: US-003
- Tytuł: Walidacja danych wejściowych z CSV
- Opis: Jako użytkownik, oczekuję, że system będzie przetwarzał tylko te wiersze z CSV, które zawierają tekst obserwacji o odpowiedniej długości.
- Kryteria akceptacji:
  - System ignoruje wiersze, w których pole z tekstem obserwacji ma mniej niż 50 lub więcej niż 10 000 znaków (po trimowaniu).
  - System ignoruje wiersze, w których brakuje pola z tekstem obserwacji.
  - Proces importu kontynuuje działanie dla poprawnych wierszy, nawet jeśli niektóre zostaną odrzucone.

- ID: US-004
- Tytuł: Przeglądanie listy podsumowań
- Opis: Jako zalogowany użytkownik, chcę widzieć listę wszystkich moich podsumowań, aby mieć szybki przegląd mojej pracy.
- Kryteria akceptacji:
  - Po zalogowaniu wyświetlana jest prosta lista podsumowań.
  - Każdy element na liście zawiera co najmniej datę, numer ula (jeśli dostępne) i fragment wygenerowanego tekstu.
  - Lista jest posortowana chronologicznie (od najnowszych do najstarszych).

- ID: US-005
- Tytuł: Edycja podsumowania w celu uzupełnienia brakujących danych
- Opis: Jako użytkownik, chcę móc edytować podsumowanie bezpośrednio na liście, aby szybko uzupełnić brakujące informacje, takie jak data czy numer ula.
- Kryteria akceptacji:
  - Użytkownik może kliknąć na pole (np. data, numer ula) na liście, aby je edytować.
  - Po wprowadzeniu zmiany i jej zatwierdzeniu (np. kliknięcie poza polem edycji), zmiana jest natychmiast zapisywana w bazie danych.
  - Zaktualizowane dane są od razu widoczne na liście.

- ID: US-006
- Tytuł: Edycja treści wygenerowanego podsumowania
- Opis: Jako użytkownik, chcę mieć możliwość edycji tekstu podsumowania wygenerowanego przez AI, aby dostosować go do swoich potrzeb.
- Kryteria akceptacji:
  - Użytkownik może otworzyć tryb edycji dla treści podsumowania.
  - Po dokonaniu zmian i ich zapisaniu, nowa treść nadpisuje poprzednią wersję w bazie danych.
  - System nie przechowuje historii zmian (brak wersjonowania).

- ID: US-007
- Tytuł: Akceptacja podsumowania wygenerowanego przez AI
- Opis: Jako użytkownik, chcę móc oznaczyć podsumowanie jako "zaakceptowane", aby potwierdzić jego jakość i przydatność.
- Kryteria akceptacji:
  - Przy każdym podsumowaniu wygenerowanym przez AI widoczny jest przycisk "Zaakceptuj".
  - Kliknięcie przycisku powoduje zapisanie zdarzenia akceptacji w bazie danych, zawierającego co najmniej ID podsumowania, ID użytkownika i znacznik czasu.
  - Po akceptacji przycisk może zniknąć lub zmienić swój stan, aby wskazać, że podsumowanie zostało już zaakceptowane.

- ID: US-008
- Tytuł: Usuwanie podsumowania
- Opis: Jako użytkownik, chcę móc trwale usunąć podsumowanie, którego już nie potrzebuję.
- Kryteria akceptacji:
  - Przy każdym podsumowaniu na liście znajduje się opcja usunięcia.
  - Po kliknięciu opcji usunięcia, rekord jest trwale kasowany z bazy danych.
  - W ramach MVP nie jest wymagane dodatkowe okno dialogowe z potwierdzeniem.
  - Usunięty element natychmiast znika z listy.

- ID: US-009
- Tytuł: Manualne tworzenie nowego podsumowania
- Opis: Jako użytkownik, chcę mieć możliwość ręcznego dodania nowego podsumowania bez konieczności importowania CSV.
- Kryteria akceptacji:
  - W interfejsie użytkownika znajduje się przycisk "Dodaj nowe podsumowanie".
  - Po kliknięciu użytkownik widzi formularz z polami do wypełnienia (data, numer ula, tekst obserwacji, cecha szczególna itp.).
  - Po wypełnieniu i zapisaniu formularza, nowe podsumowanie pojawia się na liście.
  - W tym przypadku podsumowanie nie jest generowane przez AI.

- ID: US-010: Bezpieczny dostęp i uwierzytelnianie
- Tytuł: Bezpieczny dostęp
- Opis: Jako użytkownik chcę mieć możliwość rejestracji i logowania się do systemu w sposób zapewniający bezpieczeństwo moich danych.
- Kryteria akceptacji:
  - Logowanie i rejestracja odbywają się na dedykowanych stronach.
  - Logowanie wymaga podania adresu email i hasła.
  - Rejestracja wymaga podania adresu email, hasła i potwierdzenia hasła.
  - Użytkownik może logować się do systemu poprzez przycisk w prawym górnym rogu.
  - Użytkownik może się wylogować z systemu poprzez przycisk w prawym górnym rogu w głównym
  - Nie korzystamy z zewnętrznych serwisów logowania (np. Google, GitHub).
  - Odzyskiwanie hasła powinno być możliwe.

## 6. Metryki sukcesu

- Metryka 1 (Jakość generowanych treści): Procent zaakceptowanych podsumowań wygenerowanych przez AI.
  - Definicja: (Liczba unikalnych podsumowań z zarejestrowanym zdarzeniem akceptacji) / (Całkowita liczba podsumowań wygenerowanych przez AI) \* 100%.
  - Cel dla MVP: ≥ 75%.

- Metryka 2 (Adopcja funkcji AI): Procent podsumowań tworzonych z wykorzystaniem AI.
  - Definicja: (Liczba podsumowań wygenerowanych przez AI) / (Całkowita liczba wszystkich nowo utworzonych podsumowań, w tym manualnych) \* 100%.
  - Cel dla MVP: ≥ 75%.
