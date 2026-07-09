(ns app.renderer.emacs
  #?(:cljs (:require-macros [app.renderer.emacs]))
  #?(:clj (:require [clojure.string :as str])))

#?(:clj
   (defn ->elisp [form]
     (cond
       (string? form)  (pr-str form)
       (char? form)    (str "?" form)
       (true? form)    "t"
       (false? form)   "nil"
       (nil? form)     "nil"
       (keyword? form) (str form)
       (symbol? form)  (str form)
       (vector? form)  (str "[" (str/join " " (map ->elisp form)) "]")
       (seq? form)     (str "(" (str/join " " (map ->elisp form)) ")")
       :else           (str form))))

#?(:clj
   (defmacro with-emacs [& body]
     (let [elisp (->elisp (cons 'progn body))]
       `(emacs-eval ~elisp))))

#?(:cljs
   (def ^:private child-process (js/require "child_process")))

#?(:cljs
   (defn emacs-eval [elisp]
     (.execFileSync child-process "emacsclient" #js ["-e" elisp])))
