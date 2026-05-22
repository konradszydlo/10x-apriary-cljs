# Plan Testów: Ochrona Tras (Route Protection)

**Moduł:** `src/com/apriary/middleware.clj`
**Middleware:** `wrap-signed-in`, `wrap-redirect-signed-in`
**Data:** 2025-12-01
**Wersja:** 1.0

---

## 1. Cel Testowania

Weryfikacja poprawności działania middleware chroniącego trasy aplikacji:
- Middleware `wrap-signed-in` - wymaga zalogowania
- Middleware `wrap-redirect-signed-in` - przekierowuje zalogowanych użytkowników
- Przekierowania dla niezalogowanych użytkowników
- Dostęp do chronionych zasobów

---

## 2. Zakres Testów

### 2.1. Middleware `wrap-signed-in`

#### Test 1: Dostęp Zalogowanego Użytkownika do Chronionej Strony
**Cel:** Sprawdzić, że zalogowany użytkownik ma dostęp do chronionych tras

**Kod testu:**
```clojure
(deftest wrap-signed-in-authenticated-access-test
  (testing "Zalogowany użytkownik ma dostęp do chronionej strony"
    (let [user-id (random-uuid)
          ctx {:session {:uid user-id}}
          handler (fn [ctx] {:status 200 :body "Protected content"})
          wrapped-handler (mid/wrap-signed-in handler)
          response (wrapped-handler ctx)]

      ;; Użytkownik powinien uzyskać dostęp
      (is (= 200 (:status response)))
      (is (= "Protected content" (:body response))))))
```

---

#### Test 2: Brak Dostępu Niezalogowanego Użytkownika
**Cel:** Sprawdzić, że niezalogowany użytkownik jest przekierowywany do strony logowania

**Kod testu:**
```clojure
(deftest wrap-signed-in-unauthenticated-redirect-test
  (testing "Niezalogowany użytkownik jest przekierowywany do /signin"
    (let [ctx {:session {}}  ;; Brak :uid
          handler (fn [ctx] {:status 200 :body "Protected content"})
          wrapped-handler (mid/wrap-signed-in handler)
          response (wrapped-handler ctx)]

      ;; Przekierowanie do strony logowania
      (is (= 303 (:status response)))
      (is (= "/signin?error=not-signed-in" (get-in response [:headers "location"]))))))
```

---

#### Test 3: Przekierowanie Przy Braku Sesji
**Cel:** Sprawdzić zachowanie gdy sesja w ogóle nie istnieje

**Kod testu:**
```clojure
(deftest wrap-signed-in-no-session-test
  (testing "Brak sesji powoduje przekierowanie"
    (let [ctx {}  ;; Całkowity brak :session
          handler (fn [ctx] {:status 200 :body "Protected"})
          wrapped-handler (mid/wrap-signed-in handler)
          response (wrapped-handler ctx)]

      (is (= 303 (:status response)))
      (is (= "/signin?error=not-signed-in" (get-in response [:headers "location"]))))))
```

---

#### Test 4: Przekierowanie Gdy :uid Jest Nil
**Cel:** Sprawdzić gdy sesja istnieje, ale :uid jest nil

**Kod testu:**
```clojure
(deftest wrap-signed-in-nil-uid-test
  (testing "Sesja z :uid jako nil powoduje przekierowanie"
    (let [ctx {:session {:uid nil}}
          handler (fn [ctx] {:status 200 :body "Protected"})
          wrapped-handler (mid/wrap-signed-in handler)
          response (wrapped-handler ctx)]

      (is (= 303 (:status response)))
      (is (= "/signin?error=not-signed-in" (get-in response [:headers "location"]))))))
```

---

### 2.2. Middleware `wrap-redirect-signed-in`

#### Test 5: Przekierowanie Zalogowanego Użytkownika
**Cel:** Sprawdzić, że zalogowany użytkownik jest przekierowywany z auth pages

**Kod testu:**
```clojure
(deftest wrap-redirect-signed-in-authenticated-redirect-test
  (testing "Zalogowany użytkownik jest przekierowywany z /signin do /app"
    (let [user-id (random-uuid)
          ctx {:session {:uid user-id}}
          handler (fn [ctx] {:status 200 :body "Sign in page"})
          wrapped-handler (mid/wrap-redirect-signed-in handler)
          response (wrapped-handler ctx)]

      ;; Przekierowanie do aplikacji
      (is (= 303 (:status response)))
      (is (= "/app" (get-in response [:headers "location"]))))))
```

---

#### Test 6: Dostęp Niezalogowanego do Auth Pages
**Cel:** Sprawdzić, że niezalogowani użytkownicy mogą odwiedzać strony auth

**Kod testu:**
```clojure
(deftest wrap-redirect-signed-in-unauthenticated-access-test
  (testing "Niezalogowany użytkownik może odwiedzić /signin"
    (let [ctx {:session {}}
          handler (fn [ctx] {:status 200 :body "Sign in page"})
          wrapped-handler (mid/wrap-redirect-signed-in handler)
          response (wrapped-handler ctx)]

      ;; Strona powinna się załadować normalnie
      (is (= 200 (:status response)))
      (is (= "Sign in page" (:body response))))))
```

---

### 2.3. Integracja z Trasami

#### Test 7: Chronione Trasy (/summaries)
**Cel:** Sprawdzić, że /summaries wymaga zalogowania

**Kod testu:**
```clojure
(deftest protected-routes-test
  (testing "/summaries wymaga zalogowania"
    (let [ctx-unauthenticated {:session {}}
          ;; Symuluj handler dla /summaries
          summaries-handler (fn [ctx] {:status 200 :body "Summaries list"})
          protected-handler (mid/wrap-signed-in summaries-handler)
          response (protected-handler ctx-unauthenticated)]

      ;; Niezalogowany - przekierowanie
      (is (= 303 (:status response)))
      (is (str/includes? (get-in response [:headers "location"]) "/signin"))))

  (testing "/summaries dostępne dla zalogowanych"
    (let [ctx-authenticated {:session {:uid (random-uuid)}}
          summaries-handler (fn [ctx] {:status 200 :body "Summaries list"})
          protected-handler (mid/wrap-signed-in summaries-handler)
          response (protected-handler ctx-authenticated)]

      ;; Zalogowany - dostęp
      (is (= 200 (:status response))))))
```

---

#### Test 8: Publiczne Trasy (/signin, /, /forgot-password)
**Cel:** Sprawdzić, że auth pages są dostępne bez logowania

**Kod testu:**
```clojure
(deftest public-routes-test
  (testing "/ (signup) dostępne bez logowania"
    (let [ctx {:session {}}
          signup-handler (fn [ctx] {:status 200 :body "Signup page"})
          wrapped-handler (mid/wrap-redirect-signed-in signup-handler)
          response (wrapped-handler ctx)]

      (is (= 200 (:status response)))))

  (testing "/signin dostępne bez logowania"
    (let [ctx {:session {}}
          signin-handler (fn [ctx] {:status 200 :body "Signin page"})
          wrapped-handler (mid/wrap-redirect-signed-in signin-handler)
          response (wrapped-handler ctx)]

      (is (= 200 (:status response)))))

  (testing "/forgot-password dostępne bez logowania"
    (let [ctx {:session {}}
          forgot-handler (fn [ctx] {:status 200 :body "Forgot password"})
          ;; Ta strona nie powinna mieć wrap-redirect-signed-in
          response (forgot-handler ctx)]

      (is (= 200 (:status response))))))
```

---

### 2.4. Scenariusze Użytkownika

#### Test 9: Próba Dostępu do Chronionej Strony Bez Logowania
**Cel:** Pełny scenariusz: użytkownik próbuje otworzyć /summaries bez logowania

**Kod testu:**
```clojure
(deftest unauthorized-access-scenario-test
  (testing "Użytkownik próbuje otworzyć /summaries bez logowania"
    (let [ctx {:session {}}
          summaries-handler (fn [ctx] {:status 200 :body "Summaries"})
          protected-handler (mid/wrap-signed-in summaries-handler)
          response (protected-handler ctx)]

      ;; 1. Przekierowanie do /signin
      (is (= 303 (:status response)))
      (is (= "/signin?error=not-signed-in" (get-in response [:headers "location"])))

      ;; 2. Komunikat błędu jest przekazany
      (is (str/includes? (get-in response [:headers "location"]) "error=not-signed-in")))))
```

---

#### Test 10: Zalogowany Użytkownik Próbuje Odwiedzić /signin
**Cel:** Sprawdzić przekierowanie już zalogowanego użytkownika

**Kod testu:**
```clojure
(deftest logged-in-redirect-scenario-test
  (testing "Zalogowany użytkownik odwiedza /signin"
    (let [ctx {:session {:uid (random-uuid)}}
          signin-handler (fn [ctx] {:status 200 :body "Signin"})
          wrapped-handler (mid/wrap-redirect-signed-in signin-handler)
          response (wrapped-handler ctx)]

      ;; Przekierowanie do /app
      (is (= 303 (:status response)))
      (is (= "/app" (get-in response [:headers "location"]))))))
```

---

### 2.5. Przypadki Brzegowe

#### Test 11: Middleware z Niepoprawną Sesją
**Cel:** Sprawdzić zachowanie z uszkodzoną strukturą sesji

**Kod testu:**
```clojure
(deftest corrupted-session-test
  (testing "Sesja z niepoprawną strukturą"
    (let [ctx {:session "invalid-not-a-map"}
          handler (fn [ctx] {:status 200 :body "Protected"})
          wrapped-handler (mid/wrap-signed-in handler)]

      ;; Powinno być bezpieczne (przekierowanie lub błąd)
      (try
        (let [response (wrapped-handler ctx)]
          (is (or (= 303 (:status response))
                  (= 500 (:status response)))))
        (catch Exception e
          ;; Akceptowalne - błąd obsługi
          (is (some? e)))))))
```

---

#### Test 12: UUID w Sesji Ale Użytkownik Nie Istnieje
**Cel:** Sprawdzić gdy :uid wskazuje na nieistniejącego użytkownika

**Uwaga:** Middleware nie weryfikuje istnienia użytkownika w bazie (tylko obecność :uid)

**Kod testu:**
```clojure
(deftest nonexistent-user-session-test
  (testing "UUID w sesji ale użytkownik usunięty"
    (let [deleted-user-id (random-uuid)
          ctx {:session {:uid deleted-user-id}}
          handler (fn [ctx] {:status 200 :body "Protected"})
          wrapped-handler (mid/wrap-signed-in handler)
          response (wrapped-handler ctx)]

      ;; Middleware wrap-signed-in sprawdza tylko obecność :uid
      ;; Nie weryfikuje w bazie, więc przepuści
      (is (= 200 (:status response)))

      ;; TODO: Rozważyć dodanie weryfikacji użytkownika w middleware
      ;; lub w handler'ach chronionych stron
      )))
```

---

### 2.6. Łączenie Middleware

#### Test 13: Kolejność Middleware Ma Znaczenie
**Cel:** Sprawdzić, że middleware działają w odpowiedniej kolejności

**Kod testu:**
```clojure
(deftest middleware-order-test
  (testing "Kolejność middleware: wrap-signed-in -> handler"
    (let [execution-log (atom [])
          handler (fn [ctx]
                   (swap! execution-log conj :handler)
                   {:status 200 :body "OK"})
          custom-mid (fn [handler]
                      (fn [ctx]
                        (swap! execution-log conj :custom-mid)
                        (handler ctx)))
          wrapped (-> handler
                     mid/wrap-signed-in
                     custom-mid)

          ctx {:session {:uid (random-uuid)}}
          _ (wrapped ctx)]

      ;; Middleware zewnętrzny wykona się pierwszy
      (is (= [:custom-mid :handler] @execution-log)))))
```

---

## 3. Testy Integracyjne

#### Test 14: Pełny Przepływ z Middleware
**Cel:** Test end-to-end z middleware stack

**Kod testu:**
```clojure
(deftest full-middleware-flow-test
  (testing "Pełny przepływ: niezalogowany -> logowanie -> dostęp"
    ;; 1. Próba dostępu bez logowania
    (let [ctx-unauth {:session {}}
          protected (fn [ctx] {:status 200 :body "Protected"})
          wrapped (mid/wrap-signed-in protected)
          response1 (wrapped ctx-unauth)]

      (is (= 303 (:status response1)))
      (is (str/includes? (get-in response1 [:headers "location"]) "/signin")))

    ;; 2. Logowanie
    (let [test-ctx (test-context)
          _ (create-test-user test-ctx "user@example.com" "password")
          signin-response (auth/signin (assoc test-ctx :params
                                             {:email "user@example.com"
                                              :password "password"}))
          session (:session signin-response)]

      (is (some? (:uid session)))

      ;; 3. Dostęp do chronionej strony
      (let [ctx-auth {:session session}
            protected (fn [ctx] {:status 200 :body "Protected"})
            wrapped (mid/wrap-signed-in protected)
            response2 (wrapped ctx-auth)]

        (is (= 200 (:status response2)))
        (is (= "Protected" (:body response2)))))))
```

---

## 4. Testy Bezpieczeństwa

#### Test 15: Middleware Nie Ujawnia Szczegółów Błędu
**Cel:** Sprawdzić, że komunikaty błędów nie ujawniają wrażliwych informacji

**Kod testu:**
```clojure
(deftest middleware-error-disclosure-test
  (testing "Middleware nie ujawnia szczegółów błędu"
    (let [ctx {:session {}}
          handler (fn [ctx] {:status 200 :body "Protected"})
          wrapped (mid/wrap-signed-in handler)
          response (wrapped ctx)]

      ;; Tylko generyczny komunikat
      (is (str/includes? (get-in response [:headers "location"]) "not-signed-in"))

      ;; Nie ujawnia innych szczegółów
      (is (not (str/includes? (str response) "null")))
      (is (not (str/includes? (str response) "exception"))))))
```

---

## 5. Kryteria Akceptacji

- [ ] `wrap-signed-in` przepuszcza zalogowanych użytkowników
- [ ] `wrap-signed-in` przekierowuje niezalogowanych do /signin
- [ ] `wrap-signed-in` przekierowuje przy braku sesji
- [ ] `wrap-signed-in` przekierowuje gdy :uid jest nil
- [ ] `wrap-redirect-signed-in` przekierowuje zalogowanych do /app
- [ ] `wrap-redirect-signed-in` przepuszcza niezalogowanych
- [ ] Chronione trasy (/summaries) wymagają zalogowania
- [ ] Publiczne trasy (/, /signin, /forgot-password) są dostępne
- [ ] Komunikat błędu "not-signed-in" jest przekazywany
- [ ] Zalogowani nie mogą odwiedzać auth pages (przekierowanie)
- [ ] Middleware obsługują uszkodzone sesje bezpiecznie
- [ ] Middleware nie weryfikują istnienia użytkownika w bazie (tylko :uid)
- [ ] Kolejność middleware ma znaczenie
- [ ] Pełny przepływ z middleware działa end-to-end
- [ ] Middleware nie ujawniają wrażliwych szczegółów błędów

---

**Koniec dokumentu**
