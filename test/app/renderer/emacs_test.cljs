(ns app.renderer.emacs-test
  (:require [app.renderer.emacs :as emacs]
            [cljs.test :refer-macros [deftest is testing]]
            [app.renderer.emacs :refer-macros [with-emacs]]))

(deftest converts-scalar-values-to-elisp
  (testing "strings use readable escaping"
    (is (= "\"hello \\\"Emacs\\\"\\n\""
           (emacs/->elisp "hello \"Emacs\"\n"))))

  (testing "booleans and nil use their Elisp equivalents"
    (is (= "t" (emacs/->elisp true)))
    (is (= "nil" (emacs/->elisp false)))
    (is (= "nil" (emacs/->elisp nil))))

  (testing "keywords, symbols, and numbers retain their printed names"
    (is (= ":foreground" (emacs/->elisp :foreground)))
    (is (= "font-lock-mode" (emacs/->elisp 'font-lock-mode)))
    (is (= "42" (emacs/->elisp 42)))))

(deftest converts-lists-recursively
  (is (= "(set-face-attribute (quote default) nil :height 140)"
         (emacs/->elisp
          '(set-face-attribute 'default nil :height 140))))

  (is (= "(progn (message \"ready\") (redisplay))"
         (emacs/->elisp
          '(progn (message "ready") (redisplay))))))
