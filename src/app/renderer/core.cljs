(ns app.renderer.core
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [garden.core :as garden]
            [reagent.core :as r]
            [reagent.dom.client :as rdc]
            ["@uiw/react-codemirror" :default CodeMirror]
            ["@replit/codemirror-minimap" :refer [showMinimap]]
            [app.renderer.emacs :refer [with-emacs]]))

(enable-console-print!)

(let [express (js/require "express")
      server (-> ^js (express)
                 (.use (.json express))
                 (.post "/code"
                        (fn [req res]
                          (let [next-code (some-> req .-body (aget "code"))]
                            (if (string? next-code)
                              (do
                                (.json res (clj->js {:ok true
                                                     :code next-code})))
                              (-> res
                                  (.status 400)
                                  (.json (clj->js {:ok false
                                                   :error "Expected JSON body with string field `code`."})))))))
                 (.listen 0))]
  (.on server "listening"
       (fn []
         (js/console.log (str "Listening for Emacs callbacks on" (.. server address -port)))

         (with-emacs
           (defconst newmacs-port 0)

           (message "Newmacs connected")

           (defvar newmacs-objects (make-hash-table :weakness 'value)
             "Hashtable indexed by sxhash-eq. Acts as an obarray of passed to ClojureScript so they can be retreived.")))))

(defonce code (r/atom "console.log('hello from cljs');"))

(def user-chrome-location
  (.join (js/require "path")
         (.homedir (js/require "os"))
         ".newmacs"))

(defn user-chrome []
  (let [css (if (str/blank? source)
              ""
              (try
                (apply garden/css (reader/read-string (.readFileSync (js/require "fs") user-chrome-location "utf8")))
                (catch :default error
                  (if (not= "ENOENT" (.-code error))
                    (js/console.error "Could not load ~/.newmacs" error))
                  "")))]
    (fn []
      [:style#user-chrome css])))

(def minimap-extension
  (.compute showMinimap #js ["doc"]
            (fn [_state]
              #js {:create      (fn [_view]
                                  (let [dom (js/document.createElement "div")]
                                    #js {:dom dom}))
                   :displayText "blocks"
                   :showOverlay "always"})))

(defn editor []
  [:> CodeMirror
   {:height     "100%"
    :value      @code
    :extensions #js [minimap-extension]
    :onChange   (fn [value _ev]
                  (reset! code value))}])

(defn root []
  [:<>
   [user-chrome]
   [editor]])

(defn start! []
  (rdc/render (rdc/create-root
               (js/document.getElementById "app-container"))
              [root]))
