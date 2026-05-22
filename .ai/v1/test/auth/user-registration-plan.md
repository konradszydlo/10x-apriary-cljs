# Plan Testów: Rejestracja Użytkownika (User Registration)

**Moduł:** `src/com/apriary/auth.clj` - funkcja `signup`
**Strona UI:** `src/com/apriary/pages/home.clj` - funkcja `home-page`
**Data:** 2025-12-01
**Wersja:** 1.0

---

## 1. Cel Testowania

Weryfikacja poprawności procesu rejestracji nowych użytkowników, w tym walidacji danych wejściowych, tworzenia konta użytkownika, hashowania hasła oraz zarządzania sesją.

---

## 2. Zakres Testów

### 2.1. Testy Jednostkowe (Unit Tests)

#### Test 1: Walidacja Formatu Email
**Funkcja:** `valid-email?`
**Lokalizacja:** `src/com/apriary/auth.clj:13-15`

**Scenariusze testowe:**
1. Poprawny email: `test@example.com` → `true`
2. Email z subdomeną: `user@mail.example.co.uk` → `true`
3. Email bez @: `testexample.com` → `false`
4. Email bez domeny: `test@` → `false`
5. Email bez części lokalnej: `@example.com` → `false`
6. Pusty string: `""` → `false`
7. Nil: `nil` → `false`
8. Email ze znakami specjalnymi: `test+tag@example.com` → zgodnie ze specyfikacją regex

**Kod testu:**
```clojure
(deftest valid-email-test
  (testing "Poprawne formaty email"
    (is (true? (auth/valid-email? "test@example.com")))
    (is (true? (auth/valid-email? "user@mail.example.co.uk"))))

  (testing "Niepoprawne formaty email"
    (is (false? (auth/valid-email? "testexample.com")))
    (is (false? (auth/valid-email? "test@")))
    (is (false? (auth/valid-email? "@example.com")))
    (is (false? (auth/valid-email? "")))
    (is (false? (auth/valid-email? nil)))))
```

---

#### Test 2: Walidacja Długości Hasła
**Funkcja:** `valid-password?`
**Lokalizacja:** `src/com/apriary/auth.clj:17-19`

**Scenariusze testowe:**
1. Hasło 8 znaków: `"password"` → `true`
2. Hasło 9 znaków: `"password1"` → `true`
3. Hasło 7 znaków: `"pass123"` → `false`
4. Pusty string: `""` → `false`
5. Nil: `nil` → `false`
6. Hasło bardzo długie (100 znaków): → `true`
7. Hasło ze znakami specjalnymi: `"p@ssw0rd!"` → `true` (jeśli >= 8 znaków)

**Kod testu:**
```clojure
(deftest valid-password-test
  (testing "Poprawne hasła (>= 8 znaków)"
    (is (true? (auth/valid-password? "password")))
    (is (true? (auth/valid-password? "password1")))
    (is (true? (auth/valid-password? "p@ssw0rd!")))
    (is (true? (auth/valid-password? (apply str (repeat 100 "a"))))))

  (testing "Niepoprawne hasła (< 8 znaków)"
    (is (false? (auth/valid-password? "pass123")))
    (is (false? (auth/valid-password? "")))
    (is (false? (auth/valid-password? nil)))))
```

---

#### Test 3: Hashowanie Hasła
**Funkcja:** `hash-password`
**Lokalizacja:** `src/com/apriary/auth.clj:7-8`

**Scenariusze testowe:**
1. Hashowanie tego samego hasła dwukrotnie generuje różne hashe (salt)
2. Hash ma odpowiedni format BCrypt (`$2a$10$...`)
3. Hash ma odpowiednią długość (60 znaków)
4. Hashowane hasło można zweryfikować funkcją `verify-password`

**Kod testu:**
```clojure
(deftest hash-password-test
  (testing "BCrypt generuje unikalne hashe z solą"
    (let [password "password123"
          hash1 (auth/hash-password password)
          hash2 (auth/hash-password password)]
      (is (not= hash1 hash2))
      (is (string? hash1))
      (is (= 60 (count hash1)))
      (is (str/starts-with? hash1 "$2a$"))))

  (testing "Zhashowane hasło można zweryfikować"
    (let [password "mypassword"
          hash (auth/hash-password password)]
      (is (auth/verify-password password hash))
      (is (false? (auth/verify-password "wrongpassword" hash))))))
```

---

### 2.2. Testy Integracyjne (Integration Tests)

#### Test 4: Rejestracja z Poprawnymi Danymi
**Handler:** `signup`
**Endpoint:** `POST /auth/signup`

**Dane wejściowe:**
```clojure
{:email "newuser@example.com"
 :password "password123"
 :password-confirm "password123"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303` (przekierowanie)
2. Lokalizacja: `/app`
3. Sesja zawiera `:uid` (UUID użytkownika)
4. Użytkownik został utworzony w bazie danych
5. Hasło zostało zhashowane (BCrypt)
6. Pole `:user/joined-at` zawiera datę

**Kod testu:**
```clojure
(deftest signup-success-test
  (testing "Pomyślna rejestracja użytkownika"
    (let [ctx (test-context)
          params {:email "newuser@example.com"
                  :password "password123"
                  :password-confirm "password123"}
          response (auth/signup (assoc ctx :params params))]

      ;; Weryfikacja odpowiedzi
      (is (= 303 (:status response)))
      (is (= "/app" (get-in response [:headers "location"])))
      (is (uuid? (get-in response [:session :uid])))

      ;; Weryfikacja użytkownika w bazie
      (let [user (biff/lookup (:biff/db ctx) :user/email "newuser@example.com")]
        (is (some? user))
        (is (= "newuser@example.com" (:user/email user)))
        (is (str/starts-with? (:user/password-hash user) "$2a$"))
        (is (inst? (:user/joined-at user)))))))
```

---

#### Test 5: Rejestracja z Niepoprawnym Email
**Dane wejściowe:**
```clojure
{:email "invalid-email"
 :password "password123"
 :password-confirm "password123"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/?error=invalid-email`
3. Użytkownik NIE został utworzony w bazie
4. Sesja NIE zawiera `:uid`

**Kod testu:**
```clojure
(deftest signup-invalid-email-test
  (testing "Rejestracja z niepoprawnym emailem"
    (let [ctx (test-context)
          params {:email "invalid-email"
                  :password "password123"
                  :password-confirm "password123"}
          response (auth/signup (assoc ctx :params params))]

      (is (= 303 (:status response)))
      (is (= "/?error=invalid-email" (get-in response [:headers "location"])))
      (is (nil? (get-in response [:session :uid])))

      ;; Weryfikacja, że użytkownik nie został utworzony
      (is (nil? (biff/lookup (:biff/db ctx) :user/email "invalid-email"))))))
```

---

#### Test 6: Rejestracja z Zbyt Krótkim Hasłem
**Dane wejściowe:**
```clojure
{:email "user@example.com"
 :password "short"
 :password-confirm "short"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/?error=invalid-password`
3. Użytkownik NIE został utworzony

**Kod testu:**
```clojure
(deftest signup-invalid-password-test
  (testing "Rejestracja z hasłem krótszym niż 8 znaków"
    (let [ctx (test-context)
          params {:email "user@example.com"
                  :password "short"
                  :password-confirm "short"}
          response (auth/signup (assoc ctx :params params))]

      (is (= 303 (:status response)))
      (is (= "/?error=invalid-password" (get-in response [:headers "location"])))
      (is (nil? (biff/lookup (:biff/db ctx) :user/email "user@example.com"))))))
```

---

#### Test 7: Rejestracja z Niezgodnymi Hasłami
**Dane wejściowe:**
```clojure
{:email "user@example.com"
 :password "password123"
 :password-confirm "different456"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/?error=password-mismatch`
3. Użytkownik NIE został utworzony

**Kod testu:**
```clojure
(deftest signup-password-mismatch-test
  (testing "Rejestracja z niezgodnymi hasłami"
    (let [ctx (test-context)
          params {:email "user@example.com"
                  :password "password123"
                  :password-confirm "different456"}
          response (auth/signup (assoc ctx :params params))]

      (is (= 303 (:status response)))
      (is (= "/?error=password-mismatch" (get-in response [:headers "location"])))
      (is (nil? (biff/lookup (:biff/db ctx) :user/email "user@example.com"))))))
```

---

#### Test 8: Rejestracja z Istniejącym Email
**Przygotowanie:** Utwórz użytkownika z emailem `existing@example.com`

**Dane wejściowe:**
```clojure
{:email "existing@example.com"
 :password "password123"
 :password-confirm "password123"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/?error=email-exists`
3. Drugi użytkownik NIE został utworzony
4. Liczba użytkowników z tym emailem: 1

**Kod testu:**
```clojure
(deftest signup-email-exists-test
  (testing "Rejestracja z już istniejącym emailem"
    (let [ctx (test-context)]
      ;; Utwórz istniejącego użytkownika
      (biff/submit-tx ctx
        [{:db/op :create
          :db/doc-type :user
          :xt/id (random-uuid)
          :user/id (random-uuid)
          :user/email "existing@example.com"
          :user/password-hash (auth/hash-password "oldpass")
          :user/joined-at (java.util.Date.)}])

      ;; Próba rejestracji z tym samym emailem
      (let [params {:email "existing@example.com"
                    :password "password123"
                    :password-confirm "password123"}
            response (auth/signup (assoc ctx :params params))]

        (is (= 303 (:status response)))
        (is (= "/?error=email-exists" (get-in response [:headers "location"])))))))
```

---

### 2.3. Testy UI (UI Tests)

#### Test 9: Renderowanie Formularza Rejestracji
**Strona:** `home-page`

**Scenariusze:**
1. Strona zawiera pole email z odpowiednimi atrybutami
2. Strona zawiera pole password z wymogiem 8 znaków
3. Strona zawiera pole password-confirm
4. Formularz ma action `/auth/signup`
5. Przycisk "Sign up" jest widoczny
6. Link do strony logowania jest widoczny

**Kod testu:**
```clojure
(deftest signup-page-rendering-test
  (testing "Strona rejestracji renderuje poprawnie"
    (let [ctx (test-context)
          page-html (str (home/home-page ctx))]

      ;; Sprawdź pola formularza
      (is (str/includes? page-html "name=\"email\""))
      (is (str/includes? page-html "type=\"email\""))
      (is (str/includes? page-html "name=\"password\""))
      (is (str/includes? page-html "name=\"password-confirm\""))
      (is (str/includes? page-html "action=\"/auth/signup\""))
      (is (str/includes? page-html "Sign up"))
      (is (str/includes? page-html "Already have an account"))
      (is (str/includes? page-html "/signin")))))
```

---

#### Test 10: Wyświetlanie Komunikatów o Błędach
**Scenariusze:**
1. `?error=invalid-email` → "Invalid email address. Please try again."
2. `?error=invalid-password` → "Password must be at least 8 characters."
3. `?error=password-mismatch` → "Passwords do not match. Please try again."
4. `?error=email-exists` → "An account with this email already exists. Please sign in."

**Kod testu:**
```clojure
(deftest signup-error-messages-test
  (testing "Komunikaty błędów rejestracji"
    (let [ctx (test-context)]

      (testing "Błąd niepoprawnego emaila"
        (let [page-html (str (home/home-page (assoc ctx :params {:error "invalid-email"})))]
          (is (str/includes? page-html "Invalid email address"))))

      (testing "Błąd niezgodnych haseł"
        (let [page-html (str (home/home-page (assoc ctx :params {:error "password-mismatch"})))]
          (is (str/includes? page-html "Passwords do not match"))))

      (testing "Błąd istniejącego konta"
        (let [page-html (str (home/home-page (assoc ctx :params {:error "email-exists"})))]
          (is (str/includes? page-html "account with this email already exists")))))))
```

---

## 3. Przypadki Brzegowe (Edge Cases)

### Test 11: Email z Białymi Znakami
**Dane:** `"  user@example.com  "`
**Oczekiwany wynik:** Email powinien być przycięty (`str/trim`), rejestracja powinna się udać

### Test 12: Email w Różnych Wielkościach Liter
**Dane:** `"User@EXAMPLE.COM"`
**Oczekiwany wynik:** System powinien obsłużyć (zachować lub normalizować wielkość liter)

### Test 13: Bardzo Długie Hasło
**Dane:** Hasło 500 znaków
**Oczekiwany wynik:** Rejestracja powinna się udać (BCrypt obsługuje długie hasła)

### Test 14: Hasło ze Znakami Specjalnymi i Unicode
**Dane:** `"pąśsẃöŕd123!@#$%"`
**Oczekiwany wynik:** Rejestracja powinna się udać

---

## 4. Testy Bezpieczeństwa

### Test 15: Weryfikacja Hashowania BCrypt
**Cel:** Sprawdzić, że hasła NIE są przechowywane w postaci jawnej

**Kod testu:**
```clojure
(deftest password-hashing-security-test
  (testing "Hasła są hashowane przed zapisem do bazy"
    (let [ctx (test-context)
          params {:email "secure@example.com"
                  :password "mypassword123"
                  :password-confirm "mypassword123"}
          _ (auth/signup (assoc ctx :params params))
          user (biff/lookup (:biff/db ctx) :user/email "secure@example.com")]

      ;; Hash powinien być różny od hasła jawnego
      (is (not= "mypassword123" (:user/password-hash user)))

      ;; Hash powinien mieć format BCrypt
      (is (str/starts-with? (:user/password-hash user) "$2a$"))

      ;; Weryfikacja hasła powinna działać
      (is (auth/verify-password "mypassword123" (:user/password-hash user))))))
```

---

## 5. Testy Wydajnościowe (Performance Tests)

### Test 16: Czas Hashowania Hasła
**Cel:** Sprawdzić, że hashowanie BCrypt nie jest zbyt wolne (ale też nie za szybkie - bezpieczeństwo)

**Oczekiwany wynik:** Hashowanie powinno zająć 50-300ms (w zależności od work factor)

```clojure
(deftest bcrypt-performance-test
  (testing "BCrypt hashowanie w akceptowalnym czasie"
    (let [start (System/currentTimeMillis)
          _ (auth/hash-password "testpassword")
          duration (- (System/currentTimeMillis) start)]

      ;; BCrypt powinien być wolny (security), ale nie za wolny (UX)
      (is (< duration 1000) "Hashowanie nie powinno trwać dłużej niż 1s")
      (is (> duration 10) "Hashowanie powinno trwać co najmniej 10ms (security)"))))
```

---

## 6. Kryteria Akceptacji

### Warunki Zaliczenia Testów

- [ ] Wszystkie testy jednostkowe walidacji (email, hasło) przechodzą
- [ ] Testy hashowania BCrypt przechodzą (unikalne hashe, format)
- [ ] Rejestracja z poprawnymi danymi tworzy użytkownika i sesję
- [ ] Wszystkie przypadki błędów zwracają odpowiednie kody błędów
- [ ] Hasła są hashowane przed zapisem (NIGDY w postaci jawnej)
- [ ] Email jest walidowany po stronie backendu (nie tylko frontend)
- [ ] Niezgodne hasła są odrzucane
- [ ] Duplikaty emaili są wykrywane
- [ ] Strona rejestracji renderuje wszystkie wymagane pola
- [ ] Komunikaty błędów są wyświetlane poprawnie
- [ ] Testy brzegowe przechodzą (białe znaki, długie hasła)
- [ ] Sesja jest tworzona po udanej rejestracji
- [ ] UUID użytkownika jest generowane poprawnie

---

## 7. Narzędzia i Środowisko Testowe

### Narzędzia
- **Framework testowy:** `clojure.test`
- **Baza danych:** XTDB (in-memory dla testów)
- **Kontekst testowy:** Biff test context
- **Mock'owanie:** Brak (testy integracyjne z rzeczywistą bazą in-memory)

### Środowisko
- Testy uruchamiane lokalnie i w CI/CD (GitHub Actions)
- Baza danych w pamięci (izolacja testów)
- Każdy test powinien być niezależny (setup i teardown)

---

## 8. Helper Functions dla Testów

```clojure
(defn test-context
  "Tworzy kontekst testowy z in-memory bazą danych"
  []
  {:biff/db (xtdb/db test-node)
   :biff.xtdb/node test-node})

(defn create-test-user
  "Tworzy użytkownika testowego w bazie"
  [ctx email password]
  (let [user-id (random-uuid)
        password-hash (auth/hash-password password)]
    (biff/submit-tx ctx
      [{:db/op :create
        :db/doc-type :user
        :xt/id user-id
        :user/id user-id
        :user/email email
        :user/password-hash password-hash
        :user/joined-at (java.util.Date.)}])
    user-id))
```

---

## 9. Harmonogram Wykonania

1. **Faza 1 (Dzień 1):** Testy jednostkowe walidacji (Test 1-3)
2. **Faza 2 (Dzień 2-3):** Testy integracyjne rejestracji (Test 4-8)
3. **Faza 3 (Dzień 4):** Testy UI (Test 9-10)
4. **Faza 4 (Dzień 5):** Przypadki brzegowe i bezpieczeństwo (Test 11-15)
5. **Faza 5 (Dzień 6):** Testy wydajnościowe i finalizacja (Test 16)

---

## 10. Raportowanie Błędów

### Struktura Raportu Błędu
- **ID:** SIGNUP-XXX
- **Tytuł:** Krótki opis problemu
- **Priorytet:** Krytyczny / Wysoki / Średni / Niski
- **Kroki reprodukcji:** Szczegółowe kroki
- **Oczekiwany wynik:** Co powinno się wydarzyć
- **Rzeczywisty wynik:** Co faktycznie się wydarzyło
- **Środowisko:** Wersja, system operacyjny
- **Logi:** Powiązane logi i stack trace

### Przykład
```
ID: SIGNUP-001
Tytuł: Email z białymi znakami nie jest przycinany
Priorytet: Średni
Kroki:
1. Wejdź na stronę rejestracji
2. Wprowadź email "  user@example.com  "
3. Wypełnij pozostałe pola
4. Kliknij "Sign up"

Oczekiwany: Email powinien być przycięty, użytkownik utworzony
Rzeczywisty: Błąd walidacji emaila
```

---

**Koniec dokumentu**
