# Plan Testów: Funkcje Pomocnicze (Helper Functions)

**Moduł:** `src/com/apriary/auth.clj`
**Funkcje:** `valid-email?`, `valid-password?`, `now`, `plus-hours`, `after?`, `get-base-url`
**Data:** 2025-12-01
**Wersja:** 1.0

---

## 1. Cel Testowania

Weryfikacja poprawności działania funkcji pomocniczych używanych w systemie autentykacji.

---

## 2. Zakres Testów

### 2.1. Walidacja Email (`valid-email?`)

#### Test 1: Poprawne Formaty Email
**Kod testu:**
```clojure
(deftest valid-email-correct-formats-test
  (testing "Poprawne formaty email"
    (is (true? (auth/valid-email? "user@example.com")))
    (is (true? (auth/valid-email? "user.name@example.com")))
    (is (true? (auth/valid-email? "user+tag@example.com")))
    (is (true? (auth/valid-email? "user@subdomain.example.com")))
    (is (true? (auth/valid-email? "user@example.co.uk")))
    (is (true? (auth/valid-email? "123@example.com")))
    (is (true? (auth/valid-email? "user_name@example.com")))))
```

#### Test 2: Niepoprawne Formaty Email
**Kod testu:**
```clojure
(deftest valid-email-invalid-formats-test
  (testing "Niepoprawne formaty email"
    (is (false? (auth/valid-email? "not-an-email")))
    (is (false? (auth/valid-email? "@example.com")))
    (is (false? (auth/valid-email? "user@")))
    (is (false? (auth/valid-email? "user")))
    (is (false? (auth/valid-email? "")))
    (is (false? (auth/valid-email? nil)))
    (is (false? (auth/valid-email? "user @example.com")))  ;; spacja
    (is (false? (auth/valid-email? "user@.com")))
    (is (false? (auth/valid-email? "user..name@example.com")))))  ;; podwójna kropka
```

#### Test 3: Przypadki Brzegowe Email
**Kod testu:**
```clojure
(deftest valid-email-edge-cases-test
  (testing "Przypadki brzegowe email"
    ;; Bardzo długi email
    (let [long-local (apply str (repeat 100 "a"))
          long-email (str long-local "@example.com")]
      (is (boolean? (auth/valid-email? long-email))))

    ;; Email z cyframi
    (is (true? (auth/valid-email? "123456@example.com")))

    ;; Email z myślnikiem
    (is (true? (auth/valid-email? "user-name@example.com")))

    ;; Email bez TLD (niepoprawny)
    (is (false? (auth/valid-email? "user@localhost")))))
```

---

### 2.2. Walidacja Hasła (`valid-password?`)

#### Test 4: Poprawne Hasła
**Kod testu:**
```clojure
(deftest valid-password-correct-test
  (testing "Poprawne hasła (>= 8 znaków)"
    (is (true? (auth/valid-password? "12345678")))  ;; Dokładnie 8
    (is (true? (auth/valid-password? "123456789"))) ;; 9 znaków
    (is (true? (auth/valid-password? "password123")))
    (is (true? (auth/valid-password? "P@ssw0rd!")))
    (is (true? (auth/valid-password? "very-long-password-with-many-characters")))
    (is (true? (auth/valid-password? (apply str (repeat 100 "a")))))))  ;; 100 znaków
```

#### Test 5: Niepoprawne Hasła
**Kod testu:**
```clojure
(deftest valid-password-invalid-test
  (testing "Niepoprawne hasła (< 8 znaków)"
    (is (false? (auth/valid-password? "1234567")))  ;; 7 znaków
    (is (false? (auth/valid-password? "short")))
    (is (false? (auth/valid-password? "")))
    (is (false? (auth/valid-password? nil)))
    (is (false? (auth/valid-password? "       ")))  ;; 7 spacji
    (is (false? (auth/valid-password? 12345678)))))  ;; Nie string
```

#### Test 6: Hasła Ze Znakami Specjalnymi
**Kod testu:**
```clojure
(deftest valid-password-special-chars-test
  (testing "Hasła ze znakami specjalnymi"
    (is (true? (auth/valid-password? "p@ssw0rd")))
    (is (true? (auth/valid-password? "pass!@#$")))
    (is (true? (auth/valid-password? "pąśsẃöŕd")))  ;; Unicode
    (is (true? (auth/valid-password? "🔒secure🔒")))  ;; Emoji (jeśli >= 8 znaków)
    (is (true? (auth/valid-password? "tab\ttab\t")))))  ;; Tabulacja
```

---

### 2.3. Funkcje Czasowe

#### Test 7: Funkcja `now`
**Kod testu:**
```clojure
(deftest now-function-test
  (testing "Funkcja now zwraca aktualną datę"
    (let [before (java.util.Date.)
          result (auth/now)
          after (java.util.Date.)]

      (is (instance? java.util.Date result))
      (is (<= (.getTime before) (.getTime result) (.getTime after))))))
```

---

#### Test 8: Funkcja `plus-hours`
**Kod testu:**
```clojure
(deftest plus-hours-function-test
  (testing "Dodawanie godzin do daty"
    (let [base-date (java.util.Date. 1000000000000)  ;; Stały timestamp
          result-1h (auth/plus-hours base-date 1)
          result-24h (auth/plus-hours base-date 24)
          result-0h (auth/plus-hours base-date 0)]

      ;; 1 godzina = 3600000 ms
      (is (= 3600000 (- (.getTime result-1h) (.getTime base-date))))

      ;; 24 godziny = 86400000 ms
      (is (= 86400000 (- (.getTime result-24h) (.getTime base-date))))

      ;; 0 godzin = ta sama data
      (is (= (.getTime base-date) (.getTime result-0h)))))

  (testing "Ujemne godziny"
    (let [base-date (java.util.Date.)
          result (auth/plus-hours base-date -1)]

      ;; -1 godzina = -3600000 ms
      (is (< (.getTime result) (.getTime base-date)))))

  (testing "Ułamkowe godziny"
    (let [base-date (java.util.Date. 0)
          result (auth/plus-hours base-date 0.5)]  ;; 30 minut

      ;; 0.5h = 1800000 ms
      (is (= 1800000 (.getTime result))))))
```

---

#### Test 9: Funkcja `after?`
**Kod testu:**
```clojure
(deftest after-function-test
  (testing "Sprawdzanie czy data1 jest po date2"
    (let [past (java.util.Date. 1000000000000)
          future (java.util.Date. 2000000000000)
          now (java.util.Date.)]

      ;; Przyszłość jest po przeszłości
      (is (true? (auth/after? future past)))

      ;; Przeszłość nie jest po przyszłości
      (is (false? (auth/after? past future)))

      ;; Teraz jest po przeszłości
      (is (true? (auth/after? now past)))

      ;; Ta sama data
      (is (false? (auth/after? past past)))))

  (testing "Małe różnice czasowe"
    (let [date1 (java.util.Date. 1000000000000)
          date2 (java.util.Date. 1000000000001)]  ;; 1ms później

      (is (true? (auth/after? date2 date1)))
      (is (false? (auth/after? date1 date2))))))
```

---

### 2.4. Funkcja `get-base-url`

#### Test 10: Pobieranie Base URL Z Kontekstu
**Kod testu:**
```clojure
(deftest get-base-url-test
  (testing "Pobieranie base URL z kontekstu"
    (let [ctx {:biff/base-url "https://example.com"}
          result (auth/get-base-url ctx)]

      (is (= "https://example.com" result))))

  (testing "Base URL z portem"
    (let [ctx {:biff/base-url "http://localhost:8080"}
          result (auth/get-base-url ctx)]

      (is (= "http://localhost:8080" result))))

  (testing "Brak base-url w kontekście"
    (let [ctx {}
          result (auth/get-base-url ctx)]

      (is (nil? result)))))
```

---

## 3. Testy Integracyjne

#### Test 11: Walidacja W Kontekście Signup
**Kod testu:**
```clojure
(deftest validation-in-signup-context-test
  (testing "Walidacja email i hasła w kontekście signup"
    (let [ctx (test-context)]

      ;; Niepoprawny email
      (let [response (auth/signup (assoc ctx :params
                                        {:email "invalid"
                                         :password "password123"
                                         :password-confirm "password123"}))]
        (is (str/includes? (get-in response [:headers "location"]) "invalid-email")))

      ;; Zbyt krótkie hasło
      (let [response (auth/signup (assoc ctx :params
                                        {:email "valid@example.com"
                                         :password "short"
                                         :password-confirm "short"}))]
        (is (str/includes? (get-in response [:headers "location"]) "invalid-password"))))))
```

---

#### Test 12: Funkcje Czasowe W Kontekście Reset Token
**Kod testu:**
```clojure
(deftest time-functions-in-reset-context-test
  (testing "Funkcje czasowe w kontekście reset password"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")
          created-at (auth/now)
          expires-at (auth/plus-hours created-at 1)]

      ;; Utwórz token
      (let [token-id (create-reset-token ctx user-id "hash" :expires-at expires-at)
            token (biff/lookup (:biff/db ctx) :xt/id token-id)]

        ;; Sprawdź daty
        (is (auth/after? expires-at created-at))
        (is (= expires-at (:password-reset-token/expires-at token)))

        ;; Token nie powinien być wygasły zaraz po utworzeniu
        (is (not (auth/after? (auth/now) expires-at)))))))
```

---

## 4. Przypadki Brzegowe

#### Test 13: Email Z Białymi Znakami
**Kod testu:**
```clojure
(deftest email-with-whitespace-test
  (testing "Email z białymi znakami"
    ;; Przed walidacją powinien być trim
    (is (false? (auth/valid-email? " user@example.com")))
    (is (false? (auth/valid-email? "user@example.com ")))
    (is (false? (auth/valid-email? " user@example.com ")))

    ;; Spacja w środku
    (is (false? (auth/valid-email? "user @example.com")))))
```

---

#### Test 14: Hasło Z Samymi Spacjami
**Kod testu:**
```clojure
(deftest password-whitespace-only-test
  (testing "Hasło składające się tylko z białych znaków"
    ;; 8 spacji - technicznie spełnia wymóg długości
    ;; Ale czy powinniśmy akceptować?
    (let [eight-spaces "        "]
      ;; Jeśli length check jest jedynym kryterium - przejdzie
      (is (= 8 (count eight-spaces)))

      ;; TODO: Rozważyć dodanie walidacji "non-blank"
      (is (true? (auth/valid-password? eight-spaces))))))
```

---

## 5. Testy Wydajnościowe

#### Test 15: Wydajność Walidacji Email
**Kod testu:**
```clojure
(deftest email-validation-performance-test
  (testing "Walidacja 10000 emaili w rozsądnym czasie"
    (let [emails (concat
                  (repeatedly 5000 #(str (random-uuid) "@example.com"))
                  (repeatedly 5000 #(str "invalid" (rand-int 1000))))
          start (System/currentTimeMillis)
          _ (doall (map auth/valid-email? emails))
          duration (- (System/currentTimeMillis) start)]

      ;; 10000 walidacji < 100ms
      (is (< duration 100) (str "Walidacja zajęła " duration "ms")))))
```

---

## 6. Kryteria Akceptacji

- [ ] `valid-email?` akceptuje poprawne formaty email
- [ ] `valid-email?` odrzuca niepoprawne formaty
- [ ] `valid-email?` obsługuje przypadki brzegowe
- [ ] `valid-password?` wymaga minimum 8 znaków
- [ ] `valid-password?` akceptuje znaki specjalne i Unicode
- [ ] `now` zwraca aktualną datę
- [ ] `plus-hours` poprawnie dodaje godziny
- [ ] `plus-hours` obsługuje ujemne wartości
- [ ] `after?` poprawnie porównuje daty
- [ ] `get-base-url` pobiera URL z kontekstu
- [ ] Walidacja działa w kontekście signup/signin
- [ ] Funkcje czasowe działają w kontekście reset token
- [ ] Email z białymi znakami jest odrzucany
- [ ] Wydajność walidacji jest odpowiednia

---

**Koniec dokumentu**
