(ns app.renderer.core
  (:require [reagent.core :as r :refer [atom]]
            [reagent.dom :as rd]
            ["@uiw/react-codemirror" :default CodeMirror]
            ["@replit/codemirror-minimap" :refer [showMinimap]]
            [app.renderer.emacs :refer [with-emacs]]))

(with-emacs
  (message "Startup"))

(enable-console-print!)

(defonce code (atom "console.log('hello from cljs');"))

(defn create-minimap [_view]
  (let [dom (js/document.createElement "div")]
    #js {:dom dom}))

(def minimap-extension
  (.compute showMinimap #js ["doc"]
            (fn [_state]
              #js {:create      create-minimap
                   :displayText "blocks"
                   :showOverlay "always"})))

(defn editor []
  [:> CodeMirror
   {:height     "100%"
    :value      @code
    :extensions #js [minimap-extension]
    :onChange   (fn [value _ev]
                  (reset! code value))}])

(defn root-component []
  [editor])

(defn ^:dev/after-load start! []
  (rd/render
   [root-component]
   (js/document.getElementById "app-container")))
