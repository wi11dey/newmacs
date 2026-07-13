(ns app.renderer.core
  (:require [reagent.core :as r :refer [atom]]
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

(defonce code (atom "console.log('hello from cljs');"))

(def user-chrome-location
  (.join (js/require "path")
         (.homedir (js/require "os"))
         ".newmacs"))

(defn user-chrome []
  (let [css (atom "")]
    ;; TODO do this without a class
    (r/create-class
     {:display-name "user-chrome"

      :component-did-mount
      (fn [this]
        (let [fs (js/require "fs")
              path (js/require "path")
              timer (atom nil)
              reload (fn []
                       (.readFile fs user-chrome-location "utf8"
                                  (fn [error contents]
                                    (cond
                                      (nil? error) (reset! css contents)
                                      (= "ENOENT" (.-code error)) (reset! css "")
                                      :else (js/console.error
                                             "Could not load ~/.newmacs"
                                             error)))))
              schedule-reload (fn []
                                (when-let [pending @timer]
                                  (js/clearTimeout pending))
                                (reset! timer (js/setTimeout reload 50)))
              watcher (.watch fs (.dirname path user-chrome-location)
                              (fn [_event changed-file]
                                (when (or (nil? changed-file)
                                          (= (.basename path user-chrome-location)
                                             (str changed-file)))
                                  (schedule-reload))))]
          (reload)
          (aset this "userChromeWatcher" watcher)
          (aset this "userChromeTimer" timer)))

      :component-will-unmount
      (fn [this]
        (some-> (aget this "userChromeWatcher") .close)
        (when-let [pending (some-> (aget this "userChromeTimer") deref)]
          (js/clearTimeout pending)))

      :reagent-render
      (fn []
        [:style#user-chrome @css])})))

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
