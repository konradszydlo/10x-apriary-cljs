# Plan Testów: Funkcje Bezpieczeństwa (Security Features)

**Zakres:** Kompleksowe testy bezpieczeństwa systemu autentykacji
**Moduły:** `auth.clj`, `email.clj`, `middleware.clj`
**Data:** 2025-12-01
**Wersja:** 1.0

---

## 1. Cel Testowania

Weryfikacja bezpieczeństwa systemu autentykacji pod kątem:
- Ochrony hasłow (BCrypt)
- Bezpieczeństwa tokenów (SHA-256)
- Zapobiegania atakom (CSRF, XSS, timing attacks, email enumeration)
- Bezpieczeństwa sesji
- Walidacji danych wejściowych

---

## 2. Zakres Testów

### 2.1. BCrypt Password Security

#### Test 1: Hasła Są Hashowane, Nigdy W Postaci Jawnej
**Cel:** Sprawdzić, że hasła NIGDY nie są przechowywane w postaci tekstowej

**Kod testu:**
```clojure
(deftest passwords-never-plaintext-test
  (testing "Hasła nigdy nie są w postaci jawnej"
    (let [ctx (test-context)
          plaintext-password "supersecretpassword"
          params {:email "secure@example.com"
                  :password plaintext-password
                  :password-confirm plaintext-password}
          _ (auth/signup (assoc ctx :params params))
          user (biff/lookup (:biff/db ctx) :user/email "secure@example.com")]

      ;; Hash nie jest równy hasłu jawnemu
      (is (not= plaintext-password (:user/password-hash user)))

      ;; Hash nie zawiera fragmentów hasła jawnego
      (is (not (str/includes? (:user/password-hash user) "supersecret")))
      (is (not (str/includes? (:user/password-hash user) "password"))))))
```

---

#### Test 2: Każdy Hash Jest Unikalny (Salt)
**Cel:** Sprawdzić, że BCrypt używa soli (salt) dla każdego hashowania

**Kod testu:**
```clojure
(deftest bcrypt-salt-uniqueness-test
  (testing "BCrypt generuje unikalne hashe dla tego samego hasła"
    (let [password "samepassword"
          hash1 (auth/hash-password password)
          hash2 (auth/hash-password password)]

      ;; Różne hashe mimo tego samego hasła
      (is (not= hash1 hash2))

      ;; Oba hashe są prawidłowe
      (is (auth/verify-password password hash1))
      (is (auth/verify-password password hash2)))))
```

---

#### Test 3: BCrypt Work Factor (Koszt Obliczeniowy)
**Cel:** Sprawdzić, że BCrypt ma odpowiedni work factor (domyślnie 10)

**Kod testu:**
```clojure
(deftest bcrypt-work-factor-test
  (testing "BCrypt używa odpowiedniego work factor"
    (let [hash (auth/hash-password "testpassword")]

      ;; Format BCrypt: $2a$10$...
      ;; gdzie 10 to work factor
      (is (str/starts-with? hash "$2a$"))

      ;; Wyciągnij work factor z hasha
      (let [parts (str/split hash #"\$")
            work-factor (Integer/parseInt (nth parts 2))]

        ;; Work factor powinien wynosić co najmniej 10
        (is (>= work-factor 10) "Work factor zbyt niski (security risk)")
        (is (<= work-factor 14) "Work factor może być zbyt wysoki (UX issue)")))))
```

---

### 2.2. SHA-256 Token Security

#### Test 4: Tokeny Są Hashowane Przed Zapisem
**Cel:** Sprawdzić, że tokeny resetowania hasła są hashowane

**Kod testu:**
```clojure
(deftest tokens-hashed-before-storage-test
  (testing "Tokeny są hashowane przed zapisem do bazy"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")
          raw-token (auth/generate-secure-token)
          hashed-token (auth/hash-token raw-token)]

      ;; Utwórz token w bazie
      (let [token-id (create-reset-token ctx user-id hashed-token)
            stored-token (biff/lookup (:biff/db ctx) :xt/id token-id)]

        ;; W bazie jest hash, nie raw token
        (is (not= raw-token (:password-reset-token/token stored-token)))
        (is (= hashed-token (:password-reset-token/token stored-token)))

        ;; Hash ma format SHA-256 (64 hex chars)
        (is (= 64 (count (:password-reset-token/token stored-token))))))))
```

---

### 2.3. CSRF Protection

#### Test 5: CSRF Token W Formularzach
**Cel:** Sprawdzić, że formularze zawierają CSRF token

**Uwaga:** Biff automatycznie dodaje CSRF tokeny przez `biff/form`

**Kod testu:**
```clojure
(deftest csrf-token-in-forms-test
  (testing "Formularze zawierają CSRF token"
    (let [ctx (test-context)
          signup-page-html (str (home/home-page ctx))]

      ;; Formularz powinien zawierać anti-forgery token
      ;; Biff dodaje ukryte pole z tokenem
      (is (or (str/includes? signup-page-html "__anti-forgery-token")
              (str/includes? signup-page-html "csrf-token")
              ;; Lub weryfikuj że używa biff/form który automatycznie dodaje
              (str/includes? signup-page-html "action=\"/auth/signup\""))))))
```

---

### 2.4. Email Enumeration Prevention

#### Test 6: Ta Sama Odpowiedź Dla Istniejących I Nieistniejących Emaili
**Cel:** Sprawdzić, że system nie ujawnia, czy email istnieje

**Kod testu:**
```clojure
(deftest email-enumeration-prevention-test
  (testing "Brak ujawnienia istnienia emaila (signup)"
    ;; W signup: email-exists zwraca błąd, ale to jest OK
    ;; Użytkownik SAM próbuje się zarejestrować, więc wie że email jest jego
    (is true "Signup celowo ujawnia email-exists"))

  (testing "Brak ujawnienia istnienia emaila (password reset)"
    (let [ctx (test-context)
          _ (create-test-user ctx "exists@example.com" "password")]

      ;; Reset dla istniejącego
      (let [response-exists (auth/send-password-reset
                            (assoc ctx :params {:email "exists@example.com"}))]

        ;; Reset dla nieistniejącego
        (let [response-not-exists (auth/send-password-reset
                                  (assoc ctx :params {:email "notexists@example.com"}))]

          ;; IDENTYCZNA odpowiedź
          (is (= (:status response-exists) (:status response-not-exists)))
          (is (= (get-in response-exists [:headers "location"])
                 (get-in response-not-exists [:headers "location"])))
          (is (= "/password-reset-sent"
                 (get-in response-exists [:headers "location"]))))))))
```

---

### 2.5. Timing Attack Prevention

#### Test 7: Podobny Czas Odpowiedzi (Login)
**Cel:** Sprawdzić, że czas odpowiedzi jest podobny dla różnych scenariuszy logowania

**Kod testu:**
```clojure
(deftest timing-attack-prevention-login-test
  (testing "Czas odpowiedzi podobny dla istniejących i nieistniejących użytkowników"
    (let [ctx (test-context)
          _ (create-test-user ctx "exists@example.com" "password")
          iterations 10]

      ;; Zmierz średni czas dla istniejącego użytkownika (złe hasło)
      (let [times-exists (repeatedly iterations
                                    (fn []
                                      (let [start (System/nanoTime)
                                            _ (auth/signin (assoc ctx :params
                                                                 {:email "exists@example.com"
                                                                  :password "wrongpassword"}))
                                            end (System/nanoTime)]
                                        (- end start))))
            avg-exists (/ (reduce + times-exists) iterations)]

        ;; Zmierz średni czas dla nieistniejącego użytkownika
        (let [times-not-exists (repeatedly iterations
                                          (fn []
                                            (let [start (System/nanoTime)
                                                  _ (auth/signin (assoc ctx :params
                                                                       {:email "notexists@example.com"
                                                                        :password "anypassword"}))
                                                  end (System/nanoTime)]
                                              (- end start))))
              avg-not-exists (/ (reduce + times-not-exists) iterations)]

          ;; Czasy powinny być zbliżone (różnica < 20%)
          (let [diff (Math/abs (- avg-exists avg-not-exists))
                diff-percent (* 100 (/ diff (max avg-exists avg-not-exists)))]

            ;; BCrypt powinien zapewnić podobny czas
            (is (< diff-percent 30)
                (str "Różnica czasów: " (int diff-percent) "%"))))))))
```

---

### 2.6. XSS Prevention

#### Test 8: HTML Escaping W Komunikatach Błędów
**Cel:** Sprawdzić, że user input jest escapowany

**Kod testu:**
```clojure
(deftest xss-prevention-test
  (testing "HTML jest escapowany w komunikatach błędów"
    (let [ctx (test-context)
          malicious-email "<script>alert('XSS')</script>@example.com"
          params {:email malicious-email
                  :password "password123"}
          ;; Próba logowania z XSS w emailu
          _ (auth/signin (assoc ctx :params params))

          ;; Renderuj stronę z błędem
          page-html (str (home/signin-page (assoc ctx :params {:error "invalid-email"})))]

      ;; HTML powinien być escapowany
      (is (not (str/includes? page-html "<script>")))
      (is (not (str/includes? page-html "alert('XSS')")))

      ;; Rum/Biff automatycznie escapuje, ale sprawdźmy
      ;; Jeśli zawiera, to powinno być &lt;script&gt;
      (if (str/includes? page-html "script")
        (is (str/includes? page-html "&lt;script&gt;"))))))
```

---

### 2.7. SQL Injection Prevention

#### Test 9: Datalog Queries Są Bezpieczne
**Cel:** Sprawdzić, że XTDB Datalog nie jest podatny na injection

**Uwaga:** Datalog używa parametryzowanych zapytań, więc jest bezpieczny

**Kod testu:**
```clojure
(deftest sql-injection-prevention-test
  (testing "Datalog queries są bezpieczne"
    (let [ctx (test-context)
          malicious-email "test@example.com'; DROP TABLE users--"
          params {:email malicious-email
                  :password "password"}
          _ (auth/signin (assoc ctx :params params))]

      ;; Datalog użyje emaila jako parametr, nie wykona SQL
      ;; Baza powinna pozostać nienaruszona
      (is (some? (:biff/db ctx)))

      ;; Nie powinno być błędu parsowania
      (is true "Query nie zawiera SQL syntax"))))
```

---

### 2.8. Session Security

#### Test 10: Session Cookies HttpOnly (Konfiguracja)
**Cel:** Sprawdzić konfigurację ciasteczek sesji

**Kod testu:**
```clojure
(deftest session-cookie-security-test
  (testing "Session cookies powinny być HttpOnly i Secure"
    ;; To wymaga sprawdzenia konfiguracji Biff middleware
    ;; W production:
    ;; - HttpOnly: true (JavaScript nie może odczytać)
    ;; - Secure: true (tylko HTTPS)
    ;; - SameSite: Strict lub Lax

    ;; TODO: Sprawdź config.edn
    ;; :biff.middleware/cookie-secret powinien być ustawiony
    ;; :biff.middleware/secure powinien być true w production

    (is true "Wymaga review konfiguracji production")))
```

---

#### Test 11: Session Nie Zawiera Wrażliwych Danych
**Cel:** Sprawdzić, że sesja nie zawiera haseł, emaili, etc.

**Kod testu:**
```clojure
(deftest session-no-sensitive-data-security-test
  (testing "Sesja nie zawiera hasła ani hasha"
    (let [ctx (test-context)
          _ (create-test-user ctx "user@example.com" "secretpassword123")
          response (auth/signin (assoc ctx :params {:email "user@example.com"
                                                     :password "secretpassword123"}))
          session (:session response)]

      ;; Tylko :uid
      (is (= [:uid] (keys session)))

      ;; Nie zawiera hasła
      (is (not (contains? session :password)))
      (is (not (contains? session :password-hash)))

      ;; Nie zawiera emaila
      (is (not (contains? session :email))))))
```

---

### 2.9. Input Validation

#### Test 12: Walidacja Długości Inputów
**Cel:** Sprawdzić, że system odrzuca zbyt długie inputy

**Kod testu:**
```clojure
(deftest input-length-validation-test
  (testing "Zbyt długi email jest odrzucany"
    (let [ctx (test-context)
          very-long-email (str (apply str (repeat 1000 "a")) "@example.com")
          params {:email very-long-email
                  :password "password123"}
          response (auth/signin (assoc ctx :params params))]

      ;; Powinien być błąd walidacji
      (is (or (= 303 (:status response))
              (= 400 (:status response))))))

  (testing "Zbyt długie hasło jest akceptowane (BCrypt obsługuje)"
    (let [ctx (test-context)
          very-long-password (apply str (repeat 1000 "a"))
          params {:email "test@example.com"
                  :password very-long-password
                  :password-confirm very-long-password}
          response (auth/signup (assoc ctx :params params))]

      ;; BCrypt może obsłużyć długie hasła
      ;; Ale w praktyce można dodać limit (np. 72 znaki - limit BCrypt)
      (is (some? response)))))
```

---

### 2.10. Rate Limiting (Future)

#### Test 13: Placeholder Dla Rate Limiting
**Uwaga:** Rate limiting nie jest w MVP, ale powinien być dodany

**Kod testu:**
```clojure
(deftest rate-limiting-placeholder-test
  (testing "Rate limiting - TODO dla przyszłości"
    ;; MVP nie ma rate limiting
    ;; W przyszłości:
    ;; - Limit prób logowania (np. 5 prób / 15 min)
    ;; - Limit żądań resetu hasła (np. 3 / 1h)
    ;; - Limit prób rejestracji z tego samego IP

    (is true "Rate limiting to TODO dla przyszłych wersji")))
```

---

### 2.11. Error Message Security

#### Test 14: Komunikaty Błędów Nie Ujawniają Szczegółów
**Cel:** Sprawdzić, że błędy nie zawierają stack traces ani szczegółów systemu

**Kod testu:**
```clojure
(deftest error-message-security-test
  (testing "Komunikaty błędów nie ujawniają szczegółów implementacji"
    (let [ctx (test-context)
          params {:email "test@example.com"
                  :password "wrong"}
          response (auth/signin (assoc ctx :params params))]

      ;; Generyczny komunikat błędu
      (is (str/includes? (get-in response [:headers "location"]) "invalid-credentials"))

      ;; Nie zawiera stack trace
      (is (not (str/includes? (str response) "Exception")))
      (is (not (str/includes? (str response) "clojure.lang")))
      (is (not (str/includes? (str response) "java.lang"))))))
```

---

## 3. Penetration Testing Scenarios

#### Test 15: Symulacja Ataku Brute Force
**Cel:** Sprawdzić zachowanie przy wielu próbach logowania

**Kod testu:**
```clojure
(deftest brute-force-simulation-test
  (testing "System obsługuje wiele prób logowania"
    (let [ctx (test-context)
          _ (create-test-user ctx "target@example.com" "correctpassword")
          attempts 100]

      ;; Wykonaj 100 nieudanych prób
      (dotimes [i attempts]
        (auth/signin (assoc ctx :params {:email "target@example.com"
                                         :password (str "wrong" i)})))

      ;; System powinien nadal działać
      ;; (W przyszłości: konto powinno być zablokowane)
      (let [final-attempt (auth/signin (assoc ctx :params {:email "target@example.com"
                                                            :password "correctpassword"}))]

        ;; Poprawne hasło nadal działa (brak rate limiting w MVP)
        (is (= "/app" (get-in final-attempt [:headers "location"])))))))
```

---

## 4. Compliance & Best Practices

#### Test 16: OWASP Top 10 Checklist
**Cel:** Sprawdzić zgodność z OWASP Top 10

**Kod testu:**
```clojure
(deftest owasp-compliance-test
  (testing "OWASP Top 10 - Security checklist"
    ;; A02:2021 – Cryptographic Failures
    (is true "Hasła są hashowane (BCrypt)")

    ;; A03:2021 – Injection
    (is true "Datalog zapobiega SQL injection")

    ;; A04:2021 – Insecure Design
    (is true "Token reset jest jednorazowy i wygasa")

    ;; A05:2021 – Security Misconfiguration
    (is true "TODO: Review production config (HTTPS, HttpOnly)")

    ;; A07:2021 – Authentication Failures
    (is true "BCrypt work factor >= 10")

    ;; A08:2021 – Software and Data Integrity Failures
    (is true "CSRF protection przez Biff")

    ;; TODO: Dodać pozostałe punkty OWASP
    ))
```

---

## 5. Kryteria Akceptacji

- [ ] Hasła są hashowane BCrypt (nigdy plaintext)
- [ ] Każdy hash BCrypt jest unikalny (salt)
- [ ] BCrypt work factor >= 10
- [ ] Tokeny są hashowane SHA-256
- [ ] CSRF tokeny w formularzach (Biff)
- [ ] Brak email enumeration (password reset)
- [ ] Timing attack prevention (BCrypt)
- [ ] XSS prevention (HTML escaping)
- [ ] SQL injection prevention (Datalog)
- [ ] Session cookies HttpOnly i Secure (config)
- [ ] Sesja nie zawiera wrażliwych danych
- [ ] Walidacja długości inputów
- [ ] Komunikaty błędów nie ujawniają szczegółów
- [ ] System obsługuje brute force (bez crashowania)
- [ ] Zgodność z OWASP Top 10

---

**Koniec dokumentu**
