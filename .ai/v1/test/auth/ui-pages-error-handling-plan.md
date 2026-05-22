# Plan Testów: Strony UI i Obsługa Błędów (UI Pages & Error Handling)

**Moduł:** `src/com/apriary/pages/home.clj`
**Strony:** signup, signin, forgot-password, reset-password, password-reset-sent, password-reset-success
**Data:** 2025-12-01
**Wersja:** 1.0

---

## 1. Cel Testowania

Weryfikacja poprawności renderowania stron UI i wyświetlania komunikatów błędów w systemie autentykacji.

---

## 2. Zakres Testów

### 2.1. Strona Rejestracji (Sign Up Page)

#### Test 1: Renderowanie Podstawowych Elementów
**Kod testu:**
```clojure
(deftest signup-page-basic-rendering-test
  (testing "Strona rejestracji zawiera wszystkie wymagane elementy"
    (let [ctx (test-context)
          page-html (str (home/home-page ctx))]

      ;; Nagłówek
      (is (str/includes? page-html "Sign up"))

      ;; Pola formularza
      (is (str/includes? page-html "name=\"email\""))
      (is (str/includes? page-html "type=\"email\""))
      (is (str/includes? page-html "name=\"password\""))
      (is (str/includes? page-html "type=\"password\""))
      (is (str/includes? page-html "name=\"password-confirm\""))

      ;; Action formularza
      (is (str/includes? page-html "action=\"/auth/signup\""))

      ;; Przycisk submit
      (is (str/includes? page-html "Sign up"))

      ;; Link do sign in
      (is (str/includes? page-html "Already have an account"))
      (is (str/includes? page-html "/signin")))))
```

---

#### Test 2: Komunikaty Błędów Signup
**Kod testu:**
```clojure
(deftest signup-error-messages-test
  (testing "Błąd niepoprawnego emaila"
    (let [ctx (test-context)
          page-html (str (home/home-page (assoc ctx :params {:error "invalid-email"})))]
      (is (str/includes? page-html "Invalid email address"))))

  (testing "Błąd niepoprawnego hasła"
    (let [ctx (test-context)
          page-html (str (home/home-page (assoc ctx :params {:error "invalid-password"})))]
      (is (str/includes? page-html "Password must be at least 8 characters"))))

  (testing "Błąd niezgodnych haseł"
    (let [ctx (test-context)
          page-html (str (home/home-page (assoc ctx :params {:error "password-mismatch"})))]
      (is (str/includes? page-html "Passwords do not match"))))

  (testing "Błąd istniejącego emaila"
    (let [ctx (test-context)
          page-html (str (home/home-page (assoc ctx :params {:error "email-exists"})))]
      (is (str/includes? page-html "account with this email already exists")))))
```

---

### 2.2. Strona Logowania (Sign In Page)

#### Test 3: Renderowanie Podstawowych Elementów
**Kod testu:**
```clojure
(deftest signin-page-basic-rendering-test
  (testing "Strona logowania zawiera wszystkie wymagane elementy"
    (let [ctx (test-context)
          page-html (str (home/signin-page ctx))]

      ;; Nagłówek
      (is (str/includes? page-html "Sign in"))

      ;; Pola formularza
      (is (str/includes? page-html "name=\"email\""))
      (is (str/includes? page-html "name=\"password\""))

      ;; Action formularza
      (is (str/includes? page-html "action=\"/auth/signin\""))

      ;; Przycisk submit
      (is (str/includes? page-html "Sign in"))

      ;; Link do forgot password
      (is (str/includes? page-html "Forgot password"))
      (is (str/includes? page-html "/forgot-password"))

      ;; Link do sign up
      (is (str/includes? page-html "Don't have an account"))
      (is (str/includes? page-html "Sign up")))))
```

---

#### Test 4: Komunikaty Błędów Signin
**Kod testu:**
```clojure
(deftest signin-error-messages-test
  (testing "Błąd niepoprawnego emaila"
    (let [ctx (test-context)
          page-html (str (home/signin-page (assoc ctx :params {:error "invalid-email"})))]
      (is (str/includes? page-html "Invalid email address"))))

  (testing "Błąd niepoprawnych poświadczeń"
    (let [ctx (test-context)
          page-html (str (home/signin-page (assoc ctx :params {:error "invalid-credentials"})))]
      (is (str/includes? page-html "Invalid email or password"))))

  (testing "Komunikat wymogówlogowania"
    (let [ctx (test-context)
          page-html (str (home/signin-page (assoc ctx :params {:error "not-signed-in"})))]
      (is (str/includes? page-html "You must be signed in")))))
```

---

### 2.3. Strona Forgot Password

#### Test 5: Renderowanie Podstawowych Elementów
**Kod testu:**
```clojure
(deftest forgot-password-page-rendering-test
  (testing "Strona forgot password zawiera wszystkie wymagane elementy"
    (let [ctx (test-context)
          page-html (str (home/forgot-password-page ctx))]

      ;; Nagłówek
      (is (str/includes? page-html "Reset your password"))

      ;; Instrukcja
      (is (str/includes? page-html "Enter your email address"))

      ;; Pole email
      (is (str/includes? page-html "name=\"email\""))
      (is (str/includes? page-html "type=\"email\""))

      ;; Action formularza
      (is (str/includes? page-html "action=\"/auth/send-password-reset\""))

      ;; Przycisk submit
      (is (str/includes? page-html "Send reset link"))

      ;; Link powrotny
      (is (str/includes? page-html "Back to sign in"))
      (is (str/includes? page-html "/signin")))))
```

---

#### Test 6: Komunikaty Błędów Forgot Password
**Kod testu:**
```clojure
(deftest forgot-password-error-messages-test
  (testing "Błąd niepoprawnego emaila"
    (let [ctx (test-context)
          page-html (str (home/forgot-password-page (assoc ctx :params {:error "invalid-email"})))]
      (is (str/includes? page-html "Invalid email address")))))
```

---

### 2.4. Strona Password Reset Sent

#### Test 7: Renderowanie Komunikatu Potwierdzenia
**Kod testu:**
```clojure
(deftest password-reset-sent-page-rendering-test
  (testing "Strona potwierdzenia wysłania emaila"
    (let [ctx (test-context)
          page-html (str (home/password-reset-sent-page ctx))]

      ;; Nagłówek
      (is (str/includes? page-html "Check your email"))

      ;; Komunikat
      (is (str/includes? page-html "If an account exists"))
      (is (str/includes? page-html "password reset link"))

      ;; Informacja o wygaśnięciu
      (is (str/includes? page-html "expire in 1 hour"))

      ;; Link powrotny
      (is (str/includes? page-html "Return to sign in"))
      (is (str/includes? page-html "/signin")))))
```

---

### 2.5. Strona Reset Password

#### Test 8: Renderowanie Z Tokenem
**Kod testu:**
```clojure
(deftest reset-password-page-with-token-test
  (testing "Strona reset password z tokenem"
    (let [ctx (test-context)
          page-html (str (home/reset-password-page (assoc ctx :params {:token "test-token-123"})))]

      ;; Nagłówek
      (is (str/includes? page-html "Set new password"))

      ;; Pola hasła
      (is (str/includes? page-html "name=\"password\""))
      (is (str/includes? page-html "name=\"password-confirm\""))

      ;; Token jako hidden input
      (is (str/includes? page-html "name=\"token\""))
      (is (str/includes? page-html "value=\"test-token-123\""))

      ;; Action formularza
      (is (str/includes? page-html "action=\"/auth/reset-password\""))

      ;; Przycisk submit
      (is (str/includes? page-html "Reset password")))))
```

---

#### Test 9: Przekierowanie Bez Tokenu
**Kod testu:**
```clojure
(deftest reset-password-page-no-token-test
  (testing "Strona reset password bez tokenu przekierowuje"
    (let [ctx (test-context)
          response (home/reset-password-page (assoc ctx :params {}))]

      ;; Powinno być przekierowanie
      (is (= 303 (:status response)))
      (is (= "/forgot-password" (get-in response [:headers "location"]))))))
```

---

#### Test 10: Komunikaty Błędów Reset Password
**Kod testu:**
```clojure
(deftest reset-password-error-messages-test
  (testing "Błąd niepoprawnego tokenu"
    (let [ctx (test-context)
          page-html (str (home/reset-password-page
                         (assoc ctx :params {:token "token" :error "invalid-token"})))]
      (is (str/includes? page-html "password reset link is invalid or has expired"))
      (is (str/includes? page-html "Request a new reset link"))))

  (testing "Błąd wygasłego tokenu"
    (let [ctx (test-context)
          page-html (str (home/reset-password-page
                         (assoc ctx :params {:token "token" :error "token-expired"})))]
      (is (str/includes? page-html "password reset link has expired"))))

  (testing "Błąd użytego tokenu"
    (let [ctx (test-context)
          page-html (str (home/reset-password-page
                         (assoc ctx :params {:token "token" :error "token-used"})))]
      (is (str/includes? page-html "already been used"))))

  (testing "Błąd niezgodnych haseł"
    (let [ctx (test-context)
          page-html (str (home/reset-password-page
                         (assoc ctx :params {:token "token" :error "password-mismatch"})))]
      (is (str/includes? page-html "Passwords do not match")))))
```

---

### 2.6. Strona Password Reset Success

#### Test 11: Renderowanie Komunikatu Sukcesu
**Kod testu:**
```clojure
(deftest password-reset-success-page-rendering-test
  (testing "Strona sukcesu resetu hasła"
    (let [ctx (test-context)
          page-html (str (home/password-reset-success-page ctx))]

      ;; Nagłówek
      (is (str/includes? page-html "Password reset successful"))

      ;; Komunikat
      (is (str/includes? page-html "successfully reset"))
      (is (str/includes? page-html "sign in with your new password"))

      ;; Link do sign in
      (is (str/includes? page-html "Sign in"))
      (is (str/includes? page-html "/signin")))))
```

---

### 2.7. Dostępność (Accessibility)

#### Test 12: Pola Formularzy Mają Label
**Kod testu:**
```clojure
(deftest form-labels-accessibility-test
  (testing "Pola formularzy mają odpowiednie labele"
    (let [ctx (test-context)]

      ;; Signup page
      (let [signup-html (str (home/home-page ctx))]
        (is (str/includes? signup-html "for=\"email\""))
        (is (str/includes? signup-html "for=\"password\""))
        (is (str/includes? signup-html "for=\"password-confirm\"")))

      ;; Signin page
      (let [signin-html (str (home/signin-page ctx))]
        (is (str/includes? signin-html "for=\"email\""))
        (is (str/includes? signin-html "for=\"password\""))))))
```

---

#### Test 13: Required Attributes
**Kod testu:**
```clojure
(deftest required-attributes-test
  (testing "Pola wymagane mają atrybut required"
    (let [ctx (test-context)
          signup-html (str (home/home-page ctx))]

      ;; Wszystkie pola w signup powinny być required
      (is (str/includes? signup-html "required"))

      ;; Email input ma type email
      (is (re-find #"type=\"email\".*name=\"email\"|name=\"email\".*type=\"email\"" signup-html))

      ;; Password inputs mają type password
      (is (str/includes? signup-html "type=\"password\"")))))
```

---

### 2.8. Responsywność i Styling

#### Test 14: Tailwind Classes
**Kod testu:**
```clojure
(deftest tailwind-styling-test
  (testing "Strony używają klas Tailwind"
    (let [ctx (test-context)
          page-html (str (home/home-page ctx))]

      ;; Sprawdź obecność popularnych klas Tailwind
      (is (or (str/includes? page-html "text-")
              (str/includes? page-html "bg-")
              (str/includes? page-html "px-")
              (str/includes? page-html "py-")
              (str/includes? page-html "rounded")
              (str/includes? page-html "border"))))))
```

---

### 2.9. Nawigacja

#### Test 15: Poprawność Linków Nawigacyjnych
**Kod testu:**
```clojure
(deftest navigation-links-test
  (testing "Wszystkie linki nawigacyjne są poprawne"
    (let [ctx (test-context)]

      ;; Signup -> Signin
      (let [signup-html (str (home/home-page ctx))]
        (is (str/includes? signup-html "href=\"/signin\"")))

      ;; Signin -> Signup
      (let [signin-html (str (home/signin-page ctx))]
        (is (str/includes? signin-html "href=\"/\"")))  ;; / to signup page

      ;; Signin -> Forgot password
      (let [signin-html (str (home/signin-page ctx))]
        (is (str/includes? signin-html "href=\"/forgot-password\"")))

      ;; Forgot password -> Signin
      (let [forgot-html (str (home/forgot-password-page ctx))]
        (is (str/includes? forgot-html "href=\"/signin\""))))))
```

---

### 2.10. Przypadki Brzegowe

#### Test 16: Brak Parametru Error
**Kod testu:**
```clojure
(deftest no-error-parameter-test
  (testing "Strona bez parametru error renderuje się normalnie"
    (let [ctx (test-context)
          page-html (str (home/home-page ctx))]

      ;; Strona powinna się załadować
      (is (some? page-html))

      ;; Nie powinno być komunikatu błędu
      (is (not (str/includes? page-html "error"))))))
```

---

#### Test 17: Nieznany Kod Błędu
**Kod testu:**
```clojure
(deftest unknown-error-code-test
  (testing "Nieznany kod błędu pokazuje generyczny komunikat"
    (let [ctx (test-context)
          page-html (str (home/home-page (assoc ctx :params {:error "unknown-error-xyz"})))]

      ;; Powinien być generyczny komunikat
      (is (str/includes? page-html "There was an error")))))
```

---

## 3. Kryteria Akceptacji

- [ ] Signup page renderuje wszystkie pola i linki
- [ ] Signup page wyświetla komunikaty błędów
- [ ] Signin page renderuje wszystkie pola i linki
- [ ] Signin page wyświetla komunikaty błędów
- [ ] Forgot password page renderuje formularz
- [ ] Password reset sent page pokazuje potwierdzenie
- [ ] Reset password page renderuje z tokenem
- [ ] Reset password page przekierowuje bez tokenu
- [ ] Reset password page wyświetla komunikaty błędów
- [ ] Password reset success page pokazuje sukces
- [ ] Wszystkie pola mają labele (accessibility)
- [ ] Wymagane pola mają atrybut required
- [ ] Strony używają Tailwind classes
- [ ] Wszystkie linki nawigacyjne są poprawne
- [ ] Strony renderują się bez parametru error
- [ ] Nieznany kod błędu pokazuje generyczny komunikat

---

**Koniec dokumentu**
