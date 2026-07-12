(ns app.renderer.emacs
  (:require [clojure.string :as str])
  #?(:cljs (:require-macros [app.renderer.emacs])))

(defn ->elisp
  "Returns a Clojure form that, when evaluated, is a string of Elisp."
  [form]
  (cond
    (and (seq? form) (= (first form) 'clojure.core/unquote)) `(->elisp ~(second form))
    (string? form)                                           (pr-str form)
    (char? form)                                             (str "?" form)
    (true? form)                                             "t"
    (false? form)                                            "nil"
    (nil? form)                                              "nil"
    (keyword? form)                                          (str form)
    (symbol? form)                                           (str form)
    ;; TODO: map these properly
    (vector? form)                                           (str "[" (str/join " " (map ->elisp form)) "]")
    (seq? form)                                              (str "(" (str/join " " (map ->elisp form)) ")")
    :else                                                    (str form)))

#?(:clj
   (defmacro with-emacs [& body]
     (let [elisp (->elisp (cons 'progn body))]
       `(emacs-eval ~elisp))))

#?(:cljs
   (def ^:private child-process (js/require "child_process")))

#?(:cljs
   (defn emacs-eval [elisp]
     (.execFileSync child-process "emacsclient" #js ["-e" elisp])))
