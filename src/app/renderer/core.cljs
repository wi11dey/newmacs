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

(let [token (-> (js/require "crypto")
                (.randomBytes 128)
                (.toString "base64url"))
      express (js/require "express")
      server (-> ^js (express)
                 (.use (.text express #js {:type "application/edn"}))
                 (.listen 0))]
  (.on server "listening"
       (fn []
         (let [port (.. server address -port)]
           (js/console.log (str "Listening for Emacs callbacks on" port))
           (with-emacs
             (defconst newmacs-port ~port)
             (defconst newmacs-token ~token)

             (message "Newmacs connected on port %d" newmacs-port)

             (defun newmacs-new-buffer ())

             (add-hook 'after-change-major-mode-hook 'newmacs-new-buffer))))))

(defonce code (r/atom "console.log('hello from cljs');"))

(def user-chrome-location
  (.join (js/require "path")
         (.homedir (js/require "os"))
         ".newmacs"))

(defn user-chrome []
  (let [css (try
              (apply garden/css (reader/read-string (.readFileSync (js/require "fs") user-chrome-location "utf8")))
              (catch :default error
                (if (not= "ENOENT" (.-code error))
                  (js/console.error "Could not load ~/.newmacs" error))
                ""))]
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
  (-> (js/document.getElementById "app-container")
      (rdc/create-root)
      (rdc/render [root])))
