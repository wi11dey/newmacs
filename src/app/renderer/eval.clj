(ns app.renderer.eval
  (:require [cljs.analyzer :as ana]
            [cljs.env :as env]))

(defmacro analyze-ns []
  (let [ns-name  (-> &env :ns :name)
        analysis (get-in @env/*compiler* [::ana/namespaces ns-name])]
    `(quote ~analysis)))
