# Plan Testów: Bezpieczeństwo Tokenów (Token Security)

**Moduł:** `src/com/apriary/auth.clj`
**Funkcje:** `generate-secure-token`, `hash-token`, `plus-hours`, `after?`
**Data:** 2025-12-01
**Wersja:** 1.0

---

## 1. Cel Testowania

Weryfikacja bezpieczeństwa systemu tokenów resetowania hasła, w tym:
- Kryptograficzna bezpieczeństwo generowania
- Poprawność hashowania
- Logika wygasania
- Ochrona przed atakami

---

## 2. Zakres Testów

### 2.1. Generowanie Tokenów

#### Test 1: Unik

alność Generowanych Tokenów
**Funkcja:** `generate-secure-token`

**Cel:** Sprawdzić, że każdy wygenerowany token jest unikalny

**Kod testu:**
```clojure
(deftest token-uniqueness-test
  (testing "Każdy wygenerowany token jest unikalny"
    (let [tokens (repeatedly 1000 auth/generate-secure-token)
          unique-tokens (set tokens)]

      ;; Wszystkie tokeny powinny być unikalne
      (is (= 1000 (count unique-tokens))
          "Wszystkie 1000 tokenów powinno być unikalnych"))))
```

---

#### Test 2: Długość i Format Tokenu
**Funkcja:** `generate-secure-token`

**Cel:** Sprawdzić, że token ma odpowiednią długość i format

**Scenariusze:**
1. Token powinien mieć odpowiednią długość (base64 URL-safe z 32 bajtów)
2. Token powinien zawierać tylko dozwolone znaki (A-Za-z0-9_-)
3. Token nie powinien kończyć się znakami wypełnienia (=)

**Kod testu:**
```clojure
(deftest token-format-test
  (testing "Format i długość wygenerowanego tokenu"
    (let [token (auth/generate-secure-token)]

      ;; Sprawdź długość (32 bajty -> ~44 znaki base64)
      (is (>= (count token) 40) "Token powinien mieć co najmniej 40 znaków")
      (is (<= (count token) 50) "Token nie powinien być zbyt długi")

      ;; Sprawdź, czy zawiera tylko URL-safe base64 znaki
      (is (re-matches #"[A-Za-z0-9_-]+" token)
          "Token powinien zawierać tylko URL-safe base64 znaki")

      ;; Nie powinien kończyć się =
      (is (not (str/ends-with? token "=")))

      ;; String type
      (is (string? token)))))
```

---

#### Test 3: Entropia Tokenu (Randomness)
**Funkcja:** `generate-secure-token`

**Cel:** Sprawdzić, że tokeny są generowane z wystarczającą entropią

**Kod testu:**
```clojure
(deftest token-randomness-test
  (testing "Tokeny mają wysoką entropię"
    (let [tokens (repeatedly 100 auth/generate-secure-token)
          ;; Sprawdź różnorodność pierwszych znaków
          first-chars (map first tokens)
          unique-first-chars (count (set first-chars))]

      ;; Powinno być wiele różnych pierwszych znaków (min 30 z 100)
      (is (>= unique-first-chars 30)
          (str "Tylko " unique-first-chars " unikalnych pierwszych znaków"))))

  (testing "Tokeny nie zawierają przewidywalnych wzorców"
    (let [token1 (auth/generate-secure-token)
          token2 (auth/generate-secure-token)]

      ;; Tokeny nie powinny mieć wspólnego prefiksu
      (is (not= (subs token1 0 10) (subs token2 0 10))))))
```

---

### 2.2. Hashowanie Tokenów

#### Test 4: Determinizm Hashowania
**Funkcja:** `hash-token`

**Cel:** Sprawdzić, że hashowanie jest deterministyczne (ten sam input → ten sam hash)

**Kod testu:**
```clojure
(deftest token-hash-determinism-test
  (testing "Hashowanie tokenu jest deterministyczne"
    (let [token "test-token-123"
          hash1 (auth/hash-token token)
          hash2 (auth/hash-token token)]

      (is (= hash1 hash2) "Te same tokeny powinny dawać ten sam hash")
      (is (string? hash1))
      (is (= 64 (count hash1)) "SHA-256 hash powinien mieć 64 znaki hex"))))
```

---

#### Test 5: Różne Tokeny → Różne Hashe
**Funkcja:** `hash-token`

**Cel:** Sprawdzić, że różne tokeny dają różne hashe

**Kod testu:**
```clojure
(deftest token-hash-uniqueness-test
  (testing "Różne tokeny dają różne hashe"
    (let [token1 "token-one"
          token2 "token-two"
          hash1 (auth/hash-token token1)
          hash2 (auth/hash-token token2)]

      (is (not= hash1 hash2) "Różne tokeny powinny dawać różne hashe")))

  (testing "Nawet podobne tokeny dają bardzo różne hashe"
    (let [token1 "password123"
          token2 "password124"  ;; Tylko jedna cyfra różnicy
          hash1 (auth/hash-token token1)
          hash2 (auth/hash-token token2)]

      (is (not= hash1 hash2))

      ;; Avalanche effect - duża różnica w hashach przy małej zmianie
      (let [different-chars (count (filter false?
                                          (map = hash1 hash2)))]
        (is (> different-chars 30) "Większość znaków powinna być różna")))))
```

---

#### Test 6: Format Hasha SHA-256
**Funkcja:** `hash-token`

**Cel:** Sprawdzić, że hash ma poprawny format SHA-256

**Kod testu:**
```clojure
(deftest token-hash-format-test
  (testing "Hash tokenu ma format SHA-256"
    (let [token "any-token"
          hash (auth/hash-token token)]

      ;; SHA-256 w hex = 64 znaki
      (is (= 64 (count hash)))

      ;; Tylko znaki hex (0-9, a-f)
      (is (re-matches #"[0-9a-f]{64}" hash)
          "Hash powinien składać się z 64 znaków hex (lowercase)"))))
```

---

### 2.3. Logika Wygasania Tokenów

#### Test 7: Obliczanie Daty Wygaśnięcia
**Funkcja:** `plus-hours`

**Cel:** Sprawdzić, że data wygaśnięcia jest poprawnie obliczana

**Kod testu:**
```clojure
(deftest token-expiration-calculation-test
  (testing "Dodawanie godzin do daty"
    (let [now (java.util.Date. 1000000000000) ;; Stały timestamp dla testów
          future (auth/plus-hours now 1)
          diff-ms (- (.getTime future) (.getTime now))]

      ;; Różnica powinna wynosić dokładnie 1 godzinę
      (is (= 3600000 diff-ms) "1 godzina = 3600000 ms")))

  (testing "Dodawanie wielu godzin"
    (let [now (java.util.Date.)
          future24 (auth/plus-hours now 24)
          diff-hours (/ (- (.getTime future24) (.getTime now)) 3600000.0)]

      (is (< 23.99 diff-hours 24.01) "24 godziny"))))
```

---

#### Test 8: Sprawdzanie Wygaśnięcia
**Funkcja:** `after?`

**Cel:** Sprawdzić, że funkcja poprawnie określa, czy data jest w przyszłości/przeszłości

**Kod testu:**
```clojure
(deftest token-expiration-check-test
  (testing "Sprawdzanie, czy data jest po innej dacie"
    (let [past (java.util.Date. 1000000000000)
          future (java.util.Date. 2000000000000)
          now (java.util.Date.)]

      ;; Przyszłość jest po przeszłości
      (is (true? (auth/after? future past)))

      ;; Przeszłość nie jest po przyszłości
      (is (false? (auth/after? past future)))

      ;; Teraz jest po przeszłości
      (is (true? (auth/after? now past)))

      ;; Przeszłość nie jest po teraz
      (is (false? (auth/after? past now))))))
```

---

### 2.4. Testy Bezpieczeństwa

#### Test 9: Odporność na Brute Force
**Cel:** Sprawdzić, że przestrzeń tokenów jest wystarczająco duża

**Kod testu:**
```clojure
(deftest token-brute-force-resistance-test
  (testing "Przestrzeń tokenów jest wystarczająco duża"
    ;; 32 bajty = 256 bitów
    ;; Przestrzeń: 2^256 możliwości
    ;; Prawdopodobieństwo kolizji jest zaniedbywalnie małe

    (let [token (auth/generate-secure-token)
          token-bytes (* 32 8)] ; 32 bajty * 8 bitów

      ;; Sprawdź, że token ma co najmniej 256 bitów entropii
      ;; (w base64 to ~43 znaki)
      (is (>= (count token) 43)
          "Token powinien mieć co najmniej 256 bitów entropii"))))
```

---

#### Test 10: Brak Przewidywalnych Sekwencji
**Cel:** Sprawdzić, że tokeny nie są generowane sekwencyjnie

**Kod testu:**
```clojure
(deftest token-no-sequential-pattern-test
  (testing "Tokeny nie są generowane w przewidywalnej sekwencji"
    (let [tokens (repeatedly 10 auth/generate-secure-token)]

      ;; Żaden token nie powinien być inkrementem poprzedniego
      (doseq [[t1 t2] (partition 2 1 tokens)]
        (is (not= t1 t2))
        ;; Sprawdź, że tokeny nie różnią się tylko jednym znakiem
        (let [diff-count (count (filter false? (map = t1 t2)))]
          (is (> diff-count 10) "Tokeny powinny różnić się znacząco"))))))
```

---

#### Test 11: Test Rainbow Table Protection
**Cel:** Sprawdzić, że hashowanie chroni przed rainbow tables

**Kod testu:**
```clojure
(deftest token-rainbow-table-protection-test
  (testing "Hash nie ujawnia informacji o oryginalnym tokenie"
    (let [token "simple-token"
          hash (auth/hash-token token)]

      ;; Hash nie powinien zawierać fragmentów oryginalnego tokenu
      (is (not (str/includes? hash "simple")))
      (is (not (str/includes? hash "token")))

      ;; Długość hasha jest stała niezależnie od długości tokenu
      (is (= 64 (count (auth/hash-token "a"))))
      (is (= 64 (count (auth/hash-token (apply str (repeat 1000 "a")))))))))
```

---

### 2.5. Testy Integracyjne

#### Test 12: Cykl Życia Tokenu
**Cel:** Sprawdzić pełny cykl życia tokenu od utworzenia do użycia

**Kod testu:**
```clojure
(deftest token-lifecycle-test
  (testing "Pełny cykl życia tokenu"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")]

      ;; 1. Generowanie tokenu
      (let [raw-token (auth/generate-secure-token)
            hashed-token (auth/hash-token raw-token)]

        ;; 2. Tworzenie w bazie
        (let [token-id (create-reset-token ctx user-id hashed-token)
              token-record (biff/lookup (:biff/db ctx) :xt/id token-id)]

          ;; Sprawdź strukturę tokenu w bazie
          (is (some? token-record))
          (is (= hashed-token (:password-reset-token/token token-record)))
          (is (nil? (:password-reset-token/used-at token-record)))

          ;; 3. Weryfikacja tokenu
          (let [verified (biff/lookup (:biff/db ctx)
                                     :password-reset-token/token
                                     hashed-token)]
            (is (some? verified))
            (is (= user-id (:password-reset-token/user-id verified))))

          ;; 4. Użycie tokenu
          (biff/submit-tx ctx
            [{:db/op :update
              :xt/id token-id
              :password-reset-token/used-at (java.util.Date.)}])

          ;; 5. Sprawdzenie, że token jest oznaczony jako użyty
          (let [used-token (biff/lookup (:biff/db ctx) :xt/id token-id)]
            (is (some? (:password-reset-token/used-at used-token)))))))))
```

---

#### Test 13: Wiele Tokenów dla Jednego Użytkownika
**Cel:** Sprawdzić, że użytkownik może mieć wiele aktywnych tokenów

**Kod testu:**
```clojure
(deftest multiple-tokens-per-user-test
  (testing "Użytkownik może mieć wiele aktywnych tokenów"
    (let [ctx (test-context)
          user-id (create-test-user ctx "user@example.com" "password")]

      ;; Utwórz 3 tokeny dla tego samego użytkownika
      (let [token1 (auth/generate-secure-token)
            token2 (auth/generate-secure-token)
            token3 (auth/generate-secure-token)
            _ (create-reset-token ctx user-id (auth/hash-token token1))
            _ (create-reset-token ctx user-id (auth/hash-token token2))
            _ (create-reset-token ctx user-id (auth/hash-token token3))]

        ;; Wszystkie tokeny powinny być w bazie
        (let [user-tokens (find-reset-tokens-for-user ctx user-id)]
          (is (= 3 (count user-tokens)))

          ;; Wszystkie powinny być nieużyte
          (is (every? #(nil? (:password-reset-token/used-at %)) user-tokens)))))))
```

---

## 3. Testy Wydajnościowe

#### Test 14: Wydajność Generowania Tokenów
**Cel:** Sprawdzić, że generowanie tokenów nie jest zbyt wolne

**Kod testu:**
```clojure
(deftest token-generation-performance-test
  (testing "Generowanie 1000 tokenów w rozsądnym czasie"
    (let [start (System/currentTimeMillis)
          _ (doall (repeatedly 1000 auth/generate-secure-token))
          duration (- (System/currentTimeMillis) start)]

      ;; 1000 tokenów powinno być wygenerowanych < 1s
      (is (< duration 1000)
          (str "Generowanie 1000 tokenów zajęło " duration "ms")))))
```

---

#### Test 15: Wydajność Hashowania Tokenów
**Cel:** Sprawdzić, że hashowanie jest szybkie

**Kod testu:**
```clojure
(deftest token-hashing-performance-test
  (testing "Hashowanie tokenów jest szybkie"
    (let [token "test-token-for-performance"
          start (System/currentTimeMillis)
          _ (doall (repeatedly 1000 #(auth/hash-token token)))
          duration (- (System/currentTimeMillis) start)]

      ;; 1000 hashy powinny być < 500ms
      (is (< duration 500)
          (str "Hashowanie 1000 tokenów zajęło " duration "ms")))))
```

---

## 4. Przypadki Brzegowe

#### Test 16: Token z Wielkimi Literami vs Małymi
**Cel:** Sprawdzić case sensitivity

**Kod testu:**
```clojure
(deftest token-case-sensitivity-test
  (testing "Tokeny są case-sensitive"
    (let [token-lower "token-abc"
          token-upper "TOKEN-ABC"
          hash-lower (auth/hash-token token-lower)
          hash-upper (auth/hash-token token-upper)]

      ;; Różna wielkość liter = różne hashe
      (is (not= hash-lower hash-upper)))))
```

---

#### Test 17: Token z Białymi Znakami
**Cel:** Sprawdzić, że białe znaki wpływają na hash

**Kod testu:**
```clojure
(deftest token-whitespace-test
  (testing "Białe znaki wpływają na hash tokenu"
    (let [token1 "token"
          token2 " token"
          token3 "token "
          hash1 (auth/hash-token token1)
          hash2 (auth/hash-token token2)
          hash3 (auth/hash-token token3)]

      ;; Każdy powinien mieć inny hash
      (is (not= hash1 hash2))
      (is (not= hash1 hash3))
      (is (not= hash2 hash3)))))
```

---

## 5. Kryteria Akceptacji

- [ ] Tokeny są unikalne (1000 testów bez kolizji)
- [ ] Tokeny mają odpowiedni format i długość (>= 40 znaków)
- [ ] Tokeny mają wysoką entropię (losowość)
- [ ] Hashowanie jest deterministyczne (ten sam token → ten sam hash)
- [ ] Różne tokeny dają różne hashe
- [ ] Hash ma format SHA-256 (64 znaki hex)
- [ ] Obliczanie daty wygaśnięcia działa poprawnie (+1h)
- [ ] Sprawdzanie wygaśnięcia działa poprawnie (`after?`)
- [ ] Przestrzeń tokenów jest wystarczająco duża (256 bitów)
- [ ] Brak przewidywalnych sekwencji
- [ ] Ochrona przed rainbow tables (hash nie ujawnia tokenu)
- [ ] Pełny cykl życia tokenu działa
- [ ] Użytkownik może mieć wiele aktywnych tokenów
- [ ] Wydajność generowania tokenów < 1ms/token
- [ ] Wydajność hashowania < 0.5ms/hash
- [ ] Tokeny są case-sensitive
- [ ] Białe znaki wpływają na hash

---

**Koniec dokumentu**
