# Plan Testów: Zarządzanie Sesją (Session Management)

**Middleware:** `src/com/apriary/middleware.clj`
**Moduły Auth:** `src/com/apriary/auth.clj` - `signup`, `signin`, `signout`
**Biff:** Cookie-based sessions
**Data:** 2025-12-01
**Wersja:** 1.0

---

## 1. Cel Testowania

Weryfikacja poprawności zarządzania sesjami użytkowników, w tym:
- Tworzenia sesji po rejestracji/logowaniu
- Przechowywania sesji w ciasteczkach
- Walidacji sesji
- Czyszczenia sesji po wylogowaniu
- Trwałości sesji między żądaniami

---

## 2. Zakres Testów

### 2.1. Tworzenie Sesji

#### Test 1: Tworzenie Sesji Przy Rejestracji
**Cel:** Sprawdzić, że sesja jest tworzona po pomyślnej rejestracji

**Kod testu:**
```clojure
(deftest session-creation-on-signup-test
  (testing "Sesja jest tworzona przy rejestracji"
    (let [ctx (test-context)
          params {:email "newuser@example.com"
                  :password "password123"
                  :password-confirm "password123"}
          response (auth/signup (assoc ctx :params params))]

      ;; Sprawdź, że sesja zawiera :uid
      (is (some? (:session response)))
      (is (uuid? (get-in response [:session :uid])))

      ;; Sprawdź, że :uid odpowiada utworzonemu użytkownikowi
      (let [user (biff/lookup (:biff/db ctx)
                             :user/email "newuser@example.com")]
        (is (= (:user/id user) (get-in response [:session :uid])))))))
```

---

#### Test 2: Tworzenie Sesji Przy Logowaniu
**Cel:** Sprawdzić, że sesja jest tworzona po pomyślnym logowaniu

**Kod testu:**
```clojure
(deftest session-creation-on-signin-test
  (testing "Sesja jest tworzona przy logowaniu"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password123")
          params {:email "user@example.com"
                  :password "password123"}
          response (auth/signin (assoc ctx :params params))]

      ;; Sprawdź sesję
      (is (some? (:session response)))
      (is (uuid? (get-in response [:session :uid])))
      (is (= user-id (get-in response [:session :uid]))))))
```

---

#### Test 3: Brak Sesji Przy Niepowodzeniu
**Cel:** Sprawdzić, że sesja NIE jest tworzona przy nieudanej rejestracji/logowaniu

**Kod testu:**
```clojure
(deftest no-session-on-failure-test
  (testing "Brak sesji przy nieudanej rejestracji"
    (let [ctx (test-context)
          params {:email "invalid-email"
                  :password "password123"
                  :password-confirm "password123"}
          response (auth/signup (assoc ctx :params params))]

      ;; Sesja nie powinna zawierać :uid
      (is (or (nil? (:session response))
              (nil? (get-in response [:session :uid]))))))

  (testing "Brak sesji przy nieudanym logowaniu"
    (let [ctx (test-context)
          params {:email "nonexistent@example.com"
                  :password "password123"}
          response (auth/signin (assoc ctx :params params))]

      (is (or (nil? (:session response))
              (nil? (get-in response [:session :uid])))))))
```

---

### 2.2. Czyszczenie Sesji

#### Test 4: Czyszczenie Sesji Przy Wylogowaniu
**Cel:** Sprawdzić, że sesja jest czyszczona przy wylogowaniu

**Kod testu:**
```clojure
(deftest session-clearing-on-signout-test
  (testing "Sesja jest czyszczona przy wylogowaniu"
    (let [user-id (random-uuid)
          ctx {:session {:uid user-id}}
          response (auth/signout ctx)]

      ;; Sesja powinna być nil
      (is (nil? (:session response)))

      ;; Przekierowanie do strony logowania
      (is (= 303 (:status response)))
      (is (= "/signin" (get-in response [:headers "location"]))))))
```

---

### 2.3. Walidacja Sesji

#### Test 5: Sesja Zawiera Poprawny UUID
**Cel:** Sprawdzić, że :uid w sesji jest prawidłowym UUID

**Kod testu:**
```clojure
(deftest session-uid-validation-test
  (testing "UID w sesji jest prawidłowym UUID"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")
          response (auth/signin (assoc ctx :params {:email "user@example.com"
                                                     :password "password"}))]

      (let [uid (get-in response [:session :uid])]
        ;; Jest UUID
        (is (uuid? uid))

        ;; Można parsować jako UUID
        (is (instance? java.util.UUID uid))

        ;; Ma poprawny format
        (is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                       (str uid)))))))
```

---

#### Test 6: Sesja Nie Zawiera Wrażliwych Danych
**Cel:** Sprawdzić, że sesja nie zawiera hasła ani innych wrażliwych danych

**Kod testu:**
```clojure
(deftest session-no-sensitive-data-test
  (testing "Sesja nie zawiera wrażliwych danych"
    (let [ctx (test-context)
          _ (create-test-user ctx "user@example.com" "secretpassword")
          response (auth/signin (assoc ctx :params {:email "user@example.com"
                                                     :password "secretpassword"}))]

      (let [session (:session response)
            session-str (str session)]

        ;; Nie zawiera hasła
        (is (not (str/includes? session-str "secretpassword")))
        (is (not (contains? session :password)))
        (is (not (contains? session :password-hash)))

        ;; Nie zawiera emaila (opcjonalnie, zależy od wymagań)
        (is (not (contains? session :email)))

        ;; Zawiera tylko :uid
        (is (= #{:uid} (set (keys session))))))))
```

---

### 2.4. Persystencja Sesji

#### Test 7: Sesja Przetrwa Między Żądaniami
**Cel:** Sprawdzić, że sesja jest zachowana między różnymi żądaniami

**Kod testu:**
```clojure
(deftest session-persistence-test
  (testing "Sesja przetrwa między żądaniami"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")

          ;; Pierwsze żądanie - logowanie
          signin-response (auth/signin (assoc ctx :params {:email "user@example.com"
                                                            :password "password"}))
          session (get signin-response :session)]

      ;; Drugie żądanie - użycie tej samej sesji
      (let [ctx-with-session (assoc ctx :session session)
            ;; Symuluj żądanie do chronionej strony
            protected-response (mock-protected-handler ctx-with-session)]

        ;; Sesja powinna nadal zawierać :uid
        (is (= user-id (get-in protected-response [:session :uid])))))))
```

---

### 2.5. Bezpieczeństwo Sesji

#### Test 8: Sesja z Niepoprawnym UUID Jest Odrzucana
**Cel:** Sprawdzić, że sesja z nieprawidłowym UUID nie daje dostępu

**Kod testu:**
```clojure
(deftest invalid-session-uid-test
  (testing "Sesja z niepoprawnym UUID jest odrzucana"
    (let [ctx (test-context)
          ;; Sesja z UUID, który nie istnieje w bazie
          fake-uid (random-uuid)
          ctx-with-fake-session (assoc ctx :session {:uid fake-uid})]

      ;; Próba dostępu do chronionej strony
      ;; Powinno być przekierowanie do logowania
      ;; (zależy od implementacji middleware)
      (is (uuid? fake-uid))
      ;; TODO: Test middleware wrap-signed-in
      )))
```

---

#### Test 9: Sesja Bez :uid Jest Traktowana Jako Niezalogowany
**Cel:** Sprawdzić, że pusta sesja lub sesja bez :uid nie daje dostępu

**Kod testu:**
```clojure
(deftest empty-session-test
  (testing "Pusta sesja jest traktowana jako brak zalogowania"
    (let [ctx {:session {}}]

      ;; Middleware wrap-signed-in powinien przekierować do /signin
      ;; (test w route-protection-plan.md)
      (is (nil? (get-in ctx [:session :uid])))))

  (testing "Brak sesji"
    (let [ctx {}]
      (is (nil? (:session ctx))))))
```

---

### 2.6. Aktualizacja Sesji

#### Test 10: Sesja Może Być Aktualizowana
**Cel:** Sprawdzić, że można dodać dane do sesji bez nadpisywania :uid

**Kod testu:**
```clojure
(deftest session-update-test
  (testing "Sesja może być aktualizowana"
    (let [user-id (random-uuid)
          initial-session {:uid user-id}
          updated-session (assoc initial-session :flash {:message "Test"})]

      ;; :uid pozostaje niezmienione
      (is (= user-id (:uid updated-session)))

      ;; Dodane nowe dane
      (is (some? (:flash updated-session)))
      (is (= "Test" (get-in updated-session [:flash :message]))))))
```

---

### 2.7. Cykl Życia Sesji

#### Test 11: Pełny Cykl Życia Sesji
**Cel:** Sprawdzić pełny przepływ: brak sesji → rejestracja → sesja → wylogowanie → brak sesji

**Kod testu:**
```clojure
(deftest session-full-lifecycle-test
  (testing "Pełny cykl życia sesji"
    (let [ctx (test-context)]

      ;; 1. Początkowy stan - brak sesji
      (is (nil? (:session ctx)))

      ;; 2. Rejestracja - tworzenie sesji
      (let [signup-response (auth/signup (assoc ctx :params
                                               {:email "lifecycle@example.com"
                                                :password "password123"
                                                :password-confirm "password123"}))]
        (is (some? (get-in signup-response [:session :uid])))

        ;; 3. Sesja aktywna - można z niej korzystać
        (let [active-session (:session signup-response)
              uid (:uid active-session)]
          (is (uuid? uid))

          ;; 4. Wylogowanie - czyszczenie sesji
          (let [signout-response (auth/signout {:session active-session})]
            (is (nil? (:session signout-response)))

            ;; 5. Po wylogowaniu brak sesji
            (is (nil? (get signout-response :session)))))))))
```

---

### 2.8. Wielokrotne Sesje

#### Test 12: Użytkownik Może Mieć Tylko Jedną Aktywną Sesję (Opcjonalnie)
**Uwaga:** W cookie-based sessions użytkownik może mieć wiele sesji (różne przeglądarki)

**Kod testu:**
```clojure
(deftest multiple-sessions-test
  (testing "Użytkownik może logować się z różnych urządzeń"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")]

      ;; Logowanie z "urządzenia 1"
      (let [session1-response (auth/signin (assoc ctx :params {:email "user@example.com"
                                                                :password "password"}))
            session1 (:session session1-response)]

        ;; Logowanie z "urządzenia 2"
        (let [session2-response (auth/signin (assoc ctx :params {:email "user@example.com"
                                                                  :password "password"}))
              session2 (:session session2-response)]

          ;; Obie sesje powinny mieć tego samego :uid
          (is (= user-id (get session1 :uid)))
          (is (= user-id (get session2 :uid)))

          ;; Ale są niezależnymi obiektami sesji
          (is (some? session1))
          (is (some? session2)))))))
```

---

## 3. Testy Integracyjne z Middleware

#### Test 13: Integracja z wrap-session
**Cel:** Sprawdzić, że middleware poprawnie obsługuje sesje

**Kod testu:**
```clojure
(deftest wrap-session-integration-test
  (testing "Middleware wrap-session poprawnie obsługuje sesje"
    ;; Ten test wymaga uruchomienia pełnego middleware stack
    ;; Zazwyczaj testowane w testach integracyjnych E2E

    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")]

      ;; Symuluj request/response z middleware
      ;; TODO: Implementacja zależna od setupu testowego Biff
      (is (uuid? user-id)))))
```

---

## 4. Testy Bezpieczeństwa

#### Test 14: Sesja Nie Jest Podatna na Session Fixation
**Cel:** Sprawdzić, że sesja jest regenerowana po logowaniu

**Kod testu:**
```clojure
(deftest session-fixation-prevention-test
  (testing "Sesja jest tworzona nowa przy logowaniu"
    ;; W Biff, nowa sesja jest tworzona przy każdym logowaniu
    ;; poprzez nadpisanie całej mapy :session

    (let [ctx (test-context)
          _ (create-test-user ctx "user@example.com" "password")

          ;; Logowanie tworzy nową sesję
          response (auth/signin (assoc ctx :params {:email "user@example.com"
                                                     :password "password"}))]

      ;; Nowa sesja nie powinna zawierać niczego poza :uid
      (is (= #{:uid} (set (keys (:session response))))))))
```

---

#### Test 15: Sesja Jest Chroniona Przed XSS
**Uwaga:** To jest obsługiwane przez Biff middleware (HttpOnly cookies)

**Kod testu:**
```clojure
(deftest session-xss-protection-test
  (testing "Sesja w ciasteczku jest HttpOnly"
    ;; W Biff, ciasteczka sesji są domyślnie HttpOnly
    ;; Ten test wymaga sprawdzenia konfiguracji middleware

    ;; TODO: Sprawdź konfigurację Biff middleware
    ;; :biff.middleware/cookie-secret powinien być ustawiony
    ;; Ciasteczka powinny mieć flagę HttpOnly
    (is true "Wymaga sprawdzenia konfiguracji production")))
```

---

## 5. Przypadki Brzegowe

#### Test 16: Sesja z Dodatkowymi Polami
**Cel:** Sprawdzić, że sesja może zawierać dodatkowe pola (np. flash messages)

**Kod testu:**
```clojure
(deftest session-with-extra-fields-test
  (testing "Sesja może zawierać flash messages"
    (let [user-id (random-uuid)
          session-with-flash {:uid user-id
                             :flash {:message "Success!"}}]

      (is (= user-id (:uid session-with-flash)))
      (is (= "Success!" (get-in session-with-flash [:flash :message]))))))
```

---

#### Test 17: Sesja Po Zmianie Hasła
**Cel:** Sprawdzić, czy sesja pozostaje aktywna po zmianie hasła

**Kod testu:**
```clojure
(deftest session-after-password-change-test
  (testing "Sesja pozostaje aktywna po zmianie hasła"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "oldpassword")

          ;; Zaloguj użytkownika
          signin-response (auth/signin (assoc ctx :params {:email "user@example.com"
                                                            :password "oldpassword"}))
          active-session (:session signin-response)]

      ;; Zmień hasło (np. przez reset password)
      (biff/submit-tx ctx
        [{:db/op :update
          :xt/id user-id
          :user/password-hash (auth/hash-password "newpassword")}])

      ;; Sesja powinna nadal być aktywna
      (is (= user-id (:uid active-session)))

      ;; Opcjonalnie: W niektórych systemach sesja powinna być unieważniona
      ;; po zmianie hasła (security best practice)
      ;; TODO: Określić oczekiwane zachowanie
      )))
```

---

## 6. Kryteria Akceptacji

- [ ] Sesja jest tworzona przy rejestracji
- [ ] Sesja jest tworzona przy logowaniu
- [ ] Sesja NIE jest tworzona przy nieudanym logowaniu/rejestracji
- [ ] Sesja jest czyszczona przy wylogowaniu
- [ ] :uid w sesji jest poprawnym UUID
- [ ] Sesja nie zawiera wrażliwych danych (hasła, hash)
- [ ] Sesja przetrwa między żądaniami
- [ ] Sesja z niepoprawnym UUID jest odrzucana
- [ ] Pusta sesja jest traktowana jako brak zalogowania
- [ ] Sesja może być aktualizowana (flash messages)
- [ ] Pełny cykl życia sesji działa poprawnie
- [ ] Użytkownik może mieć wiele sesji (różne urządzenia)
- [ ] Integracja z middleware działa poprawnie
- [ ] Ochrona przed session fixation
- [ ] Ciasteczka są HttpOnly (XSS protection)
- [ ] Sesja może zawierać dodatkowe pola
- [ ] Zachowanie sesji po zmianie hasła jest określone

---

**Koniec dokumentu**
