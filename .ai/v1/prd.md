# Dokument wymagań produktu (PRD) - Apiary Summary

## 1. Przegląd produktu

Apiary Summary to aplikacja internetowa w wersji MVP (Minimum Viable Product), zaprojektowana w celu automatyzacji procesu tworzenia podsumowań prac pasiecznych. Aplikacja skierowana jest do właścicieli małych gospodarstw pasiecznych, którzy potrzebują szybkiego i efektywnego sposobu na dokumentowanie historii prac przy ulach w pasiece. Użytkownicy mogą importować dane w formacie CSV, na podstawie których sztuczna inteligencja (AI) generuje zwięzłe podsumowania. Aplikacja umożliwia również manualne tworzenie, przeglądanie, edycję oraz usuwanie podsumowań. System oparty jest na Biff oraz XTDB do zarządzania danymi oraz wykorzystuje OpenRouter do generowania treści przez AI.

## 2. Problem użytkownika

Głównym problemem, który rozwiązuje Apiary Summary, jest czasochłonność i nieefektywność manualnego tworzenia podsumowań historii prac w pasiece. Pszczelarze często wykonują wiele czynności, a brak czasu na ich systematyczne notowanie prowadzi do niekompletnych lub nieprecyzyjnych zapisów. Skutkuje to utrudnioną analizą historii ula, podejmowaniem decyzji w oparciu o niepełne dane i ogólnym spadkiem efektywności zarządzania pasieką. Aplikacja ma na celu zminimalizowanie tego obciążenia poprzez automatyzację procesu i dostarczenie narzędzia do łatwego zarządzania dokumentacją.

## 3. Wymagania funkcjonalne

- RF-001: System uwierzytelniania i autoryzacji użytkowników oparty na XTDB, zapewniający, że każdy użytkownik ma dostęp wyłącznie do swoich danych (Row-Level Security).
- RF-002: Możliwość importu danych CSV poprzez wklejenie tekstu w pole tekstowe (kodowanie UTF-8, separator ';', dane z nagłówkiem). **Uwaga:** W implementacji użyto textarea zamiast uploadu pliku dla uproszczenia UX.
- RF-003: Przetwarzanie wsadowe, gdzie każdy wiersz w pliku CSV generuje jedno osobne podsumowanie.
- RF-004: Walidacja danych wejściowych: pole tekstowe z obserwacjami musi zawierać od 50 do 10 000 znaków po usunięciu białych znaków z początku i końca (trim). Wiersze niespełniające tego warunku nie będą przetwarzane.
- RF-005: Integracja z OpenRouter w celu generowania podsumowań przez AI. Model konfigurowany w pliku konfiguracyjnym aplikacji. **Status:** Obecnie zamockowane - zwraca tekst obserwacji bez zmian. Wymaga implementacji rzeczywistego połączenia z API OpenRouter.
- RF-006: Format wyjściowy generowanego podsumowania: nagłówek z datą i numerem ula (jeśli dostępne), przeniesione pole "cecha szczególna" oraz lista streszczenia w maksymalnie 10 punktach.
- RF-007: Obsługa brakujących danych: jeśli data (format DD-MM-YYYY) lub numer ula są puste lub niepoprawne, podsumowanie jest generowane bez tych informacji, a użytkownik może je uzupełnić później.
- RF-008: Pełna funkcjonalność CRUD (Create, Read, Update, Delete) dla podsumowań:
  - Tworzenie: manualne lub poprzez import CSV.
  - Odczyt: prosta lista wygenerowanych podsumowań.
  - Aktualizacja: edycja w miejscu (inline-edit) z natychmiastowym zapisem; edycja nadpisuje istniejącą wersję.
  - Usuwanie: trwałe usuwanie rekordu z bazy danych.
- RF-009: Funkcja "Akceptuj": dedykowany przycisk przy każdym podsumowaniu, którego kliknięcie aktualizuje liczniki akceptacji w rekordzie generacji (generation). **Uwaga:** W implementacji użyto liczników (`accepted-unedited-count`, `accepted-edited-count`) w tabeli generation zamiast osobnej tabeli zdarzeń, dla uproszczenia schematu i łatwiejszego obliczania metryk.
- RF-010: Zapisywanie zdarzeń (generowanie, edycja, akceptacja, usunięcie) w bazie danych w celu umożliwienia późniejszej analizy metryk. **Uwaga:** W implementacji użyto minimalnego śledzenia zdarzeń poprzez znaczniki czasowe (created_at, updated_at) oraz liczniki w rekordzie generation. Brak dedykowanej tabeli audytu. XTDB zapewnia historię czasową jako dodatkową możliwość audytu.

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
- Opis: Jako zalogowany użytkownik, chcę móc wkleić dane w formacie CSV z moimi notatkami, aby system automatycznie wygenerował dla mnie podsumowania.
- Kryteria akceptacji:
  - W interfejsie użytkownika dostępne jest pole tekstowe (textarea) do wklejenia danych CSV.
  - System akceptuje CSV w kodowaniu UTF-8 z separatorem ';'.
  - Po wklejeniu i zatwierdzeniu system przetwarza każdy wiersz i wysyła dane do modelu AI.
  - Wygenerowane podsumowania pojawiają się na liście podsumowań użytkownika.
  - Jeśli dane są puste, użytkownik otrzymuje stosowny komunikat.
  - **Implementacja:** Użyto textarea z placeholder pokazującym przykładowy format CSV zamiast upload pliku.

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

## 7. Decyzje Projektowe i Notatki Implementacyjne

**Wersja dokumentu:** 1.1  
**Data aktualizacji:** 2026-05-22  
**Status:** Zaktualizowano o rzeczywiste decyzje projektowe

### 7.1 Kluczowe Zmiany w Stosunku do Oryginalnej Specyfikacji

#### Zmiana 1: Import CSV przez Textarea (zamiast upload pliku)

**Oryginalna specyfikacja:**
- RF-002: "import pliku w formacie CSV"
- US-002: "wgrać dane w formacie CSV"

**Rzeczywista implementacja:**
- Pole tekstowe (textarea) do wklejania danych CSV
- Brak funkcji upload pliku

**Uzasadnienie:**
- Prostszy interfejs użytkownika
- Łatwiejsza integracja z API (CSV jako string w request body)
- Brak potrzeby obsługi multipart/form-data
- Szybsze prototypowanie MVP
- Łatwiejsze testowanie

**Wpływ na użytkownika:**
- Użytkownik musi otworzyć plik CSV w edytorze i skopiować zawartość
- Bardziej odpowiednie dla małych plików CSV
- Brak walidacji formatu pliku po stronie przeglądarki

**Rozważenia na przyszłość:**
- Możliwość dodania upload pliku w przyszłej iteracji
- Rozważenie obu opcji jednocześnie (textarea + file upload)

---

#### Zmiana 2: Integracja OpenRouter - Obecnie Zamockowana

**Oryginalna specyfikacja:**
- RF-005: "Integracja z OpenRouter w trybie ekonomicznym"

**Rzeczywista implementacja:**
- Serwis `openrouter.clj` z flagą `MOCK_ENABLED = true`
- Zwraca tekst obserwacji bez zmian (brak rzeczywistego wywołania AI)
- Struktura kodu przygotowana pod przyszłą integrację

**Uzasadnienie:**
- Umożliwienie rozwoju reszty aplikacji bez zależności od zewnętrznego API
- Brak kosztów API podczas developmentu
- Szybsze testy i debugowanie
- Możliwość pracy offline

**Wpływ:**
- **KRYTYCZNY:** Aplikacja nie generuje rzeczywistych podsumowań AI
- Nie można zwalidować RF-006 (format wyjściowy)
- Nie można zmierzyć metryk sukcesu
- MVP nie jest gotowe do produkcji bez tej implementacji

**Wymagane kroki:**
1. Uzyskanie klucza API OpenRouter
2. Konfiguracja zmiennej środowiskowej `OPENROUTER_API_KEY`
3. Implementacja HTTP client (clj-http lub http-kit)
4. Zaprojektowanie promptu dla modelu AI
5. Parsowanie odpowiedzi z API
6. Obsługa błędów i retry logic
7. Konfiguracja rate limiting

**Priorytet:** 🔴 WYSOKI - wymagane przed wdrożeniem produkcyjnym

---

#### Zmiana 3: Śledzenie Akceptacji przez Liczniki (zamiast osobnej tabeli)

**Oryginalna specyfikacja:**
- RF-009: "zapisuje zdarzenie akceptacji (...) w osobnej tabeli"

**Rzeczywista implementacja:**
- Liczniki w rekordzie `generation`:
  - `accepted-unedited-count` - akceptacje niemodyfikowanych AI
  - `accepted-edited-count` - akceptacje zmodyfikowanych AI
- Brak osobnej tabeli `acceptance_events`

**Uzasadnienie:**
- Prostszy schemat bazy danych
- Szybsze obliczanie metryk (brak potrzeby agregacji)
- Wystarczające dla wymaganych metryk MVP
- Mniejsza liczba zapytań do bazy danych

**Wpływ:**
- Nie można zapytać o indywidualne znaczniki czasu akceptacji
- Nie można zidentyfikować, które konkretne podsumowania zostały zaakceptowane
- Można obliczyć procent akceptacji dla całego batcha

**Ograniczenia:**
- Brak możliwości "odakceptowania" pojedynczego podsumowania
- Brak historii akceptacji dla audytu
- Brak informacji kto i kiedy zaakceptował konkretne podsumowanie

**Rozważenia na przyszłość:**
- XTDB zapewnia temporal features - możliwość odtworzenia historii
- Jeśli potrzebny szczegółowy audyt, można dodać osobną tabelę

---

#### Zmiana 4: Minimalne Śledzenie Zdarzeń

**Oryginalna specyfikacja:**
- RF-010: "Zapisywanie zdarzeń (generowanie, edycja, akceptacja, usunięcie)"

**Rzeczywista implementacja:**
- Znaczniki czasowe: `created-at`, `updated-at`
- Liczniki akceptacji w `generation`
- Zmiana source: `ai-full` → `ai-partial` przy edycji
- Brak dedykowanej tabeli `events` lub `audit_log`

**Uzasadnienie:**
- Wystarczające do obliczenia metryk MVP
- Prostszy model danych
- XTDB zapewnia historię czasową jako built-in feature

**Wpływ:**
- Nie można odtworzyć pełnej historii zdarzeń bez temporal queries
- Brak informacji o zdarzeniach usunięcia
- Wystarczające do podstawowych metryk

**Możliwości XTDB:**
- Funkcje temporalne pozwalają na zapytania "as-of" określonego czasu
- Możliwość odtworzenia historii zmian dokumentu
- Nie wymaga osobnej tabeli audytu

---

### 7.2 Struktura Bazy Danych - Rzeczywiste Schema

**Tabela: user**
```clojure
[:xt/id :user/id]
[:user/id :uuid]
[:user/email :string]
[:user/password-hash :string]
[:user/joined-at inst?]
```

**Tabela: generation**
```clojure
[:xt/id :uuid]
[:generation/id :uuid]
[:generation/user-id :uuid]
[:generation/model :string]
[:generation/generated-count int >= 0]
[:generation/accepted-unedited-count int >= 0]
[:generation/accepted-edited-count int >= 0]
[:generation/duration-ms int >= 0]
[:generation/created-at inst?]
[:generation/updated-at inst?]
```

**Tabela: summary**
```clojure
[:xt/id :uuid]
[:summary/id :uuid]
[:summary/user-id :uuid]
[:summary/generation-id :uuid lub nil - opcjonalne]
[:summary/source enum - :ai-full | :ai-partial | :manual]
[:summary/created-at inst?]
[:summary/updated-at inst?]
[:summary/hive-number :string - opcjonalne]
[:summary/observation-date :string - opcjonalne, format DD-MM-YYYY]
[:summary/special-feature :string - opcjonalne]
[:summary/content :string]
```

**Tabela: password-reset-token** (dodana dla funkcji odzyskiwania hasła)
```clojure
[:xt/id :uuid]
[:password-reset-token/id :uuid]
[:password-reset-token/user-id :uuid]
[:password-reset-token/token :string - SHA-256 hash]
[:password-reset-token/expires-at inst?]
[:password-reset-token/created-at inst?]
[:password-reset-token/used-at inst? - opcjonalne]
```

---

### 7.3 Endpointy API - Rzeczywista Implementacja

#### Podsumowania (Summaries)
- `GET /api/summaries` - Lista podsumowań użytkownika
- `GET /api/summaries/{id}` - Szczegóły pojedynczego podsumowania
- `POST /api/summaries` - Tworzenie manualnego podsumowania
- `PATCH /api/summaries/{id}` - Aktualizacja podsumowania (inline edit)
- `DELETE /api/summaries/{id}` - Usunięcie podsumowania (hard delete)
- `POST /api/summaries/{id}/accept` - Akceptacja pojedynczego podsumowania
- `POST /api/summaries/generation/accept` - Masowa akceptacja dla generacji
- `POST /api/summaries/import` - Import CSV i generowanie podsumowań

#### Autentykacja (już zaimplementowane)
- `POST /auth/signup` - Rejestracja
- `POST /auth/signin` - Logowanie
- `POST /auth/signout` - Wylogowanie
- `POST /auth/send-password-reset` - Wysłanie linku do resetu hasła
- `POST /auth/reset-password` - Reset hasła z tokenem

---

### 7.4 Status Implementacji Komponentów

| Komponent | Status | Notatki |
|-----------|--------|---------|
| Autentykacja | ✅ 100% | Pełna implementacja z password recovery |
| Schemat bazy | ✅ 100% | XTDB schema zdefiniowany |
| CSV parsing | ✅ 100% | `csv-import.clj` implementuje walidację |
| OpenRouter | ⚠️ 0% | Zamockowane - wymaga implementacji |
| API endpoints | ⚠️ 90% | Zaprojektowane, częściowo zaimplementowane |
| UI komponenty | ⚠️ 70% | Zaplanowane, status implementacji do weryfikacji |
| Metryki | ❌ 0% | Brak endpointu `/api/metrics` |
| Row-Level Security | ✅ 100% | Zaimplementowane w middleware |

---

### 7.5 Gotowość MVP

**Kryteria Gotowości:**
- ✅ Użytkownik może się zarejestrować i zalogować
- ✅ Użytkownik może wkleić dane CSV
- ⚠️ System waliduje i parsuje CSV (textarea zamiast pliku)
- ❌ System generuje podsumowania AI (obecnie zamockowane)
- ⚠️ Użytkownik może przeglądać, edytować, usuwać podsumowania (UI do weryfikacji)
- ⚠️ Użytkownik może akceptować podsumowania (API gotowe, UI do weryfikacji)
- ❌ System mierzy metryki sukcesu (brak endpointu)

**Werdykt:** ⚠️ **NIE GOTOWE DO PRODUKCJI**

**Blokery:**
1. 🔴 Implementacja rzeczywistej integracji OpenRouter
2. 🔴 Implementacja endpointu metryk
3. ⚠️ Weryfikacja kompletności UI

---

### 7.6 Następne Kroki

**Priorytet 1 (KRYTYCZNE):**
1. Implementacja OpenRouter API integration
   - Konfiguracja klucza API
   - HTTP client dla OpenRouter
   - Prompt engineering dla podsumowań
   - Error handling i retry logic

2. Implementacja endpointu metryk
   - `GET /api/metrics`
   - Obliczanie acceptance rate
   - Obliczanie AI adoption rate
   - Dashboard z metrykami

**Priorytet 2 (WYSOKIE):**
3. Weryfikacja i uzupełnienie UI
   - Sprawdzenie rzeczywistego kodu UI
   - Testowanie CRUD operations
   - Testowanie inline editing
   - Testowanie accept functionality

4. Testy end-to-end
   - Pełny flow importu CSV
   - Flow akceptacji podsumowań
   - Testowanie RLS

**Priorytet 3 (ŚREDNIE):**
5. Dokumentacja
   - Aktualizacja user documentation
   - Deployment guide
   - API documentation (OpenAPI/Swagger)

---

### 7.7 Dokumenty Powiązane

- `.ai/v1/auth/IMPLEMENTATION-COMPLETE.md` - Dokumentacja systemu autentykacji
- `.ai/v1/api/api-plan.md` - Szczegółowy plan API
- `.ai/v1/ui/ui-planning-summary.md` - Architektura UI
- `.ai/v1/db/db-planning-summary.md` - Schemat bazy danych
- `.ai/PRD-IMPLEMENTATION-ANALYSIS.md` - Analiza zgodności z PRD

---

**Koniec Dokumentu PRD v1.1**
