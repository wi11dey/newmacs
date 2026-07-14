(ns app.renderer.emacs
  (:require [clojure.string :as str]
            #?(:cljs ["@tauri-apps/api/core" :refer [invoke]]))
  #?(:cljs (:require-macros [app.renderer.emacs])))

(defn ->elisp [form]
  (cond
    (and (seq? form) (= (first form) 'clojure.core/unquote)) (list `(apply str (->elisp ~(second form))))
    (string? form) (list (pr-str form))
    (seq? form) (concat '("(") (interpose " " (mapcat ->elisp form)) '(")"))
    :else (list (str form))))

#?(:clj
   (defmacro with-emacs [& body]
     `(emacs-eval-str (str ~@(->elisp (cons 'progn body))))))

#?(:cljs
   (defn emacs-eval-str [elisp]
     (invoke "emacs_eval" #js {:elisp elisp})))
