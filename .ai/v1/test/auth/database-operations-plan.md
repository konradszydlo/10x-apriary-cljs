# Plan Testów: Operacje Bazodanowe (Database Operations)

**Baza Danych:** XTDB 1.24
**Schema:** `src/com/apriary/schema.clj`
**Moduły:** `auth.clj`, wszystkie handlery
**Data:** 2025-12-01
**Wersja:** 1.0

---

## 1. Cel Testowania

Weryfikacja poprawności operacji bazodanowych w systemie autentykacji:
- Tworzenie użytkowników
- Odczytywanie danych użytkowników
- Aktualizacja haseł
- Tworzenie i zarządzanie tokenami reset
- Integralność danych
- Obsługa błędów

---

## 2. Zakres Testów

### 2.1. Schemat Użytkownika (User Schema)

#### Test 1: Tworzenie Użytkownika Z Pełnymi Danymi
**Kod testu:**
```clojure
(deftest create-user-full-data-test
  (testing "Tworzenie użytkownika z wszystkimi wymaganymi polami"
    (let [ctx (test-context)
          user-id (random-uuid)
          password-hash (auth/hash-password "password123")
          joined-at (java.util.Date.)]

      ;; Utwórz użytkownika
      (biff/submit-tx ctx
        [{:db/op :create
          :db/doc-type :user
          :xt/id user-id
          :user/id user-id
          :user/email "test@example.com"
          :user/password-hash password-hash
          :user/joined-at joined-at}])

      ;; Odczytaj z bazy
      (let [user (biff/lookup (:biff/db ctx) :xt/id user-id)]
        (is (some? user))
        (is (= user-id (:user/id user)))
        (is (= "test@example.com" (:user/email user)))
        (is (= password-hash (:user/password-hash user)))
        (is (= joined-at (:user/joined-at user)))))))
```

---

#### Test 2: Lookup Użytkownika Po Email
**Kod testu:**
```clojure
(deftest lookup-user-by-email-test
  (testing "Wyszukiwanie użytkownika po emailu"
    (let [ctx (test-context)
          user-id (create-test-user ctx "findme@example.com" "password")]

      ;; Znajdź po emailu
      (let [user (biff/lookup (:biff/db ctx) :user/email "findme@example.com")]
        (is (some? user))
        (is (= user-id (:user/id user)))
        (is (= "findme@example.com" (:user/email user))))))

  (testing "Brak użytkownika z danym emailem"
    (let [ctx (test-context)
          user (biff/lookup (:biff/db ctx) :user/email "notfound@example.com")]

      (is (nil? user)))))
```

---

#### Test 3: Aktualizacja Hasła Użytkownika
**Kod testu:**
```clojure
(deftest update-user-password-test
  (testing "Aktualizacja hasła użytkownika"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "oldpassword")
          old-user (biff/lookup (:biff/db ctx) :xt/id user-id)
          old-hash (:user/password-hash old-user)]

      ;; Zaktualizuj hasło
      (let [new-hash (auth/hash-password "newpassword")]
        (biff/submit-tx ctx
          [{:db/op :update
            :db/doc-type :user
            :xt/id user-id
            :user/password-hash new-hash}])

        ;; Sprawdź aktualizację
        (let [updated-user (biff/lookup (:biff/db ctx) :xt/id user-id)]
          (is (= new-hash (:user/password-hash updated-user)))
          (is (not= old-hash (:user/password-hash updated-user)))

          ;; Inne pola nie zmieniły się
          (is (= (:user/email old-user) (:user/email updated-user)))
          (is (= (:user/joined-at old-user) (:user/joined-at updated-user))))))))
```

---

### 2.2. Schemat Tokenu Reset (Password Reset Token Schema)

#### Test 4: Tworzenie Tokenu Reset
**Kod testu:**
```clojure
(deftest create-reset-token-test
  (testing "Tworzenie tokenu resetowania hasła"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")
          token-id (random-uuid)
          hashed-token (auth/hash-token "raw-token")
          created-at (auth/now)
          expires-at (auth/plus-hours created-at 1)]

      ;; Utwórz token
      (biff/submit-tx ctx
        [{:db/op :create
          :db/doc-type :password-reset-token
          :xt/id token-id
          :password-reset-token/id token-id
          :password-reset-token/user-id user-id
          :password-reset-token/token hashed-token
          :password-reset-token/created-at created-at
          :password-reset-token/expires-at expires-at
          :password-reset-token/used-at nil}])

      ;; Odczytaj z bazy
      (let [token (biff/lookup (:biff/db ctx) :xt/id token-id)]
        (is (some? token))
        (is (= token-id (:password-reset-token/id token)))
        (is (= user-id (:password-reset-token/user-id token)))
        (is (= hashed-token (:password-reset-token/token token)))
        (is (nil? (:password-reset-token/used-at token)))))))
```

---

#### Test 5: Lookup Tokenu Po Hash
**Kod testu:**
```clojure
(deftest lookup-token-by-hash-test
  (testing "Wyszukiwanie tokenu po hashu"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")
          raw-token "unique-token-123"
          hashed-token (auth/hash-token raw-token)
          token-id (create-reset-token ctx user-id hashed-token)]

      ;; Znajdź po hashu
      (let [token (biff/lookup (:biff/db ctx) :password-reset-token/token hashed-token)]
        (is (some? token))
        (is (= token-id (:xt/id token)))
        (is (= user-id (:password-reset-token/user-id token))))))

  (testing "Brak tokenu z danym hashem"
    (let [ctx (test-context)
          token (biff/lookup (:biff/db ctx) :password-reset-token/token "nonexistent-hash")]

      (is (nil? token)))))
```

---

#### Test 6: Oznaczanie Tokenu Jako Użytego
**Kod testu:**
```clojure
(deftest mark-token-as-used-test
  (testing "Oznaczanie tokenu jako użytego"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")
          hashed-token (auth/hash-token "token")
          token-id (create-reset-token ctx user-id hashed-token)
          initial-token (biff/lookup (:biff/db ctx) :xt/id token-id)]

      ;; Początkowo nie użyty
      (is (nil? (:password-reset-token/used-at initial-token)))

      ;; Oznacz jako użyty
      (let [used-at (auth/now)]
        (biff/submit-tx ctx
          [{:db/op :update
            :db/doc-type :password-reset-token
            :xt/id token-id
            :password-reset-token/used-at used-at}])

        ;; Sprawdź aktualizację
        (let [used-token (biff/lookup (:biff/db ctx) :xt/id token-id)]
          (is (some? (:password-reset-token/used-at used-token)))
          (is (= used-at (:password-reset-token/used-at used-token))))))))
```

---

### 2.3. Zapytania (Queries)

#### Test 7: Znajdowanie Wszystkich Tokenów Użytkownika
**Kod testu:**
```clojure
(deftest find-all-user-tokens-test
  (testing "Znajdowanie wszystkich tokenów użytkownika"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")]

      ;; Utwórz 3 tokeny
      (create-reset-token ctx user-id (auth/hash-token "token1"))
      (create-reset-token ctx user-id (auth/hash-token "token2"))
      (create-reset-token ctx user-id (auth/hash-token "token3"))

      ;; Znajdź wszystkie tokeny użytkownika
      (let [tokens (find-reset-tokens-for-user ctx user-id)]
        (is (= 3 (count tokens)))
        (is (every? #(= user-id (:password-reset-token/user-id %)) tokens))))))
```

---

#### Test 8: Znajdowanie Nieużytych Tokenów
**Kod testu:**
```clojure
(deftest find-unused-tokens-test
  (testing "Znajdowanie tylko nieużytych tokenów"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")

          ;; Utwórz tokeny
          token1-id (create-reset-token ctx user-id (auth/hash-token "token1"))
          token2-id (create-reset-token ctx user-id (auth/hash-token "token2"))
          token3-id (create-reset-token ctx user-id (auth/hash-token "token3"))]

      ;; Oznacz token2 jako użyty
      (biff/submit-tx ctx
        [{:db/op :update
          :xt/id token2-id
          :password-reset-token/used-at (auth/now)}])

      ;; Znajdź nieużyte
      (let [unused-tokens (find-unused-reset-tokens ctx user-id)]
        (is (= 2 (count unused-tokens)))
        (is (every? #(nil? (:password-reset-token/used-at %)) unused-tokens))
        (is (contains? (set (map :xt/id unused-tokens)) token1-id))
        (is (contains? (set (map :xt/id unused-tokens)) token3-id))
        (is (not (contains? (set (map :xt/id unused-tokens)) token2-id)))))))
```

---

### 2.4. Integralność Danych

#### Test 9: Unikalność Email
**Kod testu:**
```clojure
(deftest email-uniqueness-test
  (testing "Email powinien być unikalny"
    (let [ctx (test-context)
          _ (create-test-user ctx "unique@example.com" "password1")]

      ;; Próba utworzenia drugiego użytkownika z tym samym emailem
      ;; XTDB nie wymusza unikalności na poziomie bazy
      ;; Ale aplikacja powinna sprawdzać przed utworzeniem

      (let [existing-user (biff/lookup (:biff/db ctx) :user/email "unique@example.com")]
        (is (some? existing-user))

        ;; Signup handler powinien odrzucić duplikat
        (let [response (auth/signup (assoc ctx :params
                                          {:email "unique@example.com"
                                           :password "password2"
                                           :password-confirm "password2"}))]
          (is (str/includes? (get-in response [:headers "location"]) "email-exists")))))))
```

---

#### Test 10: Foreign Key (User ID w Token)
**Kod testu:**
```clojure
(deftest token-user-foreign-key-test
  (testing "Token powinien wskazywać na istniejącego użytkownika"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")
          token-id (create-reset-token ctx user-id (auth/hash-token "token"))]

      ;; Token wskazuje na użytkownika
      (let [token (biff/lookup (:biff/db ctx) :xt/id token-id)
            user (biff/lookup (:biff/db ctx) :xt/id (:password-reset-token/user-id token))]

        (is (some? user))
        (is (= user-id (:user/id user))))))

  (testing "Token z nieistniejącym user-id"
    ;; XTDB nie wymusza foreign keys
    ;; Ale można utworzyć token wskazujący na nieistniejącego użytkownika
    ;; Aplikacja powinna to walidować

    (let [ctx (test-context)
          fake-user-id (random-uuid)
          token-id (create-reset-token ctx fake-user-id (auth/hash-token "token"))
          token (biff/lookup (:biff/db ctx) :xt/id token-id)]

      ;; Token istnieje
      (is (some? token))

      ;; Ale user nie istnieje
      (let [user (biff/lookup (:biff/db ctx) :xt/id fake-user-id)]
        (is (nil? user))))))
```

---

### 2.5. Transakcje (Transactions)

#### Test 11: Atomiczność Transakcji
**Kod testu:**
```clojure
(deftest transaction-atomicity-test
  (testing "Transakcja jest atomowa - wszystko lub nic"
    (let [ctx (test-context)
          user-id (random-uuid)]

      ;; Transakcja z wieloma operacjami
      (biff/submit-tx ctx
        [{:db/op :create
          :db/doc-type :user
          :xt/id user-id
          :user/id user-id
          :user/email "atomic@example.com"
          :user/password-hash (auth/hash-password "password")
          :user/joined-at (auth/now)}])

      ;; Wszystkie dane powinny być zapisane
      (let [user (biff/lookup (:biff/db ctx) :xt/id user-id)]
        (is (some? user))
        (is (= "atomic@example.com" (:user/email user)))))))
```

---

#### Test 12: Aktualizacja Hasła I Tokenu W Jednej Transakcji
**Kod testu:**
```clojure
(deftest update-password-and-token-atomically-test
  (testing "Aktualizacja hasła i oznaczenie tokenu jako użytego w jednej transakcji"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "oldpassword")
          token-id (create-reset-token ctx user-id (auth/hash-token "token"))
          new-hash (auth/hash-password "newpassword")]

      ;; Atomiczna aktualizacja
      (biff/submit-tx ctx
        [{:db/op :update
          :xt/id user-id
          :user/password-hash new-hash}
         {:db/op :update
          :xt/id token-id
          :password-reset-token/used-at (auth/now)}])

      ;; Obie aktualizacje powinny być widoczne
      (let [user (biff/lookup (:biff/db ctx) :xt/id user-id)
            token (biff/lookup (:biff/db ctx) :xt/id token-id)]

        (is (= new-hash (:user/password-hash user)))
        (is (some? (:password-reset-token/used-at token)))))))
```

---

### 2.6. Obsługa Błędów

#### Test 13: Obsługa Nieistniejącego Dokumentu
**Kod testu:**
```clojure
(deftest handle-nonexistent-document-test
  (testing "Odczyt nieistniejącego dokumentu zwraca nil"
    (let [ctx (test-context)
          fake-id (random-uuid)
          result (biff/lookup (:biff/db ctx) :xt/id fake-id)]

      (is (nil? result)))))
```

---

## 3. Testy Wydajnościowe

#### Test 14: Wydajność Lookup Po Email
**Kod testu:**
```clojure
(deftest lookup-by-email-performance-test
  (testing "Wyszukiwanie po emailu jest szybkie"
    (let [ctx (test-context)]

      ;; Utwórz 100 użytkowników
      (doseq [i (range 100)]
        (create-test-user ctx (str "user" i "@example.com") "password"))

      ;; Zmierz czas lookup
      (let [start (System/currentTimeMillis)
            _ (dotimes [i 100]
               (biff/lookup (:biff/db ctx) :user/email (str "user" i "@example.com")))
            duration (- (System/currentTimeMillis) start)]

        ;; 100 lookupów < 100ms
        (is (< duration 100) (str "100 lookupów zajęło " duration "ms"))))))
```

---

## 4. Kryteria Akceptacji

- [ ] Tworzenie użytkownika z pełnymi danymi działa
- [ ] Lookup użytkownika po email działa
- [ ] Aktualizacja hasła użytkownika działa
- [ ] Tworzenie tokenu reset działa
- [ ] Lookup tokenu po hashu działa
- [ ] Oznaczanie tokenu jako użytego działa
- [ ] Znajdowanie wszystkich tokenów użytkownika działa
- [ ] Znajdowanie nieużytych tokenów działa
- [ ] Email jest sprawdzany pod kątem unikalności
- [ ] Token zawiera poprawne user-id
- [ ] Transakcje są atomowe
- [ ] Aktualizacja hasła i tokenu w jednej transakcji działa
- [ ] Obsługa nieistniejących dokumentów (nil) działa
- [ ] Wydajność lookup jest odpowiednia

---

**Koniec dokumentu**
