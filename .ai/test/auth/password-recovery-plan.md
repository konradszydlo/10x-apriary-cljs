# Plan Testów: Odzyskiwanie Hasła (Password Recovery Flow)

**Moduły:**
- `src/com/apriary/auth.clj` - funkcje `send-password-reset`, `reset-password`
- `src/com/apriary/email.clj` - funkcja `send-password-reset-email`

**Strony UI:** `src/com/apriary/pages/home.clj`
- `forgot-password-page`
- `password-reset-sent-page`
- `reset-password-page`
- `password-reset-success-page`

**Data:** 2025-12-01
**Wersja:** 1.0

---

## 1. Cel Testowania

Weryfikacja kompletnego przepływu odzyskiwania hasła, w tym:
- Żądania resetu hasła
- Generowania i walidacji tokenów
- Wysyłania emaili (MVP: logowanie do konsoli)
- Resetowania hasła z użyciem tokenu
- Bezpieczeństwa tokenów (wygasanie, jednorazowe użycie)

---

## 2. Zakres Testów

### 2.1. Faza 1: Żądanie Resetu Hasła (Forgot Password)

#### Test 1: Poprawne Żądanie Resetu Hasła
**Handler:** `send-password-reset`
**Endpoint:** `POST /auth/send-password-reset`

**Przygotowanie:**
Utwórz użytkownika z emailem `user@example.com`

**Dane wejściowe:**
```clojure
{:email "user@example.com"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/password-reset-sent`
3. Token został utworzony w bazie danych
4. Token jest zhashowany (SHA-256)
5. Token ma datę wygaśnięcia (now + 1 godzina)
6. Email został wydrukowany do konsoli (MVP)
7. Zwrócony sukces NIEZALEŻNIE od istnienia emaila (bezpieczeństwo)

**Kod testu:**
```clojure
(deftest send-password-reset-success-test
  (testing "Pomyślne wysłanie żądania resetu hasła"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "oldpassword")
          params {:email "user@example.com"}

          ;; Przechwytujemy output konsoli
          console-output (with-out-str
                          (let [response (auth/send-password-reset (assoc ctx :params params))]

                            ;; Weryfikacja odpowiedzi
                            (is (= 303 (:status response)))
                            (is (= "/password-reset-sent" (get-in response [:headers "location"])))

                            ;; Weryfikacja tokenu w bazie
                            (let [tokens (find-reset-tokens-for-user ctx user-id)]
                              (is (= 1 (count tokens)))
                              (let [token (first tokens)]
                                (is (some? (:password-reset-token/token token)))
                                (is (some? (:password-reset-token/expires-at token)))
                                (is (nil? (:password-reset-token/used-at token)))

                                ;; Sprawdź datę wygaśnięcia (powinno być +1h)
                                (let [now (java.util.Date.)
                                      expires (:password-reset-token/expires-at token)
                                      diff-ms (- (.getTime expires) (.getTime now))]
                                  (is (< 3500000 diff-ms 3700000) "Wygaśnięcie ~1h"))))))

      ;; Weryfikacja emaila w konsoli
      (is (str/includes? console-output "PASSWORD RESET EMAIL"))
      (is (str/includes? console-output "user@example.com"))
      (is (str/includes? console-output "/reset-password?token=")))))
```

---

#### Test 2: Żądanie Resetu dla Nieistniejącego Email (Security)
**Cel:** Sprawdzić, że system nie ujawnia, czy email istnieje

**Dane wejściowe:**
```clojure
{:email "nonexistent@example.com"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/password-reset-sent` (ten sam co dla istniejącego)
3. Token NIE został utworzony (email nie istnieje)
4. Email NIE został wysłany
5. **Czas odpowiedzi podobny** do przypadku z istniejącym emailem

**Kod testu:**
```clojure
(deftest send-password-reset-email-enumeration-prevention-test
  (testing "Brak ujawnienia informacji o istnieniu emaila"
    (let [ctx (test-context)
          _ (create-test-user ctx "exists@example.com" "password")]

      ;; Test dla nieistniejącego emaila
      (let [response-nonexistent (auth/send-password-reset
                                  (assoc ctx :params {:email "nonexistent@example.com"}))]

        ;; Test dla istniejącego emaila
        (let [response-existent (auth/send-password-reset
                                (assoc ctx :params {:email "exists@example.com"}))]

          ;; Oba powinny mieć identyczną odpowiedź
          (is (= (:status response-nonexistent) (:status response-existent)))
          (is (= (get-in response-nonexistent [:headers "location"])
                 (get-in response-existent [:headers "location"])))
          (is (= "/password-reset-sent" (get-in response-nonexistent [:headers "location"])))))

      ;; Weryfikacja, że token NIE został utworzony dla nieistniejącego emaila
      (let [all-tokens (find-all-reset-tokens ctx)]
        (is (= 1 (count all-tokens)) "Tylko jeden token (dla istniejącego emaila)")))))
```

---

#### Test 3: Żądanie Resetu z Niepoprawnym Emailem
**Dane wejściowe:**
```clojure
{:email "not-an-email"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/forgot-password?error=invalid-email`
3. Token NIE został utworzony

**Kod testu:**
```clojure
(deftest send-password-reset-invalid-email-test
  (testing "Żądanie resetu z niepoprawnym formatem emaila"
    (let [ctx (test-context)
          params {:email "not-an-email"}
          response (auth/send-password-reset (assoc ctx :params params))]

      (is (= 303 (:status response)))
      (is (= "/forgot-password?error=invalid-email" (get-in response [:headers "location"])))

      ;; Brak tokenów w bazie
      (is (empty? (find-all-reset-tokens ctx))))))
```

---

### 2.2. Faza 2: Resetowanie Hasła (Reset Password)

#### Test 4: Pomyślny Reset Hasła z Prawidłowym Tokenem
**Handler:** `reset-password`
**Endpoint:** `POST /auth/reset-password`

**Przygotowanie:**
1. Utwórz użytkownika
2. Wygeneruj token resetu

**Dane wejściowe:**
```clojure
{:token "raw-token-value"
 :password "newpassword123"
 :password-confirm "newpassword123"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/password-reset-success`
3. Hasło użytkownika zostało zaktualizowane
4. Token został oznaczony jako użyty (`:used-at` != nil)
5. Logowanie ze starym hasłem NIE działa
6. Logowanie z nowym hasłem działa

**Kod testu:**
```clojure
(deftest reset-password-success-test
  (testing "Pomyślny reset hasła z prawidłowym tokenem"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "oldpassword")
          raw-token "test-token-raw-value"
          hashed-token (auth/hash-token raw-token)
          token-id (create-reset-token ctx user-id hashed-token)]

      ;; Reset hasła
      (let [params {:token raw-token
                    :password "newpassword123"
                    :password-confirm "newpassword123"}
            response (auth/reset-password (assoc ctx :params params))]

        ;; Weryfikacja odpowiedzi
        (is (= 303 (:status response)))
        (is (= "/password-reset-success" (get-in response [:headers "location"])))

        ;; Weryfikacja, że token został oznaczony jako użyty
        (let [token (biff/lookup (:biff/db ctx) :xt/id token-id)]
          (is (some? (:password-reset-token/used-at token))))

        ;; Weryfikacja, że hasło zostało zmienione
        (let [user (biff/lookup (:biff/db ctx) :xt/id user-id)]
          ;; Stare hasło nie działa
          (is (false? (auth/verify-password "oldpassword" (:user/password-hash user))))
          ;; Nowe hasło działa
          (is (true? (auth/verify-password "newpassword123" (:user/password-hash user)))))

        ;; Test logowania z nowym hasłem
        (let [signin-response (auth/signin (assoc ctx :params {:email "user@example.com"
                                                               :password "newpassword123"}))]
          (is (= "/app" (get-in signin-response [:headers "location"]))))))))
```

---

#### Test 5: Reset z Nieprawidłowym Tokenem
**Dane wejściowe:**
```clojure
{:token "invalid-token-xyz"
 :password "newpassword123"
 :password-confirm "newpassword123"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/reset-password?token=invalid-token-xyz&error=invalid-token`
3. Hasło NIE zostało zmienione

**Kod testu:**
```clojure
(deftest reset-password-invalid-token-test
  (testing "Reset hasła z nieprawidłowym tokenem"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "oldpassword")
          params {:token "invalid-token-xyz"
                  :password "newpassword123"
                  :password-confirm "newpassword123"}
          response (auth/reset-password (assoc ctx :params params))]

      (is (= 303 (:status response)))
      (is (str/includes? (get-in response [:headers "location"]) "error=invalid-token"))

      ;; Hasło nie powinno się zmienić
      (let [user (biff/lookup (:biff/db ctx) :xt/id user-id)]
        (is (true? (auth/verify-password "oldpassword" (:user/password-hash user))))))))
```

---

#### Test 6: Reset z Wygasłym Tokenem
**Przygotowanie:**
Utwórz token z datą wygaśnięcia w przeszłości

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/reset-password?token={token}&error=token-expired`
3. Hasło NIE zostało zmienione

**Kod testu:**
```clojure
(deftest reset-password-expired-token-test
  (testing "Reset hasła z wygasłym tokenem"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "oldpassword")
          raw-token "expired-token"
          hashed-token (auth/hash-token raw-token)

          ;; Utwórz token z wygaśnięciem w przeszłości
          token-id (create-reset-token ctx user-id hashed-token
                                       :expires-at (java.util.Date. (- (System/currentTimeMillis) 3600000)))]

      (let [params {:token raw-token
                    :password "newpassword123"
                    :password-confirm "newpassword123"}
            response (auth/reset-password (assoc ctx :params params))]

        (is (= 303 (:status response)))
        (is (str/includes? (get-in response [:headers "location"]) "error=token-expired"))

        ;; Hasło nie powinno się zmienić
        (let [user (biff/lookup (:biff/db ctx) :xt/id user-id)]
          (is (true? (auth/verify-password "oldpassword" (:user/password-hash user)))))))))
```

---

#### Test 7: Reset z Użytym Tokenem (Jednorazowość)
**Przygotowanie:**
1. Utwórz token
2. Użyj tokenu raz (pomyślny reset)
3. Spróbuj użyć tego samego tokenu ponownie

**Oczekiwany wynik:**
1. Pierwszy reset: sukces
2. Drugi reset: błąd `token-used`
3. Hasło zmienione tylko raz (podczas pierwszego resetu)

**Kod testu:**
```clojure
(deftest reset-password-token-single-use-test
  (testing "Token może być użyty tylko raz"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "oldpassword")
          raw-token "single-use-token"
          hashed-token (auth/hash-token raw-token)
          _ (create-reset-token ctx user-id hashed-token)]

      ;; Pierwszy reset - powinien się udać
      (let [params1 {:token raw-token
                     :password "firstnewpassword"
                     :password-confirm "firstnewpassword"}
            response1 (auth/reset-password (assoc ctx :params params1))]
        (is (= "/password-reset-success" (get-in response1 [:headers "location"]))))

      ;; Drugi reset z tym samym tokenem - powinien być odrzucony
      (let [params2 {:token raw-token
                     :password "secondnewpassword"
                     :password-confirm "secondnewpassword"}
            response2 (auth/reset-password (assoc ctx :params params2))]
        (is (str/includes? (get-in response2 [:headers "location"]) "error=token-used")))

      ;; Hasło powinno być zmienione na pierwsze nowe hasło
      (let [user (biff/lookup (:biff/db ctx) :xt/id user-id)]
        (is (true? (auth/verify-password "firstnewpassword" (:user/password-hash user))))
        (is (false? (auth/verify-password "secondnewpassword" (:user/password-hash user))))))))
```

---

#### Test 8: Reset z Niezgodnymi Hasłami
**Dane wejściowe:**
```clojure
{:token "valid-token"
 :password "password123"
 :password-confirm "different456"}
```

**Oczekiwany wynik:**
1. Status HTTP: `303`
2. Lokalizacja: `/reset-password?token={token}&error=password-mismatch`
3. Hasło NIE zostało zmienione
4. Token NIE został oznaczony jako użyty

**Kod testu:**
```clojure
(deftest reset-password-mismatch-test
  (testing "Reset hasła z niezgodnymi hasłami"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "oldpassword")
          raw-token "valid-token"
          hashed-token (auth/hash-token raw-token)
          token-id (create-reset-token ctx user-id hashed-token)]

      (let [params {:token raw-token
                    :password "password123"
                    :password-confirm "different456"}
            response (auth/reset-password (assoc ctx :params params))]

        (is (= 303 (:status response)))
        (is (str/includes? (get-in response [:headers "location"]) "error=password-mismatch"))

        ;; Hasło nie powinno się zmienić
        (let [user (biff/lookup (:biff/db ctx) :xt/id user-id)]
          (is (true? (auth/verify-password "oldpassword" (:user/password-hash user)))))

        ;; Token nie powinien być oznaczony jako użyty
        (let [token (biff/lookup (:biff/db ctx) :xt/id token-id)]
          (is (nil? (:password-reset-token/used-at token))))))))
```

---

#### Test 9: Reset z Zbyt Krótkim Hasłem
**Dane wejściowe:**
```clojure
{:token "valid-token"
 :password "short"
 :password-confirm "short"}
```

**Oczekiwany wynik:**
1. Błąd: `error=invalid-password`
2. Hasło NIE zostało zmienione
3. Token NIE został oznaczony jako użyty

**Kod testu:**
```clojure
(deftest reset-password-invalid-password-test
  (testing "Reset hasła z hasłem krótszym niż 8 znaków"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "oldpassword")
          raw-token "valid-token"
          hashed-token (auth/hash-token raw-token)
          token-id (create-reset-token ctx user-id hashed-token)]

      (let [params {:token raw-token
                    :password "short"
                    :password-confirm "short"}
            response (auth/reset-password (assoc ctx :params params))]

        (is (str/includes? (get-in response [:headers "location"]) "error=invalid-password"))

        ;; Hasło i token bez zmian
        (let [user (biff/lookup (:biff/db ctx) :xt/id user-id)
              token (biff/lookup (:biff/db ctx) :xt/id token-id)]
          (is (true? (auth/verify-password "oldpassword" (:user/password-hash user))))
          (is (nil? (:password-reset-token/used-at token))))))))
```

---

### 2.3. Testy Email (MVP: Console Output)

#### Test 10: Format Emaila Resetującego
**Cel:** Sprawdzić, że email zawiera wymagane informacje

**Scenariusz:**
Przechwytujemy output konsoli i weryfikujemy zawartość

**Oczekiwana zawartość:**
1. Adres email odbiorcy
2. Temat: "Reset your Apriary Summary password"
3. Link do resetu: `/reset-password?token={token}`
4. Informacja o wygaśnięciu (1 godzina)
5. Informacja o zignorowaniu, jeśli nie żądano

**Kod testu:**
```clojure
(deftest password-reset-email-format-test
  (testing "Format emaila resetującego hasło"
    (let [ctx (test-context)
          _ (create-test-user ctx "user@example.com" "password")

          console-output (with-out-str
                          (auth/send-password-reset
                           (assoc ctx :params {:email "user@example.com"})))]

      ;; Weryfikacja zawartości emaila
      (is (str/includes? console-output "PASSWORD RESET EMAIL"))
      (is (str/includes? console-output "To: user@example.com"))
      (is (str/includes? console-output "Reset your Apriary Summary password"))
      (is (str/includes? console-output "/reset-password?token="))
      (is (str/includes? console-output "expire in 1 hour"))
      (is (str/includes? console-output "didn't request")))))
```

---

### 2.4. Testy UI

#### Test 11: Renderowanie Strony Forgot Password
**Scenariusze:**
1. Formularz ma action `/auth/send-password-reset`
2. Pole email jest wymagane
3. Link "Back to sign in" jest widoczny

**Kod testu:**
```clojure
(deftest forgot-password-page-rendering-test
  (testing "Strona forgot password renderuje poprawnie"
    (let [ctx (test-context)
          page-html (str (home/forgot-password-page ctx))]

      (is (str/includes? page-html "action=\"/auth/send-password-reset\""))
      (is (str/includes? page-html "name=\"email\""))
      (is (str/includes? page-html "type=\"email\""))
      (is (str/includes? page-html "Send reset link"))
      (is (str/includes? page-html "Back to sign in"))
      (is (str/includes? page-html "/signin")))))
```

---

#### Test 12: Renderowanie Strony Reset Password
**Scenariusze:**
1. Jeśli brak tokenu → przekierowanie do `/forgot-password`
2. Z tokenem: formularz z polami password i password-confirm
3. Token przekazany jako hidden input

**Kod testu:**
```clojure
(deftest reset-password-page-rendering-test
  (testing "Strona reset password bez tokenu przekierowuje"
    (let [ctx (test-context)
          response (home/reset-password-page (assoc ctx :params {}))]
      (is (= 303 (:status response)))
      (is (= "/forgot-password" (get-in response [:headers "location"])))))

  (testing "Strona reset password z tokenem renderuje formularz"
    (let [ctx (test-context)
          page-html (str (home/reset-password-page
                         (assoc ctx :params {:token "test-token"})))]

      (is (str/includes? page-html "action=\"/auth/reset-password\""))
      (is (str/includes? page-html "name=\"password\""))
      (is (str/includes? page-html "name=\"password-confirm\""))
      (is (str/includes? page-html "name=\"token\""))
      (is (str/includes? page-html "value=\"test-token\""))
      (is (str/includes? page-html "Reset password")))))
```

---

## 3. Testy End-to-End

#### Test 13: Pełny Przepływ Odzyskiwania Hasła
**Scenariusz:**
1. Użytkownik rejestruje się
2. Użytkownik wysyła żądanie resetu hasła
3. Użytkownik kopiuje link z konsoli
4. Użytkownik resetuje hasło
5. Użytkownik loguje się z nowym hasłem

**Kod testu:**
```clojure
(deftest full-password-recovery-flow-test
  (testing "Pełny przepływ odzyskiwania hasła"
    (let [ctx (test-context)
          email "flowtest@example.com"
          old-password "oldpassword123"
          new-password "newpassword456"]

      ;; 1. Rejestracja
      (let [_ (create-test-user ctx email old-password)]

        ;; 2. Żądanie resetu
        (let [console-output (with-out-str
                              (auth/send-password-reset
                               (assoc ctx :params {:email email})))

              ;; Wyciągnij token z outputu konsoli
              token-match (re-find #"/reset-password\?token=([A-Za-z0-9_-]+)" console-output)
              raw-token (second token-match)]

          (is (some? raw-token) "Token powinien być w emailu")

          ;; 3. Reset hasła
          (let [reset-response (auth/reset-password
                               (assoc ctx :params {:token raw-token
                                                   :password new-password
                                                   :password-confirm new-password}))]
            (is (= "/password-reset-success" (get-in reset-response [:headers "location"]))))

          ;; 4. Logowanie z nowym hasłem
          (let [signin-response (auth/signin
                                (assoc ctx :params {:email email
                                                    :password new-password}))]
            (is (= "/app" (get-in signin-response [:headers "location"])))
            (is (some? (get-in signin-response [:session :uid]))))

          ;; 5. Logowanie ze starym hasłem nie działa
          (let [signin-old (auth/signin
                           (assoc ctx :params {:email email
                                               :password old-password}))]
            (is (str/includes? (get-in signin-old [:headers "location"]) "error=invalid-credentials"))))))))
```

---

## 4. Kryteria Akceptacji

### Warunki Zaliczenia Testów

- [ ] Żądanie resetu tworzy token w bazie
- [ ] Token jest zhashowany (SHA-256)
- [ ] Token ma datę wygaśnięcia (+1h)
- [ ] Email wyświetlany w konsoli (MVP)
- [ ] Brak ujawnienia istnienia emaila (email enumeration prevention)
- [ ] Reset z prawidłowym tokenem zmienia hasło
- [ ] Reset z nieprawidłowym tokenem zwraca błąd
- [ ] Wygasłe tokeny są odrzucane
- [ ] Tokeny są jednorazowe (użyty token nie działa ponownie)
- [ ] Niezgodne hasła są odrzucane
- [ ] Zbyt krótkie hasła są odrzucane
- [ ] Wszystkie strony UI renderują poprawnie
- [ ] Pełny przepływ end-to-end działa
- [ ] Logowanie z nowym hasłem działa, ze starym nie

---

**Koniec dokumentu**
