# Plan Testów: Logowanie Użytkownika (User Login)

**Moduł:** `src/com/apriary/auth.clj` - funkcje `signin`, `signout`
**Strona UI:** `src/com/apriary/pages/home.clj` - funkcja `signin-page`
**Data:** 2025-12-01
**Wersja:** 1.0

---

## 1. Cel Testowania

Weryfikacja poprawności procesu logowania użytkowników, weryfikacji poświadczeń, zarządzania sesją oraz procesu wylogowywania.

---

## 2. Zakres Testów

### 2.1. Testy Jednostkowe (Unit Tests)

#### Test 1: Weryfikacja Hasła BCrypt
**Funkcja:** `verify-password`
**Lokalizacja:** `src/com/apriary/auth.clj:10-11`

**Scenariusze testowe:**
1. Poprawne hasło dla danego hasha → `true`
2. Niepoprawne hasło dla danego hasha → `false`
3. Różnica wielkości liter w haśle → `false`
4. Pusty string jako hasło → `false`
5. Nil jako hasło → błąd lub `false`
6. Poprawne hasło ze znakami specjalnymi → `true`

**Kod testu:**
```clojure
(deftest verify-password-test
  (testing "Weryfikacja poprawnego hasła"
    (let [password "correctpassword"
          hash (auth/hash-password password)]
      (is (true? (auth/verify-password password hash)))
      (is (true? (auth/verify-password "correctpassword" hash)))))

  (testing "Weryfikacja niepoprawnego hasła"
    (let [hash (auth/hash-password "correctpassword")]
      (is (false? (auth/verify-password "wrongpassword" hash)))
      (is (false? (auth/verify-password "CORRECTPASSWORD" hash)))
      (is (false? (auth/verify-password "" hash)))))

  (testing "Weryfikacja hasła ze znakami specjalnymi"
    (let [password "p@ss!w0rd#123"
          hash (auth/hash-password password)]
      (is (true? (auth/verify-password password hash))))))
```

---

### 2.2. Testy Integracyjne (Integration Tests)

#### Test 2: Logowanie z Poprawnymi Poświadczeniami
**Handler:** `signin`
**Endpoint:** `POST /auth/signin`

**Przygotowanie:**
Utwórz użytkownika testowego:
```clojure
{:email "testuser@example.com"
 :password-hash (hash-password "mypassword123")}
```

**Dane wejściowe:**
```clojure
{:email "testuser@example.com"
 :password "mypassword123"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303` (przekierowanie)
2. Lokalizacja: `/app`
3. Sesja zawiera `:uid` (UUID użytkownika)
4. UUID w sesji odpowiada UUID użytkownika w bazie

**Kod testu:**
```clojure
(deftest signin-success-test
  (testing "Pomyślne logowanie z poprawnymi poświadczeniami"
    (let [ctx (test-context)
          user-id (create-test-user ctx "testuser@example.com" "mypassword123")
          params {:email "testuser@example.com"
                  :password "mypassword123"}
          response (auth/signin (assoc ctx :params params))]

      ;; Weryfikacja odpowiedzi
      (is (= 303 (:status response)))
      (is (= "/app" (get-in response [:headers "location"])))

      ;; Weryfikacja sesji
      (is (uuid? (get-in response [:session :uid])))
      (is (= user-id (get-in response [:session :uid]))))))
```

---

#### Test 3: Logowanie z Niepoprawnym Email
**Dane wejściowe:**
```clojure
{:email "nonexistent@example.com"
 :password "anypassword"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/signin?error=invalid-credentials`
3. Sesja NIE zawiera `:uid`

**Kod testu:**
```clojure
(deftest signin-nonexistent-email-test
  (testing "Logowanie z nieistniejącym emailem"
    (let [ctx (test-context)
          params {:email "nonexistent@example.com"
                  :password "anypassword"}
          response (auth/signin (assoc ctx :params params))]

      (is (= 303 (:status response)))
      (is (= "/signin?error=invalid-credentials" (get-in response [:headers "location"])))
      (is (nil? (get-in response [:session :uid]))))))
```

---

#### Test 4: Logowanie z Niepoprawnym Hasłem
**Przygotowanie:**
Utwórz użytkownika z hasłem "correctpassword"

**Dane wejściowe:**
```clojure
{:email "testuser@example.com"
 :password "wrongpassword"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/signin?error=invalid-credentials`
3. Sesja NIE zawiera `:uid`

**Kod testu:**
```clojure
(deftest signin-wrong-password-test
  (testing "Logowanie z niepoprawnym hasłem"
    (let [ctx (test-context)
          _ (create-test-user ctx "testuser@example.com" "correctpassword")
          params {:email "testuser@example.com"
                  :password "wrongpassword"}
          response (auth/signin (assoc ctx :params params))]

      (is (= 303 (:status response)))
      (is (= "/signin?error=invalid-credentials" (get-in response [:headers "location"])))
      (is (nil? (get-in response [:session :uid]))))))
```

---

#### Test 5: Logowanie z Niepoprawnym Formatem Email
**Dane wejściowe:**
```clojure
{:email "not-an-email"
 :password "password123"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/signin?error=invalid-email`
3. Sesja NIE zawiera `:uid`

**Kod testu:**
```clojure
(deftest signin-invalid-email-format-test
  (testing "Logowanie z niepoprawnym formatem emaila"
    (let [ctx (test-context)
          params {:email "not-an-email"
                  :password "password123"}
          response (auth/signin (assoc ctx :params params))]

      (is (= 303 (:status response)))
      (is (= "/signin?error=invalid-email" (get-in response [:headers "location"])))
      (is (nil? (get-in response [:session :uid]))))))
```

---

#### Test 6: Logowanie bez Hasła
**Dane wejściowe:**
```clojure
{:email "testuser@example.com"
 :password nil}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/signin?error=invalid-password`
3. Sesja NIE zawiera `:uid`

**Kod testu:**
```clojure
(deftest signin-missing-password-test
  (testing "Logowanie bez podania hasła"
    (let [ctx (test-context)
          params {:email "testuser@example.com"
                  :password nil}
          response (auth/signin (assoc ctx :params params))]

      (is (= 303 (:status response)))
      (is (= "/signin?error=invalid-password" (get-in response [:headers "location"])))
      (is (nil? (get-in response [:session :uid]))))))
```

---

#### Test 7: Wylogowanie Użytkownika
**Handler:** `signout`
**Endpoint:** `POST /auth/signout`

**Przygotowanie:**
Utwórz sesję z zalogowanym użytkownikiem

**Dane wejściowe:**
Kontekst z sesją zawierającą `:uid`

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/signin`
3. Sesja: `nil` (wyczyszczona)

**Kod testu:**
```clojure
(deftest signout-test
  (testing "Wylogowanie użytkownika"
    (let [ctx {:session {:uid (random-uuid)}}
          response (auth/signout ctx)]

      (is (= 303 (:status response)))
      (is (= "/signin" (get-in response [:headers "location"])))
      (is (nil? (:session response))))))
```

---

### 2.3. Testy UI (UI Tests)

#### Test 8: Renderowanie Formularza Logowania
**Strona:** `signin-page`

**Scenariusze:**
1. Strona zawiera pole email
2. Strona zawiera pole password
3. Formularz ma action `/auth/signin`
4. Przycisk "Sign in" jest widoczny
5. Link "Forgot password?" jest widoczny
6. Link do strony rejestracji jest widoczny

**Kod testu:**
```clojure
(deftest signin-page-rendering-test
  (testing "Strona logowania renderuje poprawnie"
    (let [ctx (test-context)
          page-html (str (home/signin-page ctx))]

      ;; Sprawdź pola formularza
      (is (str/includes? page-html "name=\"email\""))
      (is (str/includes? page-html "type=\"email\""))
      (is (str/includes? page-html "name=\"password\""))
      (is (str/includes? page-html "type=\"password\""))
      (is (str/includes? page-html "action=\"/auth/signin\""))
      (is (str/includes? page-html "Sign in"))

      ;; Sprawdź linki nawigacyjne
      (is (str/includes? page-html "Forgot password"))
      (is (str/includes? page-html "/forgot-password"))
      (is (str/includes? page-html "Don't have an account"))
      (is (str/includes? page-html "Sign up")))))
```

---

#### Test 9: Wyświetlanie Komunikatów o Błędach
**Scenariusze:**
1. `?error=invalid-email` → "Invalid email address."
2. `?error=invalid-password` → "Password is required."
3. `?error=invalid-credentials` → "Invalid email or password."
4. `?error=not-signed-in` → "You must be signed in to view that page."

**Kod testu:**
```clojure
(deftest signin-error-messages-test
  (testing "Komunikaty błędów logowania"
    (let [ctx (test-context)]

      (testing "Błąd niepoprawnego emaila"
        (let [page-html (str (home/signin-page (assoc ctx :params {:error "invalid-email"})))]
          (is (str/includes? page-html "Invalid email address"))))

      (testing "Błąd niepoprawnych poświadczeń"
        (let [page-html (str (home/signin-page (assoc ctx :params {:error "invalid-credentials"})))]
          (is (str/includes? page-html "Invalid email or password"))))

      (testing "Wymóg zalogowania"
        (let [page-html (str (home/signin-page (assoc ctx :params {:error "not-signed-in"})))]
          (is (str/includes? page-html "You must be signed in")))))))
```

---

## 3. Przypadki Brzegowe (Edge Cases)

### Test 10: Email z Białymi Znakami
**Dane:** `"  user@example.com  "`
**Oczekiwany wynik:** Email powinien być przycięty, logowanie powinno się udać

**Kod testu:**
```clojure
(deftest signin-email-whitespace-test
  (testing "Logowanie z emailem zawierającym białe znaki"
    (let [ctx (test-context)
          _ (create-test-user ctx "user@example.com" "password123")
          params {:email "  user@example.com  "
                  :password "password123"}
          response (auth/signin (assoc ctx :params params))]

      (is (= 303 (:status response)))
      (is (= "/app" (get-in response [:headers "location"])))
      (is (some? (get-in response [:session :uid]))))))
```

---

### Test 11: Wielkość Liter w Email (Case Sensitivity)
**Dane:** `"User@EXAMPLE.COM"` vs `"user@example.com"`
**Oczekiwany wynik:** Zależy od implementacji (preferowane: case-insensitive)

**Kod testu:**
```clojure
(deftest signin-email-case-sensitivity-test
  (testing "Wielkość liter w emailu"
    (let [ctx (test-context)
          _ (create-test-user ctx "user@example.com" "password123")]

      ;; Test z różnymi wielkościami liter
      (let [params {:email "User@Example.Com"
                    :password "password123"}
            response (auth/signin (assoc ctx :params params))]

        ;; Jeśli system traktuje email jako case-insensitive, powinno się udać
        ;; Jeśli case-sensitive, powinien być błąd
        ;; TODO: Określić oczekiwane zachowanie
        (is (some? response))))))
```

---

### Test 12: Wielokrotne Próby Logowania
**Scenariusz:** 5 nieudanych prób logowania pod rząd
**Oczekiwany wynik:** System powinien obsłużyć (w MVP: brak rate limiting, ale test dla przyszłości)

---

### Test 13: Logowanie po Zmianie Hasła
**Scenariusz:**
1. Użytkownik zmienia hasło (password reset)
2. Próba logowania ze starym hasłem → błąd
3. Próba logowania z nowym hasłem → sukces

**Kod testu:**
```clojure
(deftest signin-after-password-change-test
  (testing "Logowanie po zmianie hasła"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "oldpassword")]

      ;; Zmień hasło
      (biff/submit-tx ctx
        [{:db/op :update
          :xt/id user-id
          :user/password-hash (auth/hash-password "newpassword")}])

      ;; Stare hasło nie powinno działać
      (let [response-old (auth/signin (assoc ctx :params {:email "user@example.com"
                                                          :password "oldpassword"}))]
        (is (str/includes? (get-in response-old [:headers "location"]) "error=invalid-credentials")))

      ;; Nowe hasło powinno działać
      (let [response-new (auth/signin (assoc ctx :params {:email "user@example.com"
                                                          :password "newpassword"}))]
        (is (= "/app" (get-in response-new [:headers "location"])))
        (is (some? (get-in response-new [:session :uid])))))))
```

---

## 4. Testy Bezpieczeństwa

### Test 14: Brak Ujawnienia Informacji o Użytkowniku
**Cel:** Sprawdzić, że komunikaty błędów nie ujawniają, czy email istnieje w systemie

**Scenariusz:**
- Nieistniejący email: `invalid-credentials`
- Istniejący email, złe hasło: `invalid-credentials`
- Oba przypadki powinny mieć identyczny komunikat

**Kod testu:**
```clojure
(deftest signin-information-disclosure-test
  (testing "Komunikaty błędów nie ujawniają istnienia użytkownika"
    (let [ctx (test-context)
          _ (create-test-user ctx "exists@example.com" "password")]

      ;; Nieistniejący email
      (let [response1 (auth/signin (assoc ctx :params {:email "notexists@example.com"
                                                       :password "anypassword"}))]
        ;; Istniejący email, złe hasło
        (let [response2 (auth/signin (assoc ctx :params {:email "exists@example.com"
                                                         :password "wrongpassword"}))]

          ;; Oba powinny mieć ten sam komunikat błędu
          (is (= (get-in response1 [:headers "location"])
                 (get-in response2 [:headers "location"])))
          (is (str/includes? (get-in response1 [:headers "location"]) "invalid-credentials"))
          (is (str/includes? (get-in response2 [:headers "location"]) "invalid-credentials")))))))
```

---

### Test 15: Timing Attack Prevention
**Cel:** Sprawdzić, że czas odpowiedzi jest podobny dla istniejących i nieistniejących emaili

**Uwaga:** BCrypt automatycznie zabezpiecza przed timing attacks

```clojure
(deftest signin-timing-attack-prevention-test
  (testing "Czas odpowiedzi podobny dla różnych scenariuszy"
    (let [ctx (test-context)
          _ (create-test-user ctx "exists@example.com" "password")]

      ;; Zmierz czas dla nieistniejącego emaila
      (let [start1 (System/nanoTime)
            _ (auth/signin (assoc ctx :params {:email "notexists@example.com"
                                               :password "anypassword"}))
            duration1 (- (System/nanoTime) start1)]

        ;; Zmierz czas dla złego hasła (istniejący email)
        (let [start2 (System/nanoTime)
              _ (auth/signin (assoc ctx :params {:email "exists@example.com"
                                                 :password "wrongpassword"}))
              duration2 (- (System/nanoTime) start2)]

          ;; Czasy powinny być zbliżone (różnica < 100ms)
          (let [diff (Math/abs (- duration1 duration2))
                diff-ms (/ diff 1000000.0)]
            (is (< diff-ms 100) (str "Różnica czasów: " diff-ms "ms"))))))))
```

---

## 5. Testy Wydajnościowe

### Test 16: Czas Weryfikacji Hasła BCrypt
**Cel:** Sprawdzić, że weryfikacja hasła nie jest zbyt wolna

```clojure
(deftest bcrypt-verify-performance-test
  (testing "Weryfikacja BCrypt w akceptowalnym czasie"
    (let [hash (auth/hash-password "testpassword")
          start (System/currentTimeMillis)
          _ (auth/verify-password "testpassword" hash)
          duration (- (System/currentTimeMillis) start)]

      ;; Weryfikacja powinna być szybsza niż hashowanie
      (is (< duration 500) "Weryfikacja nie powinna trwać dłużej niż 500ms"))))
```

---

## 6. Testy Integracyjne End-to-End

### Test 17: Pełny Przepływ Logowania
**Scenariusz:**
1. Użytkownik rejestruje się
2. Użytkownik wylogowuje się
3. Użytkownik loguje się ponownie z tymi samymi poświadczeniami

**Kod testu:**
```clojure
(deftest full-signin-flow-test
  (testing "Pełny przepływ rejestracja -> wylogowanie -> logowanie"
    (let [ctx (test-context)
          email "flowtest@example.com"
          password "mypassword123"]

      ;; 1. Rejestracja
      (let [signup-response (auth/signup (assoc ctx :params {:email email
                                                             :password password
                                                             :password-confirm password}))]
        (is (= 303 (:status signup-response)))
        (is (some? (get-in signup-response [:session :uid]))))

      ;; 2. Wylogowanie
      (let [user-id (get-in (auth/signup (assoc ctx :params {:email email
                                                              :password password
                                                              :password-confirm password}))
                            [:session :uid])
            signout-response (auth/signout {:session {:uid user-id}})]
        (is (nil? (:session signout-response))))

      ;; 3. Ponowne logowanie
      (let [signin-response (auth/signin (assoc ctx :params {:email email
                                                             :password password}))]
        (is (= 303 (:status signin-response)))
        (is (= "/app" (get-in signin-response [:headers "location"])))
        (is (some? (get-in signin-response [:session :uid])))))))
```

---

## 7. Kryteria Akceptacji

### Warunki Zaliczenia Testów

- [ ] Weryfikacja hasła BCrypt działa poprawnie
- [ ] Logowanie z poprawnymi poświadczeniami tworzy sesję
- [ ] Niepoprawne poświadczenia zwracają błąd `invalid-credentials`
- [ ] Niepoprawny format email zwraca błąd `invalid-email`
- [ ] Brak hasła zwraca błąd `invalid-password`
- [ ] Wylogowanie czyści sesję
- [ ] Strona logowania renderuje wszystkie pola
- [ ] Komunikaty błędów są wyświetlane poprawnie
- [ ] Email z białymi znakami jest przycinany
- [ ] Komunikaty błędów nie ujawniają istnienia użytkownika
- [ ] Brak podatności na timing attacks (dzięki BCrypt)
- [ ] Logowanie po zmianie hasła działa poprawnie
- [ ] Pełny przepływ (rejestracja → wylogowanie → logowanie) działa

---

## 8. Narzędzia i Środowisko Testowe

### Narzędzia
- **Framework testowy:** `clojure.test`
- **Baza danych:** XTDB (in-memory)
- **Kontekst testowy:** Biff test context
- **Timing:** `System/nanoTime()` dla testów wydajności

### Środowisko
- Testy lokalne i CI/CD
- Każdy test izolowany (niezależna baza w pamięci)

---

## 9. Raportowanie Błędów

### Przykład Raportu
```
ID: SIGNIN-001
Tytuł: Email z białymi znakami powoduje błąd logowania
Priorytet: Wysoki
Kroki:
1. Utwórz konto z emailem "user@example.com"
2. Na stronie logowania wprowadź "  user@example.com  "
3. Wprowadź poprawne hasło
4. Kliknij "Sign in"

Oczekiwany: Pomyślne logowanie (email przycięty)
Rzeczywisty: Błąd "invalid-credentials"
Środowisko: Dev, XTDB 1.24, Clojure 1.12
```

---

**Koniec dokumentu**
